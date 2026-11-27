package io.github.and.sandtimer.ui

import androidx.compose.ui.graphics.Color
import io.github.and.sandtimer.data.Base
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sand and caps the glass is drawn in, against the Mac's own `Theme` (Sources/HourglassRenderer.swift). */
class ThemeTest {
    /** Channel by channel: a float's way there and back is a shade under a step off at most. */
    private fun assertThat(actual: Color, rgb: Long) {
        val expected = Color(0xFF000000L or rgb)
        assertEquals(expected.red, actual.red, 1.5f / 255f)
        assertEquals(expected.green, actual.green, 1.5f / 255f)
        assertEquals(expected.blue, actual.blue, 1.5f / 255f)
        assertEquals(1f, actual.alpha, 0f)
    }

    @Test fun `a black base is the same black caps under any sand`() {
        assertThat(Theme(Color(0xFF34BEA6), Base.BLACK).cap, 0x161618)
        assertThat(Theme(Color(0xFFECE9E2), Base.BLACK).cap, 0x161618)
    }

    @Test fun `a matching base is the sand lightened, and one of the palette its own cap`() {
        // The Mac: sand.blended(withFraction: 0.35, of: .white)
        assertThat(Theme(Color(0xFFE8833A), Base.MATCHING).cap, 0xF0AE7F)
        assertThat(Theme(2, Base.MATCHING).cap, 0xF496C0)
        assertThat(Theme(2, Base.MATCHING).sand, 0xEC62A0)
        assertThat(Theme(2, Base.BLACK).cap, 0x161618)
    }

    @Test fun `a place among the colours that isn't one is the first`() {
        assertEquals(Theme(0, Base.MATCHING).sand, Theme(99, Base.MATCHING).sand)
        assertEquals(Theme(0, Base.MATCHING).sand, Theme(-1, Base.MATCHING).sand)
    }

    @Test fun `a colour is six hex digits, as the Mac reads it, or the purple sand`() {
        assertEquals(Color(0xFF34BEA6), sandColor("#34BEA6"))
        assertEquals(Color(0xFF34BEA6), sandColor("34bea6"))
        assertEquals(Color(0xFF6C2ED6), sandColor(null))
        assertEquals(Color(0xFF6C2ED6), sandColor("teal"))
        assertEquals(Color(0xFF6C2ED6), sandColor("#8034BEA6"))  // no alpha of its own
        assertEquals(Color(0xFF6C2ED6), sandColor("-12345"))
    }

    @Test fun `black caps get light print and coloured caps dark print`() {
        val black = Theme(0, Base.BLACK)
        val teal = Theme(1, Base.MATCHING)
        val white = Theme(4, Base.MATCHING)
        assertTrue(black.darkCap)
        assertFalse(teal.darkCap)
        assertFalse(white.darkCap)
        assertThat(black.displayInk, 0xD6D6D6)
        // The Mac: cap.blended(withFraction: 0.72, of: .black)
        assertThat(white.displayInk, 0x434343)
        // A matching cap can be dark too: the black sand's.
        assertTrue(Theme(5, Base.MATCHING).darkCap)
        assertThat(Theme(5, Base.MATCHING).displayInk, 0xD6D6D6)
    }
}
