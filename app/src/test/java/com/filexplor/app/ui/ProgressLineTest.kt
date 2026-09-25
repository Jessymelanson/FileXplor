package com.filexplor.app.ui

import com.filexplor.app.data.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The numbers under the bar of a running job. */
class ProgressLineTest {

    private val mb = 1024L * 1024

    @Test
    fun `one large file shows how far it has got`() {
        assertEquals(
            "50% · 20 MB of 40 MB",
            progressLine(Progress(0, 1, 20 * mb, 40 * mb, "film.mkv"))
        )
    }

    @Test
    fun `several files show the count as well`() {
        assertEquals(
            "25% · 10 MB of 40 MB · 3 of 12 items",
            progressLine(Progress(3, 12, 10 * mb, 40 * mb, "IMG_0004.jpg"))
        )
    }

    @Test
    fun `a delete, which has no bytes, counts items`() {
        assertEquals("40% · 4 of 10 items", progressLine(Progress(4, 10, 0L, 0L, "old")))
    }

    @Test
    fun `nothing is shown while the job is still sizing itself`() {
        assertNull(progressLine(Progress.Preparing))
    }
}
