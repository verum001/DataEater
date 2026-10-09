package com.dataeater.app.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * How large the text is.
 *
 * WHY AN ENUM AND NOT A SLIDER
 * ----------------------------
 * A slider offers more precision than this needs and less predictability. Four
 * named sizes are remembered as words rather than numbers, which matters when
 * the setting has to survive a reinstall of somebody's muscle memory.
 *
 * WHY THIS IS WORTH HAVING AT ALL
 * -------------------------------
 * The intended reader is a technician on a phone, outdoors, possibly wearing
 * gloves, possibly with the sun on the screen. The default size is right for
 * somebody sitting at a desk and wrong for them. Android's own font-size
 * setting would cover this, but it is set once for every app and DataEater's
 * text is small by design — citations and page numbers are `labelSmall`.
 *
 * WHAT IT DOES NOT CHANGE
 * -----------------------
 * Padding and icon sizes are left alone. Scaling those as well would push the
 * control row off the edge of a narrow screen, and the earlier bug where the
 * database switch fell off the right edge was caused by exactly that kind of
 * over-eager scaling.
 */
enum class TextSize(val scale: Float, val label: String) {

    /** Smaller than default. For a small screen, or a preference. */
    SMALL(0.85f, "Small"),

    /** The shipped size. */
    DEFAULT(1.0f, "Default"),

    /** For reading a phone at arm's length, or in bright light. */
    LARGE(1.15f, "Large"),

    /**
     * For genuinely difficult conditions.
     *
     * Capped at 1.3 rather than open-ended: past that the composer's send
     * button and the control row no longer fit, and a setting that breaks the
     * screen is not a setting anybody should offer.
     */
    LARGEST(1.3f, "Largest");

    /** The scale for the next size up, or this one if already the largest. */
    val larger: TextSize
        get() = entries.getOrElse(ordinal + 1) { this }

    /** The scale for the next size down, or this one if already the smallest. */
    val smaller: TextSize
        get() = entries.getOrElse(ordinal - 1) { this }

    companion object {
        /** The stored value, or [DEFAULT] if nothing was ever chosen. */
        fun fromName(name: String?): TextSize =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Remembers the chosen text size across restarts.
 *
 * SharedPreferences in the app's private folder, holding one enum name. It has
 * to outlive the process: somebody who set the text large for a job site does
 * not want to set it again tomorrow.
 */
class TextSizeStore(context: Context) {

    private val preferences =
        context.getSharedPreferences("dataeater-text-size", Context.MODE_PRIVATE)

    fun read(): TextSize = TextSize.fromName(preferences.getString(KEY, null))

    fun write(size: TextSize) {
        preferences.edit().putString(KEY, size.name).apply()
    }

    private companion object {
        const val KEY = "text_size"
    }
}

/**
 * Every style of the default typography, with its text scaled.
 *
 * WHY NOT JUST `bodyLarge.copy(fontSize = ...)`
 * ---------------------------------------------
 * Because that would scale one style and leave the rest. The captions that
 * matter here — the citation under an answer, the warning about an unchecked
 * answer, the status line beside the composer — are all `labelSmall` and
 * `labelMedium`. Scaling only the body would make the important text smaller
 * relative to everything else, which is the opposite of the point.
 *
 * WHY LINE HEIGHT IS SCALED TOO
 * -----------------------------
 * A larger font in the same line height clips descenders and touches ascenders
 * to the line above. Material's ratios are good, so the ratio is kept and only
 * the multiplier applied.
 *
 * WHY IT IS A PURE FUNCTION
 * -------------------------
 * So the scaling can be tested on a computer with no phone and no Android
 * runtime. A font-size setting that silently stops scaling four of the twelve
 * styles would be very hard to see and very easy to do.
 */
fun scaledTypography(scale: Float, base: Typography = Typography()): Typography =
    Typography(
        displayLarge = base.displayLarge.scaled(scale),
        displayMedium = base.displayMedium.scaled(scale),
        displaySmall = base.displaySmall.scaled(scale),

        headlineLarge = base.headlineLarge.scaled(scale),
        headlineMedium = base.headlineMedium.scaled(scale),
        headlineSmall = base.headlineSmall.scaled(scale),

        titleLarge = base.titleLarge.scaled(scale),
        titleMedium = base.titleMedium.scaled(scale),
        titleSmall = base.titleSmall.scaled(scale),

        bodyLarge = base.bodyLarge.scaled(scale),
        bodyMedium = base.bodyMedium.scaled(scale),
        bodySmall = base.bodySmall.scaled(scale),

        labelLarge = base.labelLarge.scaled(scale),
        labelMedium = base.labelMedium.scaled(scale),
        labelSmall = base.labelSmall.scaled(scale),
    )

/** One style, with its size and line height multiplied. Letter spacing is left alone. */
private fun TextStyle.scaled(scale: Float): TextStyle =
    copy(
        fontSize = (fontSize.value * scale).sp,
        lineHeight = (lineHeight.value * scale).sp,
    )

/**
 * Multiplies a unit, used only by the tests to state the expectation.
 *
 * A tiny helper rather than an inline calculation in each test, so the rounding
 * behaviour is stated once.
 */
internal fun scaledUnit(unit: TextUnit, scale: Float): Float = unit.value * scale
