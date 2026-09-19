package com.filexplor.app.data.remote

enum class RemoteProtocol(val label: String, val defaultPort: Int, val usesShare: Boolean) {
    SFTP("SFTP", 22, false),
    FTP("FTP", 21, false),
    SMB("SMB", 445, true)
}

/**
 * One configured network location. [password] is held in plaintext only while
 * in memory — [ServerStore] encrypts it before it ever reaches disk.
 *
 * [share] applies to SMB alone, where a connection targets a named share rather
 * than a bare path.
 */
data class RemoteServer(
    val id: String,
    val protocol: RemoteProtocol,
    val label: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val share: String = "",
    val basePath: String = ""
) {
    val displayName: String
        get() = label.ifBlank { "${protocol.label} · $host" }
}
