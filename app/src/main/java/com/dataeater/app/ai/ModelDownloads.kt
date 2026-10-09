package com.dataeater.app.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Android owns the transfer, so it survives activity recreation and process death.
 * Only model bytes are requested. No chat, document, licence or device identifiers are sent.
 * The persisted download ID lets the app verify/install a finished transfer on reopening.
 */
class ModelDownloads private constructor(context: Context) {
    data class State(val modelId: String? = null, val progress: Float = 0f,
        val message: String = "", val active: Boolean = false, val installed: Int = 0)
    private val app = context.applicationContext
    private val manager = app.getSystemService(DownloadManager::class.java)
    private val prefs = app.getSharedPreferences("model_download", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var worker: Job? = null
    @Volatile private var generation = 0L
    private var downloadId = prefs.getLong("downloadId", -1)
    private fun staging(entry: ModelCatalog.Entry) = File(requireNotNull(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), "${entry.id}.part")

    init {
        cleanupStaged()
        val entry = ModelCatalog.entries.find { it.id == prefs.getString("modelId", null) }
        if (downloadId >= 0 && entry != null) watch(entry)
    }

    @Synchronized fun start(entry: ModelCatalog.Entry) {
        if (mutable.value.active) return
        require(entry in ModelCatalog.entries)
        val attempt = ++generation
        mutable.value = mutable.value.copy(modelId = entry.id, message = "Preparing download…", active = true, progress = 0f)
        worker = scope.launch {
            try {
                if (!ModelFiles.hasStorageAccess(app)) error("Storage access required")
                cleanupStaged()
                val temp = staging(entry)
                temp.parentFile?.mkdirs()
                if (temp.parentFile!!.usableSpace < entry.bytes * 2 + 100L * 1024 * 1024) error("Not enough storage")
                if (File(ModelFiles.dataEaterDirectory(), entry.fileName).exists()) error("Model already installed")
                synchronized(this@ModelDownloads) {
                    if (attempt != generation) throw CancellationException()
                    temp.delete()
                }
                val request = DownloadManager.Request(Uri.parse(entry.url))
                    .setTitle(entry.name).setDescription("DataEater model download")
                    .setMimeType("application/octet-stream")
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "${entry.id}.part")
                // Serialize enqueue with cancellation so Cancel cannot leave an orphan transfer.
                synchronized(this@ModelDownloads) {
                    if (attempt != generation) throw CancellationException()
                    downloadId = manager.enqueue(request)
                    prefs.edit().putLong("downloadId", downloadId).putString("modelId", entry.id).commit()
                }
                poll(entry, attempt)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); fail(entry, attempt, if (e.message == "Not enough storage") "Not enough free storage." else "Could not start download. Check storage access and try again.") }
        }
    }

    private fun watch(entry: ModelCatalog.Entry) {
        val attempt = ++generation
        mutable.value = mutable.value.copy(modelId = entry.id, active = true, message = "Resuming download…")
        worker = scope.launch {
            try { poll(entry, attempt) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); fail(entry, attempt, "Could not finish download. Check storage access and retry.") }
        }
    }

    private suspend fun poll(entry: ModelCatalog.Entry, attempt: Long) {
        val transferJob = currentCoroutineContext()[Job]!!
        while (transferJob.isActive && attempt == generation) {
            val id = downloadId
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                currentCoroutineContext().ensureActive()
                if (attempt != generation) return
                if (cursor == null || !cursor.moveToFirst()) { fail(entry, attempt, "Download was removed. Tap Retry."); return }
                fun number(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                val received = number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val progress = (received.toDouble() / entry.bytes).toFloat().coerceIn(0f, 1f)
                when (number(DownloadManager.COLUMN_STATUS).toInt()) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        update(attempt, 1f, "Checking model…")
                        try {
                            ModelInstaller.install(staging(entry), ModelFiles.dataEaterDirectory(), entry, { attempt != generation || !transferJob.isActive }, onStaged = { file ->
                                synchronized(this) {
                                    if (attempt != generation || !transferJob.isActive) throw InterruptedException()
                                    prefs.edit().putString("installTemp", file.absolutePath).commit()
                                }
                            }) { publish ->
                                synchronized(this) {
                                    if (attempt != generation || !transferJob.isActive) throw InterruptedException()
                                    publish()
                                    clearRecord()
                                    manager.remove(id)
                                    mutable.value = State(entry.id, 1f, "Downloaded. Ready to load.", false, mutable.value.installed + 1)
                                }
                            }
                        } catch (e: Exception) {
                            if (attempt == generation && transferJob.isActive) fail(entry, attempt, "Could not verify or save the model. Check storage access and retry.")
                        }
                        return
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = number(DownloadManager.COLUMN_REASON).toInt()
                        fail(entry, attempt, if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) "Not enough free storage." else "Download failed. Check your connection and retry.")
                        return
                    }
                    DownloadManager.STATUS_PAUSED -> update(attempt, progress, "Waiting for connection…")
                    DownloadManager.STATUS_PENDING -> update(attempt, progress, "Starting download…")
                    else -> update(attempt, progress, "Downloading · ${(progress * 100).toInt()}%")
                }
            }
            delay(750)
        }
    }

    @Synchronized fun cancel() {
        generation++
        worker?.cancel()
        if (downloadId >= 0) manager.remove(downloadId)
        val entry = ModelCatalog.entries.find { it.id == mutable.value.modelId }
        entry?.let { staging(it).delete() }
        cleanupStaged()
        clearRecord()
        mutable.value = mutable.value.copy(active = false, message = "Download cancelled.", progress = 0f)
    }
    @Synchronized private fun update(attempt: Long, progress: Float, message: String) {
        if (attempt == generation) mutable.value = mutable.value.copy(progress = progress, message = message)
    }
    @Synchronized private fun fail(entry: ModelCatalog.Entry, attempt: Long, message: String) {
        if (attempt != generation) return
        if (downloadId >= 0) manager.remove(downloadId)
        staging(entry).delete()
        cleanupStaged()
        clearRecord()
        mutable.value = mutable.value.copy(active = false, message = message)
    }
    private fun cleanupStaged() {
        if (!ModelFiles.hasStorageAccess(app)) return
        runCatching {
            val path = prefs.getString("installTemp", null) ?: return
            val file = File(path)
            // Only our persisted, generated staging file; never a user's model.
            val owned = file.name.startsWith(".dataeater-model-") && file.name.endsWith(".part") &&
                file.canonicalFile.parentFile == ModelFiles.dataEaterDirectory().canonicalFile
            if (!owned || !file.exists() || file.delete()) prefs.edit().remove("installTemp").commit()
        }
    }
    private fun clearRecord() { downloadId = -1; prefs.edit().clear().commit() }

    companion object {
        @Volatile private var instance: ModelDownloads? = null
        fun get(context: Context): ModelDownloads = instance ?: synchronized(this) {
            instance ?: ModelDownloads(context).also { instance = it }
        }
    }
}
