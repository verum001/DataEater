package com.dataeater.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dataeater.app.ai.*

data class ModelDownloadUiState(
    val models: List<ModelFiles.ModelFile>, val download: ModelDownloads.State,
    val deviceInfo: DeviceMemory.Info, val isBusy: Boolean, val hasStorageAccess: Boolean,
    val onDownloadModel: (ModelCatalog.Entry) -> Unit, val onCancelDownload: () -> Unit,
    val onLoadModel: (ModelFiles.ModelFile) -> Unit, val loadedModelName: String?,
)

/** Short, scrollable rows remain usable on small screens and with enlarged text. */
@Composable
fun ModelDownloadDialog(state: ModelDownloadUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Get models") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Download once, use offline. Models from Hugging Face.", style = MaterialTheme.typography.bodyMedium)
                for (entry in ModelCatalog.entries) {
                    val installedModel = state.models.find { it.file.name == entry.fileName }
                    val installed = installedModel != null
                    val selected = state.download.modelId == entry.id
                    val active = selected && state.download.active
                    val fits = entry.fitsMemory(state.deviceInfo.totalMegabytes)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text("${entry.sizeLabel} · ${entry.description}", style = MaterialTheme.typography.bodySmall)
                        if (!fits && !installed) Text("Needs more memory than this device has.", style = MaterialTheme.typography.bodySmall)
                        when {
                            active -> {
                                LinearProgressIndicator(progress = { state.download.progress }, modifier = Modifier.fillMaxWidth())
                                Text(state.download.message, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = state.onCancelDownload) { Text("Cancel") }
                            }
                            installed -> {
                                Text("Installed", color = MaterialTheme.colorScheme.primary)
                                if (state.loadedModelName != installedModel.displayName) {
                                    TextButton(onClick = { state.onLoadModel(installedModel); onDismiss() }, enabled = !state.isBusy && fits) { Text("Use model") }
                                }
                            }
                            else -> {
                                if (selected && state.download.message.isNotBlank()) Text(state.download.message, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { state.onDownloadModel(entry) }, enabled = !state.download.active && fits && !state.isBusy) {
                                    Text(if (!state.hasStorageAccess) "Grant storage access" else if (selected) "Retry" else "Download")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                for (entry in ModelCatalog.unavailable) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(entry.description, style = MaterialTheme.typography.bodySmall)
                    Text("Not supported yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                }
                Text("Chat and documents stay on your device. Downloads may use mobile data.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
