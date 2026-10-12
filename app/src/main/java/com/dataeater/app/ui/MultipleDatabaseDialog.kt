package com.dataeater.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultipleDatabaseDialog(names: List<String>, current: Set<String>, busy: Boolean,
    onApply: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(names, current) { mutableStateOf(current.intersect(names.toSet())) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text("Search in several databases") }) },
            bottomBar = {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { onApply(selected) }, enabled = selected.isNotEmpty() && !busy,
                        modifier = Modifier.weight(1f)) { Text("Search selected") }
                }
            }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Choose the manuals to search together. Locked databases need a saved valid Unlock Code.")
                Row {
                    TextButton(onClick = { selected = names.toSet() }, enabled = !busy) { Text("Select all") }
                    TextButton(onClick = { selected = emptySet() }, enabled = !busy) { Text("Clear") }
                }
                names.forEach { name ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                        selected = if (name in selected) selected - name else selected + name
                    }.padding(vertical = 8.dp)) {
                        Checkbox(checked = name in selected, onCheckedChange = null)
                        Text(name, modifier = Modifier.padding(top = 12.dp).weight(1f))
                    }
                }
                if (names.isEmpty()) Text("No databases found. Download or create a database first.")
            }
        }
    }
}
