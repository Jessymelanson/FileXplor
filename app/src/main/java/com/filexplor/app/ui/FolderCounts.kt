package com.filexplor.app.ui

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.filexplor.app.data.FileItem
import com.filexplor.app.data.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * How many things are directly inside a folder.
 *
 * Local folders only, and the reason is the same one that keeps thumbnails
 * local. A count is a directory listing, and on a server a listing is a round
 * trip: a folder holding thirty subfolders would mean thirty more LIST commands
 * to draw one screen, again on every scroll back. That is seconds of waiting
 * and a load the server did not ask for, to put a small number on an icon.
 *
 * On the phone the listing is a syscall — cheap, but not free, and not
 * something to do on the main thread while a list is scrolling. So it happens
 * off-thread, per row, and is remembered.
 */
object FolderCounts {

    /**
     * Bounded by entries rather than bytes, unlike the thumbnail cache.
     *
     * Here every value is one Int, so entries and memory are the same measure —
     * two thousand of them is a few tens of kilobytes and more folders than
     * anybody scrolls past in a session.
     */
    private val cache = LruCache<String, Int>(2000)

    /**
     * Keyed on the folder's own timestamp as well as its path.
     *
     * A directory's mtime changes whenever a direct child is added or removed,
     * which is exactly when the count it holds stops being true. Keying on the
     * path alone would show a stale number until the app was killed.
     */
    private fun keyOf(item: FileItem, withHidden: Boolean) =
        "${item.path}|${item.lastModified}|$withHidden"

    fun cached(item: FileItem, withHidden: Boolean): Int? = cache.get(keyOf(item, withHidden))

    /**
     * Counts one folder, or returns null where it cannot be read.
     *
     * Null and zero are kept apart deliberately. A folder the app has no
     * permission to open is not an empty folder, and badging it "0" would be
     * the app stating something it does not know.
     *
     * Hidden entries count only when hidden files are being shown. The badge
     * says how many things opening the folder will show, and a folder holding
     * one dotfile read "14" outside and "13 items" inside.
     */
    fun count(item: FileItem, withHidden: Boolean): Int? {
        val key = keyOf(item, withHidden)
        cache.get(key)?.let { return it }
        // list() rather than listFiles(): the names alone are wanted, and
        // building a File object per child to then count them is work thrown
        // away — on a folder of several thousand it is the whole cost.
        val names = runCatching { File(item.path).list() }.getOrNull() ?: return null
        val shown = if (withHidden) names.size else names.count { !it.startsWith(".") }
        cache.put(key, shown)
        return shown
    }
}

/**
 * The count for one folder row, loaded off the main thread.
 *
 * Returns the cached value on the first composition where there is one, so a
 * list scrolled back over does not blink its numbers away and fetch them again.
 */
@Composable
fun rememberFolderCount(item: FileItem, location: Location, withHidden: Boolean): Int? {
    if (!item.isDirectory || location != Location.Device) return null

    var count by remember(item.path, item.lastModified, withHidden) {
        mutableStateOf(FolderCounts.cached(item, withHidden))
    }
    LaunchedEffect(item.path, item.lastModified, withHidden) {
        if (count == null) {
            count = withContext(Dispatchers.IO) { FolderCounts.count(item, withHidden) }
        }
    }
    return count
}

/**
 * The count as it goes on the badge.
 *
 * Three characters at most. The badge sits on a thirty-point icon, and the
 * first version of this used "999+" — four characters, which came out wider
 * than the folder it was sitting on and hung off both edges. Thousands are
 * rounded instead: "1k", "42k". Past a thousand files the exact number is not
 * what anyone reads a badge for, and the order of magnitude is the message.
 */
fun formatCount(count: Int): String = when {
    count < 1000 -> count.toString()
    count < 100_000 -> "${count / 1000}k"
    else -> "99k+"
}
