package com.dataeater.app.benchmark

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dataeater.app.ai.*
import com.dataeater.app.chat.ChatMessage
import com.dataeater.app.chat.Session
import com.dataeater.app.data.DatabaseFiles
import com.dataeater.app.ui.ChatScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(manager: BenchmarkManager, state: BenchmarkManager.State,
    models: List<ModelFiles.ModelFile>, databases: List<DatabaseFiles.DatabaseFile>, canRun: Boolean,
    onRefresh: () -> Unit, onRun: () -> Unit, onBack: () -> Unit, onView: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var deleteDialog by remember { mutableStateOf(false) }
    var modelList by rememberSaveable { mutableStateOf(false) }
    var databaseList by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    LaunchedEffect(Unit) { manager.refresh() }
    LaunchedEffect(state.files) { selected = selected.filter { it in state.files } }
    BackHandler(onBack = onBack)
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Delete selected results?") },
        text = { Text("Delete ${selected.size} saved benchmark result(s) and their ratings?") },
        confirmButton = { TextButton(onClick = { manager.delete(selected.toSet()); deleteDialog = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } })
    Scaffold(modifier = Modifier.statusBarsPadding().navigationBarsPadding().imePadding(), topBar = {
        TopAppBar(title = { Text("Benchmark") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ask the same questions of installed models. Each answer starts a fresh conversation. Keep the app open during the run.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = draft, onValueChange = { draft = it.take(4000) }, label = { Text("Question") }, minLines = 2, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("benchmark-question"))
            Button(enabled = !state.busy && draft.isNotBlank() && form.questions.size < 50, onClick = {
                manager.form { it.copy(questions = it.questions + draft.trim()) }; draft = ""
            }) { Text("Add question") }
            form.questions.forEachIndexed { index, question ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}. $question", Modifier.weight(1f))
                    TextButton(enabled = !state.busy, onClick = { manager.form { it.copy(questions = it.questions.filterIndexed { i, _ -> i != index }) } }) { Text("Remove") }
                }
            }
            OutlinedButton(onClick = { modelList = !modelList }, modifier = Modifier.fillMaxWidth()) { Text("Select models (${form.models.size}) ${if (modelList) "▴" else "▾"}") }
            if (modelList) {
                if (models.isEmpty()) Text("No installed models. Download models in Settings first.")
                models.forEach { model ->
                    CheckRow(model.displayName, model.file.name in form.models, !state.busy && (model.file.name in form.models || form.models.size < 20),
                        "benchmark-model-${model.file.name}") { checked -> manager.form { it.copy(models = if (checked) it.models + model.file.name else it.models - model.file.name) } }
                }
            }
            ToggleRow("Use database", form.useDatabase, !state.busy) { value -> manager.form { it.copy(useDatabase = value) } }
            if (form.useDatabase) {
                OutlinedButton(onClick = { databaseList = !databaseList }, modifier = Modifier.fillMaxWidth()) { Text("Select databases (${form.databases.size}) ${if (databaseList) "▴" else "▾"}") }
                if (databaseList) {
                    if (databases.isEmpty()) Text("No databases. Download or create one in Settings first.")
                    databases.forEach { db -> CheckRow(db.displayName, db.file.name in form.databases, !state.busy,
                        "benchmark-database-${db.file.name}") { checked -> manager.form { it.copy(databases = if (checked) it.databases + db.file.name else it.databases - db.file.name) } } }
                }
                Text("Searches all selected databases together. Private databases must be unlocked first.", style = MaterialTheme.typography.bodySmall)
            } else Text("Answers use model knowledge and are not checked against documents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            ToggleRow("Human rating", form.humanRating, !state.busy) { value -> manager.form { it.copy(humanRating = value) } }
            TextButton(enabled = !state.busy && !state.fileBusy, onClick = { onRefresh(); manager.refresh() }) { Text("Refresh lists") }
            Button(onClick = onRun, enabled = canRun && !state.busy && !state.fileBusy && form.questions.isNotEmpty() && form.models.isNotEmpty() && (!form.useDatabase || form.databases.isNotEmpty()), modifier = Modifier.fillMaxWidth()) { Text("Run benchmark") }
            if (state.run != null) OutlinedButton(onClick = onView, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "View running benchmark" else "View latest benchmark") }
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("benchmark-status"))
            HorizontalDivider(); Text("Results", style = MaterialTheme.typography.titleLarge)
            Text("Saved in DataEater/benchmark. Tap a filename to view answers and ratings.", style = MaterialTheme.typography.bodySmall)
            if (state.files.isEmpty()) Text("No saved results yet.")
            state.files.forEach { name ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = name in selected, enabled = !state.fileBusy && !state.busy, onCheckedChange = { checked -> selected = if (checked) selected + name else selected - name }, modifier = Modifier.semantics { contentDescription = "Select $name" })
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).clickable(enabled = !state.busy && !state.fileBusy) { manager.open(name, onView) }.padding(vertical = 12.dp))
                }
            }
            val enabled = selected.isNotEmpty() && !state.busy && !state.fileBusy
            // Separate rows remain usable with large system text.
            OutlinedButton(onClick = { deleteDialog = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
            OutlinedButton(onClick = { manager.download(selected.toSet()) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Download") }
            OutlinedButton(onClick = { manager.share(selected.toSet()) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Share") }
        }
    }
}
@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, tag: String, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { change(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, change, enabled = enabled, modifier = Modifier.testTag(tag).semantics { contentDescription = label })
        Text(label, modifier = Modifier.weight(1f))
    }
}
@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, change, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
fun BenchmarkChat(manager: BenchmarkManager, state: BenchmarkManager.State, download: ModelDownloads.State, onBack: () -> Unit) {
    val run = state.run ?: return
    val context = LocalContext.current
    DisposableEffect(state.busy) {
        val window = (context as? android.app.Activity)?.window
        if (state.busy) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler(onBack = onBack)
    val messages = run.answers.flatMap { a -> listOf(
        ChatMessage("question-${a.id}", true, "${a.model}\n${run.questions[a.questionIndex]}"),
        ChatMessage(a.id, false, a.text.ifBlank { if (a.status == "Running") "Answering…" else a.error.ifBlank { a.status } }, sources = a.sources,
            databaseHadNoAnswer = a.status == "No match", answeredWithoutDatabase = !run.useDatabase,
            incomplete = a.status in listOf("Cancelled", "Failed"), mayIncludeGeneralKnowledge = run.useDatabase && run.behavior.strictness == DatabaseStrictness.FLEXIBLE),
    ) }
    ChatScreen(session = Session(id = run.id, title = "Benchmark", modelName = "Model comparison", databaseName = run.databases.joinToString(), useDatabase = run.useDatabase, messages = messages),
        models = emptyList(), loadedModelName = "Benchmark", isGenerating = state.busy, aiStatus = state.message, draft = "", onDraftChange = {}, onSend = {},
        onOpenDrawer = onBack, onOpenSettings = onBack, onDownloadAi = {}, download = download, onChooseModel = {}, onToggleDatabase = {}, databases = emptyList(), onChooseDatabase = {},
        benchmarkControls = {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!run.useDatabase) Text("Database off: answers are not checked against documents.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                Text(state.message.ifBlank { run.status }, style = MaterialTheme.typography.bodySmall)
                if (state.busy) Button(onClick = { manager.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Stop benchmark") }
                else Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { manager.retrySave() }, Modifier.weight(1f)) { Text("Save result") }
                    TextButton(onClick = onBack, Modifier.weight(1f)) { Text("Results") }
                }
            }
        }, answerFooter = { message ->
            val a = run.answers.firstOrNull { it.id == message.id }
            if (a != null) {
                Text("${a.model} · ${a.status}" + (a.answerMs?.let { " · %.2f s".format(java.util.Locale.US, it / 1000.0) } ?: ""), style = MaterialTheme.typography.labelSmall)
                if (run.humanRating && a.status != "Running") Rating(a) { stars, feedback -> manager.rate(a.id, stars, feedback) }
            }
        }, modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding())
}
@Composable
private fun Rating(answer: BenchmarkAnswer, onRate: (Int?, String) -> Unit) {
    var feedback by remember(answer.id, answer.feedback) { mutableStateOf(answer.feedback) }
    var expanded by rememberSaveable(answer.id) { mutableStateOf(false) }
    Text("Give feedback", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth()) {
        (1..5).forEach { star ->
            TextButton(onClick = { onRate(star, answer.feedback) }, modifier = Modifier.weight(1f).semantics { contentDescription = "Rate ${answer.id} $star stars" }, contentPadding = PaddingValues(0.dp)) {
                Text(if ((answer.stars ?: 0) >= star) "★" else "☆", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide feedback" else "Add feedback", style = MaterialTheme.typography.labelSmall) }
    if (expanded) {
        OutlinedTextField(feedback, { feedback = it.take(2000) }, label = { Text("Feedback (optional)") }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { onRate(answer.stars, feedback) }) { Text("Save feedback") }
    }
    if (answer.stars != null || answer.feedback.isNotBlank()) TextButton(onClick = { onRate(null, "") }) { Text("Clear rating and feedback", style = MaterialTheme.typography.labelSmall) }
}
