package com.dataeater.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one card style used everywhere.
 *
 * WHY NOT JUST `Card(...)`
 * -----------------------
 * The mockups show surfaces that are separated by a soft hairline, not by a
 * hard outline and not by a drop shadow. Material's default `Card` gives a
 * tonal fill with no border, which on a warm canvas makes two adjacent cards
 * blur into one another — the user cannot tell where a section ends.
 *
 * So every card in DataEater goes through here, and gets:
 *
 *   * a rounded shape, matching the rounded text fields and buttons
 *   * a very slightly raised fill, so it sits above the canvas
 *   * a thin outline in `outlineVariant`, which is the "subtle separator" the
 *     mockups ask for
 *   * no shadow, because a shadow on a white background on a phone reads as
 *     dirt rather than depth
 *
 * One function means one place to change the look later.
 */

/** The corner radius every surface in the app shares. */
val DataEaterCornerRadius = 16.dp

/** Inside every card. Generous, because a technician taps with a thumb. */
val DataEaterCardPadding = 16.dp

@Composable
fun DataEaterCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(DataEaterCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

/** Standard inside padding, applied as a wrapper so cards cannot forget it. */
@Composable
fun DataEaterCardBody(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(DataEaterCardPadding)) {
        content()
    }
}

/** The gap between cards, and between a card and the text around it. */
val DataEaterGap = 12.dp