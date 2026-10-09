package com.dataeater.app.ui.theme

/**
 * Typography is built in `TextSize.kt`, not here.
 *
 * This file used to hold a `Typography` that overrode only `bodyLarge` and left
 * the other fifteen styles to Material's defaults. That could not be scaled: a
 * font-size setting would have enlarged the body text while leaving the
 * captions — the citations and the "not checked against any document" warning
 * — exactly as small as before, which are the texts that most need to be read.
 *
 * `scaledTypography(scale)` now starts from Material's defaults and scales every
 * style, so all of them move together.
 *
 * The old override also set `letterSpacing = 0.5.sp` on body text, which is
 * wider than Material's own and was making paragraphs look loosely spaced
 * against every other text in the app. It is not carried over.
 */
