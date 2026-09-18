package com.filexplor.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * What a running operation has done so far.
 *
 * Reported by file count and by bytes, because neither alone describes the job.
 * Copying four hundred photographs and copying one film are both "one item" by
 * one measure and wildly different by the other, and a bar that only counts
 * files sits at 0% for two minutes on the second.
 */
data class Progress(
    val filesDone: Int,
    val filesTotal: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
    /** What is being worked on right now, for the line under the bar. */
    val currentName: String
) {
    val fraction: Float
        get() = when {
            bytesTotal > 0L -> (bytesDone.toDouble() / bytesTotal).toFloat().coerceIn(0f, 1f)
            filesTotal > 0 -> (filesDone.toFloat() / filesTotal).coerceIn(0f, 1f)
            else -> 0f
        }

    /**
     * Nothing has been counted yet, so there is no fraction to draw.
     *
     * A paste walks the entire tree before it moves a byte -- a listing per
     * folder, which on a server is a round trip each -- and until that finishes
     * there is no honest number. Drawing 0% for it says "started and got
     * nowhere"; an indeterminate bar says "working", which is the truth.
     */
    val preparing: Boolean get() = filesTotal == 0 && bytesTotal <= 0L

    companion object {
        /** Shown the instant a job is accepted, before it knows its own size. */
        val Preparing = Progress(0, 0, 0L, 0L, "")
    }
}

/** What happened, once an operation stopped. */
data class OperationResult(
    val succeeded: Int,
    val failed: List<Failure>,
    val cancelled: Boolean
) {
    data class Failure(val path: String, val reason: String)
}

/**
 * Copy, move and delete, across any two [FileSource]s.
 *
 * The reason this is one object rather than a method on the source: every
 * interesting operation in a file manager has two ends, and only one of them
 * can own the code. Copying a folder from SMB to the phone is a read loop on
 * one source and a write loop on another, and neither is in a position to
 * recurse over the other.
 *
 * Everything here is suspending and checks for cancellation between files, so a
 * copy of four thousand photographs stops when the user says stop rather than
 * when it finishes. Nothing here touches the main thread; the caller supplies
 * the dispatcher.
 */
class FileOperations {

    /**
     * Flattens a selection into the individual files inside it.
     *
     * Done up front, before anything is written, so the progress bar has a real
     * total instead of climbing as it discovers more work — which reads as the
     * job getting longer the more of it is done. The cost is one listing pass
     * over the tree, which on a server is the same listings the copy itself is
     * about to make anyway.
     */
    suspend fun plan(source: FileSource, items: List<FileItem>): Plan {
        val files = mutableListOf<PlannedFile>()
        val directories = mutableListOf<String>()
        val unreadable = mutableListOf<String>()

        suspend fun walk(item: FileItem, relative: String) {
            coroutineContext.ensureActive()
            if (item.isDirectory) {
                directories += relative
                // A folder that cannot be listed is recorded, not skipped.
                //
                // It used to be swallowed into an empty list, which made the
                // plan quietly smaller than the job: the copy created the
                // folder, put nothing in it, and reported success. For a move
                // that is the dangerous version -- the originals were then
                // deleted on the strength of a copy that had missed them.
                val children = runCatching { source.list(item.path) }
                    .onFailure { unreadable += item.path }
                    .getOrDefault(emptyList())
                children.forEach { walk(it, joinPath(relative, it.name)) }
            } else {
                files += PlannedFile(item, relative)
            }
        }

        items.forEach { walk(it, it.name) }
        return Plan(
            files = files,
            directories = directories,
            totalBytes = files.sumOf { it.item.size.coerceAtLeast(0L) },
            unreadable = unreadable
        )
    }
    /** What a copy or delete is going to touch, worked out before it starts. */
    data class Plan(
        val files: List<PlannedFile>,
        /** Relative paths, parents before children. */
        val directories: List<String>,
        val totalBytes: Long,
        /**
         * Folders that could not be listed, so whose contents are not in here.
         *
         * A plan carrying any of these is incomplete by definition, and the
         * caller has to decide what that means. For a copy it means saying so;
         * for a move it means not deleting the originals.
         */
        val unreadable: List<String> = emptyList()
    ) {
        val isEmpty: Boolean get() = files.isEmpty() && directories.isEmpty()
    }

    /**
     * Copies a planned set from one source to another.
     *
     * Directories are made first, in the order they were discovered, so a child
     * never arrives before its parent exists. A failure on one file is recorded
     * and the rest continue: stopping the whole job because one file in a
     * thousand was locked is the wrong trade when the other 999 would have
     * worked, and the report says which one missed.
     */
    suspend fun copy(
        from: FileSource,
        to: FileSource,
        destination: String,
        plan: Plan,
        onProgress: (Progress) -> Unit
    ): OperationResult {
        val failures = mutableListOf<OperationResult.Failure>()
        var done = 0
        var bytes = 0L

        plan.directories.forEach { relative ->
            coroutineContext.ensureActive()
            val folder = joinPath(destination, relative)
            // Recorded rather than swallowed. All four sources treat a folder
            // that is already there as success, so a failure here is a real
            // one -- and left silent it surfaced as every file underneath
            // failing separately, each with a message about the file and none
            // of them naming the folder that was the actual problem.
            runCatching { to.makeDirectory(folder) }.onFailure {
                failures += OperationResult.Failure(
                    folder,
                    it.message ?: "Couldn't create this folder."
                )
            }
        }

        for (planned in plan.files) {
            coroutineContext.ensureActive()
            onProgress(Progress(done, plan.files.size, bytes, plan.totalBytes, planned.item.name))

            val target = joinPath(destination, planned.relativePath)

            // Where the running byte count stood before this file.
            //
            // A retry starts the file again from nothing, so the bytes the
            // abandoned attempt got through have to come back off the total --
            // otherwise a connection that drops twice on a large file reports
            // more copied than the job contains.
            val bytesBeforeFile = bytes

            // Reported while the bytes move, not only between files.
            //
            // CountingStream has always counted them; nothing was asking it.
            // onProgress ran once before a file and once after, so a single
            // large file -- the exact case a progress bar exists for -- sat
            // still for the whole transfer and then jumped. Throttled, because
            // this lands on a StateFlow that redraws a screen and a 64 KB
            // buffer would otherwise ask for a frame every few milliseconds.
            var reportedAt = 0L
            var attempt = 0
            var outcome: Result<Unit> = Result.success(Unit)

            while (true) {
                attempt++
                coroutineContext.ensureActive()

                outcome = runCatching {
                    from.openRead(planned.item.path).use { stream ->
                        val counting = CountingStream(stream) { read ->
                            bytes += read
                            val now = System.currentTimeMillis()
                            if (now - reportedAt >= PROGRESS_INTERVAL_MS) {
                                reportedAt = now
                                onProgress(
                                    Progress(
                                        done,
                                        plan.files.size,
                                        bytes,
                                        plan.totalBytes,
                                        planned.item.name
                                    )
                                )
                            }
                        }
                        to.write(target, counting, planned.item.size)
                    }
                }
                if (outcome.isSuccess) break

                val error = outcome.exceptionOrNull()

                // Whatever arrived before it stopped is removed, and this comes
                // before the cancellation check on purpose: a Stop leaves the
                // same half-written file a dropped connection does, and the
                // user who pressed it is the last person who should be left
                // tidying up after it.
                //
                // A write that dies part way leaves a file with the right name,
                // the right place and the wrong length -- and nothing about it
                // looks wrong later. Left there it is worse than no file at
                // all, because the next copy sees the name taken and renames
                // around it, so the broken one stays for good.
                runCatching { to.delete(target, isDirectory = false) }

                // Stopping is not failing. runCatching catches Throwable, which
                // includes the cancellation thrown when the user taps Stop, and
                // swallowing that turned "stopped" into a list of per-file
                // errors about files nobody was going to miss.
                if (error is CancellationException) throw error

                // Worth another go only where the failure was the connection
                // rather than the file. A wifi handover, a server that drops an
                // idle session, a phone that changes network half way through a
                // video -- all of which used to end a copy of four hundred
                // photographs at photograph two hundred, with the rest reported
                // as failures the user had no way to retry but by starting the
                // whole job again. "No such file" and "permission denied" still
                // fail at once, because asking twice is a slower way to the
                // same answer.
                val again = attempt < ATTEMPTS_PER_FILE &&
                    error != null &&
                    (from.isTransient(error) || to.isTransient(error))
                if (!again) break

                // Back to where this file started, and said so on the bar: a
                // copy that silently sits still for a few seconds looks stuck,
                // and this is the moment it is most likely to be abandoned.
                bytes = bytesBeforeFile
                reportedAt = 0L
                onProgress(
                    Progress(
                        done,
                        plan.files.size,
                        bytes,
                        plan.totalBytes,
                        "Reconnecting — ${planned.item.name}"
                    )
                )

                // Backing off further each time. Reconnecting the instant a
                // socket dies usually fails again, because whatever took the
                // network away has not finished yet.
                delay(RETRY_BACKOFF_MS * attempt)
            }

            if (outcome.isSuccess) {
                done++
            } else {
                failures += OperationResult.Failure(
                    planned.item.path,
                    outcome.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
            onProgress(Progress(done, plan.files.size, bytes, plan.totalBytes, planned.item.name))
        }

        return OperationResult(done, failures, cancelled = false)
    }

    /**
     * Deletes a planned set, deepest first.
     *
     * The order is the whole of it. Every one of the three protocols refuses to
     * remove a directory that still has anything in it, so the files go first
     * and the directories go in reverse discovery order — which is to say,
     * children before parents.
     */
    suspend fun delete(
        source: FileSource,
        plan: Plan,
        rootPaths: List<FileItem>,
        onProgress: (Progress) -> Unit
    ): OperationResult {
        val failures = mutableListOf<OperationResult.Failure>()
        var done = 0
        val total = plan.files.size + plan.directories.size

        for (planned in plan.files) {
            coroutineContext.ensureActive()
            onProgress(Progress(done, total, 0L, 0L, planned.item.name))
            runCatching { source.delete(planned.item.path, isDirectory = false) }
                .onSuccess { done++ }
                .onFailure {
                    failures += OperationResult.Failure(
                        planned.item.path, it.message ?: "Unknown error"
                    )
                }
        }

        // The planner discovered parents before children, so walking it
        // backwards empties the tree from the leaves up.
        val roots = rootPaths.associateBy { it.name }
        plan.directories.asReversed().forEach { relative ->
            coroutineContext.ensureActive()
            val absolute = resolveDirectory(relative, roots)
            if (absolute != null) {
                onProgress(Progress(done, total, 0L, 0L, relative.substringAfterLast('/')))
                runCatching { source.delete(absolute, isDirectory = true) }
                    .onSuccess { done++ }
                    .onFailure {
                        failures += OperationResult.Failure(absolute, it.message ?: "Unknown error")
                    }
            }
        }

        return OperationResult(done, failures, cancelled = false)
    }

    /**
     * A directory's real path, rebuilt from the relative one the plan recorded.
     *
     * The plan stores paths relative to the selection so that the same plan can
     * be replayed against a destination. Deleting needs them the other way
     * round, and the first segment names which selected item the rest hangs off.
     */
    private fun resolveDirectory(relative: String, roots: Map<String, FileItem>): String? {
        val head = relative.substringBefore('/')
        val rest = relative.substringAfter('/', "")
        val root = roots[head] ?: return null
        return if (rest.isEmpty()) root.path else joinPath(root.path, rest)
    }

    /** One file, and where it goes relative to the destination. */
    data class PlannedFile(val item: FileItem, val relativePath: String)

    /**
     * Counts bytes as they pass, so progress comes from the copy itself.
     *
     * The alternative is adding each file's size once it finishes, which on a
     * folder of a few large files means a bar that does not move for a minute
     * and then jumps a third.
     */
    private class CountingStream(
        private val wrapped: InputStream,
        private val onRead: (Long) -> Unit
    ) : InputStream() {
        override fun read(): Int = wrapped.read().also { if (it >= 0) onRead(1L) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            wrapped.read(b, off, len).also { if (it > 0) onRead(it.toLong()) }

        override fun available(): Int = wrapped.available()

        /**
         * Deliberately does nothing.
         *
         * This stream is a view onto one the copy opened and closes itself, and
         * the destination it gets handed to does not know that: every remote
         * write() closes the stream it is given. So the source was being closed
         * twice on every server-to-server copy, and for FTP the second close
         * was the damaging one -- it ran completePendingCommand() with no
         * command pending, which then sat on the control connection until the
         * socket timeout a minute later. One owner, and it is the loop above.
         */
        override fun close() = Unit
    }

    private companion object {
        /** How often a running copy is allowed to redraw the progress bar. */
        const val PROGRESS_INTERVAL_MS = 150L

        /**
         * Tries per file, the first one included.
         *
         * Four, spread over twelve seconds. Three at a second apart was tuned
         * against a server dropping an idle session, and it is not enough for
         * the case that actually bites: a phone changing network, or coming
         * back from a moment with the screen off, where the interface itself
         * is down for several seconds and every attempt inside that window
         * fails identically. Past this it is not a blip, and waiting longer
         * only delays telling the user the truth.
         */
        const val ATTEMPTS_PER_FILE = 4

        /** Multiplied by the attempt number: 2s, then 4s, then 6s. */
        const val RETRY_BACKOFF_MS = 2_000L
    }
}
