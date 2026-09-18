package com.filexplor.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A folder the user asked to keep.
 *
 * The path is what it is; [label] is only a name to show, so renaming a
 * shortcut never moves or touches anything on disk. [serverId] is null for the
 * phone's own storage and set for a folder on a saved server — a remote path
 * means nothing without the server it belongs to, and storing the two apart
 * would be storing half an address.
 */
data class Favourite(
    val label: String,
    val path: String,
    val serverId: String? = null
) {
    /**
     * Identity: the same folder on the same source, whatever it is called.
     *
     * A shortcut is the folder, not the name, so the name cannot be part of
     * this — otherwise renaming one would make a second one appear. The server
     * id is a UUID and contains no bar, so splitting at the first one would
     * recover both halves if anything ever needed to; nothing does, and this is
     * only ever compared.
     */
    val key: String get() = "${serverId.orEmpty()}|${path.trimEnd('/')}"
}

/**
 * The folders on FileXplor's own home screen, in the order they are shown.
 *
 * A JSON array in preferences, matching [com.filexplor.app.data.remote.ServerStore]
 * — the app already reads and writes one list this way and a second mechanism
 * would only be a second thing to get wrong. Order is the array's order, which
 * is why the reordering here rewrites the whole list rather than editing one
 * entry: position is not a property of an entry, it is a property of the list.
 *
 * Nothing here is secret, so unlike the server list none of it is encrypted.
 */
class FavouriteStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("filexplor_favourites", Context.MODE_PRIVATE)

    fun all(): List<Favourite> {
        val raw = prefs.getString(KEY_FAVOURITES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val path = entry.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Favourite(
                    label = entry.optString("label").ifBlank { path.trimEnd('/').substringAfterLast('/') },
                    path = path,
                    serverId = entry.optString("server").takeIf { it.isNotBlank() }
                )
            // Two rows with the same key crash a LazyColumn outright, and the
            // only thing standing between the list and a duplicate is [add]
            // -- which is not enough, because the file can also arrive from a
            // restored backup or an older version of this app. Deduplicated on
            // the way out, where it protects every caller.
            }.distinctBy { it.key }
        } catch (e: Exception) {
            // A preferences file that has been corrupted or hand-edited loses
            // the shortcuts, which are re-addable in two taps. Throwing here
            // would take the home screen down with it.
            emptyList()
        }
    }

    fun contains(path: String, serverId: String?): Boolean {
        val key = Favourite("", path, serverId).key
        return all().any { it.key == key }
    }

    /** Adds [favourite] at the end. False if that folder is already saved. */
    fun add(favourite: Favourite): Boolean {
        val existing = all()
        if (existing.any { it.key == favourite.key }) return false
        write(existing + favourite)
        return true
    }

    fun remove(key: String) {
        write(all().filterNot { it.key == key })
    }

    fun rename(key: String, label: String) {
        write(all().map { if (it.key == key) it.copy(label = label) else it })
    }

    /**
     * Shifts one entry [by] places, and answers whether it moved.
     *
     * Clamped rather than wrapped: an entry already at the top that jumped to
     * the bottom on one more tap of "Move up" would be a surprise every time.
     * The false return is what stops the caller redrawing a list that has not
     * changed.
     */
    fun move(key: String, by: Int): Boolean {
        val list = all().toMutableList()
        val from = list.indexOfFirst { it.key == key }
        if (from < 0) return false
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (to == from) return false
        list.add(to, list.removeAt(from))
        write(list)
        return true
    }

    private fun write(favourites: List<Favourite>) {
        val array = JSONArray()
        favourites.forEach { favourite ->
            array.put(
                JSONObject().apply {
                    put("label", favourite.label)
                    put("path", favourite.path)
                    favourite.serverId?.let { put("server", it) }
                }
            )
        }
        prefs.edit().putString(KEY_FAVOURITES, array.toString()).apply()
    }

    private companion object {
        const val KEY_FAVOURITES = "favourites"
    }
}
