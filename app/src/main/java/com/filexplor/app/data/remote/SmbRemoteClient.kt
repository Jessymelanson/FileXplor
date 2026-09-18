package com.filexplor.app.data.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.FilterInputStream
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** Bigger than the 8 KB default, for files that are not two-kilobyte text. */
private const val SMB_COPY_BUFFER_BYTES = 64 * 1024

/** Idle time allowed on the socket — not the length of a transfer. */
private const val SOCKET_TIMEOUT_MS = 120_000

/** How long one read, write or transact request may take to come back. */
private const val TRANSACT_TIMEOUT_MS = 120_000

/**
 * SMB2/3 via smbj.
 *
 * SMB addresses everything relative to a named share and uses backslash-
 * separated paths, so every path crossing this boundary gets converted — the
 * rest of the app speaks in ordinary forward-slash paths and knows nothing
 * about shares.
 */
class SmbRemoteClient(server: RemoteServer) : RemoteClient {

    /**
     * Built with timeouts, because the defaults have none worth relying on.
     *
     * smbj will wait on a socket indefinitely out of the box, so a share that
     * goes away mid-listing — a laptop closed, a NAS asleep, wifi dropped —
     * left the operation hanging with nothing but Stop to end it, and the
     * connection-lost retry in RemoteFileSource never got its chance because
     * nothing ever threw. Both numbers match the other two protocols: patient
     * enough for a stalled mobile uplink, short enough to give up on a machine
     * that has gone.
     */
    private val client = SMBClient(
        SmbConfig.builder()
            .withSoTimeout(SOCKET_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .withTimeout(TRANSACT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    )
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    init {
        try {
            val conn = client.connect(server.host, server.port)
            connection = conn

            // A username of the form DOMAIN\user is common on Windows shares.
            val domain = server.username.substringBefore('\\', "")
            val user = server.username.substringAfter('\\')
            val auth = AuthenticationContext(user, server.password.toCharArray(), domain.ifBlank { null })

            val sess = conn.authenticate(auth)
            session = sess

            val shareName = server.share.trim().trim('/', '\\')
            if (shareName.isEmpty()) {
                close()
                throw RemoteException("An SMB connection needs a share name.")
            }
            share = sess.connectShare(shareName) as? DiskShare
                ?: run {
                    close()
                    throw RemoteException("\"$shareName\" isn't a disk share on this server.")
                }
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            close()
            throw RemoteException(
                "Couldn't connect to \\\\${server.host}\\${server.share}. Check the host, " +
                    "share name, and credentials.",
                e
            )
        }
    }

    private fun requireShare(): DiskShare =
        share ?: throw RemoteException("The connection is closed.")

    /** SMB wants backslashes and no leading separator. */
    private fun smbPath(path: String): String =
        path.trim('/').replace('/', '\\')

    override fun list(path: String): List<RemoteEntry> =
        requireShare().list(smbPath(path))
            .filter { it.fileName != "." && it.fileName != ".." }
            .map { info ->
                val isDirectory =
                    (info.fileAttributes and FILE_ATTRIBUTE_DIRECTORY) == FILE_ATTRIBUTE_DIRECTORY
                RemoteEntry(
                    name = info.fileName,
                    isDirectory = isDirectory,
                    lastModified = info.lastWriteTime?.toEpochMillis() ?: 0L,
                    size = info.endOfFile
                )
            }

    override fun read(path: String): ByteArray {
        val file = requireShare().openFile(
            smbPath(path),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return file.use { it.inputStream.use { stream -> stream.readBytes() } }
    }

    /**
     * The remote file as a stream, with the SMB handle closed behind it.
     *
     * The handle is what holds the share open; the stream is a view onto it.
     * Closing only the stream leaves the file open on the server, which on a
     * Windows share is enough to stop anyone else writing to it.
     */
    override fun openRead(path: String): InputStream {
        val file = requireShare().openFile(
            smbPath(path),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return object : FilterInputStream(file.inputStream) {
            // Closed once. The destination of a copy closes the stream it is
            // handed and the copy closes it again, and a second close on an
            // SMB handle the server has already released is an error report
            // about a file that transferred perfectly well.
            private var closed = false

            override fun close() {
                if (closed) return
                closed = true
                try { super.close() } finally { file.close() }
            }
        }
    }

    override fun write(path: String, bytes: ByteArray) {
        val file = requireShare().openFile(
            smbPath(path),
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            // OVERWRITE_IF truncates an existing file, which is what rewriting
            // one needs — otherwise a shorter write would leave the tail of the
            // old contents hanging off the end of the new.
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )
        file.use { it.outputStream.use { stream -> stream.write(bytes) } }
    }

    override fun write(path: String, source: InputStream, length: Long) {
        val file = requireShare().openFile(
            smbPath(path),
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )
        file.use { handle ->
            handle.outputStream.use { out ->
                source.use { it.copyTo(out, SMB_COPY_BUFFER_BYTES) }
            }
        }
    }

    override fun delete(path: String, isDirectory: Boolean) {
        val share = requireShare()
        val target = smbPath(path)
        // Not recursive: emptying the folder first is FileOperations' job, and
        // it is the half that can be reported and stopped. A recursive delete
        // here would be one opaque call that either works or does not.
        if (isDirectory) share.rmdir(target, false) else share.rm(target)
    }

    override fun exists(path: String): Boolean =
        try {
            val target = smbPath(path)
            val disk = requireShare()
            // A directory is not a file to SMB. Asking only about files reports
            // every existing folder as missing, so the caller tries to create
            // it again on every single sync.
            disk.fileExists(target) || disk.folderExists(target)
        } catch (e: Exception) {
            false
        }

    override fun rename(fromPath: String, toPath: String) {
        val file = requireShare().openFile(
            smbPath(fromPath),
            EnumSet.of(AccessMask.GENERIC_ALL),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        file.use { it.rename(smbPath(toPath), true) }
    }

    override fun makeDirectory(path: String) {
        val target = smbPath(path)
        val disk = requireShare()
        // fileExists() is false for a directory, so the caller's exists() check
        // cannot see one that is already there — this has to test for itself.
        if (disk.folderExists(target)) return
        try {
            disk.mkdir(target)
        } catch (e: Exception) {
            if (!disk.folderExists(target)) {
                throw RemoteException("Couldn't create the folder $path on the share.", e)
            }
        }
    }

    override fun close() {
        try {
            share?.close()
        } catch (e: Exception) {
            // Teardown failures aren't actionable.
        }
        share = null
        try {
            session?.close()
        } catch (e: Exception) {
        }
        session = null
        try {
            connection?.close()
        } catch (e: Exception) {
        }
        connection = null
        try {
            client.close()
        } catch (e: Exception) {
        }
    }

    private companion object {
        const val FILE_ATTRIBUTE_DIRECTORY = 0x10L
    }
}
