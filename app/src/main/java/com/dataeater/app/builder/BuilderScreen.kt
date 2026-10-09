package com.dataeater.app.builder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dataeater.app.ui.theme.DataEaterCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BuilderScreen(model: BuilderViewModel, canUseDatabase: Boolean, onDatabaseReady: (File) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val state = model.state
    var help by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable(state.task) { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var tasks by remember { mutableStateOf(false) }
    var replacing by rememberSaveable { mutableStateOf(false) }
    var legacyReview by rememberSaveable { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var privateExport by remember { mutableStateOf<PublisherVault.Item?>(null) }
    var batchEditor by remember { mutableStateOf<Editor?>(null) }
    var editorError by remember { mutableStateOf<String?>(null) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) model.documents(it) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(model::documentFolder) }
    val database = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::database) }
    val key = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.importKey(it, model.pendingKeyKind) } }
    val reviewZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importReview) }
    val reviewFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(model::importReviewFolder) }
    val batchFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pending = model.pendingBatch
        if (uri != null && pending != null) model.importBatch(pending.first, pending.second, uri)
        model.pendingBatch = null
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pending = model.pendingExport
        if (uri != null && pending != null) model.export(uri, pending.file, pending.item, pending.reviewId, pending.prompt)
        model.pendingExport = null
    }
    fun requestExport(request: BuilderViewModel.Export, name: String) { model.pendingExport = request; save.launch(name) }
    fun back() {
        if (state.busy) confirmExit = true else { model.clearForm(); onBack() }
    }
    BackHandler { if (help) help = false else if (batchEditor != null) batchEditor = null else back() }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("Operation running") },
        text = { Text("Cancel the operation before leaving. Existing databases and keys will be kept.") },
        confirmButton = { TextButton(onClick = { model.cancel(); confirmExit = false }) { Text("Cancel operation") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep working") } })
    if (confirmReplace) AlertDialog(onDismissRequest = { confirmReplace = false }, title = { Text("Use a new content key?") },
        text = { Text("Old access codes will not unlock this new copy. The old key and database are kept.") },
        confirmButton = { TextButton(onClick = { confirmReplace = false; model.run(true, legacyReview) }) { Text("Create new copy") } },
        dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } })
    privateExport?.let { item -> AlertDialog(onDismissRequest = { privateExport = null }, title = { Text("Export private key?") },
        text = { Text("Keep the exported file in a secure backup. Never publish it or send it to a customer. Uninstalling DataEater removes keys stored only in the app.") },
        confirmButton = { TextButton(onClick = {
            privateExport = null
            requestExport(BuilderViewModel.Export(item = item), BuilderCore.id(item.name) + if (item.kind == "signing") ".key" else ".secret")
        }) { Text("Save backup") } }, dismissButton = { TextButton(onClick = { privateExport = null }) { Text("Cancel") } }) }
    if (help) BuilderHelp(onBack = { help = false })
    batchEditor?.let { editor -> BuilderEditor(editor, state.busy, editorError, onDismiss = { batchEditor = null; editorError = null }, onSave = { text ->
        try {
            if (editor.notes) model.saveNote(editor.reviewId!!, editor.name, text)
            else {
                val expected = BuilderReview.parse(model.readBatch(editor.reviewId!!, editor.name, true))
                val actual = BuilderReview.parse(text)
                require(actual.map { it.number } == expected.map { it.number } && expected.zip(actual).all { it.first.text.isBlank() || it.second.text.isNotBlank() }) { "Keep all page markers and complete page text." }
                model.saveBatch(editor.reviewId, editor.name, text)
            }
            batchEditor = null; editorError = null
        } catch (error: Exception) { editorError = error.message ?: "Check the page markers." }
    }) }
    Scaffold(topBar = { TopAppBar(title = { Text("Create database") }, navigationIcon = {
        IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }, actions = { TextButton(onClick = { help = true }) { Text("Help") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                OutlinedButton(onClick = { tasks = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(state.task.title, Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, "Choose task")
                }
                DropdownMenu(expanded = tasks, onDismissRequest = { tasks = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                    for (task in BuilderViewModel.Task.entries) DropdownMenuItem(text = { Text(task.title) }, onClick = {
                        tasks = false; replacing = false; legacyReview = false; model.task(task)
                    })
                }
            }
            Text(state.task.description, style = MaterialTheme.typography.bodyMedium)
            DataEaterCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state.task) {
                        BuilderViewModel.Task.BUILD, BuilderViewModel.Task.REVIEW -> {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { documents.launch(arrayOf("*/*")) }, enabled = !state.busy) { Text("Add documents") }
                                OutlinedButton(onClick = { folder.launch(null) }, enabled = !state.busy) { Text("Choose folder") }
                            }
                            Text("PDF, text or Markdown. Scanned PDFs need OCR elsewhere.", style = MaterialTheme.typography.bodySmall)
                            Text("${state.inputs.size} document(s) selected")
                            for (input in state.inputs.take(20)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(input.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { model.removeInput(input.uri) }, enabled = !state.busy) { Text("Remove") }
                                }
                            }
                            if (state.inputs.size > 20) Text("And ${state.inputs.size - 20} more documents.")
                            if (state.task == BuilderViewModel.Task.BUILD) Field("Database name", "name", model)
                        }
                        BuilderViewModel.Task.REVIEWED -> {
                            Choice("Review workspace", state.reviews.firstOrNull { it.id == state.reviewId }?.name,
                                state.reviews.map { it.id to it.name }, !state.busy, model::review)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { reviewZip.launch(arrayOf("*/*")) }, enabled = !state.busy) { Text("Import review ZIP") }
                                OutlinedButton(onClick = { reviewFolder.launch(null) }, enabled = !state.busy) { Text("Import review folder") }
                            }
                            Field("Database name", "name", model)
                        }
                        BuilderViewModel.Task.INSPECT, BuilderViewModel.Task.ENCRYPT, BuilderViewModel.Task.LICENCE -> {
                            OutlinedButton(onClick = { database.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Text(state.database?.name ?: "Choose database")
                            }
                            if (state.task != BuilderViewModel.Task.INSPECT) {
                                KeyChoice("Signing key", "signing", model, onImport = {
                                    model.pendingKeyKind = "signing"; key.launch(arrayOf("*/*"))
                                })
                                if (state.task == BuilderViewModel.Task.ENCRYPT) {
                                    Field("Creator name", "creator", model); Field("Contact", "contact", model)
                                } else {
                                    KeyChoice("Content key", "content", model, onImport = {
                                        model.pendingKeyKind = "content"; key.launch(arrayOf("*/*"))
                                    })
                                    Field("Phone request code", "request", model, lines = 3)
                                }
                            }
                        }
                        BuilderViewModel.Task.KEY -> {
                            Field("Key name", "keyName", model)
                            Text("The private key stays in the app. A new key keeps older keys intact. Save a backup after creating it.")
                        }
                    }
                    if (state.task != BuilderViewModel.Task.INSPECT && state.task != BuilderViewModel.Task.KEY) {
                        TextButton(onClick = { advanced = !advanced }, enabled = !state.busy) { Text(if (advanced) "Hide advanced options" else "Advanced options") }
                        if (advanced) when (state.task) {
                            BuilderViewModel.Task.BUILD, BuilderViewModel.Task.REVIEWED -> {
                                Field("Description", "description", model, lines = 2)
                                Field("Language code", "language", model); Field("Content licence", "license", model)
                                Field("Document publisher", "publisher", model); Field("Passage size", "target", model)
                                Text("Target characters per passage. Whole paragraphs and table rows may be longer.", style = MaterialTheme.typography.bodySmall)
                                if (state.task == BuilderViewModel.Task.BUILD) {
                                    Row { Checkbox(legacyReview, onCheckedChange = { legacyReview = it }, enabled = !state.busy); Text("Prepare text review instead", Modifier.padding(top = 12.dp)) }
                                    if (legacyReview) Field("Batch size", "batch", model)
                                }
                            }
                            BuilderViewModel.Task.REVIEW -> Field("Batch size", "batch", model)
                            BuilderViewModel.Task.ENCRYPT -> {
                                Field("Public signing key (optional)", "publicKey", model, lines = 3)
                                Text("Paste a public key to protect a database without importing its private signing key.", style = MaterialTheme.typography.bodySmall)
                                KeyChoice("Reuse content key (optional)", "content", model, onImport = {
                                    model.pendingKeyKind = "content"; key.launch(arrayOf("*/*"))
                                })
                                TextButton(onClick = { model.secret(null) }, enabled = !state.busy) { Text("Use a fresh content key") }
                                Row { Checkbox(replacing, onCheckedChange = { replacing = it }, enabled = !state.busy); Text("New key for this copy", Modifier.padding(top = 12.dp)) }
                            }
                            BuilderViewModel.Task.LICENCE -> Field("Expiry", "expiry", model)
                            else -> Unit
                        }
                    }
                    Button(onClick = { if (replacing && state.task == BuilderViewModel.Task.ENCRYPT) confirmReplace = true else model.run(false, legacyReview) },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(state.task.action) }
                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        OutlinedButton(onClick = model::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel operation") }
                    }
                    if (state.status.isNotBlank()) Text(state.status)
                }
            }
            val review = state.result?.review ?: state.reviews.firstOrNull { it.id == state.reviewId }
            if (review != null) {
                DataEaterCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Review: ${review.name}", fontWeight = FontWeight.Bold)
                        Text("Keep originals unchanged. Check edits against the PDF, especially numbers and warnings.")
                        OutlinedButton(onClick = { requestExport(BuilderViewModel.Export(reviewId = review.id), BuilderCore.id(review.name) + "-review.zip") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Save review ZIP") }
                        OutlinedButton(onClick = { requestExport(BuilderViewModel.Export(prompt = true), "LLM_PROMPT.txt") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Save review prompt") }
                        val batches = remember(review.id, state.busy) { try { model.batches(review.id) } catch (_: Exception) { emptyList() } }
                        var selectedBatch by remember(review.id) { mutableStateOf<String?>(null) }
                        val selected = selectedBatch?.takeIf { it in batches } ?: batches.firstOrNull()
                        Choice("Batch", selected?.let { "${batches.indexOf(it) + 1} of ${batches.size}" },
                            batches.mapIndexed { index, name -> name to "Batch ${index + 1} of ${batches.size}" }, !state.busy) { selectedBatch = it }
                        selected?.let { name ->
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { try { batchEditor = Editor("Edit reviewed text", review.id, name, model.readBatch(review.id, name)) } catch (e: Exception) { editorError = e.message } }, enabled = !state.busy) { Text("Edit") }
                                TextButton(onClick = { try { batchEditor = Editor("Original text", review.id, name, model.readBatch(review.id, name, true), readOnly = true) } catch (e: Exception) { editorError = e.message } }, enabled = !state.busy) { Text("Original") }
                                TextButton(onClick = { model.pendingBatch = review.id to name; batchFile.launch(arrayOf("text/*", "application/octet-stream")) }, enabled = !state.busy) { Text("Import edit") }
                                TextButton(onClick = { batchEditor = Editor("Uncertainty notes", review.id, name, model.readNote(review.id, name), notes = true) }, enabled = !state.busy) { Text("Notes") }
                            }
                        }
                        Button(onClick = { model.review(review.id); model.task(BuilderViewModel.Task.REVIEWED) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Build reviewed text") }
                    }
                }
            }
            state.result?.let { result ->
                DataEaterCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        result.file?.let { file ->
                            Button(onClick = { requestExport(BuilderViewModel.Export(file = file), file.name) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Save file") }
                            if (file.extension == "dataeater") {
                                OutlinedButton(onClick = { model.useDatabase(file, onDatabaseReady) }, enabled = !state.busy && canUseDatabase, modifier = Modifier.fillMaxWidth()) { Text("Use in DataEater") }
                                if (!canUseDatabase) Text("Grant folder access in Settings to use this database, or save it to the DataEater folder.", style = MaterialTheme.typography.bodySmall)
                            } else if (file.extension == "lic") {
                                OutlinedButton(onClick = { batchEditor = Editor("Access code", null, file.name, file.readText(), readOnly = true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("View access code") }
                            }
                        }
                        result.signing?.let { item -> OutlinedButton(onClick = { privateExport = item }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Back up signing key") } }
                        result.secret?.let { item -> OutlinedButton(onClick = { privateExport = item }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Back up content key") } }
                        TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Details") }
                        if (details) SelectionContainer { Text(result.text) }
                    }
                }
            }
            if (state.task == BuilderViewModel.Task.KEY || state.task == BuilderViewModel.Task.LICENCE || state.task == BuilderViewModel.Task.ENCRYPT) {
                val saved = state.keys
                if (saved.isNotEmpty()) DataEaterCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saved private keys", fontWeight = FontWeight.Bold)
                        for (item in saved) OutlinedButton(onClick = { privateExport = item }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Back up ${item.name} · ${if (item.kind == "signing") "signing" else "content"}")
                        }
                    }
                }
            }
            if (editorError != null && batchEditor == null) Text(editorError!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Field(label: String, key: String, model: BuilderViewModel, lines: Int = 1) {
    OutlinedTextField(value = model.state.fields[key].orEmpty(), onValueChange = { if (it.length <= 20000) model.field(key, it) },
        label = { Text(label) }, enabled = !model.state.busy, singleLine = lines == 1, minLines = lines, maxLines = lines + 3,
        modifier = Modifier.fillMaxWidth())
}
@Composable
private fun Choice(label: String, selected: String?, items: List<Pair<String, String>>, enabled: Boolean, onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected ?: "Choose"}", Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, "Choose")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            for ((id, name) in items) DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onChoose(id) })
        }
    }
}
@Composable
private fun KeyChoice(label: String, kind: String, model: BuilderViewModel, onImport: () -> Unit) {
    val state = model.state; val selected = if (kind == "signing") state.signingId else state.secretId
    val items = state.keys.filter { it.kind == kind }
    Choice(label, items.firstOrNull { it.id == selected }?.name, items.map { it.id to (it.name + " · " + it.id.take(4)) }, !state.busy) {
        if (kind == "signing") model.signing(it) else model.secret(it)
    }
    TextButton(onClick = onImport, enabled = !state.busy) { Text("Import ${if (kind == "signing") "signing" else "content"} key") }
}
private data class Editor(val title: String, val reviewId: String?, val name: String, val initial: String, val readOnly: Boolean = false, val notes: Boolean = false)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuilderEditor(editor: Editor, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(editor) { mutableStateOf(editor.initial) }
    var discard by remember { mutableStateOf(false) }
    fun leave() { if (!editor.readOnly && text != editor.initial) discard = true else onDismiss() }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard unsaved edits?") },
        text = { Text("Save the batch before leaving to keep these changes.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    Dialog(onDismissRequest = { leave() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { leave() }
        Scaffold(topBar = { TopAppBar(title = { Text(editor.title) }, navigationIcon = {
            IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!editor.readOnly && !editor.notes) Text("Keep every page marker. Correct extraction only; do not invent technical information.")
                OutlinedTextField(text, onValueChange = { if (it.toByteArray().size <= 2 * 1024 * 1024) text = it }, readOnly = editor.readOnly,
                    modifier = Modifier.fillMaxWidth().weight(1f))
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                if (!editor.readOnly) Button(onClick = { onSave(text) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save") }
                else OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuilderHelp(onBack: () -> Unit) {
    val context = LocalContext.current
    val names = remember { context.assets.list("builder/help").orEmpty().sorted() }
    var topic by remember { mutableStateOf<String?>(null) }
    val text = remember(topic) { topic?.let { context.assets.open("builder/help/$it").bufferedReader().use { stream -> stream.readText() } } }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { if (topic != null) topic = null else onBack() }
        Scaffold(topBar = { TopAppBar(title = { Text("Builder help") }, navigationIcon = {
            IconButton(onClick = { if (topic != null) topic = null else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (text != null) SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
                else for (name in names) {
                    val title = remember(name) { context.assets.open("builder/help/$name").bufferedReader().use { it.readLine() } }
                    OutlinedButton(onClick = { topic = name }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(title) }
                }
            }
        }
    }
}
