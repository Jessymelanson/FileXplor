package com.filexplor.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream

/** The totals behind a folder's Details. */
class FolderSummaryTest {

    /** A tree of folders and sized files; [broken] folders refuse to list. */
    private class Tree(vararg entries: Pair<String, Long>) : FileSource {
        private val all = entries.toMap()
        val broken = mutableSetOf<String>()
        override val root = "/"
        override val label = "tree"

        override fun list(path: String): List<FileItem> {
            if (path in broken) throw IOException("Permission denied")
            return all.filterKeys { parentPath(it) == path }.map { (p, size) ->
                FileItem(p.substringAfterLast('/'), p, isDirectory = size < 0, size = size)
            }
        }

        override fun stat(path: String): FileItem? = null
        override fun openRead(path: String): InputStream = throw UnsupportedOperationException()
        override fun write(path: String, source: InputStream, length: Long) = Unit
        override fun makeDirectory(path: String) = Unit
        override fun delete(path: String, isDirectory: Boolean) = Unit
        override fun rename(path: String, newName: String) = Unit
        override fun exists(path: String) = path in all
    }

    private val tree = Tree(
        "/Films/a.mkv" to 1_500_000_000L,
        "/Films/b.mkv" to 800_000_000L,
        "/Films/Extras" to -1L,
        "/Films/Extras/c.mp4" to 50_000_000L,
        "/Films/Extras/.nomedia" to 0L,
        "/Films/Extras/Deep" to -1L
    )

    @Test
    fun `counts files and folders all the way down, with their size`() = runBlocking {
        val summary = summariseFolder(tree, "/Films") {}
        assertEquals(4, summary.files)
        assertEquals(2, summary.folders)
        assertEquals(2_350_000_000L, summary.bytes)
        assertEquals(0, summary.unreadable)
        assertTrue(summary.complete)
    }

    @Test
    fun `a folder that cannot be read is reported, not taken as empty`() = runBlocking {
        tree.broken += "/Films/Extras"
        val summary = summariseFolder(tree, "/Films") {}
        assertEquals(2, summary.files)
        assertEquals(1, summary.unreadable)
    }
}
