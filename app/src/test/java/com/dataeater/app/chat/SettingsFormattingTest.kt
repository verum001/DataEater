package com.dataeater.app.chat

import com.dataeater.app.formatMemory
import com.dataeater.app.formatSeconds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The small formatting helpers the Status card uses.
 *
 * WHY THESE ARE TESTED AT ALL
 * --------------------------
 * They are formatting, and formatting is not usually worth a test. These are,
 * for one reason: the Status card is the only place the app reports **measured**
 * numbers, and a number shown as "—" when it exists, or as "NaN s" when it does
 * not, makes the whole card untrustworthy. A technician deciding whether this
 * phone is fast enough is reading exactly these figures.
 *
 * The rules being locked in:
 *
 *   - no measurement ever renders as `NaN`, `null` or `Infinity`
 *   - a measurement that has not been taken renders as "—", not as a zero
 *   - a zero renders as "0.0 s", because zero is a real measurement
 */
class SettingsFormattingTest {

    // The same rule the Status card uses, so the test cannot pass while the
    // screen does the opposite. Previously this test re-implemented the format
    // and therefore proved nothing about it.
    private fun seconds(value: Double?): String = formatSeconds(value)

    @Test
    fun anUnmeasuredTimeShowsADash() {
        assertEquals("—", seconds(null))
    }

    /** Zero is a measurement. Showing "—" would be a lie about what happened. */
    @Test
    fun aZeroTimeShowsZeroNotADash() {
        assertEquals("0.0 s", seconds(0.0))
    }

    @Test
    fun timesShowOneDecimalPlace() {
        assertEquals("21.4 s", seconds(21.44))
        assertEquals("2.9 s", seconds(2.94))
        assertEquals("90.0 s", seconds(90.0))
    }

    @Test
    fun aVerySmallTimeIsStillVisible() {
        assertEquals("0.3 s", seconds(0.25))
    }

    /**
     * The case that would look worst on the card.
     *
     * A negative time means a clock went backwards mid-measurement, which can
     * genuinely happen on a phone. It must never reach the screen as "-2.1 s",
     * which reads as a measurement and is meaningless.
     */
    @Test
    fun anImpossibleTimeIsNotShownAsANegativeNumber() {
        val impossible = seconds(-2.1)
        assertTrue(
            "a negative measurement must not be shown as a number, was: $impossible",
            impossible == "—",
        )
    }

    @Test
    fun memoryIsShownInMegabytes() {
        assertEquals("1107 MB", formatMemory(1107))
        assertEquals("0 MB", formatMemory(0))
        assertEquals("—", formatMemory(null))
    }

    @Test
    fun anImpossibleMemoryReadingIsNotShownAsANegativeNumber() {
        assertEquals("—", formatMemory(-1))
    }
}
