package com.filexplor.app.ui

import java.util.Locale

/**
 * Bytes, in the units people use.
 *
 * Powers of 1024 with the short names, which is what every file manager and
 * every desktop shows. Strictly those names belong to powers of 1000, and being
 * strictly right here would mean reporting a different size than the phone's
 * own settings screen for the same file.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    // One decimal below ten, none above: "9.4 MB" is useful, "941.7 MB" is
    // three digits of noise on a number nobody reads that precisely.
    return if (value < 10) {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    } else {
        String.format(Locale.getDefault(), "%.0f %s", value, units[unit])
    }
}

/**
 * Date and time, fixed pattern.
 *
 * Not the platform's localised short format, which needs a Context to look up.
 * Reaching for one from inside a list row is how the photo app in this family
 * crashed its own grid.
 */
fun formatDate(epochMillis: Long): String =
    if (epochMillis <= 0L) {
        "—"
    } else {
        java.text.SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
            .format(java.util.Date(epochMillis))
    }

/**
 * "57% · 1.2 GB of 2.1 GB", with "· 3 of 12 items" when there is more than one.
 *
 * The bar alone was all a running job showed, and on a six-minute copy of one
 * large file a bar is hard to read: nothing says whether it is a fifth done or
 * four fifths, or whether it has moved in the last minute. Null while the job
 * is still working out its own size.
 */
fun progressLine(progress: com.filexplor.app.data.Progress): String? {
    if (progress.preparing) return null
    val parts = mutableListOf("${(progress.fraction * 100).toInt()}%")
    if (progress.bytesTotal > 0L) {
        parts += "${formatBytes(progress.bytesDone)} of ${formatBytes(progress.bytesTotal)}"
    }
    if (progress.filesTotal > 1) {
        parts += "${progress.filesDone} of ${progress.filesTotal} items"
    }
    return parts.joinToString(" · ")
}

/**
 * "2.1 GB (2,300,000,000 bytes)" -- the rounded size and the exact one.
 *
 * The exact count is what tells two copies of a large file apart when the
 * rounded one reads the same for both, which is the check somebody makes
 * after a transfer they are not sure of.
 */
fun formatBytesExact(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return if (bytes == 1L) "1 byte" else "$bytes bytes"
    return "${formatBytes(bytes)} (${String.format(Locale.getDefault(), "%,d", bytes)} bytes)"
}

/** "1,234 files in 56 folders", "3 files", "2 folders", or "Empty". */
fun describeContents(summary: com.filexplor.app.data.FolderSummary): String {
    fun count(n: Int, noun: String) =
        String.format(Locale.getDefault(), "%,d", n) + " " + if (n == 1) noun else "${noun}s"
    return when {
        summary.files == 0 && summary.folders == 0 -> "Empty"
        summary.folders == 0 -> count(summary.files, "file")
        summary.files == 0 -> count(summary.folders, "folder")
        else -> "${count(summary.files, "file")} in ${count(summary.folders, "folder")}"
    }
}
