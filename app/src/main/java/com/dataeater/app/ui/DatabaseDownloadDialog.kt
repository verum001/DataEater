package com.dataeater.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dataeater.app.data.*

@Composable
fun DatabaseDownloadDialog(catalog: DatabaseDownloads.CatalogState, download: DatabaseDownloads.State,
    files: List<DatabaseFiles.DatabaseFile>, storage: Boolean, onRefresh: () -> Unit,
    onDownload: (DatabaseCatalog.Entry) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Download databases") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Download once, search offline. Latest published versions from DataEater-Databases.")
            if (catalog.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (catalog.error.isNotBlank()) Text(catalog.error)
            if (!catalog.loading && catalog.error.isBlank() && catalog.entries.isEmpty()) Text("No databases are published yet.")
            for (entry in catalog.entries) {
                val selected = download.databaseId == entry.id
                val installed = files.any { it.file.name == entry.fileName }
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text("${entry.version} · ${entry.bytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
                key(entry.id) {
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    if (entry.description.isNotBlank()) {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics {
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        }) { Text(if (expanded) "Hide description" else "Show description") }
                        if (expanded) Text(entry.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
                when {
                    selected && download.active -> {
                        LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth())
                        Text(download.message)
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                    installed -> Text("Installed", color = MaterialTheme.colorScheme.primary)
                    else -> {
                        if (selected) Text(download.message)
                        OutlinedButton(onClick = { onDownload(entry) }, enabled = !download.active && !catalog.loading) {
                            Text(if (!storage) "Grant storage access" else if (selected) "Retry" else "Download")
                        }
                    }
                }
                HorizontalDivider()
            }
            TextButton(onClick = onRefresh, enabled = !catalog.loading) { Text("Refresh") }
            Text("Saved in the DataEater folder. Manuals retain their own terms. Downloads may use mobile data.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
