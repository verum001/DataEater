package com.dataeater.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.CancellationException
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
    var query by remember { mutableStateOf("") }
    var request by remember { mutableStateOf(0) }
    var entries by remember { mutableStateOf(ModelCatalog.recommended) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var onlyFits by remember { mutableStateOf(true) }
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(showAll, request) {
        loading = true; message = ""
        try {
            val result = if (showAll) HuggingFaceModels.search(query) else HuggingFaceModels.recommended(query)
            entries = if (showAll) result.entries + ModelCatalog.recommended.filter { entry ->
                query.isBlank() || listOf(entry.name, entry.repository, entry.remoteFileName).any { it.contains(query.trim(), ignoreCase = true) }
            } else result.entries
            if (result.incomplete) message = if (showAll) "Showing recent matches. Some repositories could not be checked; narrow your search or refresh."
                else "Could not check every update. Showing reviewed versions."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = "Could not load models. Check your connection and tap Refresh." }
        finally { loading = false }
    }
    val visible = (listOfNotNull(state.download.entry) + entries)
        .filter { showAll || ModelCatalog.isRecommended(it) }
        .distinctBy { Triple(it.repository, it.remoteFileName, it.sha256) }
        .map { candidate -> ModelCatalog.recommendationFor(candidate)?.let { reviewed ->
            candidate.copy(name = reviewed.name, description = reviewed.description, documentTested = true)
        } ?: candidate }
        .filter { !onlyFits || it.fitsMemory(state.deviceInfo.totalMegabytes) || it.id == state.download.modelId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download models") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Download once, use offline. Models from Hugging Face.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Model files are separate downloads. They are not distributed or reviewed by F-Droid; check the publisher and licence shown for each model before downloading.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recommended", Modifier.weight(1f), color = if (!showAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = showAll, onCheckedChange = { showAll = it },
                        modifier = Modifier.semantics { contentDescription = "Show all models" })
                    Text("All", color = if (showAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (showAll) "Browse recent public models, including versions not yet tested here."
                    else "Selected from limited document tests. Newer, untested versions may appear in All.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = query, onValueChange = { query = it.take(120) }, label = { Text("Model or publisher") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { request++ }, enabled = !loading) { Text("Search") }
                    TextButton(onClick = { request++ }, enabled = !loading) { Text("Refresh") }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = onlyFits, onCheckedChange = { onlyFits = it })
                    Text("Estimated to fit this phone", style = MaterialTheme.typography.bodySmall)
                }
                Text("File format and memory estimates do not guarantee that a model will load or answer correctly.", style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                if (state.download.active && state.download.entry?.let { !showAll && !ModelCatalog.isRecommended(it) } == true) {
                    Text("Current download: ${state.download.entry?.name.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { state.download.progress }, modifier = Modifier.fillMaxWidth())
                    Text(state.download.message, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = state.onCancelDownload) { Text("Cancel") }
                }
                if (!loading && visible.isEmpty()) Text("No downloadable matches. Try another name or turn off the memory filter.")
                for (entry in visible) {
                    val installedModel = state.models.find { it.file.name == entry.fileName }
                    val installed = installedModel != null
                    val selected = state.download.modelId == entry.id
                    val active = selected && state.download.active
                    val fits = entry.fitsMemory(state.deviceInfo.totalMegabytes)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text("${entry.sizeLabel} · " + if (entry.documentTested) "Tested version" else "Untested version", style = MaterialTheme.typography.bodySmall)
                        if (entry.updatedAt.isNotBlank()) Text("Updated ${entry.updatedAt.take(10)}", style = MaterialTheme.typography.bodySmall)
                        Text("${entry.repository} · ${entry.license}", style = MaterialTheme.typography.bodySmall)
                        ModelDescription(entry)
                        if (!fits) Text("May need more memory than this phone has.", style = MaterialTheme.typography.bodySmall)
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
                                OutlinedButton(onClick = { state.onDownloadModel(entry) }, enabled = !loading && !state.download.active && !state.isBusy) {
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

@Composable
private fun ModelDescription(entry: ModelCatalog.Entry) {
    var expanded by remember(entry.repository, entry.revision) { mutableStateOf(false) }
    var publisherText by remember(entry.repository, entry.revision) { mutableStateOf<String?>(null) }
    var loading by remember(entry.repository, entry.revision) { mutableStateOf(false) }
    var attempt by remember(entry.repository, entry.revision) { mutableStateOf(0) }
    LaunchedEffect(expanded, attempt) {
        if (!expanded || publisherText?.isNotBlank() == true) return@LaunchedEffect
        loading = true
        try { publisherText = HuggingFaceModels.description(entry) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { publisherText = "" }
        finally { loading = false }
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics {
        stateDescription = if (expanded) "Expanded" else "Collapsed"
    }) {
        Text(if (expanded) "Hide description" else "Show description", Modifier.weight(1f))
        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
    if (expanded) {
        Text(entry.description, style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (!publisherText.isNullOrBlank()) {
            Text("From Hugging Face", style = MaterialTheme.typography.labelMedium)
            Text(publisherText.orEmpty(), style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Publisher description is unavailable.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { attempt++ }) { Text("Retry description") }
        }
    }
}
