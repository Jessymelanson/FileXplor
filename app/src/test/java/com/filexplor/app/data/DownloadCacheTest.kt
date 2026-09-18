package com.filexplor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * How a downloaded copy is named, and when it counts as present.
 *
 * The naming rule is the whole of this class's correctness. Get it wrong and
 * the app either re-downloads a file it already has — merely wasteful — or
 * hands somebody a different file under the name they tapped, which is not.
 */
class DownloadCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val server = Location.Server("server-one")

    private fun item(path: String, size: Long = 100L, modified: Long = 1000L) = FileItem(
        name = path.substringAfterLast('/'),
        path = path,
        isDirectory = false,
        size = size,
        lastModified = modified
    )

    @Test
    fun `same name in different folders does not share a copy`() {
        // The bug this class was written to fix: keyed on the bare filename,
        // these two were one entry, and opening the second silently overwrote
        // the first.
        val a = DownloadCache.fileFor(temp.root, server, item("/photos/report.pdf"))
        val b = DownloadCache.fileFor(temp.root, server, item("/docs/report.pdf"))
        assertNotEquals(a.absolutePath, b.absolutePath)
    }

    @Test
    fun `same path on different servers does not share a copy`() {
        val a = DownloadCache.fileFor(temp.root, Location.Server("one"), item("/report.pdf"))
        val b = DownloadCache.fileFor(temp.root, Location.Server("two"), item("/report.pdf"))
        assertNotEquals(a.absolutePath, b.absolutePath)
    }

    @Test
    fun `the copy keeps the file's real name`() {
        // Whatever opens it shows this name in its own title bar, so it has to
        // be the name the user tapped and not the key.
        val file = DownloadCache.fileFor(temp.root, server, item("/a/b/holiday.jpg"))
        assertEquals("holiday.jpg", file.name)
    }

    @Test
    fun `the same file asked for twice lands in the same place`() {
        val a = DownloadCache.fileFor(temp.root, server, item("/x/y.apk"))
        val b = DownloadCache.fileFor(temp.root, server, item("/x/y.apk"))
        assertEquals(a.absolutePath, b.absolutePath)
    }

    @Test
    fun `a file replaced on the server is not the cached one`() {
        val old = item("/app.apk", size = 100L, modified = 1000L)
        val new = item("/app.apk", size = 250L, modified = 2000L)
        assertNotEquals(
            DownloadCache.fileFor(temp.root, server, old).absolutePath,
            DownloadCache.fileFor(temp.root, server, new).absolutePath
        )
    }

    @Test
    fun `a complete copy reports as cached`() {
        val entry = item("/app.apk", size = 5L)
        val file = DownloadCache.fileFor(temp.root, server, entry)
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(5))
        assertTrue(DownloadCache.isCached(temp.root, server, entry))
    }

    @Test
    fun `a half finished download does not report as cached`() {
        // The one that matters most. A truncated APK badged "downloaded" is an
        // install that fails, or worse, and the length check is all that stands
        // between the two.
        val entry = item("/app.apk", size = 5000L)
        val file = DownloadCache.fileFor(temp.root, server, entry)
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(120))
        assertFalse(DownloadCache.isCached(temp.root, server, entry))
    }

    @Test
    fun `nothing on the phone is ever reported as cached`() {
        assertFalse(DownloadCache.isCached(temp.root, Location.Device, item("/sdcard/a.txt")))
    }

    @Test
    fun `a size the server would not state is accepted on existence alone`() {
        // Some FTP listings give no size. Refusing to ever call those cached
        // would mean re-downloading them on every open.
        val entry = item("/mystery.bin", size = -1L)
        val file = DownloadCache.fileFor(temp.root, server, entry)
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(9))
        assertTrue(DownloadCache.isCached(temp.root, server, entry))
    }
}
