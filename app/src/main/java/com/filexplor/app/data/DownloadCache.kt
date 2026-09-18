package com.filexplor.app.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Where a server file goes when it has to become a real file.
 *
 * Android hands a file to another app as a `content://` URI backed by a path,
 * and a file on a server has neither — so opening or sharing one means fetching
 * it here first. Keeping those copies is what lets the app say "you already
 * have this" rather than pulling a 40 MB APK down a second time.
 *
 * The first version of this named the copy after the file and nothing else,
 * which is wrong in a way that only shows up later: `/photos/report.pdf` and
 * `/docs/report.pdf` are one entry under that scheme, as is the same name on
 * two different servers. Opening the second would quietly overwrite the first,
 * and a badge reading "downloaded" would be pointing at somebody else's file.
 */
object DownloadCache {

    /**
     * The copy's location, whether or not it exists yet.
     *
     * The name of the folder carries the identity — server, full path, size and
     * timestamp — and the file inside keeps its real name, so whatever opens it
     * shows the name the user tapped rather than a hash. Size and timestamp are
     * in the key deliberately: a file replaced on the server gets a different
     * folder, so the old copy is never mistaken for the new one.
     */
    fun fileFor(context: Context, location: Location, item: FileItem): File =
        fileFor(rootIn(context), location, item)

    /**
     * The same, against a given directory.
     *
     * Split out so the naming rule can be tested without a device. The rule is
     * the part worth testing — everything else here is a file existing or not.
     */
    fun fileFor(root: File, location: Location, item: FileItem): File {
        val server = (location as? Location.Server)?.id ?: "device"
        val key = digest("$server|${item.path}|${item.size}|${item.lastModified}")
        return File(File(root, key), item.name.ifBlank { "download" })
    }

    /** Whether this exact file has already been fetched. */
    fun isCached(context: Context, location: Location, item: FileItem): Boolean =
        isCached(rootIn(context), location, item)

    fun isCached(root: File, location: Location, item: FileItem): Boolean {
        if (location == Location.Device || item.isDirectory) return false
        val file = fileFor(root, location, item)
        // Size as well as existence. A download interrupted half way leaves a
        // short file behind, and reporting that as "already downloaded" would
        // hand somebody a truncated APK to install.
        return file.isFile && (item.size < 0 || file.length() == item.size)
    }

    /** Makes the folder for a copy and returns where to write it. */
    fun prepare(context: Context, location: Location, item: FileItem): File {
        val file = fileFor(context, location, item)
        file.parentFile?.mkdirs()
        return file
    }

    private fun rootIn(context: Context) = File(context.cacheDir, "remote")

    /**
     * Everything held, in bytes.
     *
     * Android empties `cacheDir` on its own when the phone runs short, which is
     * the behaviour wanted here — these are copies, and the originals are still
     * on the server. This exists so the app can say how much is being held
     * rather than leaving it as a number only Settings knows.
     */
    fun sizeOnDisk(context: Context): Long =
        rootIn(context).walkBottomUp()
            .filter { it.isFile }
            .sumOf { it.length() }

    /** Removes every downloaded copy. */
    fun clear(context: Context) {
        runCatching { rootIn(context).deleteRecursively() }
    }

    private fun digest(input: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
}
