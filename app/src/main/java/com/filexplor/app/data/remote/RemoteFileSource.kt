package com.filexplor.app.data.remote

import com.filexplor.app.data.FileItem
import com.filexplor.app.data.FileSource
import com.filexplor.app.data.joinPath
import java.io.EOFException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.SocketException

/**
 * A configured server, presented as a [FileSource].
 *
 * [RemoteClient] already speaks the six verbs against all three protocols; this
 * exists so the browser never has to know which of the two kinds of place it is
 * looking at.
 *
 * It also owns the connection's lifetime, which turns out to be the whole
 * difficulty. A file manager is a long session of short bursts — list a folder,
 * read a name, come back ten minutes later — and every server in the world
 * closes an idle connection before then. FTP has an idle timeout measured in
 * minutes, sshd has ClientAliveInterval, and an SMB session simply expires. The
 * first call after that does not fail politely: the socket is already gone at
 * the other end, so the write succeeds locally and the *next* read comes back
 * as "broken pipe".
 *
 * So the connection is opened lazily, and any operation that dies of a dead
 * socket is retried exactly once against a fresh one. From the outside the
 * connection looks permanent, which is what the rest of the app assumes.
 */
class RemoteFileSource(
    private val server: RemoteServer,
    private val connect: () -> RemoteClient
) : FileSource {

    private var client: RemoteClient? = null

    override val root: String = server.basePath.ifBlank { "/" }

    override val label: String = server.displayName

    /**
     * Runs [block] against a live client, reconnecting once if the old one had
     * quietly died.
     *
     * Only once. A server that refuses the second connection is down, or the
     * password is wrong, and retrying past that turns one clear error into a
     * long wait followed by the same error.
     *
     * Retrying is safe for everything here because a lost socket means the
     * command never reached the server: nothing was half-done to be repeated.
     * That is why the classifier below has to be narrow — a retry on a genuine
     * "no such file" would just ask twice and report the same thing, but a
     * retry on something that *did* land could do it twice.
     */
    private fun <T> withClient(block: (RemoteClient) -> T): T {
        val existing = client ?: connect().also { client = it }
        return try {
            block(existing)
        } catch (e: Throwable) {
            if (!isConnectionLost(e)) throw e
            runCatching { existing.close() }
            client = null
            val fresh = connect()
            client = fresh
            block(fresh)
        }
    }

    override fun list(path: String): List<FileItem> = withClient { client ->
        client.list(path).map { entry ->
            FileItem(
                name = entry.name,
                path = joinPath(path, entry.name),
                isDirectory = entry.isDirectory,
                size = if (entry.isDirectory) -1L else entry.size,
                lastModified = entry.lastModified
            )
        }
    }

    /**
     * One entry, found by listing its parent.
     *
     * None of the three protocols has a portable "stat this path" that reports
     * a size and a date the same way, but all three list a directory. The cost
     * is one listing of the parent, which is what the browser has usually just
     * done anyway.
     */
    override fun stat(path: String): FileItem? {
        val trimmed = path.trimEnd('/')
        val cut = trimmed.lastIndexOf('/')
        if (cut < 0) return null
        val parent = if (cut == 0) "/" else trimmed.substring(0, cut)
        val name = trimmed.substring(cut + 1)
        return runCatching { list(parent).firstOrNull { it.name == name } }.getOrNull()
    }

    override fun openRead(path: String): InputStream = withClient { it.openRead(path) }

    /**
     * Not retried past the first byte.
     *
     * The comment here used to say exactly that while the code did the
     * opposite: it went through the same retry wrapper as everything else, so
     * a connection dropped half way through an upload reconnected and called
     * write again -- with a stream that had already been partly consumed. The
     * server got whatever was left, the call returned normally, and the copy
     * counted it as a success. A truncated file with the right name and no
     * error against it is the worst outcome this app can produce.
     *
     * Reconnecting before anything has been read is still safe and still worth
     * having, because an idle connection that died between two files is the
     * common case. So the stream reports whether it has been touched.
     */
    override fun write(path: String, source: InputStream, length: Long) {
        val watched = FirstReadWatcher(source)
        val existing = client ?: connect().also { client = it }
        try {
            existing.write(path, watched, length)
        } catch (e: Throwable) {
            if (watched.started || !isConnectionLost(e)) throw e
            runCatching { existing.close() }
            client = null
            val fresh = connect()
            client = fresh
            fresh.write(path, source, length)
        }
    }

    /** Notes whether anything has been read, so a retry can know to refuse. */
    private class FirstReadWatcher(private val wrapped: InputStream) : InputStream() {
        var started = false
            private set

        override fun read(): Int = wrapped.read().also { if (it >= 0) started = true }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            wrapped.read(b, off, len).also { if (it > 0) started = true }

        override fun available(): Int = wrapped.available()
        override fun close() = wrapped.close()
    }

    /**
     * A dead socket is worth another go; a refused file is not.
     *
     * The same question [withClient] asks itself before reconnecting, exposed
     * so a copy can ask it about a failure that happened *outside* a single
     * call -- part way through reading a stream, where the retry wrapper has
     * already handed the stream over and cannot help.
     */
    override fun isTransient(error: Throwable): Boolean = isConnectionLost(error)

    override fun makeDirectory(path: String) {
        withClient { it.makeDirectory(path) }
    }

    override fun delete(path: String, isDirectory: Boolean) {
        withClient { it.delete(path, isDirectory) }
    }

    override fun rename(path: String, newName: String) {
        val trimmed = path.trimEnd('/')
        val cut = trimmed.lastIndexOf('/')
        val parent = if (cut <= 0) "/" else trimmed.substring(0, cut)
        withClient { it.rename(trimmed, joinPath(parent, newName)) }
    }

    override fun exists(path: String): Boolean = withClient { it.exists(path) }

    override fun close() {
        val open = client
        client = null
        runCatching { open?.close() }
    }

    internal companion object {

        /**
         * Whether this failure means the socket died rather than the request.
         *
         * Deliberately narrow, and checked by type before message. The three
         * protocol libraries each wrap their failures differently — sshj in a
         * TransportException, smbj in its own runtime type, commons-net in an
         * FTPConnectionClosedException — and every one of them ends up with a
         * SocketException or an EOFException somewhere in the chain when the
         * other end has gone away.
         *
         * The message check is the pragmatic half, for the wrappers that
         * swallow the cause and keep only the text. It is matched against a
         * short list of phrases that only ever describe a dead connection, and
         * not against anything a live server says about a file.
         */
        @JvmStatic
        fun isConnectionLost(error: Throwable): Boolean {
            var cause: Throwable? = error
            val seen = mutableSetOf<Throwable>()
            while (cause != null && seen.add(cause)) {
                when (cause) {
                    is SocketException,
                    is EOFException,
                    is InterruptedIOException -> return true
                }
                val message = cause.message?.lowercase()
                if (message != null && DEAD_SOCKET.any { message.contains(it) }) return true
                cause = cause.cause
            }
            return false
        }

        private val DEAD_SOCKET = listOf(
            "broken pipe",
            "connection reset",
            "connection closed",
            "connection aborted",
            "socket is closed",
            "socket closed",
            "stream closed",
            "not connected",
            "session is not connected",
            "connection is not open"
        )
    }
}
