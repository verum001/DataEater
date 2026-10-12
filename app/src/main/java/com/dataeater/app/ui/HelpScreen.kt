package com.dataeater.app.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val back = { if (page != null) page = null else onBack() }
    BackHandler(onBack = back)
    val pages = listOf("Changelog", "Version", "Getting started", "Download databases", "Create databases", "Benchmark", "Privacy")
    Scaffold(
        modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
        topBar = { TopAppBar(title = { Text(page ?: "Help") }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }) },
    ) { padding ->
        if (page == null) {
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                pages.forEach { title ->
                    ListItem(headlineContent = { Text(title) }, modifier = Modifier.clickable { page = title })
                    HorizontalDivider()
                }
            }
        } else {
            val text = when (page) {
                "Changelog" -> remember { context.assets.open("changelog.txt").bufferedReader().use { it.readText() } }
                "Version" -> {
                    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
                    "DataEater ${info.versionName}\n\nOn-device AI is available offline. OpenRouter is an optional online connection."
                }
                "Getting started" -> "1. Tap Download AI on the main screen, or open Settings → Model Selection → Change Model → Download models.\n\n2. Allow storage access when asked. Recommended shows the selected reviewed versions. Switch to All for recent Hugging Face models. Search by model or publisher, or tap Refresh. Tap Show description for publisher details when available. The list shows size, update date and whether that exact file has been tested for document answers. Memory fit is an estimate. Choose a model and tap Download. You can close the list while it downloads.\n\n3. When it is installed, tap Use model.\n\n4. Turn Database off for general chat. To ask about documents, open Settings → Database Selection → Change Database → Download databases, or create your own database. Select a database and turn the DB switch on. The database selector is on the left below the message box; Settings is at the top right.\n\nLong-press a message to select and copy text. Swipe from the left edge to open your conversations.\n\nThe AI remembers recent complete turns in the current conversation. Start a new conversation for a new subject. Changing the database starts a separate memory context. Long chats may exceed a small model’s memory.\n\nFor optional online AI, open Settings → AI connection → Use OpenRouter. Add your key and review the sharing options."
                "Download databases" -> "Open Settings and expand Database Selection with Change Database. Tap Download databases to see the latest available manuals.\n\nEach database shows its version and size. Tap Show description for the included manuals and their terms; Hide description closes it.\n\nTap Download and grant storage access if asked. Downloads continue while the list is closed. Progress, Cancel and Retry are available in the list. The completed file is checked before it is saved in the DataEater folder.\n\nReturn to Database Selection and choose the downloaded database. Search in several databases opens a checkbox screen. Tick the databases you want and tap Search selected. Each chat remembers its selection. Locked databases need an Unlock Code.\n\nDownloads use an internet connection and may use mobile data. After downloading, local searches work offline."
                "Create databases" -> "Open Settings → Create database. Enter a name and choose your files, then tap Create. The database is saved in the DataEater folder. Preparation can continue in the background.\n\nEncrypt creates a private database. On first use, confirm creation of the secret key vault. Stored private database keys appear under Create. Tap a database name to generate a licence using the receiving phone's request code. Save a private key backup securely; uninstalling the app removes keys stored only in the vault. Remove key deletes the saved key, not the database.\n\nOptimise with LLM creates a ZIP containing the original files, parsed text and an optimisation prompt. Nothing is sent automatically. The ZIP is unencrypted: share it only with a service you trust. Choose the returned optimised ZIP, then tap Continue after optimisation."
                "Benchmark" -> "Open Settings → Start benchmark, below Create database. Enter a question and tap Add question; repeat for more questions. Expand Select models and tick installed models. Use database starts on: expand Select databases and tick the manuals to search together. Unlock private databases first. Turn it off to compare general knowledge answers.\n\nHuman rating adds five stars and optional feedback under each finished answer. Tap Run benchmark to watch questions and answers in the chat screen. Keep the app open; Stop benchmark preserves finished answers. Models run one at a time with fresh conversations. Reply settings and processor preference come from Settings. Each answer is limited to five minutes.\n\nResults are saved in DataEater/benchmark. Tap a filename to reopen it and change ratings. Select files with checkboxes: Download copies readable reports to Downloads/DataEater, Share opens the Android chooser, and Delete removes selected results and their ratings. Reports include phone information, model load/answer times, questions, answers, sources and a comparison table. Ratings are personal judgments; speed does not prove accuracy. Runtime caches, model limits and phone conditions can affect comparisons.\n\nCompleted answers are checkpointed. Closing or force-stopping the app may interrupt the current answer; reopening its result marks it interrupted. Reports may contain private questions and document answers. Share them only when you intend to." 
                else -> "On-device AI runs locally. OpenRouter online AI sends your question and recent online messages to OpenRouter and its provider. Selected database excerpts are sent only when you enable sharing in OpenRouter settings. Local chat history is not sent to online memory.\n\nDownloading a model connects to Hugging Face. Downloading a database connects to GitHub. Your questions, documents and private keys are not sent with downloads. Optimisation ZIPs contain unencrypted source files and are shared only when you send them yourself.\n\nWith Database on, answers use your documents. With Database off, answers use the model’s knowledge and may be wrong."
            }
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
