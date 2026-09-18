package com.filexplor.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.filexplor.app.data.FileKind

/**
 * What each kind of file looks like in a list.
 *
 * Two things, deliberately, because one is not enough. A shape alone has to be
 * read — at the size a list row gives it, a spreadsheet glyph and a slide glyph
 * are both "a small grey rectangle with lines in it". Colour is what is
 * actually seen while scrolling, and the shape is what confirms it once you
 * stop.
 */
fun iconFor(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Filled.Folder
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.VIDEO -> Icons.Filled.Movie
    FileKind.AUDIO -> Icons.Filled.MusicNote
    FileKind.PDF -> Icons.Filled.PictureAsPdf
    FileKind.DOCUMENT -> Icons.Filled.Description
    FileKind.SPREADSHEET -> Icons.Filled.GridOn
    FileKind.PRESENTATION -> Icons.Filled.Slideshow
    // Article and Terminal rather than Notes and Code: both of those are thin
    // line glyphs with no body, so the badge sat under them in mid-air
    // instead of on them. A solid shape is what the chip needs to land on.
    FileKind.TEXT -> Icons.AutoMirrored.Filled.Article
    FileKind.EBOOK -> Icons.AutoMirrored.Filled.MenuBook
    FileKind.ARCHIVE -> Icons.Filled.Archive
    FileKind.CODE -> Icons.Filled.Terminal
    FileKind.APK -> Icons.Filled.Android
    FileKind.FONT -> Icons.Filled.FontDownload
    FileKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/**
 * Fixed hues, not theme ones.
 *
 * The tempting version derives these from the palette so a file list always
 * matches the theme. It also makes every icon on screen a shade of the same
 * colour, which is the exact opposite of what a type colour is for — and the
 * app has thirty-two palettes, so several of them would collapse two types
 * into the same hue.
 *
 * These are the conventions people already carry from desktops: documents blue,
 * spreadsheets green, presentations orange, PDFs red, archives amber, code
 * violet. Each is given a light and a dark form rather than one compromise,
 * because a hue that reads on white is washed out on black and one that reads
 * on black is invisible on white.
 */
@Composable
@ReadOnlyComposable
fun colourFor(kind: FileKind): Color {
    val dark = MaterialTheme.colorScheme.surface.luminanceIsDark()
    return when (kind) {
        // The folder is the one that follows the theme. It is the most common
        // row on any screen and the one that means "go here" rather than "this
        // is a thing", so it belongs to the app's own accent.
        FileKind.FOLDER -> MaterialTheme.colorScheme.primary
        FileKind.IMAGE -> if (dark) Color(0xFF6FD3A0) else Color(0xFF1E8A5C)
        FileKind.VIDEO -> if (dark) Color(0xFFB58CF5) else Color(0xFF6A3BC0)
        FileKind.AUDIO -> if (dark) Color(0xFFF58CC0) else Color(0xFFB3216B)
        FileKind.PDF -> if (dark) Color(0xFFF4756B) else Color(0xFFC22C1E)
        FileKind.DOCUMENT -> if (dark) Color(0xFF7FB4F5) else Color(0xFF1F5FAE)
        FileKind.SPREADSHEET -> if (dark) Color(0xFF7FD07F) else Color(0xFF237A23)
        FileKind.PRESENTATION -> if (dark) Color(0xFFF5A85C) else Color(0xFFB55C0A)
        FileKind.TEXT -> if (dark) Color(0xFFBFC7D1) else Color(0xFF55606B)
        FileKind.EBOOK -> if (dark) Color(0xFFD3B27F) else Color(0xFF87641E)
        FileKind.ARCHIVE -> if (dark) Color(0xFFF5CE6B) else Color(0xFF9A7410)
        FileKind.CODE -> if (dark) Color(0xFF9FB6F5) else Color(0xFF3F4FAE)
        FileKind.APK -> if (dark) Color(0xFF8BD46B) else Color(0xFF3F8A1E)
        FileKind.FONT -> if (dark) Color(0xFFC9A0F5) else Color(0xFF7A3FB0)
        FileKind.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Whether a ground is dark enough to want the light form of a hue.
 *
 * Asked of the surface rather than of the theme's own light/dark flag, because
 * the animated palettes publish a transparent surface and take their darkness
 * from what is painted behind it — so the flag and the actual background can
 * disagree.
 */
private fun Color.luminanceIsDark(): Boolean =
    if (alpha == 0f) true else (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

/**
 * The short tag shown on a file's icon: `ZIP`, `DOCX`, `KT`.
 *
 * Suppressed for PDF, because Material's own glyph for it already has the
 * three letters drawn into the page — badging it produced `report.pdf` with
 * PDF printed on it twice, one above the other.
 *
 * Longer than four characters stops being a glance and starts being reading,
 * and every extension worth badging is shorter than that.
 */
fun badgeFor(kind: FileKind, extension: String): String? {
    if (kind == FileKind.PDF) return null
    if (extension.isBlank()) return null
    return if (extension.length <= 4) extension.uppercase() else null
}
