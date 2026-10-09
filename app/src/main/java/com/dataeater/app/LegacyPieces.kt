package com.dataeater.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable

/**
 * The pieces of the old single-screen app that still earn their place.
 *
 * These moved out of `MainActivity.kt` when the chat screen replaced that
 * layout, because that file had grown past 1800 lines and every screen shared
 * it.
 *
 * What is left here is the database picker and the clipboard helper. The
 * locked-database flow used to be here too — the Request Code and Unlock Code
 * cards — and has since moved to `ui/UnlockScreen.kt`, where it finally got a
 * title of its own. It had been rendering underneath the Search screen's
 * heading, which said "Search" while the page was asking for a licence.
 *
 * They live in the `app` package rather than `ui` because they are used by
 * more than one screen and were written before the `ui` package existed.
 */

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dataeater.app.data.DatabaseChoice
import com.dataeater.app.data.DatabaseFiles
import com.dataeater.app.data.DatabaseLock
import com.dataeater.app.data.DatabaseReader
import com.dataeater.app.engine.SearchEngine
import com.dataeater.app.model.Chunk
import com.dataeater.app.ui.theme.DataEaterCard


@Composable
internal fun DatabasePickerCard(
    available: List<DatabaseFiles.DatabaseFile>,
    selected: String,
    /**
     * The database that is actually open.
     *
     * Passed in rather than guessed, because after a licence expires the file
     * may still be the selected one while nothing is open. Highlighting the
     * selected file would then claim a database is ready when it is not.
     */
    activeName: String?,
    lockStatuses: Map<String, DatabaseLock.Status>,
    onSelect: (String) -> Unit,
) {
    DataEaterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Database",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Put .dataeater files in ${DatabaseFiles.folder().absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Currently using: $activeName",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (available.isEmpty()) {
                Text(
                    text = "No database files found. Copy a .dataeater file into /sdcard/DataEater/.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                for (candidate in available) {
                    val isChosen = candidate.displayName == selected
                    // Read once per row rather than per label, so a file that
                    // cannot be read shows one honest message instead of two
                    // contradictory ones.
                    val state = when (val found = lockStatuses[candidate.displayName]) {
                        is DatabaseLock.Status.Locked -> "locked - needs an Unlock Code"
                        is DatabaseLock.Status.Unreadable -> "cannot be read"
                        else -> null
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = "${candidate.sizeInKilobytes} KB",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            // On its own line, so it cannot collide with the
                            // "in use" marker beside it.
                            if (state != null) {
                                Text(
                                    text = state,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        // The name is already bold when chosen, so a separate
                        // marker is only needed to say "already open".
                        if (isChosen) {
                            Text(
                                text = "in use",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            OutlinedButton(onClick = { onSelect(candidate.displayName) }) {
                                Text("Use")
                            }
                        }
                    }
                }
            }


        }
    }
}

/**
 * Copies text to the clipboard.
 *
 * Android shows a system message saying what was copied, so the user is never
 * surprised. Only ever called with a **public** key - never with an Unlock
 * Code, which is a secret.
 */
internal fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
        as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** One search result, with its citation. */
@Composable
private fun ResultCard(
    title: String,
    section: String,
    page: Int,
    text: String,
) {
    DataEaterCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Text(
                text = "$section \u2014 page $page",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
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
