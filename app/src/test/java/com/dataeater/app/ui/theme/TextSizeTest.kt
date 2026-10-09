package com.dataeater.app.ui.theme

import androidx.compose.material3.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scaling every text style by the chosen size.
 *
 * THE FAILURE THIS GUARDS AGAINST
 * -------------------------------
 * A font-size setting that scales some of the text and not the rest. That
 * produces no crash and no error: the body text grows, the captions stay
 * small, and the app looks subtly wrong in a way nobody can name.
 *
 * So the test counts the styles rather than checking a few. If a seventeenth
 * style is added to Material later and left out of [scaledTypography], the
 * count below no longer matches and this test fails.
 *
 * The captions are the reason this setting exists at all — the citation under
 * an answer and the warning about an unchecked answer are both `labelSmall` —
 * so their scaling is asserted by name as well as in the total.
 */
class TextSizeTest {

    private val base = Typography()

    @Test
    fun everyStyleIsScaled() {
        val scaled = scaledTypography(1.5f, base)

        // Material has fifteen text styles. All fifteen must move.
        val unchanged = listOf(
            "displayLarge", "displayMedium", "displaySmall",
            "headlineLarge", "headlineMedium", "headlineSmall",
            "titleLarge", "titleMedium", "titleSmall",
            "bodyLarge", "bodyMedium", "bodySmall",
            "labelLarge", "labelMedium", "labelSmall",
        ).filter { name ->
            styleValue(base, name) == styleValue(scaled, name)
        }

        assertTrue(
            "these styles did not scale: $unchanged",
            unchanged.isEmpty(),
        )
    }

    /**
     * The one that matters most.
     *
     * Citations and safety warnings are `labelSmall`. If scaling missed them,
     * the setting would enlarge the answers and leave the evidence small, which
     * is precisely backwards.
     */
    @Test
    fun theSmallestCaptionsScaleToo() {
        val scaled = scaledTypography(1.3f, base)

        assertNotEquals(base.labelSmall.fontSize, scaled.labelSmall.fontSize)
        assertNotEquals(base.labelMedium.fontSize, scaled.labelMedium.fontSize)
        assertTrue(
            "the citation caption should grow, was ${base.labelSmall.fontSize} " +
                "now ${scaled.labelSmall.fontSize}",
            scaled.labelSmall.fontSize.value > base.labelSmall.fontSize.value,
        )
    }

    @Test
    fun sizesGrowWithTheScale() {
        val small = scaledTypography(0.85f, base).bodyLarge.fontSize.value
        val normal = scaledTypography(1.0f, base).bodyLarge.fontSize.value
        val large = scaledTypography(1.3f, base).bodyLarge.fontSize.value

        assertTrue("$small < $normal", small < normal)
        assertTrue("$normal < $large", normal < large)
    }

    /**
     * Line height has to grow with the font.
     *
     * A larger font in the same line height clips the descenders of "g", "y"
     * and "p" and touches the line above. This is the mistake that makes
     * scaled text look worse than no scaling.
     */
    @Test
    fun lineHeightGrowsToo() {
        val normal = scaledTypography(1.0f, base).bodyLarge
        val large = scaledTypography(1.3f, base).bodyLarge

        assertTrue(
            "line height did not grow: ${normal.lineHeight} -> ${large.lineHeight}",
            large.lineHeight.value > normal.lineHeight.value,
        )
    }

    /** The default size must change nothing at all, or every screen shifts. */
    @Test
    fun theDefaultScaleLeavesEverythingAlone() {
        val scaled = scaledTypography(TextSize.DEFAULT.scale, base)

        assertEquals(base.bodyLarge.fontSize, scaled.bodyLarge.fontSize)
        assertEquals(base.bodyLarge.lineHeight, scaled.bodyLarge.lineHeight)
        assertEquals(base.labelSmall.fontSize, scaled.labelSmall.fontSize)
    }

    @Test
    fun eachChoiceHasADifferentScale() {
        val scales = TextSize.entries.map { it.scale }

        assertEquals("four choices expected", 4, scales.size)
        assertEquals(
            "two choices share a scale, so one of them does nothing",
            scales.size,
            scales.toSet().size,
        )
    }

    /**
     * The largest size is capped.
     *
     * Past about 1.3 the composer's send button and the control row stop
     * fitting a narrow screen. An earlier bug already pushed the database
     * switch off the right edge, and a font setting that can do it again on any
     * phone would be a setting nobody should ship.
     */
    @Test
    fun theLargestSizeStaysWithinWhatTheScreenCanTake() {
        assertTrue(
            "the largest scale is ${TextSize.LARGEST.scale}, which will not fit",
            TextSize.LARGEST.scale <= 1.3f,
        )
        assertEquals(
            "nothing may be larger than the capped maximum",
            TextSize.LARGEST,
            TextSize.LARGEST.larger,
        )
    }

    @Test
    fun steppingUpAndDownStopsAtTheEnds() {
        // The order is SMALL, DEFAULT, LARGE, LARGEST.
        assertEquals(TextSize.DEFAULT, TextSize.SMALL.larger)
        assertEquals(TextSize.LARGE, TextSize.DEFAULT.larger)
        assertEquals(TextSize.LARGEST, TextSize.LARGE.larger)

        assertEquals(TextSize.LARGE, TextSize.LARGEST.smaller)
        assertEquals(TextSize.DEFAULT, TextSize.LARGE.smaller)
        assertEquals(TextSize.SMALL, TextSize.DEFAULT.smaller)

        // At the ends it stays put rather than wrapping round to the other one.
        assertEquals(TextSize.SMALL, TextSize.SMALL.smaller)
        assertEquals(TextSize.LARGEST, TextSize.LARGEST.larger)

        // And a full round trip returns to where it started.
        assertEquals(TextSize.SMALL, TextSize.SMALL.larger.smaller)
        assertEquals(TextSize.LARGEST, TextSize.LARGEST.smaller.larger)
    }

    /** A stored value that is no longer a valid choice must not crash the app. */
    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() {
        assertEquals(TextSize.DEFAULT, TextSize.fromName("ENORMOUS"))
        assertEquals(TextSize.DEFAULT, TextSize.fromName(null))
        assertEquals(TextSize.LARGE, TextSize.fromName("LARGE"))
    }

    private fun styleValue(type: Typography, name: String): Float = when (name) {
        "displayLarge" -> type.displayLarge.fontSize.value
        "displayMedium" -> type.displayMedium.fontSize.value
        "displaySmall" -> type.displaySmall.fontSize.value
        "headlineLarge" -> type.headlineLarge.fontSize.value
        "headlineMedium" -> type.headlineMedium.fontSize.value
        "headlineSmall" -> type.headlineSmall.fontSize.value
        "titleLarge" -> type.titleLarge.fontSize.value
        "titleMedium" -> type.titleMedium.fontSize.value
        "titleSmall" -> type.titleSmall.fontSize.value
        "bodyLarge" -> type.bodyLarge.fontSize.value
        "bodyMedium" -> type.bodyMedium.fontSize.value
        "bodySmall" -> type.bodySmall.fontSize.value
        "labelLarge" -> type.labelLarge.fontSize.value
        "labelMedium" -> type.labelMedium.fontSize.value
        "labelSmall" -> type.labelSmall.fontSize.value
        else -> error("style '$name' was added to Material and not to this test")
    }
}
