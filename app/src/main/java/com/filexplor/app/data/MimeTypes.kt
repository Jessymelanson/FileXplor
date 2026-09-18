package com.filexplor.app.data

import android.webkit.MimeTypeMap

/**
 * The media type to hand another app along with a file.
 *
 * `MimeTypeMap` is the platform's own table and is asked first, but it is
 * thinner than it looks: it has never heard of `.md`, `.kt`, `.log`, `.yml` or
 * most of what a developer's phone is full of, and answers null. Falling
 * straight to the wildcard for those is what turns "open this" into a chooser
 * offering every app on the phone, with the text editor no more likely than the
 * camera.
 *
 * So the gaps are filled here, and the rule is the same one the rest of this
 * app follows: say the most specific true thing. `text/plain` for a Kotlin file
 * is not the whole truth, but it is what makes every text editor on the phone
 * offer to open it, which is the point of being asked.
 */
object MimeTypes {

    private val EXTRA = mapOf(
        // Text the platform does not know is text.
        "md" to "text/plain",
        "markdown" to "text/plain",
        "log" to "text/plain",
        "nfo" to "text/plain",
        "rst" to "text/plain",
        "srt" to "text/plain",
        "vtt" to "text/plain",
        "ini" to "text/plain",
        "cfg" to "text/plain",
        "conf" to "text/plain",
        "properties" to "text/plain",
        "toml" to "text/plain",
        "yml" to "text/plain",
        "yaml" to "text/plain",
        "gradle" to "text/plain",
        "env" to "text/plain",

        // Source files. All text, and all better opened by an editor than by
        // whatever claims the wildcard.
        "kt" to "text/plain",
        "kts" to "text/plain",
        "java" to "text/x-java",
        "py" to "text/x-python",
        "rb" to "text/plain",
        "go" to "text/plain",
        "rs" to "text/plain",
        "c" to "text/x-c",
        "h" to "text/x-c",
        "cpp" to "text/x-c",
        "hpp" to "text/x-c",
        "cs" to "text/plain",
        "swift" to "text/plain",
        "php" to "text/plain",
        "lua" to "text/plain",
        "sql" to "text/plain",
        "sh" to "text/x-shellscript",
        "bat" to "text/plain",
        "ps1" to "text/plain",
        "tsx" to "text/plain",
        "jsx" to "text/plain",
        "scss" to "text/css",

        // Archives, where the platform is patchy.
        "7z" to "application/x-7z-compressed",
        "rar" to "application/vnd.rar",
        "tar" to "application/x-tar",
        "tgz" to "application/gzip",
        "xz" to "application/x-xz",
        "zst" to "application/zstd",
        "iso" to "application/x-iso9660-image",

        // Documents and books.
        "epub" to "application/epub+zip",
        "mobi" to "application/x-mobipocket-ebook",
        "azw3" to "application/vnd.amazon.ebook",
        "djvu" to "image/vnd.djvu",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",

        // Media the table sometimes misses.
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "opus" to "audio/ogg",
        "mkv" to "video/x-matroska",
        // Ambiguous, and settled the same way on both sides of the app:
        // FileKind calls a .ts a video, so this has to as well. It used to say
        // text/plain -- the TypeScript reading -- which meant tapping a
        // recorded stream offered it to every text editor on the phone and to
        // no video player at all. On a phone's storage a .ts is a transport
        // stream; TypeScript lives on a computer, and .tsx above still reads as
        // source because nothing else claims it.
        "ts" to "video/mp2t",
        "flac" to "audio/flac",

        // The one that has to be exact or the installer will not take it.
        "apk" to "application/vnd.android.package-archive"
    )

    /** The type for a file, or the wildcard where nothing better is known. */
    fun of(extension: String): String {
        if (extension.isBlank()) return WILDCARD
        val lower = extension.lowercase()
        EXTRA[lower]?.let { return it }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(lower) ?: WILDCARD
    }

    /** Whether this is something Android's package installer should be handed. */
    fun isInstallable(extension: String): Boolean = extension.lowercase() == "apk"

    const val WILDCARD = "*/*"
}
