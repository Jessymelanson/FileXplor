package com.filexplor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The path rules a paste is decided by.
 *
 * These are pure string questions with expensive wrong answers: [isWithin] is
 * the only thing standing between "move this folder" and moving a folder into
 * itself, which copied the tree into a corner of itself and then deleted the
 * original -- copy included. Worth pinning down rather than eyeballing.
 */
class PathRulesTest {

    @Test
    fun `a folder contains itself`() {
        assertTrue(isWithin("/storage/music", "/storage/music"))
    }

    @Test
    fun `a trailing slash does not change the answer`() {
        assertTrue(isWithin("/storage/music/", "/storage/music"))
        assertTrue(isWithin("/storage/music", "/storage/music/"))
    }

    @Test
    fun `a child is within its parent`() {
        assertTrue(isWithin("/storage/music/rock", "/storage/music"))
        assertTrue(isWithin("/storage/music/rock/live", "/storage/music"))
    }

    @Test
    fun `a sibling with a shared prefix is not within`() {
        // The one that a plain startsWith gets wrong, and the reason the
        // separator is part of the test.
        assertFalse(isWithin("/storage/musicals", "/storage/music"))
        assertFalse(isWithin("/storage/music2", "/storage/music"))
    }

    @Test
    fun `a parent is not within its child`() {
        assertFalse(isWithin("/storage", "/storage/music"))
    }

    @Test
    fun `parentPath finds the holding folder`() {
        assertEquals("/storage/music", parentPath("/storage/music/song.mp3"))
        assertEquals("/storage/music", parentPath("/storage/music/rock/"))
        assertEquals("/", parentPath("/song.mp3"))
    }

    @Test
    fun `samePath ignores a trailing slash`() {
        assertTrue(samePath("/a/b", "/a/b/"))
        assertFalse(samePath("/a/b", "/a/c"))
    }

    // ---- names ----------------------------------------------------------

    @Test
    fun `an ordinary name is allowed`() {
        assertNull(nameProblem("Holiday photos"))
        assertNull(nameProblem("report.final.pdf"))
        assertNull(nameProblem(".hidden"))
    }

    @Test
    fun `a name with a separator is refused`() {
        // Renaming to "../elsewhere" moved the file out of the folder, which
        // looked exactly like a rename that had deleted it.
        assertNotNull(nameProblem("../elsewhere"))
        assertNotNull(nameProblem("folder/file"))
    }

    @Test
    fun `the reserved names are refused`() {
        assertNotNull(nameProblem("."))
        assertNotNull(nameProblem(".."))
    }

    @Test
    fun `an empty or blank name is refused`() {
        assertNotNull(nameProblem(""))
        assertNotNull(nameProblem("   "))
    }

    // ---- collision naming -----------------------------------------------

    @Test
    fun `a free name is left alone`() {
        assertEquals("song.mp3", uniqueName("song.mp3", setOf("other.mp3")))
    }

    @Test
    fun `a taken name gains a number before the extension`() {
        // Before the extension, so the copy still opens in the same app.
        assertEquals("song (2).mp3", uniqueName("song.mp3", setOf("song.mp3")))
    }

    @Test
    fun `numbering keeps climbing past the ones already there`() {
        assertEquals(
            "song (4).mp3",
            uniqueName("song.mp3", setOf("song.mp3", "song (2).mp3", "song (3).mp3"))
        )
    }

    @Test
    fun `a name with no extension is numbered at the end`() {
        assertEquals("Photos (2)", uniqueName("Photos", setOf("Photos")))
    }

    @Test
    fun `a dotfile is not treated as all extension`() {
        // lastIndexOf('.') is 0 here, and treating that as the extension would
        // produce " (2).bashrc".
        assertEquals(".bashrc (2)", uniqueName(".bashrc", setOf(".bashrc")))
    }
}
