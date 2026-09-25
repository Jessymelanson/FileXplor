package com.filexplor.app.data

import com.filexplor.app.data.remote.RemoteFileSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * A copy either delivers the whole file or says it did not.
 *
 * Found on a test FTP server: a transfer the server cut off at 50 MB of 200 was
 * reported as "1 item moved", and the move deleted the original.
 */
class CopyIntegrityTest {

    /** Files held in memory. [cut] serves only the first N bytes of a path. */
    private class MemorySource(private val transient: Boolean = false) : FileSource {
        val files = mutableMapOf<String, ByteArray>()
        val cut = mutableMapOf<String, Int>()
        /** Opens that are cut short before the file is served whole. */
        var cutOpens = Int.MAX_VALUE
        var opens = 0
        var endless: InputStream? = null

        override val root = "/"
        override val label = "memory"
        override fun list(path: String) =
            files.keys.filter { parentPath(it) == path }.mapNotNull { stat(it) }

        override fun stat(path: String) = files[path]?.let {
            FileItem(path.substringAfterLast('/'), path, false, it.size.toLong())
        }

        override fun openRead(path: String): InputStream {
            endless?.let { return it }
            opens++
            val data = files.getValue(path)
            val limit = if (opens <= cutOpens) cut[path] ?: data.size else data.size
            return ByteArrayInputStream(data, 0, limit)
        }

        override fun write(path: String, source: InputStream, length: Long) {
            files[path] = source.readBytes()
        }

        override fun isTransient(error: Throwable) =
            transient && RemoteFileSource.isConnectionLost(error)

        override fun makeDirectory(path: String) = Unit
        override fun delete(path: String, isDirectory: Boolean) { files.remove(path) }
        override fun rename(path: String, newName: String) = Unit
        override fun exists(path: String) = path in files
    }

    private val ops = FileOperations()
    private val data = ByteArray(200) { it.toByte() }

    private fun planOf(source: MemorySource, path: String, listedSize: Long? = null): FileOperations.Plan {
        val item = source.stat(path)!!.let { if (listedSize != null) it.copy(size = listedSize) else it }
        return FileOperations.Plan(listOf(FileOperations.PlannedFile(item, item.name)), emptyList(), item.size)
    }

    @Test
    fun `a file that arrives short is a failure, not a copy`() = runBlocking {
        val from = MemorySource().apply { files["/a.bin"] = data; cut["/a.bin"] = 50 }
        val to = MemorySource()

        val result = ops.copy(from, to, "/out", planOf(from, "/a.bin")) {}

        assertEquals(0, result.succeeded)
        assertEquals(1, result.failed.size)
        assertTrue(result.failed.single().reason, result.failed.single().reason.contains("Only part"))
        // Nothing left behind under the real name.
        assertFalse("/out/a.bin" in to.files)
    }

    @Test
    fun `a short transfer is tried again, and the whole file kept`() = runBlocking {
        val from = MemorySource(transient = true).apply {
            files["/a.bin"] = data; cut["/a.bin"] = 50; cutOpens = 1
        }
        val to = MemorySource()

        val result = ops.copy(from, to, "/out", planOf(from, "/a.bin")) {}

        assertEquals(1, result.succeeded)
        assertEquals(2, from.opens)
        assertArrayEquals(data, to.files["/out/a.bin"])
    }

    @Test
    fun `a file that shrank since it was listed is copied as it is now`() = runBlocking {
        // Listed at 300 bytes, rewritten to 200 before the copy reached it.
        val from = MemorySource().apply { files["/a.bin"] = data }
        val to = MemorySource()

        val result = ops.copy(from, to, "/out", planOf(from, "/a.bin", listedSize = 300)) {}

        assertEquals(1, result.succeeded)
        assertArrayEquals(data, to.files["/out/a.bin"])
    }

    @Test
    fun `a file that grew since it was listed is copied whole`() = runBlocking {
        val from = MemorySource().apply { files["/a.bin"] = data }
        val to = MemorySource()

        val result = ops.copy(from, to, "/out", planOf(from, "/a.bin", listedSize = 100)) {}

        assertEquals(1, result.succeeded)
        assertEquals(200, to.files["/out/a.bin"]!!.size)
    }

    @Test
    fun `stop interrupts a file part way instead of waiting for the end`() = runBlocking {
        // A file that never ends, arriving a kilobyte at a time.
        val from = MemorySource().apply {
            files["/film.mkv"] = ByteArray(0)
            endless = object : InputStream() {
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    Thread.sleep(2)
                    return minOf(len, 1024)
                }
            }
        }
        val to = MemorySource()
        val plan = planOf(from, "/film.mkv", listedSize = Long.MAX_VALUE)

        val copying = async(Dispatchers.IO) { ops.copy(from, to, "/out", plan) {} }
        delay(200)
        copying.cancel()

        withTimeout(3_000) {
            try {
                copying.await()
                fail("A stopped copy should not complete.")
            } catch (expected: CancellationException) {
                // Stopped, and promptly -- the timeout is the real assertion.
            }
        }
        assertFalse("/out/film.mkv" in to.files)
    }

    @Test
    fun `an incomplete transfer counts as a dropped connection, so it is retried`() {
        assertTrue(RemoteFileSource.isConnectionLost(IncompleteTransferException("short")))
    }
}
