package com.filexplor.app.ui

import com.filexplor.app.data.FolderSummary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** The wording in the Details panel. */
class DetailsTextTest {

    private lateinit var saved: Locale

    @Before
    fun pinLocale() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(saved)

    private fun summary(files: Int, folders: Int) = FolderSummary(files, folders, 0L, 0, true)

    @Test
    fun `sizes carry the exact byte count`() {
        assertEquals("2.1 GB (2,300,000,000 bytes)", formatBytesExact(2_300_000_000L))
        assertEquals("512 bytes", formatBytesExact(512L))
        assertEquals("1 byte", formatBytesExact(1L))
    }

    @Test
    fun `contents read as a sentence`() {
        assertEquals("1,234 files in 56 folders", describeContents(summary(1234, 56)))
        assertEquals("1 file in 1 folder", describeContents(summary(1, 1)))
        assertEquals("3 files", describeContents(summary(3, 0)))
        assertEquals("2 folders", describeContents(summary(0, 2)))
        assertEquals("Empty", describeContents(summary(0, 0)))
    }
}
