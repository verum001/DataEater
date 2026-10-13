package com.dataeater.app.benchmark

import android.content.*
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.dataeater.app.ai.*
import com.dataeater.app.data.*
import com.dataeater.app.engine.RagEngine
import com.dataeater.app.security.LicenceStore
import com.dataeater.app.security.UnlockCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BenchmarkManager private constructor(private val context: Context) {
    data class Form(val questions: List<String> = emptyList(), val models: Set<String> = emptySet(), val databases: Set<String> = emptySet(), val useDatabase: Boolean = true, val humanRating: Boolean = false)
    data class State(val form: Form = Form(), val run: BenchmarkRun? = null, val busy: Boolean = false,
        val message: String = "", val files: List<String> = emptyList(), val fileBusy: Boolean = false)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = BenchmarkStore(File(ModelFiles.dataEaterDirectory(), "benchmark"))
    private val writes = Mutex()
    private val ratings = ConcurrentHashMap<String, Pair<Int?, String>>()
    private var job: Job? = null
    fun form(change: (Form) -> Form) { if (!mutable.value.busy) mutable.update { it.copy(form = change(it.form), message = "") } }
    fun message(text: String) { mutable.update { it.copy(message = text) } }
    private fun now() = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    private suspend fun save() = withContext(Dispatchers.IO) { writes.withLock { mutable.value.run?.let(store::save) } }
    fun refresh() = filesAction { mutable.update { it.copy(files = store.list().map(File::getName)) } }
    private fun filesAction(block: suspend () -> Unit) {
        if (mutable.value.fileBusy) return
        mutable.update { it.copy(fileBusy = true) }
        scope.launch {
            try { withContext(Dispatchers.IO) { writes.withLock { block() } } }
            catch (error: Exception) { message("Could not finish: ${error.message}") }
            finally { mutable.update { it.copy(fileBusy = false) } }
        }
    }
    fun start(preferGpu: Boolean, behavior: ReplyBehavior, releaseChatEngine: suspend () -> Unit): Boolean {
        if (mutable.value.busy || mutable.value.fileBusy) return false
        val f = mutable.value.form
        if (f.questions.isEmpty() || f.models.isEmpty() || (f.useDatabase && f.databases.isEmpty())) {
            message("Add a question, select models, and select databases when Use database is on."); return false
        }
        if (!ModelFiles.hasStorageAccess(context)) { message("Allow DataEater folder access in Settings first."); return false }
        if (f.questions.size > 50 || f.models.size > 20) { message("Use at most 50 questions and 20 models per benchmark."); return false }
        val models = ModelFiles.findAll(context).filter { it.file.name in f.models }
        if (models.size != f.models.size) { message("A selected model is missing. Refresh the list and select again."); return false }
        val databases = if (f.useDatabase) DatabaseFiles.findAll().filter { it.file.name in f.databases } else emptyList()
        if (f.useDatabase && databases.size != f.databases.size) { message("A selected database is missing. Refresh the list and select again."); return false }
        val id = "benchmark-" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + UUID.randomUUID().toString().take(8)
        ratings.clear()
        val memory = DeviceMemory.read(context)
        val phone = linkedMapOf("Phone" to "${Build.MANUFACTURER} ${Build.MODEL}", "Device" to Build.DEVICE,
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "ABI" to Build.SUPPORTED_ABIS.joinToString(),
            "CPU cores" to Runtime.getRuntime().availableProcessors().toString(), "Total RAM MiB" to memory.totalMegabytes.toString(),
            "Available RAM at start MiB" to memory.availableMegabytes.toString(), "App version" to context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
            "Runtime" to "LiteRT-LM (installed app)", "Hardware details" to "GPU/chipset and CPU clock not measured; actual runtime backend is recorded per model")
        models.forEach { phone["Model file ${it.file.name}"] = "${it.file.length()} bytes; modified ${java.time.Instant.ofEpochMilli(it.file.lastModified())}" }
        databases.forEach { phone["Database file ${it.file.name}"] = "${it.file.length()} bytes; modified ${java.time.Instant.ofEpochMilli(it.file.lastModified())}" }
        phone["Board / hardware"] = "${Build.BOARD} / ${Build.HARDWARE}"
        if (Build.VERSION.SDK_INT >= 31) phone["Chipset reported by Android"] = "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        val run = BenchmarkRun(id, now(), phone, models.map { it.file.name }, databases.map { it.file.name }, f.questions.toList(), f.useDatabase, f.humanRating, preferGpu, behavior)
        mutable.update { it.copy(run = run, busy = true, message = "Preparing benchmark…") }
        job = scope.launch {
            try {
                // Main chat engine is released before allocating benchmark engines.
                releaseChatEngine()
                save()
                val rag = withContext(Dispatchers.IO) {
                    val opened = mutableListOf<DatabaseReader.OpenedDatabase>()
                    var bytes = 0L; var passages = 0L
                    for (db in databases) {
                        ensureActive()
                        val value = when (val lock = DatabaseLock.inspect(db.file)) {
                            is DatabaseLock.Status.Open -> DatabaseReader.openFile(db.file)
                            is DatabaseLock.Status.Unreadable -> error("${db.file.name}: ${lock.reason}")
                            is DatabaseLock.Status.Locked -> {
                                val stored = LicenceStore.get(context, lock.manifest.databaseId, lock.manifest.creatorPublicKey) ?: error("Unlock ${db.file.name} in Database Selection first.")
                                val licence = UnlockCode.parse(String(stored, Charsets.UTF_8))
                                DatabaseReader.openLicensed(db.file, licence, lock.manifest)
                            }
                        }
                        bytes += value.chunks.sumOf { (it.text.toByteArray().size + it.searchContext.toByteArray().size).toLong() }
                        passages += value.chunks.size; DatabaseCollection.checkSize(bytes, passages); opened.add(value)
                    }
                    if (opened.isEmpty()) null else DatabaseCollection.combine(opened).let { RagEngine(it.chunks, it.sources) }
                }
                val runner = BenchmarkRunner(
                    engineFor = { name ->
                        val file = models.first { it.file.name == name }.file
                        require(file.isFile) { "Model file is missing." }
                        val info = DeviceMemory.read(context)
                        require(DeviceMemory.verdict((file.length() / 1048576).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), info) != DeviceMemory.Verdict.TOO_LARGE) { "Not enough free memory for this model. Close other apps or use a smaller model." }
                        LiteRtAiEngine(file, context.cacheDir, preferGpu)
                    }, budgetFor = { ModelCatalog.forFile(it)?.inputChars ?: 6000 },
                    backend = { (it as? LiteRtAiEngine)?.backendInUse ?: "Unknown" }, clockMs = SystemClock::elapsedRealtime, time = ::now,
                )
                runner.execute(run, rag, checkpoint = { value, status ->
                    mutable.update { it.copy(run = value.rated(ratings), message = status) }; save()
                }, stream = { value -> mutable.update { it.copy(run = value.rated(ratings)) } })
            } catch (cancelled: CancellationException) {
                mutable.update { it.copy(run = it.run?.copy(status = "Cancelled", endedAt = now()), message = "Cancelled") }
            } catch (error: Exception) {
                mutable.update { it.copy(run = it.run?.copy(status = "Failed: ${error.message}", endedAt = now()), message = "Could not finish: ${error.message}") }
            } finally {
                withContext(NonCancellable) {
                    try { save(); withContext(Dispatchers.IO) { mutable.update { it.copy(files = store.list().map(File::getName)) } } }
                    catch (error: Exception) { message("Could not save the result: ${error.message}. Free space or restore storage access and try Save result.") }
                    mutable.update { it.copy(busy = false) }
                }
            }
        }
        return true
    }
    fun cancel() { message("Stopping…"); job?.cancel() }
    fun retrySave() { scope.launch { try { save(); refresh(); message("Result saved.") } catch (e: Exception) { message("Could not save: ${e.message}") } } }
    fun rate(id: String, stars: Int?, feedback: String) {
        val run = mutable.value.run ?: return
        if (!run.humanRating || run.answers.none { it.id == id && it.status != "Running" }) return
        require(stars == null || stars in 1..5)
        ratings[id] = stars to feedback.take(2000)
        mutable.update { it.copy(run = it.run?.rated(ratings)) }
        scope.launch { try { save() } catch (e: Exception) { message("Rating not saved: ${e.message}. Try Save result.") } }
    }
    fun open(name: String, onOpened: () -> Unit) {
        if (mutable.value.busy) return
        filesAction {
            val run = store.read(name.removeSuffix(".txt"))
            val repaired = if (run.endedAt == null) run.copy(status = "Interrupted", endedAt = now(), answers = run.answers.map {
                if (it.status == "Running") it.copy(status = "Cancelled", error = "App closed before this answer finished") else it
            }) else run
            store.save(repaired); ratings.clear()
            mutable.update { it.copy(run = repaired, message = repaired.status) }
            withContext(Dispatchers.Main) { onOpened() }
        }
    }
    fun delete(names: Set<String>) = filesAction {
        require(!mutable.value.busy) { "Stop the benchmark before deleting results." }
        names.forEach { store.delete(it.removeSuffix(".txt")) }
        if (mutable.value.run?.id?.plus(".txt") in names) mutable.update { it.copy(run = null) }
        mutable.update { it.copy(files = store.list().map(File::getName), message = "Deleted ${names.size} result(s).") }
    }
    fun download(names: Set<String>) = filesAction {
        var saved = 0
        try {
            names.sorted().forEach { name ->
                val source = store.file(name.removeSuffix(".txt")); require(source.isFile)
                if (Build.VERSION.SDK_INT >= 29) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, source.name); put(MediaStore.MediaColumns.MIME_TYPE, "text/plain"); put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DataEater"); put(MediaStore.MediaColumns.IS_PENDING, 1) }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Downloads folder is unavailable.")
                    try {
                        resolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } } ?: error("Could not write Downloads.")
                        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); require(resolver.update(uri, values, null, null) > 0)
                    } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DataEater").apply { mkdirs() }
                    var target = File(dir, source.name); var n = 2
                    while (target.exists()) target = File(dir, source.nameWithoutExtension + "-${n++}.txt")
                    try { source.copyTo(target) } catch (e: Exception) { target.delete(); throw e }
                }
                saved++
            }
            message("Saved $saved result(s) in Downloads/DataEater.")
        } catch (e: Exception) { error("Saved $saved/${names.size} result(s). ${e.message}") }
    }
    fun share(names: Set<String>) = filesAction {
        require(names.isNotEmpty())
        // Narrow private cache provider; never expose the entire shared DataEater folder.
        val dir = File(context.cacheDir, "benchmark-share").apply { mkdirs() }
        dir.listFiles().orEmpty().filter { it.lastModified() < System.currentTimeMillis() - 86400000 }.forEach { it.delete() }
        val uris = ArrayList<android.net.Uri>()
        names.sorted().forEach { name ->
            val source = store.file(name.removeSuffix(".txt")); require(source.isFile)
            val copy = File.createTempFile(source.nameWithoutExtension + "-", ".txt", dir); source.copyTo(copy, overwrite = true)
            uris.add(FileProvider.getUriForFile(context, context.packageName + ".benchmark-files", copy))
        }
        val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newUri(context.contentResolver, "Benchmark results", uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        }
        withContext(Dispatchers.Main) { context.startActivity(Intent.createChooser(intent, "Share benchmark results").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    companion object {
        @Volatile private var instance: BenchmarkManager? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: BenchmarkManager(context.applicationContext).also { instance = it } }
    }
}
