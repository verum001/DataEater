package com.dataeater.app

import com.dataeater.app.builder.BuilderScreen
import com.dataeater.app.builder.BuilderViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import com.dataeater.app.ui.ModelDownloadDialog
import com.dataeater.app.ui.ModelDownloadUiState
import com.dataeater.app.ui.HelpScreen
import androidx.compose.runtime.saveable.rememberSaveable
import com.dataeater.app.ai.ModelDownloads
import com.dataeater.app.ai.OpenRouterAiEngine
import com.dataeater.app.ai.OpenRouterSettingsStore
import com.dataeater.app.ai.OpenRouterProtocol
import com.dataeater.app.ai.BoundedGeneration
import com.dataeater.app.ai.ModelCatalog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dataeater.app.ai.ReplyBehavior
import com.dataeater.app.ai.ReplyBehaviorStore
import com.dataeater.app.ai.DatabaseStrictness
import com.dataeater.app.ui.OnlineModelPicker
import com.dataeater.app.ai.AnswerMode
import com.dataeater.app.ai.DeviceMemory
import com.dataeater.app.ai.LiteRtAiEngine
import com.dataeater.app.ai.LocalAiEngine
import com.dataeater.app.ai.ModelDeletion
import com.dataeater.app.ai.ModelFiles
import com.dataeater.app.chat.ChatMessage
import com.dataeater.app.chat.ChatResponder
import com.dataeater.app.chat.SessionController
import com.dataeater.app.chat.SessionStore
import com.dataeater.app.data.DatabaseDeletion
import com.dataeater.app.data.DatabaseChoice
import com.dataeater.app.data.DatabaseFiles
import com.dataeater.app.data.DatabaseLock
import com.dataeater.app.data.LicenceFiles
import com.dataeater.app.data.DatabaseReader
import com.dataeater.app.engine.AnswerCleaner
import com.dataeater.app.engine.RagEngine
import com.dataeater.app.engine.SearchEngine
import com.dataeater.app.model.Chunk
import com.dataeater.app.security.DeviceKeys
import com.dataeater.app.security.LicenceStore
import com.dataeater.app.security.UnlockCode
import com.dataeater.app.ui.ChatScreen
import com.dataeater.app.ui.SearchScreen
import com.dataeater.app.ui.UnlockScreen
import com.dataeater.app.ui.SessionDrawer
import com.dataeater.app.ui.theme.Appearance
import com.dataeater.app.ui.theme.AppearanceStore
import com.dataeater.app.ui.theme.DataEaterCard
import com.dataeater.app.ui.theme.DataEaterTheme
import com.dataeater.app.ui.theme.TextSize
import com.dataeater.app.ui.theme.TextSizeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CancellationException

/**
 * How many results a keyword search shows.
 *
 * Five, because the answer list is on a phone and a wall of matches helps
 * nobody.
 */
private const val MAX_RESULTS = 5

/**
 * A model that takes longer than this is treated as failed.
 *
 * Ninety seconds, not thirty: a large model on a cheap phone genuinely does
 * take this long for the first token, and giving up early would make the app
 * look broken on exactly the devices it is meant to help.
 */
private const val GENERATION_TIMEOUT_MS = 90_000L

private const val PERF_TAG = "DataEaterPerf"

/**
 * What happened when we tried to open the database the user chose.
 *
 * `Locked` is deliberately not an error. A locked database is a normal
 * database that happens to need an Unlock Code, and treating it as a failure
 * is what made the app claim the file was damaged.
 */
private sealed class OpenResult {
    data object Empty : OpenResult()
    data class Ready(val database: DatabaseReader.OpenedDatabase) : OpenResult()
    data class Locked(val status: DatabaseLock.Status.Locked) : OpenResult()
}

/**
 * How much memory this app is using right now, in megabytes.
 *
 * "PSS" is the kernel's fair measure: it counts shared memory only as much
 * as this app actually uses, so it is the honest number for comparing two
 * models rather than total resident memory.
 */
private fun readMemoryMegabytes(): Int {
    val info = Debug.MemoryInfo()
    Debug.getMemoryInfo(info)
    return info.totalPss / 1024
}

class MainActivity : ComponentActivity() {

    /**
     * The chosen appearance.
     *
     * Held here rather than inside the screen because it wraps the whole
     * screen: MaterialTheme has to be *above* the content for a switch to
     * repaint everything, so a toggle living inside the settings screen would
     * change that screen and nothing else.
     */
    private val appearanceStore by lazy { AppearanceStore(this) }
    private var appearance by mutableStateOf(Appearance.SYSTEM)

    /** Held beside [appearance] because both wrap the whole screen, not a part. */
    private val textSizeStore by lazy { TextSizeStore(this) }
    private var textSize by mutableStateOf(TextSize.DEFAULT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appearance = appearanceStore.read()
        textSize = textSizeStore.read()
        setContent {
            DataEaterTheme(appearance = appearance, textSize = textSize) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DataEaterScreen(
                        // The value AND the setter travel together, and both are
                        // passed on purpose.
                        //
                        // They were previously separate parameters and the value
                        // was never passed, so the screen was permanently told
                        // the setting was on its default. The theme read the
                        // real field, so the font changed — but no button ever
                        // lit up, and the control looked broken.
                        //
                        // A value and a way to change it that can disagree is
                        // the shape of that bug, so they are one call each.
                        appearance = appearance,
                        onAppearanceChange = { chosen ->
                            appearance = chosen
                            appearanceStore.write(chosen)
                        },
                        textSize = textSize,
                        onTextSizeChange = { chosen ->
                            textSize = chosen
                            textSizeStore.write(chosen)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The whole app.
 *
 * WHY THE STATE LIVES HERE
 * -----------------------
 * One screen owns the conversation, the open database, the loaded model and
 * the settings. That sounds like a lot for one function, and it is — but the
 * alternative, splitting it across screens, is what caused the earlier bug
 * where the model list and the load button existed in two places and could
 * disagree. The rules that *decide* anything are in `ChatResponder` and
 * `SessionController`, both tested on a computer; what is left here is wiring.
 *
 * WHY SEARCH IS STILL HERE ALONGSIDE CHAT
 * ---------------------------------------
 * Chat answers a question. Search shows what is actually in the documents.
 * A technician who needs to see the exact wording of a procedure still has to
 * be able to look, and burying that behind an AI summary would be a
 * regression. It is one tap away from the bar, on its own screen.
 */
@Composable
fun DataEaterScreen(
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    textSize: TextSize,
    onTextSizeChange: (TextSize) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val downloads = remember { ModelDownloads.get(context) }
    val downloadState by downloads.state.collectAsState()

    // --- conversations -------------------------------------------------
    /**
     * The sessions and which one is on screen.
     *
     * Held in a `remember`, not a `rememberSaveable`: the controller is the
     * thing that writes conversations to disk, and Compose saving it across a
     * configuration change would only produce a second, competing copy.
     */
    val sessions = remember { SessionController(SessionStore(context)) }
    var session by remember { mutableStateOf<com.dataeater.app.chat.Session?>(null) }
    var draft by remember { mutableStateOf("") }
    var showDrawer by remember { mutableStateOf(false) }
    var sessionListVersion by remember { mutableStateOf(0) }

    // --- which screen --------------------------------------------------
    var showSettings by remember { mutableStateOf(false) }
    var showModelCatalog by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showBuilder by rememberSaveable { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    /**
     * True while the Unlock screen is showing.
     *
     * Its own flag rather than reusing [showSearch], because the back button
     * has to return to the right place afterwards: a locked database found from
     * Search goes back to Search, one found by choosing a database in the
     * composer goes back to the chat.
     */
    var showUnlock by remember { mutableStateOf(false) }

    /**
     * Licence files found on the SD card, and which one is being tried.
     *
     * Re-read every time the Unlock screen opens rather than kept live: the
     * customer has just saved a file with a file manager, so the folder will
     * have changed while the app was in the background, and there is no reason
     * to watch a directory.
     */
    var licenceFiles by remember { mutableStateOf<List<LicenceFiles.LicenceFile>>(emptyList()) }
    var activeLicenceFile by remember { mutableStateOf<String?>(null) }

    // --- database ------------------------------------------------------
    var database by remember { mutableStateOf<DatabaseReader.OpenedDatabase?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Only external databases in /sdcard/DataEater/.
    var availableDatabases by remember {
        mutableStateOf<List<DatabaseFiles.DatabaseFile>>(emptyList())
    }

    /**
     * Which database is open.
     *
     * Starts on whatever was open last time, provided that file is still
     * there. See [DatabaseChoice] for why the fallback matters.
     */
    var selectedDatabase by remember {
        mutableStateOf(
            DatabaseChoice.restore(
                DatabaseChoice.stored(context),
                DatabaseFiles.findAll().map { it.displayName },
            )
        )
    }

    /**
     * A locked database the user has selected but cannot open yet.
     *
     * Non-null means "waiting for an Unlock Code", so the screen explains the
     * situation and who to contact rather than claiming the file is damaged.
     */
    var lockedDatabase by remember { mutableStateOf<DatabaseLock.Status.Locked?>(null) }

    /** Lock state of every database found, so the picker can label them. */
    var lockStatuses by remember {
        mutableStateOf<Map<String, DatabaseLock.Status>>(emptyMap())
    }

    /**
     * This phone's Request Code, once the user asks for it.
     *
     * Note it belongs to the **phone, not the database**. The same code works
     * for every locked database, so the customer only ever sends it once.
     */
    var requestCode by remember { mutableStateOf<String?>(null) }
    var requestCodeError by remember { mutableStateOf<String?>(null) }
    var isPreparingRequestCode by remember { mutableStateOf(false) }
    var hasCopiedRequestCode by remember { mutableStateOf(false) }

    /**
     * The Unlock Code the customer is about to paste in.
     *
     * Held in memory only, and deliberately NOT saved anywhere until it has
     * been checked. It contains the wrapped content key, so unlike a Request
     * Code it is a secret: it is never logged and never put on the clipboard.
     */
    var enteredUnlockCode by remember { mutableStateOf("") }
    var unlockProblem by remember { mutableStateOf<String?>(null) }
    var isCheckingUnlockCode by remember { mutableStateOf(false) }

/**
     * Hands the Request Code to whatever app the customer uses to talk to the
     * creator.
     *
     * Sharing rather than only copying, because it matches what they will do
     * with the reply: the creator will send a file back, and one tap here gets
     * the request into the same conversation.
     *
     * Nothing sensitive is shared. The Request Code is a public key.
     */
    fun shareRequestCode() {
        val code = requestCode ?: return
        val intent = android.content.Intent(
            android.content.Intent.ACTION_SEND,
        ).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "DataEater Request Code")
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "My DataEater Request Code:\n\n$code\n\n" +
                    "Send me back a .lic file and I will save it to " +
                    "${LicenceFiles.folderPath()}",
            )
        }
        try {
            context.startActivity(
                android.content.Intent.createChooser(intent, "Send my Request Code")
            )
        } catch (problem: Exception) {
            // No app able to send, which is unusual but not a crash worth
            // interrupting the customer over. The Copy button still works.
        }
    }

/**
     * Offers to write the Request Code somewhere the customer chooses.
     *
     * For the person who would rather put a file on the phone than send a
     * message — a desktop user copying it across with a cable, or anyone who
     * wants it in a folder rather than a conversation. Android's own document
     * picker is used, so the folder is theirs to choose and the app never has
     * to be told where it is.
     */
    fun saveRequestCodeToFile() {
        val code = requestCode ?: return
        try {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_CREATE_DOCUMENT,
                ).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(
                        android.content.Intent.EXTRA_TITLE,
                        "dataeater-request-code.txt",
                    )
                    putExtra(android.content.Intent.EXTRA_TEXT, code)
                }
            )
        } catch (problem: Exception) {
            // Nothing on the phone can choose a file. Sending still works.
        }
    }

    /**
     * Creates the phone's key pair if it does not exist yet, and reads the
     * Request Code out of it.
     *
     * Creating a key inside AndroidKeyStore can take a moment, so it runs off
     * the main thread. The private key is created once and never leaves the
     * phone; there is no code path that reads it out.
     */
    fun prepareRequestCode() {
        if (isPreparingRequestCode) return
        isPreparingRequestCode = true
        requestCodeError = null
        hasCopiedRequestCode = false
        coroutineScope.launch {
            try {
                requestCode = withContext(Dispatchers.IO) { DeviceKeys.requestCode() }
                // Made and handed straight over.
                //
                // There is no reason for the customer to see this string: it is
                // a public key, they cannot read it, and every extra screen
                // showing it is another way for them to think they are being
                // asked to do something with it. So making it opens the share
                // sheet, which is what they were about to do anyway.
                if (requestCode != null) shareRequestCode()
            } catch (problem: Exception) {
                requestCode = null
                requestCodeError = problem.message
                    ?: "This phone could not create a key for licences."
            } finally {
                isPreparingRequestCode = false
            }
        }
    }

    // --- AI ------------------------------------------------------------
    var ragEngine by remember { mutableStateOf<RagEngine?>(null) }
    var models by remember { mutableStateOf<List<ModelFiles.ModelFile>>(emptyList()) }
    var hasStorageAccess by remember { mutableStateOf(ModelFiles.hasStorageAccess(context)) }
    val routerStore = remember(context) { OpenRouterSettingsStore(context) }
    var routerSettings by remember { mutableStateOf(routerStore.read()) }
    val behaviorStore = remember(context) { ReplyBehaviorStore(context) }
    var replyBehavior by remember { mutableStateOf(behaviorStore.read()) }
    var showOnlineModels by remember { mutableStateOf(false) }
    var pendingPaidModel by remember { mutableStateOf<OpenRouterProtocol.Model?>(null) }
    var engine by remember { mutableStateOf<LocalAiEngine?>(null) }
    DisposableEffect(Unit) { onDispose { engine?.close() } }
    var aiStatus by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var isDatabaseBusy by remember { mutableStateOf(true) }
    var databaseStatus by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }

    // ---- measurements ---------------------------------------------------
    // Model choice must be based on measured numbers, not on impressions,
    // so the app measures its own load time, answer time and memory use.
    var loadSeconds by remember { mutableStateOf<Double?>(null) }
    var answerSeconds by remember { mutableStateOf<Double?>(null) }
    var memoryMegabytes by remember { mutableStateOf<Int?>(null) }

    // What this phone can actually afford to load. Read from Android rather
    // than guessed, so the same app behaves correctly on a cheap phone.
    var deviceInfo by remember { mutableStateOf(DeviceMemory.read(context)) }

    /**
     * Where the model runs.
     *
     * Mirrors the active session's own preference. The session is the thing
     * that remembers, and this is the live value the engine is built with.
     */
    var preferGpu by remember { mutableStateOf(true) }

    /**
     * Records a database name on the active session.
     *
     * A session remembers which database its conversation is about. Without
     * this, opening a database while viewing an old conversation would leave
     * the transcript claiming a database it was never taken from.
     */
    fun recordDatabaseInSession(name: String) {
        val updated = sessions.updateSettings(databaseName = name) ?: return
        session = updated
    }

    /** Saves a message onto the active session and shows it. */
    fun commit(message: ChatMessage) {
        val updated = sessions.append(message)
        if (updated != null) session = updated
    }

    /**
     * Asks the model and writes the answer into the conversation as it arrives.
     *
     * The answer is updated in memory word by word so the user watches it being
     * written, and marked [ChatMessage.incomplete] if it is stopped, so a
     * saved conversation never shows a half answer as though it were whole.
     */
    fun generate(
        prompt: String,
        answerId: String,
        sources: List<String>,
        answeredWithoutDatabase: Boolean = false,
        history: List<ChatMessage> = emptyList(),
        contextKey: String? = null,
        inputBudget: Int = 6000,
        replan: ((Int) -> BoundedGeneration.Request)? = null,
    ) {
        val activeEngine = engine ?: return
        val requestBehavior = replyBehavior

        // The empty answer exists on screen straight away, so the "Thinking"
        // row has something to sit under.
        commit(
            ChatMessage(
                id = answerId,
                fromUser = false,
                text = "",
                sources = sources,
                answeredWithoutDatabase = answeredWithoutDatabase,
                mayIncludeGeneralKnowledge = !answeredWithoutDatabase && requestBehavior.strictness == DatabaseStrictness.FLEXIBLE,
                contextKey = contextKey,
                incomplete = true,
            )
        )
        draft = ""
        isGenerating = true
        isBusy = true

        generationJob = coroutineScope.launch {
            val stream = com.dataeater.app.chat.StreamingAnswer()
            val answerStart = System.currentTimeMillis()
            var completed = false
            var suffix = ""
            // One IO checkpoint per second, rather than one full file per token.
            val checkpoints = launch {
                while (isActive) {
                    delay(1000)
                    val snapshot = sessions.active ?: continue
                    val saved = withContext(Dispatchers.IO) { sessions.saveSnapshot(snapshot) }
                    if (!saved) aiStatus = "Conversation could not be saved. Free storage before closing the app."
                }
            }

            try {
                val finished = withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                    BoundedGeneration.generate(
                        engine = activeEngine,
                        initial = BoundedGeneration.Request(prompt, if (answeredWithoutDatabase) AnswerMode.GENERAL_KNOWLEDGE else AnswerMode.DATABASE, history, sources),
                        initialBudget = inputBudget,
                        behavior = requestBehavior,
                        replan = replan,
                        onSources = { used -> sessions.replaceMessage(answerId, stream.pending().orEmpty(), incomplete = true, sources = used, persist = false)?.let { session = it } },
                    ) { newText ->
                        // This callback arrives on a background thread. Compose
                        // state must be changed on the main thread, otherwise
                        // the text can silently fail to appear on screen.
                        if (stream.append(newText)) coroutineScope.launch {
                            // Queued callbacks must not overwrite the final answer.
                            val pending = stream.pending() ?: return@launch
                            sessions.replaceMessage(
                                messageId = answerId,
                                text = AnswerCleaner.clean(pending),
                                incomplete = true,
                                persist = false,
                            )?.let { session = it }
                        }
                    }
                    true
                }
                if (finished == null) {
                    suffix = "\n\n[No answer after ${GENERATION_TIMEOUT_MS / 1000} seconds.]"
                } else if (AnswerCleaner.clean(stream.pending().orEmpty()).isBlank()) {
                    suffix = "\n\n[This model returned no answer. Please choose another model.]"
                } else {
                    completed = true
                }
            } catch (problem: CancellationException) {
                // The user stopped it. Not an error.
                suffix = "\n\n[Stopped.]"
            } catch (problem: Exception) {
                suffix = "\n\n[Error: ${problem.message}]"
            }

            // Remove any reasoning the model printed anyway.
            val finalText = AnswerCleaner.clean(stream.finish(suffix))
            // Stop can cancel the generation coroutine; the final checkpoint
            // still runs, after any older checkpoint has finished writing.
            withContext(NonCancellable) { checkpoints.cancelAndJoin() }
            sessions.replaceMessage(
                messageId = answerId,
                text = finalText,
                incomplete = !completed,
                final = true,
                persist = false,
            )?.let { session = it }
            val snapshot = sessions.active
            if (snapshot != null) {
                val saved = withContext(NonCancellable + Dispatchers.IO) { sessions.saveSnapshot(snapshot) }
                if (!saved) aiStatus = "Conversation could not be saved. Free storage before closing the app."
            }

            answerSeconds = (System.currentTimeMillis() - answerStart) / 1000.0
            memoryMegabytes = readMemoryMegabytes()
            // Written to logcat so the numbers can be read from a computer
            // without the user having to photograph anything.
            Log.i(
                PERF_TAG,
                "PERF model=${engine?.modelName} load=${loadSeconds ?: -1.0}s " +
                    "answer=$answerSeconds s appPss=$memoryMegabytes MB " +
                    "database=${session?.useDatabase} " +
                    "backend=${(engine as? LiteRtAiEngine)?.backendInUse}",
            )
            isGenerating = false
            isBusy = false
            generationJob = null
        }
    }

    /**
     * Checks an Unlock Code and, if it is sound, opens the database.
     *
     * The order matters and is fixed by `UnlockCode`: parse, then signature,
     * then expiry, then database id — and only then does the private key get
     * involved. Everything before the private key is testable on a computer.
     */
    fun unlockWithCode() {
        val locked = lockedDatabase ?: return
        if (isCheckingUnlockCode) return
        isCheckingUnlockCode = true
        unlockProblem = null
        coroutineScope.launch {
            try {
                val file = availableDatabases
                    .firstOrNull { it.displayName == selectedDatabase }?.file
                    ?: throw DatabaseReader.DataEaterException(
                        "The database file is no longer there."
                    )

                val licence = withContext(Dispatchers.IO) {
                    val parsed = UnlockCode.parse(enteredUnlockCode)
                    val checked = UnlockCode.check(
                        parsed,
                        locked.manifest.creatorPublicKey,
                        expectedDatabaseId = locked.manifest.databaseId,
                    )
                    if (checked is UnlockCode.Checked.Rejected) {
                        throw UnlockCode.Refused(checked.reason)
                    }

                    // Past every check: now, and only now, the key pair.
                    val contentKey = UnlockCode.recoverContentKey(parsed)
                    Pair(DatabaseReader.openLocked(file, contentKey), contentKey)
                }

                database = licence.first
                lockedDatabase = null
                ragEngine = withContext(Dispatchers.Default) { RagEngine(licence.first.chunks, licence.first.sources) }
                recordDatabaseInSession(licence.first.manifest.name)

                // Remembered so the database opens again next time, with no code to paste.
                //
                // What is stored is the Unlock Code, NOT the content key. That
                // distinction matters: an Unlock Code cannot decrypt anything
                // by itself, because the phone's private key stays inside
                // AndroidKeyStore. Writing the bare content key to a file would
                // put something directly useful on disk.
                //
                // It is re-checked from scratch every time it is used, so an
                // expiry that has since passed still takes effect.
                LicenceStore.save(
                    context,
                    locked.manifest.databaseId,
                    enteredUnlockCode.trim().toByteArray(Charsets.UTF_8),
                )
                enteredUnlockCode = ""
            } catch (problem: UnlockCode.Refused) {
                unlockProblem = problem.message
            } catch (problem: DeviceKeys.LicenceException) {
                unlockProblem = problem.message
            } catch (problem: Exception) {
                unlockProblem = problem.message ?: "The Unlock Code was refused."
            } finally {
                isCheckingUnlockCode = false
            }
        }
    }

    /**
     * Opens a locked database using a licence entered earlier, if there is
     * still a valid one.
     *
     * Returns null for every ordinary reason: no licence stored, or the stored
     * one is refused. A refusal is not reported as an error, because the user
     * is about to be shown the normal "this database is locked" screen and can
     * paste a fresh code.
     */
    fun tryRememberedLicence(
        file: java.io.File,
        locked: DatabaseLock.Status.Locked,
    ): DatabaseReader.OpenedDatabase? {
        val stored = LicenceStore.get(context, locked.manifest.databaseId)
            ?: return null
        val text = String(stored, Charsets.UTF_8)

        return try {
            val licence = UnlockCode.parse(text)
            when (val checked = UnlockCode.check(
                licence,
                locked.manifest.creatorPublicKey,
                expectedDatabaseId = locked.manifest.databaseId,
            )) {
                is UnlockCode.Checked.Rejected -> null
                is UnlockCode.Checked.Accepted ->
                    DatabaseReader.openLocked(
                        file,
                        UnlockCode.recoverContentKey(licence),
                    )
            }
        } catch (problem: DatabaseReader.IntegrityException) {
            // A new licence cannot repair changed database bytes. Show the error.
            throw problem
        } catch (problem: Exception) {
            // Expired, wrong phone, damaged file — all of which simply mean
            // "ask for a new code". None of them is worth a scary message here.
            null
        }
    }

    /**
     * Opens whichever database the user has chosen.
     *
     * A LOCKED database cannot be opened, because the text inside it is
     * encrypted. That is not a failure: it is a database waiting for its
     * Unlock Code, and the interface shows it as such instead of pretending
     * the file is broken.
     */
    fun openSelectedDatabase(): OpenResult {
        if (selectedDatabase.isBlank()) return OpenResult.Empty

        val chosen = availableDatabases.firstOrNull { it.displayName == selectedDatabase }
            ?: throw IllegalStateException("The database file is no longer there.")

        return when (val status = DatabaseLock.inspect(chosen.file)) {
            is DatabaseLock.Status.Open -> OpenResult.Ready(
                DatabaseReader.openFile(chosen.file)
            )
            is DatabaseLock.Status.Locked ->
                // A licence already entered on this phone is tried first, so a
                // customer who has paid does not have to paste the same code
                // every morning.
                //
                // It is verified in full again every time — signature, expiry,
                // database id — so a remembered code stops working the day it
                // expires, exactly as it should.
                tryRememberedLicence(chosen.file, status)
                    ?.let { OpenResult.Ready(it) }
                    ?: OpenResult.Locked(status)
            is DatabaseLock.Status.Unreadable ->
                throw DatabaseReader.DataEaterException(status.reason)
        }
    }

    /**
     * Works out which databases are locked, without opening any of them.
     *
     * Only `manifest.json` is read, and it is the first entry in the ZIP, so
     * this is cheap. It is still disk I/O on a real phone, so it is moved off
     * the main thread: the interface must not freeze while it happens.
     */
    suspend fun inspectAll(): Map<String, DatabaseLock.Status> =
        withContext(Dispatchers.IO) {
            availableDatabases.associate { candidate ->
                candidate.displayName to DatabaseLock.inspect(candidate.file)
            }
        }


/**
     * Shows the Unlock screen, first re-reading the licence folder.
     *
     * Going through one function so the folder is refreshed no matter which
     * button got us here — the customer may have saved the file while the app
     * was closed, and a stale empty list would look like the file went missing.
     */
    fun openUnlockScreen() {
        // Inline rather than in a coroutine: this lists one folder and reads a
        // few files of a kilobyte each. `DatabaseLock.inspect` reads a ZIP
        // member and is moved off the main thread; this is much less work.
        licenceFiles = LicenceFiles.findAll()
        activeLicenceFile = null
        unlockProblem = null
        showUnlock = true
    }

    fun clearOpenDatabase() {
        database = null
        lockedDatabase = null
        ragEngine = null
        showUnlock = false
        enteredUnlockCode = ""
        unlockProblem = null
    }

    // Runs once when the screen first appears: restore the conversation, open
    // the database and look for AI model files that are on the phone.
    LaunchedEffect(Unit) {
        try {
            availableDatabases = if (hasStorageAccess) DatabaseFiles.findAll() else emptyList()
            lockStatuses = if (hasStorageAccess) inspectAll() else emptyMap()
            try {
                when (val opened = withContext(Dispatchers.IO) { openSelectedDatabase() }) {
                    OpenResult.Empty -> {
                        clearOpenDatabase()
                        recordDatabaseInSession("")
                    }
                    is OpenResult.Ready -> {
                        database = opened.database
                        lockedDatabase = null
                        ragEngine = withContext(Dispatchers.Default) { RagEngine(opened.database.chunks, opened.database.sources) }
                    }
                    is OpenResult.Locked -> {
                        database = null
                        lockedDatabase = opened.status
                        // Through the same door as everywhere else.
                        //
                        // The remembered database is very often a locked one, so
                        // this is the usual way into the Unlock screen — and it
                        // was setting the flag without reading the licence folder,
                        // so a customer who had already saved their .lic file was
                        // told no file was found.
                        openUnlockScreen()
                    }
                }
            } catch (problem: Exception) {
                errorMessage = problem.message ?: "Unknown problem"
            }
            models = ModelFiles.findAll(context)
            deviceInfo = DeviceMemory.read(context)

            // The conversation comes last, once the database is known, so the
            // first session can be stamped with the database it is talking to.
            val restored = sessions.start()
            if (database != null && restored.databaseName.isBlank()) {
                recordDatabaseInSession(database!!.manifest.name)
            }
            preferGpu = restored.preferGpu
            session = sessions.active
            if (selectedDatabase.isBlank()) recordDatabaseInSession("")
        } finally {
            isDatabaseBusy = false
        }
    }

    /** The name on screen of the database awaiting an Unlock Code, if any. */
    fun lockedDatabaseName(): String? = lockedDatabase?.manifest?.name

    /** Refreshes files and replaces the active retrieval state, never choosing a fallback. */
    suspend fun refreshDatabases() {
        availableDatabases = withContext(Dispatchers.IO) { DatabaseFiles.findAll() }
        selectedDatabase = DatabaseChoice.restore(
            selectedDatabase, availableDatabases.map { it.displayName },
        )
        if (selectedDatabase.isBlank()) DatabaseChoice.forget(context)
        lockStatuses = inspectAll()
        clearOpenDatabase()
        when (val opened = withContext(Dispatchers.IO) { openSelectedDatabase() }) {
            OpenResult.Empty -> recordDatabaseInSession("")
            is OpenResult.Ready -> {
                database = opened.database
                ragEngine = withContext(Dispatchers.Default) { RagEngine(opened.database.chunks, opened.database.sources) }
                recordDatabaseInSession(opened.database.manifest.name)
            }
            is OpenResult.Locked -> {
                lockedDatabase = opened.status
                recordDatabaseInSession(opened.status.manifest.name)
            }
        }
    }

    fun rescanDatabases() {
        if (isBusy || isDatabaseBusy) return
        if (!ModelFiles.hasStorageAccess(context)) {
            databaseStatus = "Grant storage access to read databases."
            return
        }
        isDatabaseBusy = true
        databaseStatus = "Refreshing databases…"
        coroutineScope.launch {
            errorMessage = null
            try {
                refreshDatabases()
                databaseStatus = "Database list refreshed."
            } catch (problem: Exception) {
                databaseStatus = "Could not open the database: ${problem.message}"
                errorMessage = problem.message ?: "Unknown problem"
            } finally {
                isDatabaseBusy = false
            }
        }
    }

    fun deleteDatabase(chosen: DatabaseFiles.DatabaseFile) {
        if (isBusy || isDatabaseBusy) return
        isDatabaseBusy = true
        databaseStatus = "Deleting ${chosen.displayName}…"
        coroutineScope.launch {
            var deleted = false
            try {
                withContext(Dispatchers.IO) {
                    DatabaseDeletion.delete(chosen.file, DatabaseFiles.folder())
                }
                deleted = true
                if (selectedDatabase == chosen.displayName) {
                    selectedDatabase = ""
                    DatabaseChoice.forget(context)
                    clearOpenDatabase()
                    recordDatabaseInSession("")
                    errorMessage = null
                }
                refreshDatabases()
                databaseStatus = "Deleted ${chosen.displayName}."
            } catch (problem: Exception) {
                databaseStatus = if (deleted) {
                    "Deleted ${chosen.displayName}, but could not reopen the selected database: ${problem.message}"
                } else "Could not delete ${chosen.displayName}: ${problem.message}"
                availableDatabases = DatabaseFiles.findAll()
                lockStatuses = inspectAll()
            } finally {
                isDatabaseBusy = false
            }
        }
    }

    var pendingDatabaseDeletion by remember { mutableStateOf<DatabaseFiles.DatabaseFile?>(null) }
    val databaseDeletePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val chosen = pendingDatabaseDeletion
        pendingDatabaseDeletion = null
        if (chosen != null) {
            if (granted) deleteDatabase(chosen)
            else databaseStatus = "Database not deleted: storage write permission was not granted."
        }
    }

    fun databaseChoices(): List<String> = availableDatabases.map { it.displayName }

    fun databaseIdFor(label: String): String? =
        availableDatabases.firstOrNull { it.displayName == label }?.displayName

    /** [inspectAll] without suspending, for a path that cannot. */
    fun inspectAllBlocking(): Map<String, DatabaseLock.Status> =
        availableDatabases.associate { it.displayName to DatabaseLock.inspect(it.file) }

    /**
     * Opens a stored licence if there is a valid one, and reports nothing if not.
     *
     * Used when a locked database is chosen, so a customer who paid last week is
     * not asked to paste anything again.
     */
    fun openRememberedOnly() {
        coroutineScope.launch {
            try {
                when (val opened = withContext(Dispatchers.IO) { openSelectedDatabase() }) {
                    OpenResult.Empty -> {
                        clearOpenDatabase()
                        recordDatabaseInSession("")
                    }
                    is OpenResult.Ready -> {
                        database = opened.database
                        lockedDatabase = null
                        ragEngine = withContext(Dispatchers.Default) { RagEngine(opened.database.chunks, opened.database.sources) }
                        recordDatabaseInSession(opened.database.manifest.name)
                        showUnlock = false
                    }
                    is OpenResult.Locked -> lockedDatabase = opened.status
                }
            } catch (problem: Exception) {
                errorMessage = problem.message ?: "Unknown problem"
            }
        }
    }



    /**
     * Tries one licence file, reporting the real reason if it is refused.
     *
     * The reason matters: "made for a different phone", "expired" and "this is
     * not a DataEater licence" are three different problems, and a customer
     * who is told only "refused" cannot act on any of them.
     */
    fun useLicenceFile(file: LicenceFiles.LicenceFile) {
        if (isCheckingUnlockCode) return
        val text = LicenceFiles.read(file.name)
        if (text == null) {
            // A missing file is not a bad licence. Saying otherwise would send
            // the customer to ask the creator for a code they already have.
            licenceFiles = LicenceFiles.findAll()
            unlockProblem = "That file could not be read. It may have been " +
                "moved or deleted — save it into the folder again."
            return
        }

        activeLicenceFile = file.name
        enteredUnlockCode = text
        unlockWithCode()
    }





    /**
     * Chooses a database, on any screen.
     *
     * The choice is remembered on disk so the app reopens it next launch, and
     * stamped onto the active session so the conversation says which documents
     * it is about.
     */
    fun choose(name: String) {
        if (isBusy || isDatabaseBusy) return
        selectedDatabase = name
        DatabaseChoice.remember(context, name)
        rescanDatabases()
    }

/**
     * Chooses a database and sends the user wherever that choice leads.
     *
     * ONE function, because there are two ways in — the composer dropdown and
     * the settings list — and when each had its own copy they drifted. The
     * composer copy left out the locked case entirely, so choosing a locked
     * database put the app on "Opening your database…" with no database behind
     * it: a spinner that never resolved and no way forward.
     *
     * A locked database cannot be opened, so the Unlock screen is raised at once
     * rather than after a failed attempt. The user is never left on a dead end.
     */
    fun chooseAndRoute(id: String) {
        if (isBusy || isDatabaseBusy) return
        if (id == selectedDatabase) return

        val status = availableDatabases
            .firstOrNull { it.displayName == id }
            ?.file
            ?.let { DatabaseLock.inspect(it) }

        if (status !is DatabaseLock.Status.Locked) {
            choose(id)
            return
        }

        // Recorded first, so the Unlock screen has a manifest to show and the
        // remembered licence still gets its chance.
        //
        // The locked status is set HERE, synchronously, rather than waiting for
        // the attempt below. The Unlock screen cannot be drawn without it, and
        // waiting meant the screen and the state disagreed for one frame — which
        // crashed the app, because the branch force-unwrapped a null.
        selectedDatabase = id
        DatabaseChoice.remember(context, id)
        availableDatabases = DatabaseFiles.findAll()
        lockStatuses = inspectAllBlocking()
        lockedDatabase = status
        openUnlockScreen()
        openRememberedOnly()
    }

    /**
     * Loads an AI model and reports what happened.
     *
     * Shared by the chat screen and the settings screen, so both report the
     * same way and there is only one copy of the timing measurements.
     *
     * Loading takes up to about twenty seconds for a large model, so it runs in
     * a coroutine and `aiStatus` is what the user watches meanwhile.
     */
    fun useOpenRouter(key: String, model: OpenRouterProtocol.Model, allowDatabase: Boolean) {
        if (isBusy || isDatabaseBusy) return
        coroutineScope.launch {
            isBusy = true
            try {
                val secret = withContext(Dispatchers.IO) {
                    routerStore.save(key, model, allowDatabase)
                    routerStore.key()
                }
                val online = OpenRouterAiEngine(secret, model.id, model.name)
                online.load()
                engine?.close()
                engine = online
                routerSettings = routerStore.read()
                loadSeconds = null; answerSeconds = null
                aiStatus = "Online AI ready. Questions are sent to OpenRouter."
                sessions.updateSettings(modelName = online.modelName)?.let { session = it }
            } catch (_: Exception) {
                routerSettings = routerStore.read()
                aiStatus = "Could not set up OpenRouter. Check or replace your saved API key."
            } finally { isBusy = false }
        }
    }

    if (showOnlineModels) OnlineModelPicker(onChoose = { model ->
        showOnlineModels = false
        if (model.free) useOpenRouter("", model, routerSettings.allowDatabase) else pendingPaidModel = model
    }, onDismiss = { showOnlineModels = false })
    pendingPaidModel?.let { model ->
        AlertDialog(onDismissRequest = { pendingPaidModel = null }, title = { Text("Use paid model?") },
            text = { Text("${model.name} uses your OpenRouter credits.") },
            confirmButton = { TextButton(onClick = { pendingPaidModel = null; useOpenRouter("", model, routerSettings.allowDatabase) }) { Text("Use model") } },
            dismissButton = { TextButton(onClick = { pendingPaidModel = null }) { Text("Cancel") } })
    }

    fun useLocalAi() {
        if (isBusy || isDatabaseBusy) return
        if (engine is OpenRouterAiEngine) {
            engine?.close(); engine = null
            loadSeconds = null; answerSeconds = null
            aiStatus = "On-device AI selected. Choose a local model."
        }
    }

    fun removeOpenRouterKey() {
        if (isBusy || isDatabaseBusy) return
        useLocalAi()
        coroutineScope.launch {
            isBusy = true
            try { withContext(Dispatchers.IO) { routerStore.remove() }; routerSettings = routerStore.read(); aiStatus = "OpenRouter key removed." }
            catch (_: Exception) { aiStatus = "Could not remove the saved key. Please retry." }
            finally { isBusy = false }
        }
    }

    fun loadModel(chosen: ModelFiles.ModelFile) {
        if (isBusy || isDatabaseBusy) return
        coroutineScope.launch {
            isBusy = true
            aiStatus = "Loading ${chosen.displayName}, please wait..."
            try {
                engine?.close()
                engine = null
                val newEngine = LiteRtAiEngine(
                    modelFile = chosen.file,
                    cacheDirectory = context.cacheDir,
                    preferGpu = preferGpu,
                )
                val loadStart = System.currentTimeMillis()
                newEngine.load()
                loadSeconds = (System.currentTimeMillis() - loadStart) / 1000.0
                memoryMegabytes = readMemoryMegabytes()
                engine = newEngine
                aiStatus = "Model ready, using ${newEngine.backendInUse}."

                // The session remembers which model its answers came from.
                sessions.updateSettings(modelName = newEngine.modelName)?.let { session = it }
            } catch (problem: Exception) {
                aiStatus = "Could not load the model: ${problem.message}"
            }
            isBusy = false
        }
    }

    fun rescanModels() {
        hasStorageAccess = ModelFiles.hasStorageAccess(context)
        models = ModelFiles.findAll(context)
        deviceInfo = DeviceMemory.read(context)
    }

    // Refresh after Android's permission screen, including returning with Back.
    fun refreshStorageAccess() {
        val granted = ModelFiles.hasStorageAccess(context)
        val changed = granted != hasStorageAccess
        if (changed && (isBusy || isDatabaseBusy)) return
        rescanModels()
        if (changed) {
            if (granted) {
                selectedDatabase = DatabaseChoice.stored(context) ?: ""
                rescanDatabases()
            } else {
                availableDatabases = emptyList()
                lockStatuses = emptyMap()
                clearOpenDatabase()
                databaseStatus = "Grant storage access to read databases."
            }
        }
    }

    LaunchedEffect(downloadState.installed) { rescanModels() }
    var pendingModelDownload by remember { mutableStateOf<ModelCatalog.Entry?>(null) }
    val downloadWritePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val entry = pendingModelDownload
        pendingModelDownload = null
        if (granted && entry != null) downloads.start(entry)
        else aiStatus = "Download needs storage write permission."
    }

    val readStoragePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshStorageAccess() }
    val storageSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshStorageAccess() }
    val refreshStorageOnResume by rememberUpdatedState({ refreshStorageAccess() })
    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStorageOnResume()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(isBusy, isDatabaseBusy) {
        if (!isBusy && !isDatabaseBusy) refreshStorageAccess()
    }

    fun deleteModel(chosen: ModelFiles.ModelFile) {
        if (isBusy || isDatabaseBusy) return
        isBusy = true
        coroutineScope.launch {
            try {
                // Validate before closing an engine or touching any file.
                ModelDeletion.validate(chosen.file, ModelFiles.dataEaterDirectory())
                if (engine?.modelName == chosen.displayName) {
                    engine?.close()
                    engine = null
                    loadSeconds = null
                    answerSeconds = null
                    memoryMegabytes = readMemoryMegabytes()
                }
                withContext(Dispatchers.IO) {
                    ModelDeletion.delete(chosen.file, ModelFiles.dataEaterDirectory())
                }
                aiStatus = "Deleted ${chosen.displayName}."
                if (session?.modelName == chosen.displayName) {
                    sessions.updateSettings(modelName = null)?.let { session = it }
                }
            } catch (problem: Exception) {
                aiStatus = "Could not delete ${chosen.displayName}: ${problem.message}"
            } finally {
                rescanModels()
                isBusy = false
            }
        }
    }

    // Android 8–10 need write permission as well as the existing read access.
    var pendingModelDeletion by remember { mutableStateOf<ModelFiles.ModelFile?>(null) }
    val modelDeletePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val chosen = pendingModelDeletion
        pendingModelDeletion = null
        if (chosen != null) {
            if (granted) deleteModel(chosen)
            else aiStatus = "Model not deleted: storage write permission was not granted."
        }
    }

    /**
     * Switches to another conversation.
     *
     * Restores that session's model, database and processor choice, as the
     * brief requires. A model is reloaded only if the phone does not already
     * have the right one — switching sessions should be instant, not a
     * twenty-second wait.
     */
    fun switchSession(id: String) {
        val restored = sessions.switchTo(id)
        session = restored
        preferGpu = restored.preferGpu
        showDrawer = false
        draft = ""
        aiStatus = ""

        // Restore the database if this session was talking about a different one.
        if (restored.databaseName.isNotBlank() && restored.databaseName != selectedDatabase) {
            val known = availableDatabases.any { it.displayName == restored.databaseName }
            if (known) {
                selectedDatabase = restored.databaseName
                DatabaseChoice.remember(context, restored.databaseName)
                rescanDatabases()
            }
        }

        // Restore the model if it is a different one from what is loaded.
        val wanted = restored.modelName
        val running = engine?.modelName
        if (wanted != null && wanted != running) {
            models.firstOrNull { it.displayName == wanted }
                ?.let { loadModel(it) }
        }
    }

    /** Starts a new conversation. */
    fun newSession() {
        val created = sessions.newSession()
        session = created
        preferGpu = created.preferGpu
        showDrawer = false
        draft = ""
        aiStatus = ""
    }

    /**
     * The database toggle.
     *
     * Recorded on the session, not globally, so switching back to a
     * conversation restores the state it was actually had in. That is the
     * difference between "I checked this against the manual" and "I did not",
     * so it is not a cosmetic setting.
     */
    fun setDatabaseOn(on: Boolean) {
        sessions.updateSettings(useDatabase = on, persist = !isGenerating)?.let { session = it }
        aiStatus = if (on) {
            "Answers will be looked up in your documents."
        } else {
            "The database is off. Answers come from the model's own memory " +
                "and are not checked against any document."
        }
    }

    /**
     * Sends the typed question.
     *
     * All of the deciding is done by [ChatResponder], which is tested without
     * a phone. What is left here is: put the user's words on screen, call the
     * model if the responder says to, and write down what came back.
     */
    fun send() {
        if (isBusy || isDatabaseBusy) return
        if (session?.useDatabase != false && database == null) {
            aiStatus = "Choose a database in Settings before asking a question."
            return
        }
        val active = engine
        if (active is OpenRouterAiEngine && session?.useDatabase != false && !routerSettings.allowDatabase) {
            aiStatus = "Database sharing is off for online AI. Enable it in OpenRouter settings, or turn Database off."
            return
        }
        val typed = draft.trim()
        if (typed.isEmpty()) return

        val baseContext = if (session?.useDatabase != false) database?.let { "${it.fileName}:${it.manifest.databaseId}" } else "general"
        val scopedContext = replyBehavior.memoryContext(baseContext ?: "general")
        val memoryContext = if (active is OpenRouterAiEngine) "online:$scopedContext" else scopedContext
        val requestBehavior = replyBehavior
        val requestHistory = session?.messages.orEmpty().toList()
        val requestRagEngine = ragEngine
        val requestDatabaseName = database?.manifest?.name ?: "None"
        val useDatabase = session?.useDatabase ?: true
        val inputBudget = if (active is OpenRouterAiEngine) 16000 else ModelCatalog.forFile((active?.modelName ?: "") + ".litertlm")?.inputChars ?: 6000
        fun decideWithin(budget: Int): ChatResponder.Decision = ChatResponder(
            ragEngine = requestRagEngine,
            databaseName = requestDatabaseName,
            contextKey = memoryContext,
            inputBudget = budget,
            textCost = if (active is LiteRtAiEngine) { value -> value.toByteArray(Charsets.UTF_8).size } else { value -> value.length },
            instructionReserve = if (active is LiteRtAiEngine) requestBehavior.instruction(if (useDatabase) AnswerMode.DATABASE else AnswerMode.GENERAL_KNOWLEDGE).toByteArray(Charsets.UTF_8).size + 128 else 700,
        ).decide(typed, requestHistory, useDatabase, active != null)
        val replan: ((Int) -> BoundedGeneration.Request)? = if (active is LiteRtAiEngine) { budget -> BoundedGeneration.request(decideWithin(budget)) } else null

        // The question goes on screen immediately, before any work is done,
        // so the conversation never feels like it swallowed the input.
        val userMessage = ChatMessage(
            id = newMessageId(),
            fromUser = true,
            text = typed,
            contextKey = memoryContext,
        )
        val answerId = newMessageId()

        when (val decision = decideWithin(inputBudget)) {

            is ChatResponder.Decision.NoModel -> {
                // Nothing was sent, so the question is not added. Showing a
                // question with no answer would look like the app had failed.
                aiStatus = decision.reason
            }

            is ChatResponder.Decision.NoPassages -> {
                // The database had nothing. Recorded as a message in its own
                // right, because "I looked and there was nothing" is a real
                // answer and the user should be able to see it happened.
                val reply = ChatMessage(
                    id = answerId,
                    fromUser = false,
                    text = "Nothing in \"${decision.databaseName}\" matches that " +
                        "question, so I did not answer from memory.\n\n" +
                        "That database holds ${decision.passageCount} pieces of " +
                        "text from ${decision.documentCount} document(s).\n\n" +
                        "If you meant a different database, switch it off and on " +
                        "again in Settings, or choose another database there.",
                    databaseHadNoAnswer = true,
                    contextKey = memoryContext,
                )
                commit(userMessage)
                commit(reply)
                aiStatus = "No passage found, the model was not asked."
                Log.i(
                    PERF_TAG,
                    "RETRIEVE empty database=${decision.databaseName} " +
                        "chunks=${decision.passageCount} questionLength=${typed.length}",
                )
            }

            is ChatResponder.Decision.FromDatabase -> {
                commit(userMessage)
                generate(
                    prompt = decision.prompt,
                    answerId = answerId,
                    sources = decision.sources,
                    history = decision.history,
                    contextKey = memoryContext,
                    inputBudget = inputBudget,
                    replan = replan,
                )
            }

            is ChatResponder.Decision.WithoutDatabase -> {
                commit(userMessage)
                generate(
                    prompt = decision.prompt,
                    answerId = answerId,
                    sources = emptyList(),
                    answeredWithoutDatabase = true,
                    history = decision.history,
                    contextKey = memoryContext,
                    inputBudget = inputBudget,
                    replan = replan,
                )
                aiStatus = decision.warning
            }
        }
    }


    fun requestModelDownload(entry: ModelCatalog.Entry) {
                    if (!ModelFiles.hasStorageAccess(context)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            storageSettings.launch(ModelFiles.accessSettingsIntent(context))
                        } else readStoragePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        pendingModelDownload = entry
                        downloadWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else downloads.start(entry)
    }
    val downloadUi = ModelDownloadUiState(models, downloadState, deviceInfo,
        isBusy || isDatabaseBusy, hasStorageAccess, { requestModelDownload(it) },
        { downloads.cancel() }, { loadModel(it) }, engine?.modelName)

    // The back button closes whatever is on top, innermost first.
    BackHandler(enabled = showUnlock) { choose("") }
    BackHandler(enabled = showSettings && !showBuilder) { showSettings = false }
    BackHandler(enabled = showSearch && !showSettings) { showSearch = false }
    BackHandler(enabled = showDrawer && !showSettings && !showSearch) {
        showDrawer = false
    }

    val current = session
    val openDatabase = database

    when {
        showBuilder -> BuilderScreen(
            model = viewModel<BuilderViewModel>(),
            canUseDatabase = hasStorageAccess && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED),
            onDatabaseReady = { file ->
                showBuilder = false
                showSettings = false
                availableDatabases = DatabaseFiles.findAll()
                choose(file.nameWithoutExtension)
            },
            onBack = { showBuilder = false },
        )
        showHelp -> HelpScreen(onBack = { showHelp = false })
        showSettings -> SettingsScreen(
            state = SettingsState(
                databaseName = (lockedDatabase?.manifest?.name
                    ?: openDatabase?.manifest?.name) ?: "None",
                databaseDetail = when {
                    lockedDatabase != null -> "Locked - needs an Unlock Code"
                    openDatabase != null ->
                        "${openDatabase.chunks.size} passages, " +
                            "${openDatabase.sources.size} document(s)"
                    else -> "No database open"
                },
                databaseIsLocked = lockedDatabase != null,

                // The database list now lives here rather than on the search
                // screen, because choosing a database is a setting and belongs
                // next to choosing a model.
                databases = availableDatabases,
                selectedDatabase = selectedDatabase,
                lockStatuses = lockStatuses,
                // A locked database is chosen and then handed to the search
                // screen, which is where the Unlock Code is entered. Selecting
                // one here and leaving the user in settings would be a dead
                // end with no way forward.
                onChooseDatabase = { chosen ->
                    if (chosen != selectedDatabase) {
                        choose(chosen)
                        // A locked database cannot be opened, so the only way
                        // forward is its Unlock Code, and that is entered on
                        // the search screen. Handing over there rather than
                        // leaving the user in Settings would be a dead end.
                        val chosenFile = availableDatabases
                            .firstOrNull { it.displayName == chosen }?.file
                        if (chosenFile != null &&
                            DatabaseLock.inspect(chosenFile) is DatabaseLock.Status.Locked
                        ) {
                            showSettings = false
                            openUnlockScreen()
                        }
                    }
                },
                onRescanDatabases = { rescanDatabases() },
                onDeleteDatabase = { chosen ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingDatabaseDeletion = chosen
                        databaseDeletePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else deleteDatabase(chosen)
                },

                models = models,
                loadedModelName = engine?.modelName,
                download = downloadState,
                onCancelDownload = { downloads.cancel() },
                onDownloadModel = { entry -> requestModelDownload(entry) },
                aiStatus = aiStatus,
                isBusy = isBusy || isDatabaseBusy,
                isModelBusy = isBusy,
                isDatabaseBusy = isDatabaseBusy,
                databaseStatus = databaseStatus,
                deviceInfo = deviceInfo,

                hasStorageAccess = hasStorageAccess,
                onGrantStorageAccess = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        storageSettings.launch(ModelFiles.accessSettingsIntent(context))
                    } else {
                        readStoragePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                onRescanModels = { refreshStorageAccess() },
                onLoadModel = { model -> loadModel(model) },
                onDeleteModel = { model ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingModelDeletion = model
                        modelDeletePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else deleteModel(model)
                },

                preferGpu = preferGpu,
                onPreferGpuChange = { chosen ->
                    preferGpu = chosen
                    // The session remembers where its answers ran.
                    sessions.updateSettings(preferGpu = chosen, persist = !isGenerating)?.let { session = it }
                },

                appearance = appearance,
                onAppearanceChange = onAppearanceChange,

                textSize = textSize,
                onTextSizeChange = onTextSizeChange,

                loadSeconds = loadSeconds,
                answerSeconds = answerSeconds,
                memoryMegabytes = memoryMegabytes,
                backendInUse = if (engine is OpenRouterAiEngine) "Online" else (engine as? LiteRtAiEngine)?.backendInUse,
                replyBehavior = replyBehavior,
                onReplyBehaviorChange = { chosen -> replyBehavior = chosen; behaviorStore.write(chosen) },
                openRouter = routerSettings,
                onlineActive = engine is OpenRouterAiEngine,
                onUseOpenRouter = { key, model, share -> useOpenRouter(key, model, share) },
                onUseLocal = { useLocalAi() },
                onRemoveOpenRouterKey = { removeOpenRouterKey() },
                onCreateDatabase = { showBuilder = true },
            ),
            onBack = { showSettings = false },
        )

        // Both together, not showUnlock alone: the screen needs a manifest, and
        // a force-unwrapped null here is a crash rather than a wrong screen.
        showUnlock && lockedDatabase != null -> UnlockScreen(
            // A delegated property cannot be smart-cast, so it is read once
            // into a value that can be.
            status = lockedDatabase!!,
            requestCode = requestCode,
            requestCodeError = requestCodeError,
            isPreparingRequestCode = isPreparingRequestCode,
            hasCopiedRequestCode = hasCopiedRequestCode,
            onPrepareRequestCode = { prepareRequestCode() },
            onCopyRequestCode = {
                requestCode?.let { code ->
                    copyToClipboard(context, "DataEater Request Code", code)
                    hasCopiedRequestCode = true
                }
            },
            licenceFiles = licenceFiles,
            activeFileName = activeLicenceFile,
            onUseLicenceFile = { file -> useLicenceFile(file) },
            onShareRequestCode = { shareRequestCode() },
            onSaveRequestCode = { saveRequestCodeToFile() },
            enteredUnlockCode = enteredUnlockCode,
            onUnlockCodeChange = {
                enteredUnlockCode = it
                // Clear the previous complaint the moment the user edits, so an
                // old error does not sit under a box they are fixing.
                if (unlockProblem != null) unlockProblem = null
            },
            unlockProblem = unlockProblem,
            isCheckingUnlockCode = isCheckingUnlockCode,
            onUnlock = { unlockWithCode() },
            onBack = { choose("") },
        )

        showSearch -> SearchScreen(
            database = openDatabase,
            availableDatabases = availableDatabases,
            selectedDatabase = selectedDatabase,
            onSelectDatabase = { chosen -> choose(chosen) },
            lockStatuses = lockStatuses,
            lockedDatabase = lockedDatabase,
            onUnlock = {
                // Hand over to the Unlock screen, which is what that screen is
                // for now. Nothing is unlocked from here.
                showSearch = false
                openUnlockScreen()
            },
            onBack = { showSearch = false },
        )

        errorMessage != null -> ProblemScreen(
            message = errorMessage!!,
            onChooseDatabase = {
                errorMessage = null
                choose("")
                showSettings = true
            },
        )

        // The remembered database may be locked at launch, before the user has
        // touched anything. Without this branch the app sat on "Opening your
        // database…" for ever: there was a lock to open and nothing behind it.
        lockedDatabase != null -> UnlockScreen(
            status = lockedDatabase!!,
            requestCode = requestCode,
            requestCodeError = requestCodeError,
            isPreparingRequestCode = isPreparingRequestCode,
            hasCopiedRequestCode = hasCopiedRequestCode,
            onPrepareRequestCode = { prepareRequestCode() },
            onCopyRequestCode = {
                requestCode?.let { code ->
                    copyToClipboard(context, "DataEater Request Code", code)
                    hasCopiedRequestCode = true
                }
            },
            licenceFiles = licenceFiles,
            activeFileName = activeLicenceFile,
            onUseLicenceFile = { file -> useLicenceFile(file) },
            onShareRequestCode = { shareRequestCode() },
            onSaveRequestCode = { saveRequestCodeToFile() },
            enteredUnlockCode = enteredUnlockCode,
            onUnlockCodeChange = {
                enteredUnlockCode = it
                if (unlockProblem != null) unlockProblem = null
            },
            unlockProblem = unlockProblem,
            isCheckingUnlockCode = isCheckingUnlockCode,
            onUnlock = { unlockWithCode() },
            onBack = { choose("") },
        )

        current == null -> LoadingScreen()

        else -> SessionDrawer(
            visible = showDrawer, sessions = remember(current, sessionListVersion) { sessions.sessions }, activeId = current.id,
            onSelect = { switchSession(it) }, onNewSession = { newSession() },
            deletionEnabled = !isBusy && !isDatabaseBusy,
            onDelete = { id ->
                if (isBusy || isDatabaseBusy) false else runCatching {
                    val wasActive = session?.id == id
                    val remaining = sessions.delete(id)
                    sessionListVersion += 1
                    session = remaining
                    if (wasActive) {
                        switchSession(remaining.id)
                        showDrawer = true
                    }
                    true
                }.getOrDefault(false)
            },
            onOpen = { focusManager.clearFocus(); keyboard?.hide(); showDrawer = true },
            onClose = { showDrawer = false },
            onOpenHelp = { showDrawer = false; showHelp = true },
        ) {
            ChatScreen(
                session = current,
                models = if (engine is OpenRouterAiEngine) emptyList() else models.map { it.displayName },
                onOpenOnlineModels = if (engine is OpenRouterAiEngine && !isBusy && !isDatabaseBusy) ({ showOnlineModels = true }) else null,
                loadedModelName = engine?.modelName,
                isGenerating = isGenerating,
                aiStatus = aiStatus,
                draft = draft,
                onDraftChange = { draft = it },
                onSend = { send() },
                onOpenDrawer = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    showDrawer = true
                },
                onOpenSettings = { showSettings = true },
                onDownloadAi = { showModelCatalog = true },
                download = downloadState,
                onChooseModel = { wanted ->
                    // Choosing a model here loads it straight away, so the
                    // control row is the whole of "which model" and Settings is
                    // where you go to read why one might not fit.
                    models.firstOrNull { it.displayName == wanted }?.let { loadModel(it) }
                },
                onToggleDatabase = { setDatabaseOn(it) },
                // The dropdown shows readable names, but choosing one has to
                // be translated back to the id the app matches on. Passing the
                // label straight through looked correct and did nothing at all:
                // "Demo (built in)" matched no file, so the picker quietly
                // reported the database as missing and nothing changed.
                databases = databaseChoices(),
                onChooseDatabase = { label ->
                    databaseIdFor(label)?.let { chooseAndRoute(it) }
                },
                // imePadding, not just navigationBarsPadding: without it the
                // keyboard covers the composer, so a user typing cannot see
                // their own text or reach the send button. That was found by
                // trying to ask a question on the phone.
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            )


        }
    }
    if (showModelCatalog) ModelDownloadDialog(downloadUi, onDismiss = { showModelCatalog = false })
}

/**
 * A unique id for one message.
 *
 * Time plus a random suffix, so two messages made in the same millisecond
 * cannot collide — which would make the streaming update rewrite the wrong one.
 */
private fun newMessageId(): String =
    "${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(6)}"

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            "Opening your database…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProblemScreen(message: String, onChooseDatabase: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onChooseDatabase != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onChooseDatabase) { Text("Choose a database") }
        }
    }
}
