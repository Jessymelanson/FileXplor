package com.filexplor.app.data.remote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists configured servers. The password field is encrypted through
 * [CredentialCipher] before it's written and decrypted on read, so the
 * preferences file never holds a usable credential.
 *
 * Learned SSH host key fingerprints live here too, keyed by server, which is
 * what makes the SFTP client's trust-on-first-use check meaningful across runs.
 */
class ServerStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("filexplor_servers", Context.MODE_PRIVATE)

    /**
     * Every configured server, **without** its password.
     *
     * Decrypting is an Android Keystore round trip per server — a binder call
     * into the keystore daemon, and slow enough to be felt. Almost every caller
     * only wants a name, a count or a host to show, so none of them should pay
     * for it: the app used to decrypt every password twice on the main thread
     * before its first frame, purely to draw a label.
     *
     * [find] is the one that hands out a usable credential.
     */
    fun all(): List<RemoteServer> = rawEntries().mapNotNull { fromJson(it, withPassword = false) }

    /**
     * One server, with its password decrypted — the form needed to connect, and
     * to populate the edit dialog. Only this server's password is unwrapped.
     */
    fun find(id: String): RemoteServer? {
        val entry = rawEntries().firstOrNull { it.optString("id") == id } ?: return null
        return fromJson(entry, withPassword = true)
    }

    private fun rawEntries(): List<JSONObject> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Writes one server, leaving the others exactly as they were on disk.
     *
     * The obvious version of this — `all().filterNot { same id } + server` —
     * silently destroys data. [all] returns every server with its password
     * blanked, by design and for good reason, so round-tripping the list
     * through it and writing the result back re-encrypts an empty string over
     * every other server's credential. One server configured hides it
     * completely; two is all it takes to lose one.
     *
     * So the untouched entries are carried across as the JSON already stored,
     * never as decoded models. The only object that gets rebuilt is the one
     * actually being saved.
     */
    fun save(server: RemoteServer): RemoteServer {
        val withId = if (server.id.isBlank()) server.copy(id = UUID.randomUUID().toString()) else server
        val entries = rawEntries()
        val array = JSONArray()
        entries.forEach { entry ->
            if (entry.optString("id") != withId.id) array.put(entry)
        }
        array.put(toJson(withId))

        // A server edited to point somewhere else is a different machine, so
        // the SSH key learned from the old one is forgotten. Kept, it made the
        // first connection to the new address fail with the warning meant for
        // an intercepted connection, and the only way out was deleting the
        // server and adding it again.
        val previous = entries.firstOrNull { it.optString("id") == withId.id }
        val moved = previous != null && (
            previous.optString("host") != withId.host ||
                previous.optInt("port") != withId.port ||
                previous.optString("protocol") != withId.protocol.name
            )

        val edit = prefs.edit().putString(KEY_SERVERS, array.toString())
        if (moved) edit.remove(hostKeyKey(withId.id))
        edit.apply()
        return withId
    }

    /** Removes one server, and leaves every other one's password intact — see [save]. */
    fun delete(id: String) {
        val array = JSONArray()
        rawEntries().forEach { entry ->
            if (entry.optString("id") != id) array.put(entry)
        }
        prefs.edit()
            .putString(KEY_SERVERS, array.toString())
            .remove(hostKeyKey(id))
            .apply()
    }

    fun hostKey(serverId: String): String? = prefs.getString(hostKeyKey(serverId), null)

    fun setHostKey(serverId: String, fingerprint: String) {
        prefs.edit().putString(hostKeyKey(serverId), fingerprint).apply()
    }

    private fun toJson(server: RemoteServer): JSONObject = JSONObject().apply {
        put("id", server.id)
        put("protocol", server.protocol.name)
        put("label", server.label)
        put("host", server.host)
        put("port", server.port)
        put("username", server.username)
        put("password", CredentialCipher.encrypt(server.password) ?: "")
        put("share", server.share)
        put("basePath", server.basePath)
    }

    private fun fromJson(json: JSONObject, withPassword: Boolean): RemoteServer? {
        return try {
            val storedPassword = if (withPassword) json.optString("password") else ""
            RemoteServer(
                id = json.getString("id"),
                protocol = RemoteProtocol.valueOf(json.getString("protocol")),
                label = json.optString("label"),
                host = json.getString("host"),
                port = json.optInt("port"),
                username = json.optString("username"),
                // A password that won't decrypt (Keystore key lost through a
                // restore onto another device) becomes empty rather than
                // breaking the whole list — the user is prompted to re-enter it.
                password = if (storedPassword.isBlank()) {
                    ""
                } else {
                    CredentialCipher.decrypt(storedPassword).orEmpty()
                },
                share = json.optString("share"),
                basePath = json.optString("basePath")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun hostKeyKey(serverId: String) = "hostkey_$serverId"

    private companion object {
        const val KEY_SERVERS = "servers"
    }
}

/** Opens a live connection for a server. Split out so the repository depends on
 *  the concept rather than on three protocol libraries. */
class RemoteConnector(private val store: ServerStore) {

    fun connect(server: RemoteServer): RemoteClient = when (server.protocol) {
        RemoteProtocol.FTP -> FtpRemoteClient(server)
        RemoteProtocol.SMB -> SmbRemoteClient(server)
        RemoteProtocol.SFTP -> SftpRemoteClient(
            server = server,
            knownHostKey = store.hostKey(server.id),
            onHostKeyLearned = { fingerprint -> store.setHostKey(server.id, fingerprint) }
        )
    }
}
