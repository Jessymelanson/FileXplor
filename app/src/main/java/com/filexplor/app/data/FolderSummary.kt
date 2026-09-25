package com.filexplor.app.data

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * What a folder holds, all the way down: the numbers behind its Details.
 *
 * Hidden files are counted. The point of the number is how much room the
 * folder takes and how much a copy of it would move, and a dotfile takes room
 * whether or not it is on show.
 */
data class FolderSummary(
    val files: Int,
    val folders: Int,
    val bytes: Long,
    /** Folders that could not be listed, so whose contents are missing. */
    val unreadable: Int,
    /** False while the count is still running. */
    val complete: Boolean
)

/**
 * Walks [path] and totals it, reporting as it goes.
 *
 * Breadth first, one listing per folder -- on a server that is a round trip
 * each, so a large tree takes a while, and the running totals are what keep
 * the dialog from looking stuck in the meantime. Cancelled between listings,
 * which is where all the waiting is.
 *
 * Depth is capped as a guard against a folder that contains itself through a
 * link; nothing real is a hundred folders deep.
 */
suspend fun summariseFolder(
    source: FileSource,
    path: String,
    onProgress: (FolderSummary) -> Unit
): FolderSummary {
    var files = 0
    var folders = 0
    var bytes = 0L
    var unreadable = 0
    var reportedAt = 0L
    val pending = ArrayDeque<Pair<String, Int>>().apply { add(path to 0) }

    while (pending.isNotEmpty()) {
        coroutineContext.ensureActive()
        val (dir, depth) = pending.removeFirst()
        val children = try {
            source.list(dir)
        } catch (e: Exception) {
            unreadable++
            continue
        }
        for (child in children) {
            if (child.isDirectory) {
                folders++
                if (depth < MAX_DEPTH) pending.add(child.path to depth + 1)
            } else {
                files++
                bytes += child.size.coerceAtLeast(0L)
            }
        }
        val now = System.currentTimeMillis()
        if (now - reportedAt >= REPORT_INTERVAL_MS) {
            reportedAt = now
            onProgress(FolderSummary(files, folders, bytes, unreadable, complete = false))
        }
    }
    return FolderSummary(files, folders, bytes, unreadable, complete = true)
}

private const val MAX_DEPTH = 100
private const val REPORT_INTERVAL_MS = 150L
