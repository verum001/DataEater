package com.dataeater.app.builder

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(model: BuilderViewModel, canUseDatabase: Boolean, onDatabaseReady: (File) -> Unit, onBack: () -> Unit) {
    val state = model.state
    var encrypted by rememberSaveable { mutableStateOf(false) }
    var optimise by rememberSaveable { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf(false) }
    var returned by rememberSaveable { mutableStateOf(false) }
    var remove by remember { mutableStateOf<PublisherVault.Item?>(null) }
    var licence by remember { mutableStateOf<PublisherVault.Item?>(null) }
    var backup by remember { mutableStateOf<PublisherVault.Item?>(null) }
    var request by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("never") }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) model.documents(it) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) model.restoreVaultBackup(it) }
    val review = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) { returned = true; model.importReview(it) }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pending = model.pendingExport; model.pendingExport = null
        if (uri != null && pending != null) model.export(uri, pending.file, pending.item, pending.reviewId, pending.prompt)
    }
    fun save(file: File) { model.pendingExport = BuilderViewModel.Export(file = file); export.launch(file.name) }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun create(continued: Boolean) {
        if (!canUseDatabase) return
        if (Build.VERSION.SDK_INT >= 33) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
        notice = true
        model.createSimple(encrypted, optimise, continued)
    }
    BackHandler(onBack = onBack)
    if (setup) AlertDialog(onDismissRequest = { setup = false }, title = { Text("Create secret key vault?") },
        text = { Text("Private database keys will be encrypted and stored inside DataEater using Android's key store. Save a secure key backup: uninstalling the app removes keys stored only here.") },
        confirmButton = { TextButton(onClick = { setup = false; encrypted = true; model.setupVault() }) { Text("Create vault") } },
        dismissButton = { TextButton(onClick = { setup = false }) { Text("Cancel") } })
    remove?.let { item -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove saved key?") },
        text = { Text("Remove the stored key for ${item.name}? The database stays on the phone. Without a key backup you cannot issue new licences for it.") },
        confirmButton = { TextButton(onClick = { remove = null; model.removeKey(item) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } }) }
    backup?.let { item -> AlertDialog(onDismissRequest = { backup = null }, title = { Text("Save private key backup?") },
        text = { Text("The backup contains the database content key and its signing key. Keep it private; never send it with the database or optimisation ZIP.") },
        confirmButton = { TextButton(onClick = {
            backup = null; model.prepareVaultBackup(item) { file -> save(file) }
        }) { Text("Save backup") } }, dismissButton = { TextButton(onClick = { backup = null }) { Text("Cancel") } }) }
    licence?.let { item -> AlertDialog(onDismissRequest = { if (!state.busy) licence = null }, title = { Text("Licence key: ${item.name}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(request, { request = it }, label = { Text("Phone request code") }, enabled = !state.busy)
            OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry (never, 30d, or YYYY-MM-DD)") }, enabled = !state.busy)
            Text(state.status)
            state.result?.file?.takeIf { it.extension == "lic" }?.let { file ->
                OutlinedButton(onClick = { save(file) }, enabled = !state.busy) { Text("Save licence key") }
            }
        } }, confirmButton = { TextButton(onClick = { model.licenceFor(item, request, expiry) }, enabled = !state.busy && request.isNotBlank()) { Text("Generate") } },
        dismissButton = { TextButton(onClick = { licence = null }, enabled = !state.busy) { Text("Done") } }) }
    if (notice) Dialog(onDismissRequest = { notice = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (state.busy) "Creating database" else "Database preparation", style = MaterialTheme.typography.headlineMedium)
                Text("Created database will be saved at /sdcard/DataEater. The process runs in the background.")
                if (optimise && !returned) Text("The optimisation ZIP contains unencrypted original files, parsed text and the prompt. Nothing is sent online automatically.")
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.status)
                state.result?.let { Text(it.text) }
                Button(onClick = { notice = false }) { Text(if (state.busy) "Continue in background" else "Done") }
                if (state.busy) TextButton(onClick = model::cancel) { Text("Cancel process") }
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Create database") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state.fields["name"].orEmpty(), { model.field("name", it) }, label = { Text("Name") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { files.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "application/octet-stream")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Choose files") }
            state.inputs.forEach { input -> Row(Modifier.fillMaxWidth()) {
                Text(input.name, Modifier.weight(1f)); TextButton(onClick = { model.removeInput(input.uri) }, enabled = !state.busy) { Text("Remove") }
            } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Encrypt", Modifier.padding(top = 12.dp))
                Switch(encrypted, { if (it && !state.vaultReady) setup = true else encrypted = it }, enabled = !state.busy, modifier = Modifier.semantics { contentDescription = "Encrypt" })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Optimise with LLM", Modifier.padding(top = 12.dp))
                Switch(optimise, { optimise = it }, enabled = !state.busy, modifier = Modifier.semantics { contentDescription = "Optimise with LLM" })
            }
            if (!canUseDatabase) Text("Grant storage access in Settings to save in the DataEater folder.")
            Button(onClick = { returned = false; create(false) }, enabled = !state.busy && canUseDatabase && state.fields["name"].orEmpty().isNotBlank() && state.inputs.isNotEmpty() && (!encrypted || state.vaultReady), modifier = Modifier.fillMaxWidth()) { Text("Create") }
            if (optimise) {
                OutlinedButton(onClick = { review.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Choose optimised ZIP") }
                Button(onClick = { create(true) }, enabled = !state.busy && returned && state.reviewId != null && canUseDatabase && (!encrypted || state.vaultReady), modifier = Modifier.fillMaxWidth()) { Text("Continue after optimisation") }
            }
            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.status); TextButton(onClick = { notice = true }) { Text("Show process") } }
            else { Text(state.status); state.result?.let { result ->
                Text(result.text)
                result.file?.let { file ->
                    if (file.extension == "dataeater") OutlinedButton(onClick = { onDatabaseReady(file) }) { Text("Use database") }
                    else if (file.extension == "zip") OutlinedButton(onClick = { save(file) }) { Text("Save optimisation ZIP elsewhere") }
                }
            } }
            HorizontalDivider()
            Text("Secret key vault", fontWeight = FontWeight.Bold)
            val keys = state.keys.filter { it.kind == "content" }
            if (keys.isEmpty()) Text("No private database keys stored.")
            for (item in keys) {
                TextButton(onClick = { request = ""; model.clearResult(); licence = item }, enabled = !state.busy) { Text(item.name.let { if (it.endsWith(".dataeater")) it else "$it.dataeater" }) }
                Row { TextButton(onClick = { backup = item }, enabled = !state.busy) { Text("Backup") }; TextButton(onClick = { remove = item }, enabled = !state.busy) { Text("Remove key") } }
            }
            OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "application/octet-stream")) }, enabled = !state.busy) { Text("Restore key backup") }
        }
    }
}
