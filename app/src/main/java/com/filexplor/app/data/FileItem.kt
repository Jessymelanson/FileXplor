package com.filexplor.app.data

/**
 * One entry in a listing, wherever the listing came from.
 *
 * Deliberately the same shape for a file on the phone and a file on a server.
 * The screen that draws a row, the code that sorts a folder and the code that
 * decides what a selection can have done to it are the bulk of a file manager,
 * and none of them are improved by knowing which protocol produced the row.
 *
 * [path] is absolute within its own [Location] and is the only handle anything
 * else uses. Names are not unique across a listing in any useful way — two
 * folders can each hold a `notes.txt` — and a path is what every source can
 * take back.
 */
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    /** Bytes. `-1` where the source did not say, which some FTP listings do. */
    val size: Long = -1L,
    /** Epoch millis, or `0` where unknown. */
    val lastModified: Long = 0L
) {
    /** Lower-cased and without the dot: `jpg`, `mp4`, `` for a name with none. */
    val extension: String
        get() {
            if (isDirectory) return ""
            val cut = name.lastIndexOf('.')
            return if (cut <= 0 || cut == name.lastIndex) "" else {
                name.substring(cut + 1).lowercase()
            }
        }

    /** Files whose name begins with a dot, which are hidden unless asked for. */
    val isHidden: Boolean get() = name.startsWith(".")

    val kind: FileKind get() = FileKind.of(this)
}

/**
 * What a file is, as far as this app needs to care.
 *
 * Decided from the extension rather than by sniffing the contents. Sniffing
 * means opening every file in a folder to draw one screen, which on a server is
 * a round trip each; the extension is what the platform itself dispatches on
 * when something is opened, so agreeing with it is also the honest answer.
 */
enum class FileKind {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    DOCUMENT,
    SPREADSHEET,
    PRESENTATION,
    TEXT,
    EBOOK,
    ARCHIVE,
    CODE,
    APK,
    FONT,
    OTHER;

    companion object {
        private val IMAGES = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif",
            "svg", "ico", "tif", "tiff", "dng", "raw"
        )
        private val VIDEOS = setOf(
            "mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts", "flv", "wmv", "mpg", "mpeg"
        )
        // SOUND and SOURCE rather than AUDIO and CODE, which would each be the
        // name of an enum entry as well. Inside the companion the property wins,
        // so `extension in AUDIO -> AUDIO` quietly returns the Set instead of
        // the constant and the whole function's type widens to Any. It compiles
        // right up until something expects a FileKind back.
        private val SOUND = setOf(
            "mp3", "flac", "wav", "ogg", "opus", "m4a", "aac", "wma", "mid", "midi", "amr"
        )
        // Split out of one DOCUMENTS bucket. A spreadsheet and a slide deck are
        // as different from each other as either is from a video, and a folder
        // of mixed office files was the one place the old single icon left you
        // reading filenames to find anything.
        private val WORDS = setOf("doc", "docx", "odt", "rtf", "pages", "wpd")
        private val SHEETS = setOf("xls", "xlsx", "ods", "csv", "tsv", "numbers")
        private val SLIDES = setOf("ppt", "pptx", "odp", "key")
        private val PLAIN = setOf("txt", "md", "log", "nfo", "rst", "srt", "vtt")
        private val BOOKS = setOf("epub", "mobi", "azw", "azw3", "fb2", "djvu")
        private val ARCHIVES = setOf(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "cab", "tgz", "zst", "lz4"
        )
        // No "ts" here, on purpose: VIDEOS is checked first, so listing it in
        // both only made the two sets look like they disagreed about a file
        // neither of them decided.
        private val SOURCE = setOf(
            "kt", "java", "js", "tsx", "jsx", "py", "rb", "go", "rs", "c", "h",
            "cpp", "hpp", "cs", "swift", "php", "lua", "sql", "sh", "bat", "ps1",
            "html", "htm", "css", "scss", "xml", "json", "yml", "yaml", "toml", "ini",
            "gradle", "properties", "cfg", "conf"
        )
        private val FONTS = setOf("ttf", "otf", "woff", "woff2", "eot", "fon")
        private val PACKAGES = setOf("apk", "apks", "xapk", "aab")

        fun of(item: FileItem): FileKind = when {
            item.isDirectory -> FOLDER
            item.extension in IMAGES -> IMAGE
            item.extension in VIDEOS -> VIDEO
            item.extension in SOUND -> AUDIO
            item.extension == "pdf" -> PDF
            item.extension in PACKAGES -> APK
            item.extension in ARCHIVES -> ARCHIVE
            item.extension in WORDS -> DOCUMENT
            item.extension in SHEETS -> SPREADSHEET
            item.extension in SLIDES -> PRESENTATION
            item.extension in BOOKS -> EBOOK
            item.extension in PLAIN -> TEXT
            item.extension in SOURCE -> CODE
            item.extension in FONTS -> FONT
            else -> OTHER
        }
    }
}

/**
 * Where a listing lives.
 *
 * A sealed pair rather than a string scheme, so the compiler can tell anyone
 * who adds a third kind of location which branches they have to answer for.
 */
sealed interface Location {
    /** The phone's own filesystem. */
    data object Device : Location

    /** A configured FTP, SFTP or SMB server, by its [com.filexplor.app.data.remote.RemoteServer] id. */
    data class Server(val id: String) : Location
}

/** A [Location] and a path within it — enough to name any file the app can reach. */
data class FilePath(val location: Location, val path: String)

/** How a listing is ordered. */
enum class SortOrder(val label: String) {
    NAME("Name A to Z"),
    NAME_DESC("Name Z to A"),
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    LARGEST("Largest first"),
    SMALLEST("Smallest first")
}

/** How a listing is drawn. */
enum class ViewMode(val label: String) {
    LIST("List"),
    DETAILS("Details"),
    GRID("Grid")
}

/**
 * Folders before files, always, whatever the sort is.
 *
 * Every file manager does this and it is worth saying why: a folder and a file
 * are not comparable by the thing being sorted on. Sorting by size puts every
 * folder at the bottom under "unknown", and sorting by date scatters them
 * through the list — in both cases the structure of the place you are standing
 * in disappears into its contents.
 */
fun List<FileItem>.sortedFor(order: SortOrder): List<FileItem> {
    val byOrder: Comparator<FileItem> = when (order) {
        SortOrder.NAME -> compareBy { it.name.lowercase() }
        SortOrder.NAME_DESC -> compareByDescending { it.name.lowercase() }
        SortOrder.NEWEST -> compareByDescending { it.lastModified }
        SortOrder.OLDEST -> compareBy { it.lastModified }
        SortOrder.LARGEST -> compareByDescending { it.size }
        SortOrder.SMALLEST -> compareBy { it.size }
    }
    return sortedWith(compareByDescending<FileItem> { it.isDirectory }.then(byOrder))
}
