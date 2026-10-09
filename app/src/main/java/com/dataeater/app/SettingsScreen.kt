package com.dataeater.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dataeater.app.ui.ReplyBehaviorCard
import com.dataeater.app.ai.ReplyBehavior
import com.dataeater.app.ui.OpenRouterCard
import com.dataeater.app.ai.OpenRouterSettings
import com.dataeater.app.ai.OpenRouterProtocol
import com.dataeater.app.ai.DeviceMemory
import com.dataeater.app.ui.ModelDownloadDialog
import com.dataeater.app.ui.ModelDownloadUiState
import com.dataeater.app.ai.ModelCatalog
import com.dataeater.app.ai.ModelDownloads
import com.dataeater.app.ai.ModelFiles
import com.dataeater.app.data.DatabaseFiles
import com.dataeater.app.data.DatabaseLock
import com.dataeater.app.ui.theme.Appearance
import com.dataeater.app.ui.theme.DataEaterCard
import com.dataeater.app.ui.theme.TextSize
import com.dataeater.app.ui.theme.DataEaterGap

/**
 * The settings screen: what is loaded, what is open, and where it runs.
 *
 * WHY TWO SEPARATE AREAS AT THE TOP
 * ---------------------------------
 * The mockups show model selection and database selection as two distinct
 * areas, each of which opens its own list when tapped. They used to be one
 * card with a row for the database, a row for the model, and a permanent list
 * of models underneath — so the screen was always tall, and the database list
 * was somewhere else entirely, reached by leaving settings.
 *
 * Now each is its own card. Both start collapsed: one line saying what is
 * currently chosen. Tapping opens that area and shows its list, with the
 * action button on the right of each row. Only one is open at a time, because
 * two open lists on a phone means a long scroll to find anything.
 *
 * WHY THE LISTS ARE INSIDE THE CARD AND NOT A SEPARATE SCREEN
 * -----------------------------------------------------------
 * Choosing a model and choosing a database are both small acts, and both are
 * things a user does while setting up. Leaving the screen to make the choice
 * and coming back to see the result is three taps instead of one, and it is
 * how the two settings came to disagree with each other before.
 *
 * WHY A MODEL'S SIZE AND VERDICT STILL APPEAR
 * -------------------------------------------
 * Two models can differ by more than a gigabyte and one of them may not fit in
 * this phone at all. That has to be visible *before* the button is pressed,
 * not as a failure afterwards.
 *
 * WHAT IS NOT HERE, AND WHY
 * -------------------------
 * The model catalogue offers explicit downloads; inference remains on device.
 */

/** Everything the screen needs, gathered in one place. */
data class SettingsState(
    // --- database ------------------------------------------------------
    val databaseName: String,
    val databaseDetail: String,
    val databaseIsLocked: Boolean,

    /** Every database the user could switch to, the demo included. */
    val databases: List<DatabaseFiles.DatabaseFile>,

    /** Which one is selected. */
    val selectedDatabase: String,

    val lockStatuses: Map<String, DatabaseLock.Status>,
    val onChooseDatabase: (String) -> Unit,
    val onRescanDatabases: () -> Unit,
    val onDeleteDatabase: (DatabaseFiles.DatabaseFile) -> Unit,
    val databaseStatus: String,
    val isDatabaseBusy: Boolean,

    // --- model ---------------------------------------------------------
    val models: List<ModelFiles.ModelFile>,
    val loadedModelName: String?,
    val aiStatus: String,
    val isBusy: Boolean,
    val isModelBusy: Boolean,
    val deviceInfo: DeviceMemory.Info,

    val hasStorageAccess: Boolean,
    val onGrantStorageAccess: () -> Unit,
    val onRescanModels: () -> Unit,
    val onLoadModel: (ModelFiles.ModelFile) -> Unit,
    val onDeleteModel: (ModelFiles.ModelFile) -> Unit,
    val download: ModelDownloads.State,
    val onDownloadModel: (ModelCatalog.Entry) -> Unit,
    val onCancelDownload: () -> Unit,

    // --- processor -----------------------------------------------------
    val preferGpu: Boolean,
    val onPreferGpuChange: (Boolean) -> Unit,

    // --- appearance ----------------------------------------------------
    val appearance: Appearance,
    val onAppearanceChange: (Appearance) -> Unit,

    // --- text size -----------------------------------------------------
    val textSize: TextSize,
    val onTextSizeChange: (TextSize) -> Unit,

    // --- measured, not claimed ------------------------------------------
    /**
     * How long the model took to load, in seconds, or null if it has not.
     *
     * Measured on this phone, not looked up. It varies enormously between
     * devices, and the number is only useful because it is this device's.
     */
    val loadSeconds: Double?,

    /** How long the last answer took, in seconds. */
    val answerSeconds: Double?,

    /** How much memory the app is using, in megabytes. */
    val memoryMegabytes: Int?,

    /** Which backend the model actually ran on, which may not be the one asked for. */
    val backendInUse: String?,
    val replyBehavior: ReplyBehavior = ReplyBehavior(),
    val onReplyBehaviorChange: (ReplyBehavior) -> Unit = {},
    val openRouter: OpenRouterSettings = OpenRouterSettings(),
    val onlineActive: Boolean = false,
    val onUseOpenRouter: (String, OpenRouterProtocol.Model, Boolean) -> Unit = { _, _, _ -> },
    val onUseLocal: () -> Unit = {},
    val onRemoveOpenRouterKey: () -> Unit = {},
    val onCreateDatabase: () -> Unit = {},
)

/** Which of the two areas at the top is open. */
private enum class OpenArea { NONE, MODEL, DATABASE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, onBack: () -> Unit) {
    // Only one area open at a time, for the reason given above.
    var open by remember { mutableStateOf(OpenArea.NONE) }
    var getModels by remember { mutableStateOf(false) }
    if (getModels) ModelDownloadDialog(ModelDownloadUiState(
        state.models, state.download, state.deviceInfo, state.isBusy, state.hasStorageAccess,
        state.onDownloadModel, state.onCancelDownload, state.onLoadModel, state.loadedModelName), onDismiss = { getModels = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(DataEaterGap),
        ) {
            // --- the two areas, model first as the mockups show ---------
            OpenRouterCard(state.openRouter, state.onlineActive, state.isBusy || state.isDatabaseBusy,
                state.onUseOpenRouter, state.onUseLocal, state.onRemoveOpenRouterKey)
            ReplyBehaviorCard(state.replyBehavior, state.isBusy || state.isDatabaseBusy, state.onReplyBehaviorChange)
            ModelSelectionCard(
                state = state,
                onGetModels = { getModels = true },
                isOpen = open == OpenArea.MODEL,
                onToggle = {
                    open = if (open == OpenArea.MODEL) OpenArea.NONE else OpenArea.MODEL
                },
            )

            DatabaseSelectionCard(
                state = state,
                isOpen = open == OpenArea.DATABASE,
                onToggle = {
                    open =
                        if (open == OpenArea.DATABASE) OpenArea.NONE else OpenArea.DATABASE
                },
            )

            ExecutionCard(state)
            StatusCard(state)
            Button(
                onClick = state.onCreateDatabase,
                enabled = !state.isBusy && !state.isDatabaseBusy,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) { Text("Create database", modifier = Modifier.padding(vertical = 8.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. Model selection
// ---------------------------------------------------------------------------

/**
 * The model area.
 *
 * Collapsed: one row saying which model is loaded, or that none is.
 * Open: the model list with Load and Delete actions. Deletion requires confirmation.
 */
@Composable
private fun ModelSelectionCard(
    state: SettingsState,
    onGetModels: () -> Unit,
    isOpen: Boolean,
    onToggle: () -> Unit,
) {
    DataEaterCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                // The card grows and shrinks with its list, rather than
                // jumping, so the eye stays on the row that was tapped.
                .animateContentSize(tween(200))
        ) {
            SectionTitle("Model Selection")

            ChooserHeader(
                icon = Icons.Filled.Build,
                label = "Model",
                value = state.loadedModelName ?: "None loaded",
                detail = if (state.loadedModelName == null) {
                    "Tap to choose one"
                } else {
                    "Tap to change"
                },
                isOpen = isOpen,
                onClick = onToggle,
            )

            ExpandedArea(visible = isOpen) {
                when {
                    !state.hasStorageAccess -> PermissionNeeded(state)
                    state.models.isEmpty() -> NoModelsYet(state)
                    else -> LoadableModels(state)
                }

                OutlinedButton(onClick = onGetModels, modifier = Modifier.fillMaxWidth()) {
                    Text("Get models")
                }
                if (state.download.active) {
                    LinearProgressIndicator(progress = { state.download.progress }, modifier = Modifier.fillMaxWidth())
                    Text(state.download.message, style = MaterialTheme.typography.bodySmall)
                }
                if (state.aiStatus.isNotBlank()) {
                    StatusLine(state.aiStatus, isRunning = state.isModelBusy)
                }
            }
        }
    }
}

@Composable
private fun PermissionNeeded(state: SettingsState) {
    Text(
        text = "DataEater needs permission to read its folder.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = state.onGrantStorageAccess, enabled = !state.isBusy) { Text("Grant access") }
        OutlinedButton(onClick = state.onRescanModels) {
            Text("I have granted it")
        }
    }
}

@Composable
private fun NoModelsYet(state: SettingsState) {
    Text(
        text = "No models found.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "Put a .litertlm file into ${ModelFiles.dataEaterDirectory().absolutePath}",
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
    OutlinedButton(
        onClick = state.onRescanModels,
        modifier = Modifier.padding(top = 8.dp),
    ) { Text("Scan again") }
}

/**
 * The models the user can load or delete, with actions on the right.
 *
 * A list rather than a dropdown, on purpose. Two models can differ by more
 * than a gigabyte, and one may not fit this phone at all. A dropdown hides
 * that; a list shows the size and the reason a model cannot be used, which is
 * information the user needs *before* choosing, not as a failure afterwards.
 */
@Composable
private fun LoadableModels(state: SettingsState) {
    var pendingDelete by remember { mutableStateOf<ModelFiles.ModelFile?>(null) }
    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete model?") },
            text = {
                Text(
                    "Delete ${model.file.name} (${model.sizeInMegabytes} MB) from this phone? " +
                        "You will need to copy the file back to use it again." +
                        if (model.displayName == state.loadedModelName) {
                            " The loaded model will be closed first."
                        } else "",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isBusy,
                    onClick = {
                        pendingDelete = null
                        state.onDeleteModel(model)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "${state.models.size} model(s) in ${ModelFiles.dataEaterDirectory().name}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        for (model in state.models) {
            val isLoaded = model.displayName == state.loadedModelName
            val verdict = DeviceMemory.verdict(
                model.sizeInMegabytes.toInt(),
                state.deviceInfo,
            )
            val canLoad = verdict != DeviceMemory.Verdict.TOO_LARGE

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // weighted and shrinkable: a model file name can be very long,
                // and an unconstrained one pushes its own Load button off the
                // right edge of the screen.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLoaded) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${model.sizeInMegabytes} MB" +
                            if (isLoaded) "  ·  loaded" else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (verdict != DeviceMemory.Verdict.COMFORTABLE) {
                        Text(
                            text = DeviceMemory.explain(
                                verdict,
                                model.sizeInMegabytes.toInt(),
                                state.deviceInfo,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (canLoad) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    if (isLoaded) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Loaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        OutlinedButton(
                            onClick = { state.onLoadModel(model) },
                            enabled = !state.isBusy && canLoad,
                        ) { Text("Load") }
                    }
                    TextButton(
                        onClick = { pendingDelete = model },
                        enabled = !state.isBusy,
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }

            if (model != state.models.last()) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Database selection
// ---------------------------------------------------------------------------

/**
 * The database area, opening the same way as the model one.
 *
 * Only database files in the visible folder are offered.
 */
@Composable
private fun DatabaseSelectionCard(
    state: SettingsState,
    isOpen: Boolean,
    onToggle: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<DatabaseFiles.DatabaseFile?>(null) }
    pendingDelete?.let { database ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete database?") },
            text = {
                Text(
                    "Delete ${database.file.name} (${database.sizeInKilobytes} KB) from this phone? " +
                        "You will need to copy the file back to use it again." +
                        if (database.displayName == state.selectedDatabase) {
                            " It will no longer be used for answers."
                        } else "",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isBusy,
                    onClick = {
                        pendingDelete = null
                        state.onDeleteDatabase(database)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
    DataEaterCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(tween(200))
        ) {
            SectionTitle("Database Selection")

            ChooserHeader(
                icon = Icons.AutoMirrored.Filled.List,
                label = "Database",
                value = state.databaseName,
                detail = state.databaseDetail,
                // A locked database in the caution colour: it is chosen, but
                // not open, and the two look different on purpose.
                valueColour = if (state.databaseIsLocked) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                isOpen = isOpen,
                onClick = onToggle,
            )

            ExpandedArea(visible = isOpen) {
                if (!state.hasStorageAccess) PermissionNeeded(state)
                for (database in state.databases) {
                    DatabaseRow(
                        name = database.displayName,
                        detail = "${database.sizeInKilobytes} KB",
                        isSelected = state.selectedDatabase == database.displayName,
                        status = state.lockStatuses[database.displayName],
                        // A file that cannot be read at all is not offered:
                        // choosing it would produce a database screen with
                        // nothing behind it.
                        enabled = !state.isBusy && state.lockStatuses[database.displayName] !is
                            DatabaseLock.Status.Unreadable,
                        canDelete = !state.isBusy,
                        onDelete = { pendingDelete = database },
                        onChoose = { state.onChooseDatabase(database.displayName) },
                    )
                }

                if (state.hasStorageAccess && state.databases.isEmpty()) {
                    Text(
                        text = "No .dataeater files found. Put them in " +
                            "${com.dataeater.app.data.DatabaseFiles.folder().absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedButton(
                    onClick = state.onRescanDatabases,
                    enabled = !state.isBusy && state.hasStorageAccess,
                    modifier = Modifier.padding(top = 10.dp),
                ) { Text("Scan again") }
                if (state.databaseStatus.isNotBlank()) {
                    StatusLine(state.databaseStatus, isRunning = state.isDatabaseBusy)
                }
            }
        }
    }
}

/**
 * One database in the list, with the action button on the right.
 *
 * A locked database can still be chosen. It has to be: the Unlock Code is
 * entered on the search screen, and a user cannot get there without being able
 * to pick the database that needs unlocking. Choosing one hands over to that
 * screen, so the choice is never a dead end.
 */
@Composable
private fun DatabaseRow(
    name: String,
    detail: String,
    isSelected: Boolean,
    status: DatabaseLock.Status?,
    enabled: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onChoose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (status) {
                    is DatabaseLock.Status.Locked -> "$detail  ·  locked"
                    is DatabaseLock.Status.Unreadable -> "$detail  ·  cannot be read"
                    else -> detail
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (status) {
                    is DatabaseLock.Status.Locked -> MaterialTheme.colorScheme.tertiary
                    is DatabaseLock.Status.Unreadable -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            when {
                isSelected -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                !enabled -> Text(
                    text = "Unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> OutlinedButton(onClick = onChoose) { Text("Use") }
            }
            TextButton(onClick = onDelete, enabled = canDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// The expanding area both cards share
// ---------------------------------------------------------------------------

/**
 * The list that appears when an area is opened.
 *
 * Fades and grows rather than appearing at once, so it is obvious which row
 * opened it. Kept in the card rather than pushed to another screen, because
 * the chosen value appears in the row directly above as it changes.
 */
@Composable
private fun ExpandedArea(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(180)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) { content() }
    }
}

// ---------------------------------------------------------------------------
// 3. Where it runs
// ---------------------------------------------------------------------------

@Composable
private fun ExecutionCard(state: SettingsState) {
    DataEaterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle("Execution Environment")
            if (!state.onlineActive) {
            Text(
                text = "Processor",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelLarge,
            )

            // A segmented pair rather than two loose buttons: the choice is
            // between two options, and they should look like one control.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProcessorChoice(
                    label = "CPU",
                    selected = !state.preferGpu,
                    modifier = Modifier.weight(1f),
                    onClick = { state.onPreferGpuChange(false) },
                )
                ProcessorChoice(
                    label = "GPU",
                    selected = state.preferGpu,
                    modifier = Modifier.weight(1f),
                    onClick = { state.onPreferGpuChange(true) },
                )
            }

            Text(
                text = "Load the model again after changing this.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            }

            // --- appearance, directly under the processor ---------------
            Text(text = "Appearance", style = MaterialTheme.typography.labelLarge)

            AdaptiveChoices(
                labels = listOf("System", "Light", "Dark"),
                selectedIndex = state.appearance.ordinal,
                onChoose = { state.onAppearanceChange(Appearance.entries[it]) },
            )

            Text(
                // Not a preference with a right answer. Outdoors in daylight a
                // technician wants light; in a basement at night, dark. It is
                // the one setting here that is purely about where the phone is.
                text = "System follows the phone's own setting.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // --- text size, under the appearance ------------------------
            Text(text = "Text size", style = MaterialTheme.typography.labelLarge)

            AdaptiveChoices(
                labels = TextSize.entries.map { it.label },
                selectedIndex = state.textSize.ordinal,
                onChoose = { state.onTextSizeChange(TextSize.entries[it]) },
            )

            // Shows the real thing rather than describing it. "Large" is
            // abstract; four sample sizes at their actual sizes are not.
            Spacer(Modifier.height(10.dp))
            DataEaterCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "380 bar at 850 rpm",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Service Manual \u2014 3.1 Pressure \u2014 page 11",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = "Larger text makes more of a message fit less often. " +
                    "The largest size is capped so the composer still fits.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Fit choices to measured text, including Android's system font scaling. */
@Composable
private fun AdaptiveChoices(labels: List<String>, selectedIndex: Int, onChoose: (Int) -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
    val textWidth = labels.maxOf { measurer.measure(it, style).size.width }
    val minimumWidth = with(density) { textWidth.toDp() } + 8.dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val columns = ((maxWidth.value + 8f) / (minimumWidth.value.coerceAtLeast(48f) + 8f))
            .toInt().coerceIn(1, labels.size)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.indices.toList().chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { index ->
                        ProcessorChoice(labels[index], selectedIndex == index, Modifier.weight(1f)) {
                            onChoose(index)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ProcessorChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    ) {
        Text(
            text = label,
            // bodyMedium, not titleMedium.
            //
            // Four of these sit side by side, and at the largest text size a
            // 16 sp label overflowed its button and wrapped to two lines —
            // "Defaul / t". The row grew taller than the cards around it and
            // stopped reading as one control.
            style = MaterialTheme.typography.bodyMedium,
            // One line always: a size label split across two lines is worse
            // than a slightly smaller one.
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 14.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// 4. What is happening right now
// ---------------------------------------------------------------------------

/**
 * Status: what the phone is actually doing, measured rather than claimed.
 *
 * WHY THIS REPLACED A SUMMARY OF THE SETTINGS
 * ------------------------------------------
 * The old card repeated the three settings that were already on screen two
 * cards above: database, model, processor. Reading it told you what you had
 * already chosen, which is not status.
 *
 * What a technician actually wants to know is whether this is going to be
 * usable *here*: how long until the model is ready, how long an answer takes,
 * how much memory it is costing, and whether it really landed on the GPU they
 * selected. Those are all measured on the phone and logged to `logcat`.
 *
 * "—" for anything not measured yet, rather than a plausible-looking guess.
 * A speed figure nobody measured is worse than no speed figure.
 */
@Composable
private fun StatusCard(state: SettingsState) {
    DataEaterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle("Status")
            Spacer(Modifier.height(4.dp))

            StatusRow("Model loaded", state.loadedModelName ?: "No model")
            StatusRow("Running on", state.backendInUse ?: "—")
            StatusRow("Load time", formatSeconds(state.loadSeconds))
            StatusRow("Last answer", formatSeconds(state.answerSeconds))
            StatusRow("Memory in use", formatMemory(state.memoryMegabytes))

            if (state.loadSeconds != null && state.loadSeconds > 0) {
                Text(
                    text = "Measured on this phone. The chosen processor is " +
                        "applied when a model is loaded.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * A measured time in seconds, or "—" if there is not a usable measurement.
 *
 * The guard on negative values is not decoration. A phone's clock can step
 * backwards mid-measurement, and "-2.1 s" on a status card reads as a real
 * measurement rather than as a fault. A dash says "not known", which is the
 * truth. Locked in by `SettingsFormattingTest`.
 */
internal fun formatSeconds(value: Double?): String =
    if (value == null || value < 0.0) "—" else "%.1f s".format(value)

/** A measured amount of memory in megabytes. Zero is a real measurement. */
internal fun formatMemory(value: Int?): String =
    if (value == null || value < 0) "—" else "$value MB"

/**
 * One status line: a label on the left, a measured value on the right.
 *
 * The value is monospaced so the digits line up between rows. Reading a column
 * of numbers that jitters sideways is measurably slower than reading one that
 * does not, and this is a card of numbers.
 */
@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * The collapsed row, which is also the button that opens the area.
 *
 * Tapping anywhere on the row opens the list, because a row this wide with a
 * small chevron at the end is not an obvious target otherwise.
 *
 * The value is bold and coloured with the accent, because it is the part the
 * user is checking.
 */
@Composable
private fun ChooserHeader(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String,
    isOpen: Boolean,
    onClick: () -> Unit,
    valueColour: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 260.dp ||
                (maxWidth < 480.dp && LocalDensity.current.fontScale > 1.3f)
            if (stacked) {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        Icon(if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.ArrowDropDown,
                            contentDescription = if (isOpen) "Close $label" else "Change $label",
                            modifier = Modifier.size(24.dp))
                    }
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        color = valueColour, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            } else {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    // Both halves weighted, neither allowed to grow without bound.
                    // Without this the value - a database name, which can be
                    // arbitrarily long - takes the whole row and squeezes the label
                    // down to one character per line. That is not a theoretical worry:
                    // it is exactly what this screen did the first time it ran.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 4.dp),
                    ) {
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = valueColour,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                        )
                    }
                    // The chevron turns when the area is open, so the row still says
                    // what it will do once it is tapped again.
                    Icon(
                        if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.ArrowDropDown,
                        contentDescription = if (isOpen) "Close $label" else "Change $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, isRunning: Boolean) {
    Row(
        modifier = Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


