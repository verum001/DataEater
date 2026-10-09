package com.dataeater.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dataeater.app.ai.*
import com.dataeater.app.ui.theme.DataEaterCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun OpenRouterCard(settings: OpenRouterSettings, active: Boolean, busy: Boolean,
    onUse: (String, OpenRouterProtocol.Model, Boolean) -> Unit, onLocal: () -> Unit, onRemove: () -> Unit) {
    var setup by remember { mutableStateOf(false) }
    DataEaterCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI connection", style = MaterialTheme.typography.titleMedium)
            Text(if (active) "OpenRouter · Online" else "On device · Offline")
            Text("Online AI needs internet and an OpenRouter API key.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLocal, enabled = active && !busy) { Text("On device") }
                Button(onClick = { setup = true }, enabled = !busy) { Text(if (active) "OpenRouter settings" else "Use OpenRouter") }
            }
        }
    }
    if (setup) OpenRouterSetup(settings, busy, onDismiss = { setup = false }, onUse = { key, model, database ->
        onUse(key, model, database); setup = false
    }, onRemove = { onRemove(); setup = false })
}

@Composable
private fun OpenRouterSetup(settings: OpenRouterSettings, busy: Boolean, onDismiss: () -> Unit,
    onUse: (String, OpenRouterProtocol.Model, Boolean) -> Unit, onRemove: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(OpenRouterProtocol.Model(settings.modelId, settings.modelName, settings.modelId == "openrouter/free" || settings.modelId.endsWith(":free"))) }
    var database by remember { mutableStateOf(settings.allowDatabase) }
    var acknowledged by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("OpenRouter") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Online answers send your question and recent online messages to OpenRouter and its model provider. Paid models use your account credits.")
            OutlinedTextField(value = key, onValueChange = { if (it.length <= 4096) key = it },
                label = { Text("API key") }, placeholder = { Text(if (settings.hasKey) "Saved key · leave blank to keep" else "Paste your key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/settings/keys"))) }) { Text("Get an API key") }
            Text("Model: ${chosen.name}")
            Text(if (chosen.free) "Free model · account limits apply" else "Uses OpenRouter credits", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { choosing = true }) { Text("Choose online model") }
            Row { Checkbox(database, { database = it }); Text("Allow selected database excerpts online", Modifier.weight(1f).padding(top = 12.dp)) }
            if (database) Text("Relevant passages may include confidential or licensed content. Enable only for documents you may share with the provider.", style = MaterialTheme.typography.bodySmall)
            Row { Checkbox(acknowledged, { acknowledged = it }); Text("I understand what will be sent online", Modifier.weight(1f).padding(top = 12.dp)) }
            if (settings.hasKey) TextButton(onClick = onRemove, enabled = !busy) { Text("Remove saved key") }
        }
    }, confirmButton = { TextButton(onClick = { onUse(key, chosen, database) }, enabled = !busy && acknowledged && (settings.hasKey || key.isNotBlank())) { Text("Use online AI") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (choosing) OnlineModelPicker(onChoose = { chosen = it; choosing = false }, onDismiss = { choosing = false })
}

@Composable
fun OnlineModelPicker(onChoose: (OpenRouterProtocol.Model) -> Unit, onDismiss: () -> Unit) {
    var models by remember { mutableStateOf<List<OpenRouterProtocol.Model>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        scope.launch {
            loading = true; error = null
            val client = OpenRouterClient()
            try { models = withTimeout(60000) { client.models() } } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Could not load online models. Check your connection and retry." }
            finally { client.close(); loading = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Online models") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(search, { search = it.take(160) }, label = { Text("Search models") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row { Checkbox(freeOnly, { freeOnly = it }); Text("Free models only", Modifier.padding(top = 12.dp)) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it); TextButton(onClick = { refresh() }, enabled = !loading) { Text("Retry") } }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item { TextButton(onClick = { onChoose(OpenRouterProtocol.Model("openrouter/free", "Free models", true)) }) { Text("Free models · automatic choice") } }
                val filtered = models.filter { (!freeOnly || it.free) && (it.name.contains(search, true) || it.id.contains(search, true)) }
                items(filtered, key = { it.id }) { model ->
                    TextButton(onClick = { onChoose(model) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) { Text(model.name); Text(if (model.free) "Free" else "Uses credits", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (!loading && models.isNotEmpty() && filtered.isEmpty()) item { Text("No matching models.") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
