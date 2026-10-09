package com.dataeater.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dataeater.app.data.DatabaseChoice
import com.dataeater.app.data.DatabaseFiles
import com.dataeater.app.data.DatabaseLock
import com.dataeater.app.data.DatabaseReader
import com.dataeater.app.model.DatabaseManifest
import com.dataeater.app.DatabasePickerCard
import com.dataeater.app.engine.SearchEngine
import com.dataeater.app.ui.theme.DataEaterCard

/**
 * Keyword search, and the database picker.
 *
 * WHY THIS STILL EXISTS NOW THAT THERE IS A CHAT
 * ----------------------------------------------
 * The chat answers a question. This shows what is actually written in the
 * documents, word for word.
 *
 * Those are not the same job, and the difference matters to the user this app
 * is for. A technician who needs to know the exact wording of a procedure —
 * the sequence of a bleed, the order of a torque check — cannot get it from a
 * two-sentence summary, and a summary that paraphrases a safety step is worse
 * than no answer at all.
 *
 * So search is one tap away rather than deleted. Removing a working feature
 * because a newer one appeared is how people lose trust in an app.
 *
 * WHY THE DATABASE PICKER LIVES HERE
 * ---------------------------------
 * The mockups put database selection in Settings. It is still reachable there,
 * but a locked database has to be explained rather than merely chosen: the
 * user needs the Request Code, a box to paste the Unlock Code into, and a way
 * to try another file. That is too much for a dropdown, so the search screen
 * doubles as the "what is open" screen.
 */

/** How many results to show. Five is readable on a phone. */
private const val MAX_RESULTS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    database: DatabaseReader.OpenedDatabase?,
    availableDatabases: List<DatabaseFiles.DatabaseFile>,
    selectedDatabase: String,
    onSelectDatabase: (String) -> Unit,
    lockStatuses: Map<String, DatabaseLock.Status>,
    lockedDatabase: DatabaseLock.Status.Locked?,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // A locked database cannot be searched. The search box would be
            // useless, so the screen says so and offers the way through —
            // which is the Unlock screen, not this one.
            if (lockedDatabase != null) {
                LockedDatabaseNotice(
                    name = lockedDatabase.manifest.name,
                    onUnlock = onUnlock,
                )
                return@Column
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find text in the documents") },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            )

            if (database == null) {
                Text(
                    text = "No database is open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            DatabaseSummary(database)

            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                Text(
                    text = "Type part of a word or a part number. " +
                        "This searches the text itself, not the AI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val engine = remember(database, trimmed) {
                    SearchEngine(database.chunks).search(trimmed, limit = MAX_RESULTS)
                }

                if (engine.isEmpty()) {
                    NoMatchNote(trimmed)
                } else {
                    Text(
                        text = "${engine.size} match(es)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    engine.forEach { hit ->
                        SearchHitCard(
                            title = database.sources
                                .firstOrNull { it.id == hit.chunk.sourceId }
                                ?.title
                                ?: hit.chunk.sourceId,
                            section = hit.chunk.section,
                            page = hit.chunk.page,
                            text = hit.chunk.text,
                            onClick = { query = hit.chunk.text.take(30) },
                        )
                    }
                }
            }

            DatabasePickerCard(
                available = availableDatabases,
                selected = selectedDatabase,
                activeName = database?.manifest?.name,
                lockStatuses = lockStatuses,
                onSelect = onSelectDatabase,
            )
        }
    }
}

/**
 * What is open, in three lines.
 *
 * Answers "am I searching the right thing?" before the user has typed
 * anything, which is the question people actually forget to ask.
 */
@Composable
private fun DatabaseSummary(database: DatabaseReader.OpenedDatabase) {
    DataEaterCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = database?.manifest?.name ?: "Unknown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            SummaryLine("Passages", "${database.chunks.size}")
            SummaryLine("Documents", "${database.sources.size}")
            SummaryLine("Licence", database.manifest.license)
            SummaryLine("Encryption", database.manifest.encryption)
        }
    }
}

/** One "label: value" line. */
@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.4f),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

/** One match, with the words that matched. */
@Composable
private fun SearchHitCard(
    title: String,
    section: String,
    page: Int,
    text: String,
    onClick: () -> Unit,
) {
    DataEaterCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$section — page $page",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            // Selectable, so a passage can be copied for a report or a message
            // to a colleague. The wording is the evidence, and evidence gets
            // pasted into other things.
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * Nothing matched.
 *
 * Says how much was searched, because "no results" and "wrong database" look
 * identical otherwise, and the user cannot tell which one they are looking at.
 */
@Composable
private fun NoMatchNote(query: String) {
    DataEaterCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Nothing matches \"$query\".",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Only the exact text of the documents is searched here. " +
                    "Try fewer or shorter words, or a part number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The one-line notice that stands in for a locked database.
 *
 * The whole unlock flow moved to its own screen, so this no longer tries to be
 * it. It says two things: the file is locked rather than broken, and here is the
 * button that fixes it.
 */
@Composable
private fun LockedDatabaseNotice(name: String, onUnlock: () -> Unit) {
    DataEaterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "This database is locked, not damaged. Its text is " +
                    "encrypted and needs an Unlock Code before it can be read.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                Text("Enter my Unlock Code")
            }
        }
    }
}
