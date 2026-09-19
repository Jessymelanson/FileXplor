package com.filexplor.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One thing a copy, move, delete or download could not manage. */
data class TransferLogEntry(
    val at: Long,
    /** "Copying", "Moving", "Deleting", "Downloading" — the job it belonged to. */
    val operation: String,
    val path: String,
    val reason: String
)

/**
 * What failed, kept until the user says otherwise.
 *
 * A copy of four hundred photographs that reports "397 items copied, 3 failed"
 * has told the truth and been no help at all: the three that matter are the
 * ones not named, and the snackbar carrying that sentence is gone in four
 * seconds. Worse, the app now runs transfers with the screen off, so the
 * message can come and go while the phone is in a pocket.
 *
 * So the names are written down. On disk rather than in memory, because the
 * failure a user most wants to look up is the one from the transfer they
 * started before bed — and because a process killed mid-job is exactly the
 * case where nothing survives to be asked.
 *
 * Newest first, capped, and cleared only on request: this is a record of what
 * went wrong, and something that quietly tidied itself away would be no better
 * than the snackbar it replaces.
 */
class TransferLog(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("filexplor_transfer_log", Context.MODE_PRIVATE)

    fun all(): List<TransferLogEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val path = entry.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                TransferLogEntry(
                    at = entry.optLong("at"),
                    operation = entry.optString("op").ifBlank { "Transfer" },
                    path = path,
                    reason = entry.optString("why").ifBlank { "Unknown error" }
                )
            }
        } catch (e: Exception) {
            // A log that cannot be read is not worth taking the app down for.
            emptyList()
        }
    }

    /** How many, for the menu item that offers to show them. */
    fun count(): Int = all().size

    fun record(operation: String, failures: List<OperationResult.Failure>) {
        if (failures.isEmpty()) return
        val now = System.currentTimeMillis()
        add(failures.map { TransferLogEntry(now, operation, it.path, it.reason) })
    }

    fun record(operation: String, path: String, reason: String) {
        add(listOf(TransferLogEntry(System.currentTimeMillis(), operation, path, reason)))
    }

    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    /**
     * Newest first, and only ever the newest [MAX_ENTRIES].
     *
     * A folder the app has no permission to read can fail once per file for
     * thousands of files, and a log that kept all of them would be a slow read
     * of the same sentence over and over. The cap keeps the useful end.
     */
    private fun add(entries: List<TransferLogEntry>) {
        val kept = (entries + all()).take(MAX_ENTRIES)
        val array = JSONArray()
        kept.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("at", entry.at)
                    put("op", entry.operation)
                    put("path", entry.path)
                    put("why", entry.reason)
                }
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 200
    }
}
