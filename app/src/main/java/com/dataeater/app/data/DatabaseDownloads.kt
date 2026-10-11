package com.dataeater.app.data

import com.dataeater.app.ai.ModelFiles

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Android owns the transfer, so it survives activity recreation and process death.
 * Only database bytes are requested. No chat, document, licence or device identifiers are sent.
 * The persisted download ID lets the app verify/install a finished transfer on reopening.
 */
class DatabaseDownloads internal constructor(context: Context,
    private val destination: File = DatabaseFiles.folder(),
    private val namespace: String = "database_download") {
    data class State(val databaseId: String? = null, val progress: Float = 0f,
        val message: String = "", val active: Boolean = false, val installed: Int = 0)
    private val app = context.applicationContext
    private val manager = app.getSystemService(DownloadManager::class.java)
    private val prefs = app.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    data class CatalogState(val entries: List<DatabaseCatalog.Entry> = emptyList(), val loading: Boolean = false, val error: String = "")
    private val catalogPrefs = app.getSharedPreferences("${namespace}_catalog", Context.MODE_PRIVATE)
    private val mutableCatalog = MutableStateFlow(CatalogState(runCatching {
        DatabaseCatalog.parse(org.json.JSONArray(catalogPrefs.getString("releases", "[]")))
    }.getOrDefault(emptyList())))
    val catalog = mutableCatalog.asStateFlow()
    fun refresh() {
        if (mutableCatalog.value.loading) return
        mutableCatalog.value = mutableCatalog.value.copy(loading = true, error = "")
        scope.launch {
            try {
                val connection = java.net.URL(DatabaseCatalog.CATALOG).openConnection() as java.net.HttpURLConnection
                val all = try {
                    connection.connectTimeout = 15000; connection.readTimeout = 20000
                    connection.instanceFollowRedirects = false
                    require(connection.responseCode == 200)
                    connection.inputStream.use { org.json.JSONArray(com.dataeater.app.builder.BuilderCore.bounded(it, 4L * 1024 * 1024).toString(Charsets.UTF_8)) }
                } finally { connection.disconnect() }
                val entries = DatabaseCatalog.parse(all)
                catalogPrefs.edit().putString("releases", all.toString()).apply()
                mutableCatalog.value = CatalogState(entries)
            } catch (_: Exception) {
                mutableCatalog.value = mutableCatalog.value.copy(loading = false, error = "Could not refresh databases. Check your connection and try again.")
            }
        }
    }
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var worker: Job? = null
    @Volatile private var generation = 0L
    private var downloadId = prefs.getLong("downloadId", -1)
    private fun stagingName(entry: DatabaseCatalog.Entry) = if (namespace == "database_download") "${entry.id}.part" else "$namespace-${entry.id}.part"
    private fun staging(entry: DatabaseCatalog.Entry) = File(requireNotNull(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), stagingName(entry))

    init {
        cleanupStaged()
        val entry = prefs.getString("entry", null)?.let { runCatching { DatabaseCatalog.restore(it) }.getOrNull() }
        if (downloadId >= 0 && entry != null) watch(entry)
        else if (downloadId >= 0) { manager.remove(downloadId); clearRecord() }
    }

    @Synchronized fun start(entry: DatabaseCatalog.Entry) {
        if (mutable.value.active) return
        if (entry !in mutableCatalog.value.entries) {
            mutable.value = mutable.value.copy(databaseId = entry.id, message = "Refresh the database list and try again.")
            return
        }
        val attempt = ++generation
        mutable.value = mutable.value.copy(databaseId = entry.id, message = "Preparing download…", active = true, progress = 0f)
        worker = scope.launch {
            try {
                if (!ModelFiles.hasStorageAccess(app)) error("Storage access required")
                cleanupStaged()
                val temp = staging(entry)
                temp.parentFile?.mkdirs()
                if (temp.parentFile!!.usableSpace < entry.bytes * 2 + 100L * 1024 * 1024) error("Not enough storage")
                if (File(destination, entry.fileName).exists()) error("Database already installed")
                synchronized(this@DatabaseDownloads) {
                    if (attempt != generation) throw CancellationException()
                    temp.delete()
                }
                val request = DownloadManager.Request(Uri.parse(entry.url))
                    .setTitle(entry.name).setDescription("DataEater database download")
                    .setMimeType("application/octet-stream")
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, stagingName(entry))
                // Serialize enqueue with cancellation so Cancel cannot leave an orphan transfer.
                synchronized(this@DatabaseDownloads) {
                    if (attempt != generation) throw CancellationException()
                    downloadId = manager.enqueue(request)
                    prefs.edit().putLong("downloadId", downloadId).putString("entry", entry.json().toString()).commit()
                }
                poll(entry, attempt)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); fail(entry, attempt, if (e.message == "Not enough storage") "Not enough free storage." else "Could not start download. Check storage access and try again.") }
        }
    }

    private fun watch(entry: DatabaseCatalog.Entry) {
        val attempt = ++generation
        mutable.value = mutable.value.copy(databaseId = entry.id, active = true, message = "Resuming download…")
        worker = scope.launch {
            try { poll(entry, attempt) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); fail(entry, attempt, "Could not finish download. Check storage access and retry.") }
        }
    }

    private suspend fun poll(entry: DatabaseCatalog.Entry, attempt: Long) {
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
                        update(attempt, 1f, "Checking database…")
                        try {
                            DatabaseInstaller.install(staging(entry), destination, entry, { attempt != generation || !transferJob.isActive }, onStaged = { file ->
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
                                    mutable.value = State(entry.id, 1f, "Downloaded. Ready to search.", false, mutable.value.installed + 1)
                                }
                            }
                        } catch (e: Exception) {
                            if (attempt == generation && transferJob.isActive) fail(entry, attempt, "Could not verify or save the database. Check storage access and retry.")
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
        val entry = prefs.getString("entry", null)?.let { runCatching { DatabaseCatalog.restore(it) }.getOrNull() }
        entry?.let { staging(it).delete() }
        cleanupStaged()
        clearRecord()
        mutable.value = mutable.value.copy(active = false, message = "Download cancelled.", progress = 0f)
    }
    @Synchronized private fun update(attempt: Long, progress: Float, message: String) {
        if (attempt == generation) mutable.value = mutable.value.copy(progress = progress, message = message)
    }
    @Synchronized private fun fail(entry: DatabaseCatalog.Entry, attempt: Long, message: String) {
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
            // Only our persisted, generated staging file; never a user's database.
            val owned = file.name.startsWith(".dataeater-database-") && file.name.endsWith(".part") &&
                file.canonicalFile.parentFile == destination.canonicalFile
            if (!owned || !file.exists() || file.delete()) prefs.edit().remove("installTemp").commit()
        }
    }
    private fun clearRecord() { downloadId = -1; prefs.edit().clear().commit() }

    companion object {
        @Volatile private var instance: DatabaseDownloads? = null
        fun get(context: Context): DatabaseDownloads = instance ?: synchronized(this) {
            instance ?: DatabaseDownloads(context).also { instance = it }
        }
    }
}
