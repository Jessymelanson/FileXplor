package com.filexplor.app.data

import java.io.InputStream

/**
 * Everything the app can do to one place, whether that place is the phone or a
 * server.
 *
 * The whole point of the app hangs off this: copying a folder from an SMB share
 * to the Downloads folder is the same code as copying it the other way, because
 * neither end knows what the other is. Adding a fourth protocol later means
 * writing one of these and nothing else.
 *
 * Every call blocks. Implementations do real network and disk work and are
 * expected to be called from a background dispatcher — the view model owns that
 * decision, because it is the thing that knows whether a progress indicator is
 * on screen.
 */
interface FileSource {

    /** Where browsing starts when this source is opened. */
    val root: String

    /** Human-readable name for the bar at the top. */
    val label: String

    fun list(path: String): List<FileItem>

    /** One entry, or null where nothing is at [path]. */
    fun stat(path: String): FileItem?

    /**
     * The file's bytes as a stream. The caller closes it.
     *
     * Never a ByteArray. A file manager is pointed at whatever is there, and
     * the largest thing on a phone is exactly the thing somebody wants to move.
     */
    fun openRead(path: String): InputStream

    /**
     * Writes [length] bytes of [source] to [path], replacing anything there.
     *
     * [length] is passed through for the protocols that want a size up front
     * and for progress; `-1` where it genuinely is not known.
     */
    fun write(path: String, source: InputStream, length: Long)

    /**
     * Replaces [path] with [bytes], with no moment where it is neither.
     *
     * The ordinary [write] opens the destination for writing, which truncates
     * it before a single byte of the new content has arrived. That is fine for
     * a copy, where the destination is a new file and a failure leaves rubbish
     * that can be deleted -- and it is not fine for saving an edit, where the
     * destination is the only copy of what the user has been working on. A
     * write that fails half way there leaves them with neither version.
     *
     * The default implementation is the honest one for a network protocol:
     * none of the three offers a way to swap a file into place, and a
     * write-elsewhere-then-rename would trade truncation for a window in which
     * the file does not exist at all. Sources that *can* do better override it.
     */
    fun writeAtomic(path: String, bytes: ByteArray) {
        write(path, bytes.inputStream(), bytes.size.toLong())
    }

    /**
     * Whether [error] is the kind of failure that might not happen again.
     *
     * A dropped socket is; "no such file" and "permission denied" are not, and
     * retrying those is a slower way to reach the same answer. The default is
     * no, which is right for the phone's own filesystem: a local read that
     * failed will fail again for the same reason, and the disk is not going to
     * reconnect.
     *
     * This exists so a copy can decide whether to try a file again without
     * knowing which protocol it is talking to -- which is the whole point of
     * this interface.
     */
    fun isTransient(error: Throwable): Boolean = false

    fun makeDirectory(path: String)

    /**
     * Removes one entry. Directories are expected to be empty — recursion is
     * [FileOperations]' job, so that the progress of a large delete is
     * reportable and cancellable rather than disappearing into a driver.
     */
    fun delete(path: String, isDirectory: Boolean)

    fun rename(path: String, newName: String)

    fun exists(path: String): Boolean

    /** Frees whatever the source is holding. Local sources hold nothing. */
    fun close() {}
}

/** The parent of [path], or [root] when already at the top. */
fun FileSource.parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed == root.trimEnd('/') || trimmed.isEmpty()) return root
    val cut = trimmed.lastIndexOf('/')
    return when {
        cut < 0 -> root
        cut == 0 -> "/"
        else -> trimmed.substring(0, cut)
    }
}

/** [base] and [name] joined with exactly one separator between them. */
fun joinPath(base: String, name: String): String =
    if (base.endsWith("/")) base + name.trimStart('/')
    else base.trimEnd('/') + "/" + name.trimStart('/')

/**
 * Why [name] cannot be used as a file name, or null when it can.
 *
 * A name typed into Rename or New folder goes straight into a path, and the
 * separator is the thing that makes that dangerous rather than merely untidy:
 * renaming to "../thing" moves the file up a level on the phone, and on a
 * server it addresses somewhere the user may not even be able to see. Both
 * cases look like a rename that did nothing, because the entry disappears from
 * the folder either way.
 *
 * Returned as a message rather than a boolean, because the user is about to be
 * told and "that name is not allowed" on its own invites a second attempt at
 * the same thing.
 */
fun nameProblem(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "A name can't be empty."
        trimmed == "." || trimmed == ".." -> "That name is reserved."
        trimmed.contains('/') -> "A name can't contain a slash."
        trimmed.contains('\u0000') -> "That name contains a character files can't use."
        else -> null
    }
}

/** The same place, whether or not somebody wrote a trailing slash. */
fun samePath(a: String, b: String): Boolean = a.trimEnd('/') == b.trimEnd('/')

/**
 * True when [candidate] is [parent], or sits somewhere underneath it.
 *
 * The question a file manager has to ask before it moves anything: a folder
 * cannot be pasted into itself or into one of its own children. Left to run,
 * that copies the tree into a corner of itself and then deletes the original --
 * which now contains the copy.
 *
 * Compared as plain strings, which is right for the three protocols here: all
 * of them address with '/' and none of them normalise case on the app's behalf.
 * The segment boundary matters, or `/music` would be judged to contain
 * `/musicals`.
 */
fun isWithin(candidate: String, parent: String): Boolean {
    val c = candidate.trimEnd('/')
    val p = parent.trimEnd('/')
    return c == p || c.startsWith("$p/")
}

/** The directory holding [path]. */
fun parentPath(path: String): String {
    val trimmed = path.trimEnd('/')
    val cut = trimmed.lastIndexOf('/')
    return when {
        cut < 0 -> ""
        cut == 0 -> "/"
        else -> trimmed.substring(0, cut)
    }
}

/**
 * A name that is not already taken in [existing], by adding " (2)", " (3)"...
 *
 * Pasting a file into the folder it came from is a normal thing to do and must
 * not mean silently destroying the original. The suffix goes before the
 * extension so the copy still opens in the same app.
 */
fun uniqueName(desired: String, existing: Set<String>): String {
    if (desired !in existing) return desired
    val dot = desired.lastIndexOf('.')
    val stem = if (dot > 0) desired.substring(0, dot) else desired
    val suffix = if (dot > 0) desired.substring(dot) else ""
    var n = 2
    while ("$stem ($n)$suffix" in existing) n++
    return "$stem ($n)$suffix"
}

/**
 * Reads at most [limit] bytes, then stops.
 *
 * `readBytes()` reads to the end whatever the caller intended, which on a
 * multi-gigabyte log is an out-of-memory error rather than a slow read. This
 * stops where it is told, so a cap can be a cap.
 */
fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (total < limit) {
        val wanted = minOf(buffer.size, limit - total)
        val read = read(buffer, 0, wanted)
        if (read <= 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}
