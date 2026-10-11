package com.dataeater.app.builder

import android.app.Application
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.MainActivity
import com.dataeater.app.data.DatabaseDownloads
import com.dataeater.app.data.DatabaseReader
import com.dataeater.app.security.DeviceKeys
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Only synthetic documents and newly generated disposable publisher keys are used. */
class BuilderFlowDeviceTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context = instrument.targetContext
    private fun main(action: () -> Unit) = instrument.runOnMainSync(action)
    private fun await(model: BuilderViewModel) {
        val limit = android.os.SystemClock.elapsedRealtime() + 45000
        while (android.os.SystemClock.elapsedRealtime() < limit) {
            var busy = true; main { busy = model.state.busy }
            if (!busy) { assertFalse(model.state.status, model.state.status.startsWith("Could not")); return }
            Thread.sleep(40)
        }
        fail("Preparation timed out")
    }
    @Test fun createOptimiseEncryptLicenceBackupRestoreAndRemove() {
        val component = context.packageName + "/" + MainActivity::class.java.name
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("am start -W -n $component")).use { it.readBytes() }
        run {
            val root = File(context.cacheDir, "builder-flow-${UUID.randomUUID()}").apply { mkdirs() }
            val workspaces = File(context.filesDir, "builder/workspaces")
            val vault = PublisherVault(context)
            val ownedKeys = mutableListOf<PublisherVault.Item>()
            val ownedWorkspaces = mutableSetOf<File>()
            fun own(file: File) { if (file.canonicalPath.startsWith(workspaces.canonicalPath + File.separator)) ownedWorkspaces.add(file.parentFile!!) }
            val visible = mutableListOf<File>()
            try {
                val source = File(root, "synthetic.txt").apply { writeText("=== PAGE 1 ===\nSynthetic pump setting 380 bar. Never inspect while running.\n\n=== PAGE 3 ===\nStop before opening the cover. Preserve 12.5 volts.\n") }
                val signing = vault.save("Disposable verification signer", "signing", BuilderCrypto.createKey()); ownedKeys.add(signing)
                lateinit var model: BuilderViewModel
                main { model = BuilderViewModel(context.applicationContext as Application); model.documents(listOf(Uri.fromFile(source))) }
                await(model)
                val name = "verification-${UUID.randomUUID()}"
                main { model.field("name", name); model.signing(signing.id); model.createSimple(false, false) }
                await(model)
                val plain = model.state.result!!.file!!; visible.add(plain)
                workspaces.listFiles().orEmpty().firstOrNull { f -> File(f, plain.name).isFile }?.let { f -> ownedWorkspaces.add(f) }
                assertEquals(listOf(1, 3), DatabaseReader.openFile(plain).chunks.map { c -> c.page }.distinct())
                main { model.createSimple(false, true) }; await(model)
                val zip = model.state.result!!.file!!; visible.add(zip); ownedWorkspaces.add(File(workspaces, model.state.reviewId!!))
                ZipFile(zip).use { archive ->
                    assertTrue(archive.getEntry("LLM_PROMPT.txt") != null)
                    assertArrayEquals(source.readBytes(), archive.getInputStream(archive.getEntry("sources/source-1.txt")).readBytes())
                }
                main { model.importReview(Uri.fromFile(zip)) }; await(model); ownedWorkspaces.add(File(workspaces, model.state.reviewId!!))
                main { model.createSimple(true, true, true) }
                android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME")).use { it.readBytes() }
                await(model)
                android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("am start -W -n $component")).use { it.readBytes() }
                val locked = model.state.result!!.file!!; visible.add(locked)
                assertTrue(locked.name.contains("-priv.dataeater"))
                val secret = model.state.result!!.secret!!; ownedKeys.add(secret); own(File(vault.record(secret).getString("database_path")))
                assertEquals(locked.name, vault.list().first { key -> key.id == secret.id }.name)
                main { model.licenceFor(secret, DeviceKeys.requestCode(), "30d") }; await(model)
                own(model.state.result!!.file!!)
                val code = com.dataeater.app.security.UnlockCode.parse(model.state.result!!.file!!.readText())
                val content = com.dataeater.app.security.UnlockCode.recoverContentKey(code)
                assertTrue(DatabaseReader.openLocked(locked, content).chunks.any { c -> c.text.contains("380 bar") })
                var backup: File? = null
                main { model.prepareVaultBackup(secret) { file -> backup = file } }; await(model); own(backup!!)
                main { model.removeKey(secret) }; await(model)
                assertFalse(vault.list().any { key -> key.id == secret.id })
                main { model.restoreVaultBackup(Uri.fromFile(backup!!)) }; await(model)
                val restored = model.state.result!!.secret!!; ownedKeys.add(restored); ownedKeys.add(model.state.result!!.signing!!); own(File(vault.record(restored).getString("database_path")))
                main { model.licenceFor(restored, DeviceKeys.requestCode(), "never") }; await(model)
                assertTrue(model.state.result!!.file!!.readText().isNotBlank()); own(model.state.result!!.file!!)
            } finally {
                for (key in ownedKeys) vault.remove(key)
                visible.forEach { f -> f.delete() }
                ownedWorkspaces.forEach { f -> f.deleteRecursively() }
                root.deleteRecursively()
            }
        }
    }
    @Test fun liveDatabaseDownloadVerifiesAndInstalls() {
        val root = File(context.getExternalFilesDir(null), "download-check-${UUID.randomUUID()}")
        val namespace = "download_check_${UUID.randomUUID()}"
        val downloads = DatabaseDownloads(context, root, namespace)
        try {
            downloads.refresh()
            val catalogueLimit = android.os.SystemClock.elapsedRealtime() + 45000
            while (downloads.catalog.value.loading && android.os.SystemClock.elapsedRealtime() < catalogueLimit) Thread.sleep(100)
            assertTrue(downloads.catalog.value.error, downloads.catalog.value.error.isBlank())
            val entry = downloads.catalog.value.entries.first { it.fileName == "survival-0.1.0.dataeater" }
            downloads.start(entry)
            val transferLimit = android.os.SystemClock.elapsedRealtime() + 90000
            while (downloads.state.value.active && android.os.SystemClock.elapsedRealtime() < transferLimit) Thread.sleep(200)
            val state = downloads.state.value
            assertFalse("Download timed out: ${state.message}", state.active)
            assertEquals(state.message, 1, state.installed)
            val database = File(root, entry.fileName)
            assertEquals(entry.bytes, database.length())
            assertEquals(entry.sha256, java.security.MessageDigest.getInstance("SHA-256").digest(database.readBytes()).joinToString("") { "%02x".format(it) })
            assertEquals(2635, DatabaseReader.openFile(database).chunks.size)
            downloads.cancel()
            assertEquals(entry, DatabaseDownloads(context, root, namespace).catalog.value.entries.first())
        } finally {
            downloads.cancel(); root.deleteRecursively()
            context.getSharedPreferences("${namespace}_catalog", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @Test fun liveRepositoryCatalogueOffersSurvival() {
        val downloads = DatabaseDownloads.get(context)
        downloads.refresh()
        val limit = android.os.SystemClock.elapsedRealtime() + 45000
        while (downloads.catalog.value.loading && android.os.SystemClock.elapsedRealtime() < limit) Thread.sleep(100)
        assertFalse("Catalogue timed out", downloads.catalog.value.loading)
        assertTrue(downloads.catalog.value.error, downloads.catalog.value.error.isBlank())
        assertTrue(downloads.catalog.value.entries.any { it.fileName == "survival-0.1.0.dataeater" })
    }
}
