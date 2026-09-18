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
