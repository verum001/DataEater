package com.dataeater.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The DataEater palette: warm neutral surfaces, one indigo accent.
 *
 * WHERE THESE COME FROM
 * ---------------------
 * Taken from the interface mockups: a warm off-white canvas rather than pure
 * white, indigo for anything the user can act on, and a single accent used
 * sparingly. A diagnostic tool is read for a long time, often outdoors, so
 * the priority is calm contrast rather than a lot of colour.
 *
 * WHY WARM, NOT GREY
 * ------------------
 * Pure white on a phone at full brightness is harsh over a long session. The
 * canvas is very slightly warm so that black text on it settles rather than
 * glares, and so the indigo accent does not look like a bruise next to it.
 *
 * TWO ROLES, ONE HUE
 * ------------------
 * Indigo means "this is interactive" everywhere in the app: the primary button,
 * a selected item, a link, a focused field. Because the accent is never
 * decorative, a technician can tell at a glance what can be tapped.
 *
 * The single exception is `Warning`, a deep amber. It is reserved for exactly
 * one thing: a problem the user must notice, such as a locked database. Using
 * it for anything else would train the eye to ignore it.
 */

// ---------------------------------------------------------------------------
// Indigo, the accent
// ---------------------------------------------------------------------------

val IndigoLight = Color(0xFF4B49C4)
val IndigoOnLight = Color(0xFFFFFFFF)
val IndigoContainerLight = Color(0xFFE3E1FF)
val IndigoOnContainerLight = Color(0xFF0C0663)

val IndigoDark = Color(0xFFC0C1FF)
val IndigoOnDark = Color(0xFF1A1B8F)
val IndigoContainerDark = Color(0xFF3333AB)
val IndigoOnContainerDark = Color(0xFFE3E1FF)

// ---------------------------------------------------------------------------
// Warm neutrals, light
// ---------------------------------------------------------------------------

/** The canvas. Warm off-white, never pure white. */
val CanvasLight = Color(0xFFFDF8F4)

/** Cards and fields sit very slightly above the canvas. */
val SurfaceLight = Color(0xFFFFFBF7)
val SurfaceRaisedLight = Color(0xFFFFFFFF)
val SurfaceSunkenLight = Color(0xFFF4EEE7)

val OnSurfaceLight = Color(0xFF1C1B1E)
val OnSurfaceMutedLight = Color(0xFF5C5852)
val OutlineLight = Color(0xFF8A837B)
val OutlineVariantLight = Color(0xFFE6DFD7)

val SecondaryLight = Color(0xFF5D5A72)
val SecondaryContainerLight = Color(0xFFE4E1F0)

// ---------------------------------------------------------------------------
// Warm neutrals, dark
// ---------------------------------------------------------------------------

val CanvasDark = Color(0xFF151316)
val SurfaceDark = Color(0xFF1D1B1F)
val SurfaceRaisedDark = Color(0xFF262429)
val SurfaceSunkenDark = Color(0xFF100E12)

val OnSurfaceDark = Color(0xFFEAE1DA)
val OnSurfaceMutedDark = Color(0xFFB6ADA6)
val OutlineDark = Color(0xFF8F8780)
val OutlineVariantDark = Color(0xFF49443F)

val SecondaryDark = Color(0xFFC7C2DC)
val SecondaryContainerDark = Color(0xFF454159)
val OnSecondaryDark = Color(0xFFE4E1F0)

// ---------------------------------------------------------------------------
// The one warning colour
// ---------------------------------------------------------------------------

/**
 * Used for exactly one thing: something the user must notice.
 *
 * Currently that means a locked database. It is not used for "unread" counts,
 * badges or decoration, because a colour that means "important" stops meaning
 * it the moment it is used for something unimportant.
 */
val WarningLight = Color(0xFF8A4B00)
val WarningContainerLight = Color(0xFFFFDDB8)
val OnWarningContainerLight = Color(0xFF2C1600)

val WarningDark = Color(0xFFFFB86B)
val WarningContainerDark = Color(0xFF6A3800)
val OnWarningContainerDark = Color(0xFFFFDDB8)

// ---------------------------------------------------------------------------
// Legacy names, kept so nothing else has to change at once
// ---------------------------------------------------------------------------

/**
 * The Android Studio template colours.
 *
 * Kept only so an old reference still compiles. New code must use the names
 * above. These are NOT part of the app's look and should not be reintroduced.
 */
val Purple80 = IndigoDark
val PurpleGrey80 = SecondaryDark
val Pink80 = WarningDark
val Purple40 = IndigoLight
val PurpleGrey40 = SecondaryLight
val Pink40 = WarningLight