package com.dataeater.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

/** Compact action buttons retain a 48 dp touch target and grow for larger text. */
@Composable
fun DataEaterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
            .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected == true) colours.primary else colours.outlineVariant),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected == true) colours.primary else colours.surfaceContainerHighest,
            contentColor = if (selected == true) colours.onPrimary else colours.onSurface,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}
