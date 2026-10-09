package com.dataeater.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dataeater.app.ai.*
import com.dataeater.app.ui.theme.DataEaterCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyBehaviorCard(value: ReplyBehavior, busy: Boolean, onChange: (ReplyBehavior) -> Unit) {
    DataEaterCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Replies", style = MaterialTheme.typography.titleMedium)
            Text("Database strictness")
            // Wrap at large font sizes rather than clipping a fixed segmented row.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatabaseStrictness.entries.forEach { level ->
                    FilterChip(selected = value.strictness == level, onClick = { onChange(value.copy(strictness = level)) }, enabled = !busy, label = { Text(level.label) })
                }
            }
            Text(value.strictness.description, style = MaterialTheme.typography.bodySmall)
            Text("Reply length")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplyLength.entries.forEach { level ->
                    FilterChip(selected = value.length == level, onClick = { onChange(value.copy(length = level)) }, enabled = !busy, label = { Text(level.label) })
                }
            }
            Text("Applies to local and online AI. Length is a guide; models may vary.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
