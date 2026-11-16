package com.donian.galaxy_battery

import com.donian.galaxy_battery.ui.common.scrollbarFraction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Scrollbar thumb ratio calculation.
 *
 * [scrollbarFraction] also runs during the very first composition, before the content has
 * been measured. It therefore has to stay safe when the scrollable range is still zero or
 * absurdly large, and when the scroll offset already sits past the end of the track.
 */
class ScrollbarTest {

    /** Checks a single (offset, range) pair against the ratio it must produce. */
    private fun verifyRatio(offset: Int, range: Int, expected: Float) {
        assertEquals(
            "offset=$offset, range=$range",
            expected,
            scrollbarFraction(offset, range),
            0f
        )
    }

    @Test
    fun `non scrolling content reports the start of the track`() {
        verifyRatio(offset = 0, range = 0, expected = 0f)
    }

    @Test
    fun `a range collapsing to zero still reports the start`() {
        verifyRatio(offset = 120, range = 0, expected = 0f)
    }

    @Test
    fun `the ratio grows linearly with the offset`() {
        verifyRatio(offset = 0,   range = 400, expected = 0f)
        verifyRatio(offset = 200, range = 400, expected = 0.5f)
        verifyRatio(offset = 400, range = 400, expected = 1f)
    }

    @Test
    fun `an unmeasured range cannot push the ratio out of bounds`() {
        verifyRatio(offset = 0, range = Int.MAX_VALUE, expected = 0f)
    }

    @Test
    fun `an offset beyond the end clamps to the end of the track`() {
        verifyRatio(offset = 500, range = 400, expected = 1f)
    }
}