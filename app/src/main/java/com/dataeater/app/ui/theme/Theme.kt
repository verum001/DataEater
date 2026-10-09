package com.dataeater.app.ui.theme

import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Black, named so the two schemes read the same way. */
private val Black = Color(0xFF000000)

/**
 * The colour scheme, chosen by hand.
 *
 * WHY NOT THE WALLPAPER'S COLOURS
 * -------------------------------
 * Android can tint an app from the user's wallpaper. It was on, and it made
 * DataEater look violet — because the test phone's wallpaper was violet.
 *
 * That is not only an aesthetic complaint. The accent colour carries meaning
 * here: **indigo means "you can tap this"**, and the caution colour means "this
 * answer was not checked". A colour derived from a wallpaper cannot promise
 * either, and an app for reading numbers outdoors cannot have its accent
 * change with someone's home screen.
 *
 * So `dynamicColor` defaults to off, and the two schemes below are built for
 * real rather than being stubs. The option still exists for anyone who wants
 * to try it.
 */

private val LightColors = lightColorScheme(
    primary = IndigoLight,
    onPrimary = IndigoOnLight,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = IndigoOnContainerLight,

    secondary = SecondaryLight,
    onSecondary = IndigoOnLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = IndigoOnContainerLight,

    tertiary = WarningLight,
    onTertiary = IndigoOnLight,
    tertiaryContainer = WarningContainerLight,
    onTertiaryContainer = IndigoOnContainerLight,

    background = CanvasLight,
    onBackground = OnSurfaceLight,
    surface = CanvasLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceSunkenLight,
    onSurfaceVariant = OnSurfaceMutedLight,

    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = OnSurfaceLight,

)

private val DarkColors = darkColorScheme(
    primary = IndigoDark,
    onPrimary = IndigoOnDark,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = IndigoOnContainerDark,

    secondary = SecondaryDark,
    onSecondary = IndigoOnDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = IndigoOnContainerDark,

    tertiary = WarningDark,
    onTertiary = IndigoOnDark,
    tertiaryContainer = WarningContainerDark,
    onTertiaryContainer = IndigoOnContainerDark,

    background = CanvasDark,
    onBackground = OnSurfaceDark,
    surface = CanvasDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceSunkenDark,
    onSurfaceVariant = OnSurfaceMutedDark,

    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = OnSurfaceDark,

)

/**
 * The three appearance choices.
 *
 * SYSTEM is the default and is not a fourth scheme: it means "do what the
 * phone is already doing", which is right for almost everyone because it
 * changes with the time of day along with every other app.
 */
enum class Appearance { SYSTEM, LIGHT, DARK }

/**
 * Remembers the chosen appearance across restarts.
 *
 * SharedPreferences rather than `rememberSaveable`, because this has to
 * outlive the process: a technician who chose dark for a basement should not
 * find a white screen the next morning.
 *
 * KEY is in the app's private preferences, which no other app can read, and it
 * holds one enum name. Nothing about the user.
 */
class AppearanceStore(context: Context) {

    private val preferences =
        context.getSharedPreferences("dataeater-appearance", Context.MODE_PRIVATE)

    /** The stored choice, or SYSTEM if nothing was ever chosen. */
    fun read(): Appearance =
        runCatching { Appearance.valueOf(preferences.getString(KEY, null) ?: "") }
            .getOrDefault(Appearance.SYSTEM)

    fun write(appearance: Appearance) {
        preferences.edit().putString(KEY, appearance.name).apply()
    }

    private companion object {
        const val KEY = "appearance"
    }
}

@Composable
fun DataEaterTheme(
    appearance: Appearance = Appearance.SYSTEM,

    /**
     * How large the text is.
     *
     * Applied here, above the whole screen, rather than per component. A font
     * size that some screens honoured would be worse than none.
     */
    textSize: TextSize = TextSize.DEFAULT,

    /**
     * Off by default. See the note above: the app should look like itself
     * rather than like the wallpaper.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // Typography is built per size rather than being a single constant,
        // because it has to be scaled from Material's defaults — see
        // `scaledTypography` for why scaling only bodyLarge would be wrong.
        typography = scaledTypography(textSize.scale, MaterialTypography()),
        content = content,
    )
}
