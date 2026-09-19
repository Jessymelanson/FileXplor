package com.filexplor.app.data.remote

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.time.Duration

private const val CONNECT_TIMEOUT_MS = 15_000
/** Idle time allowed on the data connection — not the length of a transfer. */
private const val DATA_TIMEOUT_MS = 120_000

/** The control connection's own patience, for servers slow to acknowledge. */
private const val CONTROL_TIMEOUT_MS = 60_000

/** Long enough that a busy server answering a NOOP is not read as a dead one. */
private const val KEEPALIVE_REPLY_TIMEOUT_MS = 10_000

/** How often to poke the control connection while a large file transfers. */
private const val CONTROL_KEEPALIVE_SECONDS = 30L

/** One megabyte. Uploads here are megabytes; the library default is kilobytes. */
private const val TRANSFER_BUFFER_BYTES = 1024 * 1024

/**
 * Plain FTP. Note that this protocol authenticates and transfers in the clear —
 * the UI says so at the point of setup, because a user picking "FTP" off a list
 * has no reason to know it differs from SFTP in that respect.
 */
class FtpRemoteClient(server: RemoteServer) : RemoteClient {

    private val ftp = FTPClient().apply {
        connectTimeout = CONNECT_TIMEOUT_MS
        controlEncoding = "UTF-8"
        try {
            connect(server.host, server.port)
        } catch (e: Exception) {
            throw RemoteException("Couldn't reach ${server.host}:${server.port}.", e)
        }

        // A connection can be established and still refused — too many users,
        // a banned address, a server shutting down. `connect` returns normally
        // for all of those, and without this the failure surfaces at `login`
        // as "check the username and password", sending the user to look at
        // credentials that were never the problem.
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            val reason = replyString?.trim()?.takeIf { it.isNotBlank() }
            disconnect()
            throw RemoteException(
                if (reason == null) {
                    "${server.host} refused the connection."
                } else {
                    "${server.host} refused the connection: $reason"
                }
            )
        }

        if (!login(server.username, server.password)) {
            val reason = replyString?.trim()?.takeIf { it.isNotBlank() }
            disconnect()
            throw RemoteException(
                if (reason == null) {
                    "FTP login failed. Check the username and password."
                } else {
                    "FTP login failed: $reason"
                }
            )
        }
        // Passive mode is what works from behind consumer NAT; active mode
        // needs the server to open a connection back to the phone.
        enterLocalPassiveMode()
        setFileType(FTP.BINARY_FILE_TYPE)

        // Everything below is sized for a large file. None of it mattered in
        // the apps this client was first written for — a contact card is two
        // kilobytes and transfers instantly — and all of it matters the moment
        // a video is in the queue.

        // How long the data connection may go *idle* before giving up. Not the
        // time a transfer is allowed to take. Thirty seconds sounded generous
        // until a video went up an uplink that stalls for half a minute at a
        // time on mobile, which is normal and not a failure.
        setDataTimeout(Duration.ofMillis(DATA_TIMEOUT_MS.toLong()))

        // The control connection needs its own patience. Left at the default
        // it inherits nothing in particular, and a server that pauses before
        // acknowledging a large upload closes the whole session.
        setDefaultTimeout(CONTROL_TIMEOUT_MS)
        soTimeout = CONTROL_TIMEOUT_MS

        // A NOOP every half minute while data moves, so the control connection
        // is not idle for the length of a video. The reply timeout is the
        // subtle half: commons-net reads those NOOP replies back, and at the
        // default second it treats a server that answers slowly as a dead
        // connection — surfacing as a read timeout on a transfer that was
        // going perfectly well.
        setControlKeepAliveTimeout(Duration.ofSeconds(CONTROL_KEEPALIVE_SECONDS))
        setControlKeepAliveReplyTimeout(Duration.ofMillis(KEEPALIVE_REPLY_TIMEOUT_MS.toLong()))

        bufferSize = TRANSFER_BUFFER_BYTES
    }

    override fun list(path: String): List<RemoteEntry> =
        ftp.listFiles(path).orEmpty()
            // commons-net puts a null in the array for any LIST line its parser
            // could not make sense of — an unusual date format, a permission
            // string it doesn't know, a filename with a newline in it. Mapping
            // over that throws, and every caller of this treats a thrown listing
            // as an empty folder, so one odd line silently hides the entire
            // directory. Dropping the unparseable entries loses only those.
            .filterNotNull()
            .map { file ->
                RemoteEntry(
                    name = file.name,
                    isDirectory = file.isDirectory,
                    lastModified = file.timestamp?.timeInMillis ?: 0L,
                    size = file.size
                )
            }

    override fun read(path: String): ByteArray {
        val stream = ftp.retrieveFileStream(path)
            ?: throw RemoteException("Couldn't read $path.")
        try {
            return stream.use { it.readBytes() }
        } finally {
            // Every retrieveFileStream has to be closed out with
            // completePendingCommand or the control connection desynchronises
            // and every later call reads the wrong reply. In a `finally`
            // because the read is exactly what can throw -- a dropped socket
            // part way through a file used to skip this entirely and poison
            // the session for good, so one failed read turned into a server
            // that appeared to have nothing in it.
            settle()
        }
    }

    /**
     * Ends the transfer the control connection is waiting on.
     *
     * False means the exchange did not finish the way commons-net expected. It
     * is not always a fault: a reader that stopped early -- a preview capped at
     * a few megabytes, a cancelled download -- leaves the server sending, and
     * the 426 it answers with lands here. Either way this client's control
     * connection is now out of step with the server, and the honest repair is
     * to drop it. RemoteFileSource sees a dead socket on the next call and
     * reconnects, which costs one round trip and is the only outcome that is
     * correct in both cases.
     *
     * Not thrown from. This runs on the way out of a read, often from a
     * `finally`, and throwing here would replace whatever really happened.
     */
    private fun settle() {
        val settled = runCatching { ftp.completePendingCommand() }.getOrDefault(false)
        if (!settled) runCatching { ftp.disconnect() }
    }

    /**
     * The remote file as a stream, finishing the FTP transaction on close.
     *
     * FTP is the awkward one. `retrieveFileStream` opens a data connection and
     * leaves the control connection mid-command until `completePendingCommand`
     * runs; skip it and every later call on this client fails, having read the
     * wrong reply. So close has to do both, and in that order.
     */
    override fun openRead(path: String): InputStream {
        val stream = ftp.retrieveFileStream(path)
            ?: throw RemoteException("Couldn't read $path.")
        return object : FilterInputStream(stream) {

            /**
             * Closed once, however many times it is asked.
             *
             * A copy used to close this twice: the destination's write() closes
             * whatever stream it is handed, and the loop that opened it closed
             * it again on the way out. The first close is the real one; the
             * second ran completePendingCommand() with nothing pending, which
             * then sat on the control connection waiting for a reply that was
             * never coming until the socket timeout a minute later -- once per
             * file. FileOperations no longer double-closes, and this makes sure
             * nothing else can either.
             */
            private var closed = false

            override fun close() {
                if (closed) return
                closed = true
                try { super.close() } finally { settle() }
            }
        }
    }

    override fun write(path: String, bytes: ByteArray) {
        val ok = ByteArrayInputStream(bytes).use { ftp.storeFile(path, it) }
        if (!ok) throw RemoteException("Couldn't write $path. The server rejected it.")
    }

    /** FTP takes a stream natively, so nothing is buffered here at all. */
    override fun write(path: String, source: InputStream, length: Long) {
        val ok = source.use { ftp.storeFile(path, it) }
        if (!ok) {
            val reply = ftp.replyString?.trim()?.takeIf { it.isNotBlank() }
            throw RemoteException(
                if (reply == null) {
                    "The server rejected $path."
                } else {
                    "The server rejected $path: $reply"
                }
            )
        }
    }

    /**
     * RMD for a directory, DELE for a file, and the answer is checked.
     *
     * commons-net reports a refused command by returning false rather than by
     * throwing, so the old one-liner reported every failed delete as a success.
     * On a move that is the bad direction to be wrong in: the originals were
     * announced as removed while they were still sitting there.
     */
    override fun delete(path: String, isDirectory: Boolean) {
        val ok = if (isDirectory) ftp.removeDirectory(path) else ftp.deleteFile(path)
        if (ok) return
        val reply = ftp.replyString?.trim()?.takeIf { it.isNotBlank() }
        val name = path.trimEnd('/').substringAfterLast('/')
        throw RemoteException(
            if (reply == null) "Couldn't delete $name." else "Couldn't delete $name: $reply"
        )
    }

    /**
     * Whether [path] is a directory, asked the only way FTP reliably answers.
     *
     * `LIST` cannot be used for this. Run against a directory it returns that
     * directory's *contents*, so an empty folder and a folder that does not
     * exist both come back as nothing at all — and the caller then tries to
     * create a folder that is already sitting there. `CWD` distinguishes them:
     * it succeeds only for a directory that exists, whatever is or isn't in it.
     *
     * The working directory is put back afterwards, because everything else
     * here addresses files by absolute path and would break if this quietly
     * moved the session somewhere else.
     */
    private fun directoryExists(path: String): Boolean = try {
        val previous = ftp.printWorkingDirectory()
        val found = ftp.changeWorkingDirectory(path)
        if (previous != null) ftp.changeWorkingDirectory(previous)
        found
    } catch (e: Exception) {
        false
    }

    /** Whether a plain file sits at [path], asked by listing its parent — the
     *  one query every server answers the same way. `LIST` aimed straight at a
     *  file is widely but not universally supported. */
    private fun fileExists(path: String): Boolean = try {
        val name = path.trimEnd('/').substringAfterLast('/')
        ftp.listFiles(path.parentPath()).orEmpty().any { it.name == name && !it.isDirectory }
    } catch (e: Exception) {
        false
    }

    override fun exists(path: String): Boolean = directoryExists(path) || fileExists(path)

    override fun rename(fromPath: String, toPath: String) {
        if (!ftp.rename(fromPath, toPath)) {
            throw RemoteException("Couldn't rename to ${toPath.substringAfterLast('/')}.")
        }
    }

    override fun makeDirectory(path: String) {
        // A server that already has the folder answers 550, which is not an
        // error from the caller's point of view — hence the directory check
        // rather than trusting the return code.
        if (ftp.makeDirectory(path) || directoryExists(path)) return

        // The server's own words are worth passing on. "Couldn't create the
        // folder" alone gives the user nothing to act on, where "553 Permission
        // denied" or "550 No such directory" names the actual problem.
        val reply = ftp.replyString?.trim()?.takeIf { it.isNotBlank() }
        throw RemoteException(
            if (reply == null) {
                "Couldn't create the folder $path on the server."
            } else {
                "Couldn't create the folder $path on the server: $reply"
            }
        )
    }

    override fun close() {
        try {
            if (ftp.isConnected) {
                ftp.logout()
                ftp.disconnect()
            }
        } catch (e: Exception) {
            // Nothing useful to do while tearing a connection down.
        }
    }
}
