package com.dataeater.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.dataeater.app.ai.ModelDownloads
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dataeater.app.chat.ChatMessage
import com.dataeater.app.chat.Session
import com.dataeater.app.ui.theme.DataEaterCard

/**
 * The chat screen, as the mockups draw it.
 *
 * THE LAYOUT, TOP TO BOTTOM
 * -------------------------
 *   a bar with the session drawer button and the session name
 *   the conversation, oldest at the top, scrolled to the newest
 *   the composer, pinned to the bottom
 *   one compact control row: model, settings gear, database toggle
 *
 * WHY THE CONVERSATION SCROLLS ITSELF
 * ----------------------------------
 * "Each new question continues the conversation, with the view scrolling to
 * the latest response" is in the brief, and it is also the only sensible
 * behaviour: after sending, the newest message is off-screen, and a user who
 * cannot see it has to guess whether it worked.
 *
 * WHY THE CONTROLS SIT BELOW THE INPUT
 * -------------------------------------
 * They are the things reached for mid-conversation — check the database, move
 * it to the CPU — and a thumb's reach below the keyboard is where they belong.
 * The top bar carries navigation only.
 *
 * WHY THE DATABASE TOGGLE IS LABELLED RATHER THAN JUST SWITCHED
 * -------------------------------------------------------------
 * Turning it off means the model answers from memory. That is a legitimate
 * thing to want and this app allows it. It is also the one switch that makes
 * answers unsafe to rely on, so it is labelled here and again on every answer
 * it produces. An invisible switch on an invisible setting would be the worst
 * possible version of this feature.
 */
@Composable
fun ChatScreen(
    session: Session,
    models: List<String>,
    loadedModelName: String?,
    isGenerating: Boolean,
    aiStatus: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onDownloadAi: () -> Unit,
    download: ModelDownloads.State,
    onChooseModel: (String) -> Unit,
    onOpenOnlineModels: (() -> Unit)? = null,
    onToggleDatabase: (Boolean) -> Unit,
    databases: List<String>,
    onChooseDatabase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusedMessage = remember { mutableStateOf<String?>(null) }
    // Observe down events before children without consuming them. Clearing an
    // existing selection here still lets buttons, scrolling and the composer
    // handle the same touch. Toolbar actions and handles use their own windows.
    Column(modifier = modifier.pointerInput(focusManager) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (focusedMessage.value != null) focusManager.clearFocus()
        }
    }) {

        // --- the bar ---------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "Sessions")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = session.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- the conversation ------------------------------------------
        Conversation(
            needsAi = models.isEmpty() && loadedModelName == null,
            download = download,
            onDownloadAi = onDownloadAi,
            messages = session.messages,
            isGenerating = isGenerating,
            useDatabase = session.useDatabase,
            databaseName = session.databaseName,
            onSelectionFocusChange = { id, focused ->
                if (focused) focusedMessage.value = id
                else if (focusedMessage.value == id) focusedMessage.value = null
            },
            modifier = Modifier.weight(1f),
        )

        // --- the composer ----------------------------------------------
        Composer(
            draft = draft,
            isGenerating = isGenerating,
            aiStatus = aiStatus,
            onDraftChange = onDraftChange,
            onSend = onSend,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        // --- the control row -------------------------------------------
        ControlRow(
            models = models,
            selectedModel = loadedModelName,
            useDatabase = session.useDatabase,
            databaseName = session.databaseName,
            databases = databases,
            onChooseModel = onChooseModel,
            onOpenOnlineModels = onOpenOnlineModels,
            onChooseDatabase = onChooseDatabase,
            onOpenSettings = onOpenSettings,
            onToggleDatabase = onToggleDatabase,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/**
 * The messages, oldest first, scrolled to the newest.
 *
 * This is the part that makes a chat usable. Everything else on the screen is
 * arranged around these two actions: asking, and seeing the answer.
 */
@Composable
private fun Conversation(
    needsAi: Boolean,
    download: ModelDownloads.State,
    onDownloadAi: () -> Unit,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    useDatabase: Boolean,
    databaseName: String,
    onSelectionFocusChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // The extra index while generating is the "Thinking" row, so the view also
    // follows an answer as it arrives rather than only after it finishes.
    val lastIndex = messages.size - 1 + (if (isGenerating) 1 else 0) + (if (needsAi && messages.isNotEmpty()) 1 else 0)
    LaunchedEffect(lastIndex) {
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (needsAi) {
            item { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add AI to start chatting", style = MaterialTheme.typography.titleMedium)
                Text("Download once. Your chats stay on this phone.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onDownloadAi, modifier = Modifier.fillMaxWidth()) { Text("Download AI") }
                if (download.active) {
                    androidx.compose.material3.LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth())
                    Text(download.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            }
        }

        if (messages.isEmpty() && !needsAi) {
            item { WelcomeNote(useDatabase, databaseName) }
        }

        items(messages.size) { index ->
            MessageBubble(
                message = messages[index],
                onSelectionFocusChange = onSelectionFocusChange,
            )
        }

        if (isGenerating) {
            item { ThinkingBubble() }
        }
    }
}

/**
 * The opening message on an empty conversation.
 *
 * It has to follow the database switch, not just say a fixed thing. With the
 * database off, "the answer is looked up in your documents" is simply false —
 * and an empty screen making a false claim is the last place that should
 * happen in this app.
 */
@Composable
private fun WelcomeNote(useDatabase: Boolean, databaseName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (useDatabase && databaseName.isBlank()) {
                "Choose a database in Settings to ask questions about your documents."
            } else if (useDatabase) {
                "Ask a question. The answer is looked up in " +
                    "${databaseName.ifBlank { "your documents" }}."
            } else {
                "The database is off, so answers come from the model's own " +
                    "memory and are not checked against any document."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/**
 * One message.
 *
 * The user's own words sit in an indigo bubble on the right. The model's
 * answers sit on the left in a plain card, because they are longer and are the
 * thing being read.
 *
 * WHY THE PROVENANCE MATTERS MORE THAN THE STYLING
 * -----------------------------------------------
 * Under each answer, in small print, it says where the answer came from. Three
 * states, visibly different:
 *
 *   * sources listed — checked against your documents, with page numbers
 *   * "the database does not contain this" — searched, found nothing, and the
 *     model was deliberately not asked
 *   * "from the model's own memory" — the database was off, nothing was checked
 *
 * Without those being distinguishable, "I checked and found nothing" and "I
 * invented this" look identical on the screen. That is the whole safety
 * argument of the app, and it does not survive styling them the same.
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onSelectionFocusChange: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start,
    ) {
        if (message.fromUser) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp,
                ),
                modifier = Modifier.padding(start = 36.dp),
            ) {
                // One selection scope per message: Android handles long press,
                // selection handles, Select all and Copy for the visible text.
                SelectionContainer(modifier = Modifier.onFocusChanged {
                    onSelectionFocusChange(message.id, it.hasFocus)
                }) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        } else {
            DataEaterCard(modifier = Modifier.padding(end = 36.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SelectionContainer(modifier = Modifier.onFocusChanged {
                    onSelectionFocusChange(message.id, it.hasFocus)
                }) {
                        Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    AnswerProvenance(message)
                }
            }
        }
    }
}

/** The small print under an answer: where it came from, and what that is worth. */
@Composable
private fun AnswerProvenance(message: ChatMessage) {
    // Nothing to say yet: the answer is empty and not flagged as interrupted.
    if (message.text.isBlank() && !message.incomplete) return

    // An answer with no source to list and nothing to warn about gets no
    // footer and no rule, rather than an empty strip under every message.
    val hasSomethingToShow = message.databaseHadNoAnswer ||
        message.sources.isNotEmpty() ||
        message.incomplete ||
        message.answeredWithoutDatabase || message.mayIncludeGeneralKnowledge
    if (!hasSomethingToShow) return

    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))

    if (message.mayIncludeGeneralKnowledge) ProvenanceWarning("May include general knowledge outside the database. Check the labelled sections.")

    when {
        // With the database off there is nothing to warn about *here*: the
        // control row already reads "Database off" in the caution colour, the
        // line above the composer spells it out, and the session list will say
        // so tomorrow. Repeating it under every answer took more room than the
        // answer and was read less, not more.
        message.answeredWithoutDatabase -> Unit

        message.databaseHadNoAnswer -> ProvenanceWarning(
            "The database was searched and does not contain this."
        )

        message.sources.isNotEmpty() -> SourceSpoiler(message.sources)

        message.incomplete -> ProvenanceWarning("This answer was stopped before it finished.")

        else -> Text(
            text = "No source.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The citations, collapsed until asked for.
 *
 * WHY COLLAPSED
 * -------------
 * A good answer has three citations, and written out in full they took three
 * times the room of the answer itself. That pushed the conversation upwards and
 * meant reading the answers meant scrolling past the evidence every time.
 *
 * The evidence still has to be there, and one tap still has to reach it — a
 * technician checking a value needs the page number. What changed is that it is
 * now a count until wanted.
 *
 * The count is deliberately worded as "1 source" / "3 sources" rather than an
 * icon, because the number is the reassurance: three independent passages
 * agreeing is itself information.
 */
@Composable
private fun SourceSpoiler(sources: List<String>) {
    var open by remember { mutableStateOf(false) }

    Surface(
        onClick = { open = !open },
        color = Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Text(
                    text = if (sources.size == 1) {
                        "1 source"
                    } else {
                        "${sources.size} sources"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Hide sources" else "Show sources",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (open) {
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sources.forEach { source ->
                        Text(
                            text = "• $source",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A caution, in the one colour that means "pay attention".
 *
 * Deliberately not red. Nothing has gone wrong here — the user has simply been
 * told exactly what they are reading, which is the point.
 */
@Composable
private fun ProvenanceWarning(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Shown while the model is writing. */
@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Thinking…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The input, with the send button beside it.
 *
 * Send is disabled while the box is empty or the model is busy. A button that
 * does nothing when pressed is worse than one that is visibly off.
 */
@Composable
private fun Composer(
    draft: String,
    isGenerating: Boolean,
    aiStatus: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = draft.isNotBlank() && !isGenerating

    Column(modifier = modifier.padding(top = 4.dp)) {
        // A complaint belongs beside the box it is about, not in the top bar.
        if (aiStatus.isNotBlank()) {
            Text(
                text = aiStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Ask a question", style = MaterialTheme.typography.bodyMedium)
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            SendButton(enabled = canSend, onClick = onSend)
        }
    }
}

/** A round send button, filled only when it can be pressed. */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(26.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

/**
 * The control row under the composer: model, settings gear, database toggle.
 *
 * Three controls sharing the width evenly, so the gear lands in the middle as
 * the mockups show. None of them steals height from the input above, which is
 * the part actually being used.
 */
@Composable
private fun ControlRow(
    models: List<String>,
    selectedModel: String?,
    useDatabase: Boolean,
    databaseName: String,
    databases: List<String>,
    onChooseModel: (String) -> Unit,
    onOpenOnlineModels: (() -> Unit)? = null,
    onChooseDatabase: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleDatabase: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)) {
        val needsTwoRows = maxWidth < 320.dp ||
            (maxWidth < 400.dp &&
                MaterialTheme.typography.labelLarge.fontSize.value * LocalDensity.current.fontScale > 22f)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModelPicker(models, selectedModel, onChooseModel, Modifier.weight(1f), onOpenOnlineModels)
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                if (!needsTwoRows) {
                    DatabasePicker(databases, databaseName, useDatabase,
                        onChooseDatabase, onToggleDatabase, Modifier.weight(1f))
                }
            }
            if (needsTwoRows) {
                DatabasePicker(databases, databaseName, useDatabase,
                    onChooseDatabase, onToggleDatabase, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Which model to use, as a compact dropdown.
 *
 * A dropdown rather than a list, because this row is crowded and a model
 * filename is far too long to show inline. The full list, including the reason
 * a model may not fit this phone, is in Settings.
 */
@Composable
private fun ModelPicker(
    models: List<String>,
    selectedModel: String?,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenOnlineModels: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { if (onOpenOnlineModels != null) onOpenOnlineModels() else expanded = true },
            enabled = onOpenOnlineModels != null || models.isNotEmpty(),
            color = Color.Transparent,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(3.dp))
                // weighted so the name may be shortened rather than setting a
                // floor for the whole row
                Text(
                    text = selectedModel ?: "No model",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Choose model",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (models.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "No models found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { expanded = false },
                )
            }
            models.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        expanded = false
                        onChoose(name)
                    },
                )
            }
        }
    }
}

/**
 * The database: which one, and whether it is used at all.
 *
 * TWO CONTROLS IN ONE PLACE, ON PURPOSE
 * ------------------------------------
 * Which database is open and whether answers are looked up in it are two
 * separate settings, but they are one decision to a user — "am I working from
 * my documents or not?" Splitting them meant one lived here and the other was
 * in Settings, and picking a different database in Settings did not switch it
 * here, so the two disagreed.
 *
 * So the name is a dropdown and the switch sits beside it. The switch stays on
 * its own because it is the safety control and must not be buried one tap
 * deeper than before.
 *
 * THE COLOUR IS LOAD-BEARING
 * -------------------------
 * When the database is off the whole control goes to the caution colour, not
 * just a word. An unchecked switch is easy to miss in a row of small controls;
 * a differently-coloured label is not.
 */
@Composable
private fun DatabasePicker(
    databases: List<String>,
    selectedDatabase: String,
    useDatabase: Boolean,
    onChoose: (String) -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val colour = if (useDatabase) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    Row(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                onClick = { expanded = true },
                enabled = databases.isNotEmpty(),
                color = Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = selectedDatabase.ifBlank {
                            if (useDatabase) "No database" else "Database off"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colour,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // weighted so a long name is shortened rather than
                        // setting a floor for the row, which is what pushed the
                        // switch off the edge of the screen
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "Choose database",
                        tint = colour,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                // Aligned to the end so the list grows upwards, away from the
                // keyboard and the navigation bar.
            ) {
                if (databases.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "No databases found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { expanded = false },
                    )
                }
                databases.forEach { name ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (name == selectedDatabase) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        },
                        onClick = {
                            expanded = false
                            onChoose(name)
                        },
                    )
                }
            }
        }

        Switch(
            checked = useDatabase,
            onCheckedChange = onToggle,
            modifier = Modifier.padding(start = 2.dp).semantics { contentDescription = "Use database" },
        )
    }
}

/**
 * The session panel: slides in from the left, as the mockups draw it.
 *
 * WHY A SIDE PANEL AND NOT A BOTTOM SHEET
 * --------------------------------------
 * The brief says sidebar icon and slide-out panel. A bottom sheet is the
 * right control for a choice, but this is a *place* — a list of conversations
 * you live in — and a panel from the edge reads as somewhere rather than
 * something that interrupts.
 *
 * WHY EVERY SESSION SHOWS ITS OWN SETTINGS
 * ----------------------------------------
 * Because that is what makes switching safe. Seeing "Database off" under a
 * conversation tells you, before you open it, that its answers were never
 * checked against documents. A bare list of "Session 1, Session 2" would hide
 * exactly the fact that matters.
 */
@Composable
fun SessionDrawer(
    visible: Boolean, sessions: List<Session>, activeId: String?,
    onSelect: (String) -> Unit, onNewSession: () -> Unit,
    onDelete: (String) -> Boolean, deletionEnabled: Boolean,
    onOpen: () -> Unit, onClose: () -> Unit, onOpenHelp: () -> Unit,
    content: @Composable () -> Unit,
) {
    var pendingDeletion by remember { mutableStateOf<Session?>(null) }
    var deletionFailed by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) pendingDeletion = null }
    pendingDeletion?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Delete session?") },
            text = { Text(if (deletionFailed) "Couldn’t delete this session. Please try again."
                else "Delete “${selected.title}” and all its messages? This cannot be undone.") },
            confirmButton = {
                TextButton(enabled = deletionEnabled, onClick = {
                    if (onDelete(selected.id)) pendingDeletion = null else deletionFailed = true
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("Cancel") } },
        )
    }
    val drawer = rememberDrawerState(if (visible) DrawerValue.Open else DrawerValue.Closed)
    val openCallback by rememberUpdatedState(onOpen)
    val closeCallback by rememberUpdatedState(onClose)
    LaunchedEffect(visible) { if (visible) drawer.open() else drawer.close() }
    LaunchedEffect(drawer) {
        snapshotFlow { drawer.currentValue }.collect {
            if (it == DrawerValue.Open) openCallback() else closeCallback()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = minOf(maxWidth * 0.82f, 360.dp)
        ModalNavigationDrawer(
            drawerState = drawer, gesturesEnabled = true,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(panelWidth).fillMaxHeight().testTag("session-panel"),
                    drawerShape = RoundedCornerShape(0.dp), windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        Text("Sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp))
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onNewSession, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp));Text("New session")
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(sessions.size, key = { sessions[it].id }) { index ->
                                val session = sessions[index]
                                SessionRow(session, session.id == activeId,
                                    onClick = { onSelect(session.id) },
                                    deletionEnabled = deletionEnabled,
                                    onDelete = { deletionFailed = false; pendingDeletion = session },
                                )
                            }
                        }
                        HorizontalDivider()
                        Surface(onClick = onOpenHelp, color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "Help" }) {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(16.dp));Text("Help", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }, content = content,
        )
    }
}

/** One session in the panel, highlighted when it is the one on screen. */
@Composable
private fun SessionRow(
    session: Session,
    isActive: Boolean,
    onClick: () -> Unit,
    deletionEnabled: Boolean, onDelete: () -> Unit,
) {
    val background = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val textColour = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        color = background,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = textColour,
                    maxLines = 1,
                )
                Text(
                    text = session.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) textColour else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onDelete, enabled = deletionEnabled,
                modifier = Modifier.testTag("delete-session-${session.id}")) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${session.title}", tint = textColour)
            }
            if (isActive) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Current session",
                    tint = textColour,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
