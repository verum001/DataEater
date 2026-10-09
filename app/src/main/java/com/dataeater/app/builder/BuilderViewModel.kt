package com.dataeater.app.builder

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.dataeater.app.data.DatabaseFiles
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BuilderViewModel(application: Application) : AndroidViewModel(application) {
    enum class Task(val title: String, val description: String, val action: String) {
        BUILD("Build database", "Choose documents and save one database for your questions.", "Build database"),
        REVIEW("Prepare text review", "Extract editable text and an LLM prompt. Nothing is sent online.", "Prepare review"),
        REVIEWED("Build reviewed text", "Build checked edits while keeping original page references.", "Build database"),
        INSPECT("Check database", "Check fingerprints and see extraction quality.", "Check database"),
        ENCRYPT("Protect database", "Create a locked copy and keep its content key private.", "Protect database"),
        KEY("Create signing key", "Create a private key and its matching public key.", "Create signing key"),
        LICENCE("Issue access code", "Create an unlock code for the phone that sent its request code.", "Issue access code")
    }
    data class Review(val id: String, val name: String)
    data class Result(val text: String, val file: File? = null, val review: Review? = null,
        val signing: PublisherVault.Item? = null, val secret: PublisherVault.Item? = null)
    data class State(val task: Task = Task.BUILD, val inputs: List<BuilderFiles.Input> = emptyList(), val database: BuilderFiles.Input? = null,
        val fields: Map<String, String> = mapOf("name" to "", "language" to "en", "license" to "unknown", "target" to "700", "batch" to "30000", "expiry" to "never", "creator" to "", "contact" to "", "keyName" to "Creator key"),
        val signingId: String? = null, val secretId: String? = null, val reviewId: String? = null, val keys: List<PublisherVault.Item> = emptyList(),
        val reviews: List<Review> = emptyList(), val busy: Boolean = false, val status: String = "", val result: Result? = null)
    data class Export(val file: File? = null, val item: PublisherVault.Item? = null, val reviewId: String? = null, val prompt: Boolean = false)
    var pendingExport: Export? = null
    var pendingKeyKind = "signing"
    var pendingBatch: Pair<String, String>? = null
    var state by mutableStateOf(State()); private set
    private val context = application.applicationContext
    private val files = BuilderFiles(context)
    val vault = PublisherVault(context)
    private val folder = File(context.filesDir, "builder/workspaces").apply { mkdirs() }
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var cancelled = AtomicBoolean(false)
    private val running = AtomicReference<Thread?>(null)
    private var sequence = 0L
    init { executor.execute { try { val keys = vault.list(); val reviews = reviews(); main.post { state = state.copy(keys = keys, reviews = reviews) } } catch (_: Exception) { main.post { state = state.copy(status = "Saved keys could not be opened. Restore an exported backup if needed.") } } } }
    fun field(key: String, value: String) { if (!state.busy) state = state.copy(fields = state.fields + (key to value)) }
    fun task(task: Task) { if (!state.busy) state = state.copy(task = task, result = null, status = "") }
    fun signing(id: String?) { if (!state.busy) state = state.copy(signingId = id) }
    fun secret(id: String?) { if (!state.busy) state = state.copy(secretId = id) }
    fun review(id: String) { if (!state.busy) state = state.copy(reviewId = id) }
    fun removeInput(uri: Uri) { if (!state.busy) state = state.copy(inputs = state.inputs.filterNot { it.uri == uri }) }
    fun documents(uris: List<Uri>) = submit("Reading documents…") { _, _ ->
        val selected = uris.map(files::input); require(selected.all { files.supported(it.name) }) { "Choose PDF, TXT or Markdown documents." }
        require(state.inputs.size + selected.size <= 1000) { "Select a smaller document collection." }
        main.post { state = state.copy(inputs = (state.inputs + selected).distinctBy { it.uri }) }; null
    }
    fun documentFolder(uri: Uri) = submit("Reading folder…") { _, _ ->
        val selected = files.folder(uri); main.post { state = state.copy(inputs = (state.inputs + selected).distinctBy { it.uri }) }; null
    }
    fun database(uri: Uri) = submit("Reading file…") { _, _ ->
        val selected = files.input(uri); main.post { state = state.copy(database = selected) }; null
    }
    fun importKey(uri: Uri, kind: String) = submit("Importing key…") { _, _ ->
        val record = files.record(uri); check(); val item = vault.save(files.input(uri).name, kind, record)
        main.post { state = if (kind == "signing") state.copy(signingId = item.id) else state.copy(secretId = item.id) }; null
    }
    fun importReview(uri: Uri) = submit("Importing review…") { temp, output ->
        val archive = File(temp, "review.zip"); files.copy(uri, archive, 64L * 1024 * 1024)
        val reviewFolder = File(output, "review"); BuilderReview.unzip(archive, reviewFolder)
        val imported = BuilderReview.read(reviewFolder); val title = imported.documents.first().title
        File(output, "review-name.txt").writeText(title); val review = Review(output.name, title)
        main.post { state = state.copy(reviewId = review.id) }
        Result("Review imported. Originals and page markers verified.", review = review)
    }
    fun importReviewFolder(uri: Uri) = submit("Importing review folder…") { _, output ->
        val reviewFolder = File(output, "review"); files.copyReviewFolder(uri, reviewFolder)
        val imported = BuilderReview.read(reviewFolder); val title = imported.documents.first().title
        File(output, "review-name.txt").writeText(title); val review = Review(output.name, title)
        main.post { state = state.copy(reviewId = review.id) }
        Result("Review imported. Originals and page markers verified.", review = review)
    }
    private fun options(snapshot: State): BuilderCore.Options {
        val target = snapshot.fields["target"].orEmpty().toIntOrNull() ?: error("Passage size must be a whole number.")
        require(target in 100..10000) { "Choose a passage size between 100 and 10000 characters." }
        return BuilderCore.Options(snapshot.fields["name"].orEmpty().trim(), snapshot.fields["description"].orEmpty(),
            snapshot.fields["language"].orEmpty().ifBlank { "en" }, snapshot.fields["license"].orEmpty().ifBlank { "unknown" }, snapshot.fields["publisher"].orEmpty(), target)
    }
    fun run(replaceContentKey: Boolean = false, legacyReview: Boolean = false) {
        val snapshot = state
        submit("${snapshot.task.action}…") { temp, output ->
            fun databaseFile(): File {
                val input = snapshot.database ?: error("Choose a database.")
                return File(temp, "input.dataeater").also { files.copy(input.uri, it, 64L * 1024 * 1024) }
            }
            fun signingRecord(): Pair<PublisherVault.Item, JSONObject> {
                val item = snapshot.keys.firstOrNull { it.id == snapshot.signingId && it.kind == "signing" } ?: error("Create or choose a signing key.")
                return item to vault.record(item)
            }
            when (snapshot.task) {
                Task.BUILD, Task.REVIEW -> {
                    require(snapshot.inputs.isNotEmpty()) { "Choose documents first." }
                    val documents = mutableListOf<BuilderCore.Document>(); var bytes = 0L
                    for ((index, input) in snapshot.inputs.withIndex()) {
                        check(); progress("Reading document ${index + 1} of ${snapshot.inputs.size}: ${input.name}")
                        val extracted = files.extract(input, temp); bytes += extracted.pages.sumOf { it.text.toByteArray().size.toLong() }
                        require(bytes <= BuilderCore.MAX_TEXT_BYTES) { "This collection is too large for the phone. Build smaller databases." }
                        documents.add(extracted)
                    }
                    if (snapshot.task == Task.REVIEW || legacyReview) {
                        val batch = snapshot.fields["batch"].orEmpty().toIntOrNull() ?: error("Batch size must be a whole number.")
                        require(batch in 1000..500000) { "Choose a batch size between 1000 and 500000 characters." }
                        val reviewFolder = File(output, "review"); val prompt = context.assets.open("builder/LLM_PROMPT.txt").bufferedReader().use { it.readText() }
                        BuilderReview.export(documents, reviewFolder, batch, prompt)
                        val title = snapshot.fields["name"].orEmpty().ifBlank { documents.first().title }; File(output, "review-name.txt").writeText(title)
                        Result("Review ready. Check each batch before building. Nothing was uploaded.", review = Review(output.name, title))
                    } else {
                        val options = options(snapshot); val file = File(output, BuilderCore.id(options.name) + ".dataeater")
                        Result(BuilderCore.writeDatabase(documents, options, file), file)
                    }
                }
                Task.REVIEWED -> {
                    val review = reviewFolder(snapshot.reviewId ?: error("Choose a review workspace."))
                    val imported = BuilderReview.read(review); val options = options(snapshot); val file = File(output, BuilderCore.id(options.name) + ".dataeater")
                    val report = BuilderCore.writeDatabase(imported.documents, options, file)
                    Result(report + "\nNumeric text changed on ${imported.numericChanges} pages. Check those corrections against the original PDF.", file)
                }
                Task.INSPECT -> Result(BuilderCore.inspect(databaseFile()))
                Task.KEY -> {
                    val record = BuilderCrypto.createKey(); check()
                    val item = vault.save(snapshot.fields["keyName"].orEmpty().ifBlank { "Creator key" }, "signing", record)
                    main.post { state = state.copy(signingId = item.id) }
                    Result("Signing key created and stored privately. Export a secure backup.\n\nPublic key (safe to share):\n${record.getString("public_key")}", signing = item)
                }
                Task.ENCRYPT -> {
                    val input = databaseFile()
                    val signing = snapshot.keys.firstOrNull { it.id == snapshot.signingId && it.kind == "signing" }
                    val publicKey = snapshot.fields["publicKey"].orEmpty().trim().ifBlank {
                        signing?.let { vault.record(it).getString("public_key") } ?: error("Choose a signing key, or paste its public key in Advanced options.")
                    }
                    val selected = snapshot.keys.firstOrNull { it.id == snapshot.secretId && it.kind == "content" }
                    val file = File(output, BuilderCore.id(snapshot.database!!.name.substringBeforeLast('.')) + "-locked.dataeater")
                    val secret = BuilderCrypto.encrypt(input, file, snapshot.fields["creator"].orEmpty(), snapshot.fields["contact"].orEmpty(), publicKey, selected?.let(vault::record), replaceContentKey)
                    check(); val item = vault.save(file.nameWithoutExtension, "content", secret)
                    Result("Locked copy created. The content key is stored privately. Export a secure key backup before sharing the database.", file, signing = signing, secret = item)
                }
                Task.LICENCE -> {
                    val (_, key) = signingRecord(); val selected = snapshot.keys.firstOrNull { it.id == snapshot.secretId && it.kind == "content" } ?: error("Choose the database's content key.")
                    val code = BuilderCrypto.licence(databaseFile(), vault.record(selected), key, snapshot.fields["request"].orEmpty(), snapshot.fields["expiry"].orEmpty())
                    val file = File(output, "access-code.lic"); file.writeText(code)
                    Result("Access code ready. Save this file and send it only to the matching customer device. Expiry: ${BuilderCrypto.expiry(snapshot.fields["expiry"].orEmpty()) ?: "never"}.", file)
                }
            }
        }
    }
    private fun progress(message: String) { main.post { if (state.busy) state = state.copy(status = message) } }
    private fun check() { if (cancelled.get()) throw InterruptedException("Cancelled"); BuilderCore.checkpoint() }
    private fun reviews(): List<Review> = folder.listFiles().orEmpty().filter { File(it, "review/review.json").isFile }.map {
        Review(it.name, File(it, "review-name.txt").takeIf(File::isFile)?.readText()?.take(200) ?: "Review")
    }.sortedBy { it.name }
    fun reviewFolder(id: String): File {
        require(Regex("[a-f0-9-]{36}").matches(id)) { "Invalid workspace." }
        return File(folder, "$id/review").also { require(it.isDirectory) { "The review workspace was not found." } }
    }
    fun batches(id: String): List<String> = File(reviewFolder(id), "reviewed").listFiles().orEmpty().filter { it.isFile }.map { it.name }.sorted()
    fun readBatch(id: String, name: String, original: Boolean = false): String = BuilderReview.safe(File(reviewFolder(id), if (original) "original" else "reviewed"), name).inputStream().use {
        BuilderCore.bounded(it, 2L * 1024 * 1024).toString(Charsets.UTF_8)
    }
    fun saveBatch(id: String, name: String, text: String) = submit("Saving reviewed text…") { _, _ ->
        val original = BuilderReview.parse(readBatch(id, name, true)); val edited = BuilderReview.parse(text)
        require(original.map { it.number } == edited.map { it.number }) { "Keep every original page marker in order." }
        require(original.zip(edited).all { it.first.text.isEmpty() || it.second.text.isNotEmpty() }) { "A reviewed page cannot be empty." }
        require(text.toByteArray().size <= 2 * 1024 * 1024) { "Use a smaller review batch." }
        val file = BuilderReview.safe(File(reviewFolder(id), "reviewed"), name)
        val staging = File(file.parentFile, ".${UUID.randomUUID()}.tmp")
        try { staging.writeText(text); check(); Files.move(staging.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } finally { staging.delete() }
        Result("Reviewed batch saved. Verify numbers, units, warnings and identifiers against the original.", review = state.reviews.firstOrNull { it.id == id })
    }
    fun importBatch(id: String, name: String, uri: Uri) = submit("Importing reviewed batch…") { temp, _ ->
        val file = File(temp, "batch.txt"); files.copy(uri, file, 2L * 1024 * 1024)
        val text = file.readText().replace("\r\n", "\n").replace('\r', '\n')
        val original = BuilderReview.parse(readBatch(id, name, true)); val edited = BuilderReview.parse(text)
        require(original.map { it.number } == edited.map { it.number } && original.zip(edited).all { it.first.text.isBlank() || it.second.text.isNotBlank() }) { "Keep all original page markers and complete page text." }
        val target = BuilderReview.safe(File(reviewFolder(id), "reviewed"), name)
        val staging = File(target.parentFile, ".${UUID.randomUUID()}.tmp")
        try { staging.writeText(text); check(); Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } finally { staging.delete() }
        Result("Reviewed batch imported. Check technical corrections before building.", review = state.reviews.firstOrNull { it.id == id })
    }
    fun readNote(id: String, name: String): String = BuilderReview.safe(File(reviewFolder(id), "notes"), name)
        .takeIf(File::isFile)?.readText().orEmpty()
    fun saveNote(id: String, name: String, text: String) = submit("Saving notes…") { _, _ ->
        require(text.toByteArray().size <= 2 * 1024 * 1024) { "Use a shorter note." }
        val parent = File(reviewFolder(id), "notes").apply { mkdirs() }
        val target = BuilderReview.safe(parent, name); val stage = File(parent, ".${UUID.randomUUID()}.tmp")
        try { stage.writeText(text); check(); Files.move(stage.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } finally { stage.delete() }
        Result("Notes saved separately from the reviewed text.", review = state.reviews.firstOrNull { it.id == id })
    }
    fun export(uri: Uri, file: File? = null, item: PublisherVault.Item? = null, reviewId: String? = null, prompt: Boolean = false) = submit("Saving file…") { temp, _ ->
        val source = when {
            item != null -> File(temp, if (item.kind == "signing") "creator.key" else "content.secret").also { it.writeText(vault.record(item).toString(2) + "\n") }
            reviewId != null -> File(temp, "review.zip").also { BuilderReview.zip(reviewFolder(reviewId), it) }
            prompt -> File(temp, "LLM_PROMPT.txt").also { it.writeBytes(context.assets.open("builder/LLM_PROMPT.txt").use { stream -> stream.readBytes() }) }
            else -> file ?: error("There is no result to save.")
        }
        check(); require(state.inputs.none { it.uri == uri } && state.database?.uri != uri) { "Choose a new file so the input is preserved." }
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { check(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
            } } ?: error("The chosen file could not be written.")
        } catch (error: Exception) {
            // SAF exports are new files. Remove incomplete output if the provider permits it.
            try { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) } catch (_: Exception) { }
            throw error
        }
        null
    }
    fun useDatabase(file: File, onReady: (File) -> Unit) = submit("Adding database…") { _, _ ->
        BuilderCore.inspect(file); val destination = DatabaseFiles.folder()
        require(destination.isDirectory && destination.canWrite()) { "Grant DataEater folder access in Settings, or save the database with Save file." }
        var target = File(destination, file.name); var index = 2
        while (target.exists()) { target = File(destination, file.nameWithoutExtension + "-${index++}.dataeater") }
        val stage = File(destination, ".builder-${UUID.randomUUID()}.tmp")
        try { file.copyTo(stage); check(); Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) } finally { stage.delete() }
        main.post { onReady(target) }; null
    }
    private fun submit(label: String, block: (File, File) -> Result?) {
        if (state.busy) return
        sequence++; val own = sequence; val token = AtomicBoolean(false); cancelled = token
        state = state.copy(busy = true, status = label)
        executor.execute {
            Thread.interrupted(); running.set(Thread.currentThread())
            val temp = File(context.cacheDir, "builder-${UUID.randomUUID()}").apply { mkdirs() }
            val output = File(folder, UUID.randomUUID().toString()).apply { mkdirs() }
            var result: Result? = null; var message = "Finished."; var success = false
            try {
                check(); result = block(temp, output); check(); success = true
            } catch (_: InterruptedException) { message = "Cancelled. Existing workspaces and keys were preserved." }
            catch (error: Throwable) {
                message = if (token.get()) "Cancelled. Existing workspaces and keys were preserved." else
                    if (error is OutOfMemoryError || error is StackOverflowError) "This document is too complex for the phone. Use smaller files or the Linux builder." else "Could not finish: ${error.message ?: "Choose the file again and try a smaller document."}"
            } finally {
                temp.deleteRecursively(); if (!success || output.listFiles().isNullOrEmpty()) output.deleteRecursively()
                running.set(null); Thread.interrupted()
                val keys = try { vault.list() } catch (_: Exception) { emptyList() }; val reviews = try { reviews() } catch (_: Exception) { emptyList() }
                main.post { if (sequence == own) state = state.copy(busy = false, status = message, result = result.takeIf { success } ?: state.result, keys = keys, reviews = reviews) }
            }
        }
    }
    fun cancel() { cancelled.set(true); running.get()?.interrupt(); if (state.busy) state = state.copy(status = "Cancelling…") }
    fun clearForm() { if (!state.busy) state = State(keys = state.keys, reviews = state.reviews) }
    override fun onCleared() { cancel(); executor.shutdown(); super.onCleared() }
}
