package com.filexplor.app.data.remote

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.FilterInputStream
import java.io.InputStream
import java.security.PublicKey
import java.util.EnumSet

private const val CONNECT_TIMEOUT_MS = 15_000
/**
 * Idle time allowed on the transport, not the length of a transfer.
 *
 * Thirty seconds until a large file went up a mobile uplink that stalls for
 * half a minute at a time, which is ordinary and not a failure. Matched to the
 * FTP client's data timeout so the three protocols are equally patient once
 * connected; the connect timeout above stays short, because a host that is not
 * answering at all should be reported quickly.
 */
private const val READ_TIMEOUT_MS = 120_000

/** Bigger than the 8 KB default, for files that are not two-kilobyte text. */
private const val COPY_BUFFER_BYTES = 64 * 1024

/**
 * SFTP over SSH.
 *
 * Host keys are handled trust-on-first-use: the fingerprint seen on the first
 * successful connection is remembered, and a later connection offering a
 * different key is refused. That is weaker than shipping known_hosts, but it is
 * vastly better than the usual shortcut of accepting every key presented —
 * which would silently hand any machine on the path a working man-in-the-middle
 * against the user's password and data. A changed key surfaces as an error the
 * user has to resolve deliberately.
 */
class SftpRemoteClient(
    server: RemoteServer,
    knownHostKey: String?,
    onHostKeyLearned: (String) -> Unit
) : RemoteClient {

    private val ssh: SSHClient

    // Nullable because close() runs from the failure paths below, before this
    // has been established — a non-null val would read as null there anyway and
    // NPE on teardown, masking the real connection error.
    private var sftpOrNull: SFTPClient? = null

    private val sftp: SFTPClient
        get() = sftpOrNull ?: throw RemoteException("The connection is closed.")

    init {
        ssh = SSHClient()
        ssh.connectTimeout = CONNECT_TIMEOUT_MS
        ssh.timeout = READ_TIMEOUT_MS
        ssh.addHostKeyVerifier(TofuVerifier(knownHostKey, onHostKeyLearned))
        try {
            ssh.connect(server.host, server.port)
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw RemoteException("Couldn't reach ${server.host}:${server.port}.", e)
        }
        try {
            ssh.authPassword(server.username, server.password)
        } catch (e: Exception) {
            close()
            throw RemoteException("SFTP login failed. Check the username and password.", e)
        }
        sftpOrNull = try {
            ssh.newSFTPClient()
        } catch (e: Exception) {
            close()
            throw RemoteException("Connected, but couldn't start an SFTP session.", e)
        }
    }

    override fun list(path: String): List<RemoteEntry> =
        sftp.ls(path).map { info ->
            RemoteEntry(
                name = info.name,
                isDirectory = info.isDirectory,
                // SFTP reports mtime in seconds.
                lastModified = info.attributes.mtime * 1000L,
                size = info.attributes.size
            )
        }

    override fun read(path: String): ByteArray =
        sftp.open(path).use { file ->
            file.RemoteFileInputStream().use { it.readBytes() }
        }

    /**
     * The remote file as a stream, with the handle closed behind it.
     *
     * `sftp.open` returns a handle the stream reads through, so closing the
     * stream alone would leak it. Wrapping means the caller's single `close()`
     * ends both, in that order.
     */
    override fun openRead(path: String): InputStream {
        val file = sftp.open(path)
        val stream = file.RemoteFileInputStream()
        return object : FilterInputStream(stream) {
            // Closed once. A copy hands this to the destination's write(),
            // which closes the stream it is given, and then closes it again
            // itself -- and a second SSH_FXP_CLOSE on a handle the server has
            // already released comes back as a failure that reads like the
            // file did.
            private var closed = false

            override fun close() {
                if (closed) return
                closed = true
                try { super.close() } finally { file.close() }
            }
        }
    }

    override fun write(path: String, bytes: ByteArray) {
        val modes = EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
        sftp.open(path, modes).use { file ->
            file.RemoteFileOutputStream().use { it.write(bytes) }
        }
    }

    override fun write(path: String, source: InputStream, length: Long) {
        val modes = EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
        sftp.open(path, modes).use { file ->
            file.RemoteFileOutputStream().use { out ->
                source.use { it.copyTo(out, COPY_BUFFER_BYTES) }
            }
        }
    }

    override fun delete(path: String, isDirectory: Boolean) {
        // rm is for files and rmdir is for directories; sshj throws rather than
        // silently doing the wrong thing, so a folder simply never deleted.
        if (isDirectory) sftp.rmdir(path) else sftp.rm(path)
    }

    override fun exists(path: String): Boolean =
        try {
            sftp.statExistence(path) != null
        } catch (e: Exception) {
            false
        }

    override fun rename(fromPath: String, toPath: String) {
        sftp.rename(fromPath, toPath)
    }

    override fun makeDirectory(path: String) {
        try {
            sftp.mkdir(path)
        } catch (e: Exception) {
            if (!exists(path)) {
                throw RemoteException("Couldn't create the folder $path on the server.", e)
            }
        }
    }

    override fun close() {
        try {
            sftpOrNull?.close()
        } catch (e: Exception) {
            // Teardown failures aren't actionable.
        }
        sftpOrNull = null
        try {
            ssh.disconnect()
        } catch (e: Exception) {
            // As above.
        }
    }

    private class TofuVerifier(
        private val knownHostKey: String?,
        private val onHostKeyLearned: (String) -> Unit
    ) : HostKeyVerifier {

        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val fingerprint = SecurityUtils.getFingerprint(key)
            if (knownHostKey.isNullOrBlank()) {
                onHostKeyLearned(fingerprint)
                return true
            }
            if (knownHostKey == fingerprint) return true
            throw RemoteException(
                "The server's SSH key has changed since you last connected. This can mean " +
                    "the server was rebuilt, or that something is intercepting the " +
                    "connection. Remove and re-add the server if you're sure it's expected."
            )
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }
}
