package com.dataeater.app.builder

import android.app.Application
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.data.DatabaseReader
import com.dataeater.app.security.DeviceKeys
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.UUID

class BuilderDeviceTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context = instrument.targetContext
    private fun fixture() = JSONObject(instrument.context.assets.open("builder-interop.json").bufferedReader().use { it.readText() })
    private fun synthetic(block: (File, JSONObject) -> Unit) {
        assertTrue(context.packageName.endsWith(".research"))
        val root = File(context.cacheDir, "builder-device-${UUID.randomUUID()}").apply { mkdirs() }
        try { block(root, fixture()) } finally { root.deleteRecursively() }
    }
    @Test fun pdfExtractionBuildAndReviewKeepOriginalPages() = synthetic { root, data ->
        val pdf = File(root, "invented.pdf").apply { writeBytes(Base64.getDecoder().decode(data.getString("pdf"))) }
        val document = BuilderFiles(context).extract(BuilderFiles.Input(Uri.fromFile(pdf), pdf.name), root)
        assertEquals(listOf(1, 3), document.pages.map { it.number })
        assertEquals(1, document.omitted); assertTrue(document.pages.first().text.contains("380 bar"))
        assertTrue(document.pages.last().text.contains("Never open"))
        val file = File(root, "phone.dataeater"); BuilderCore.writeDatabase(listOf(document), BuilderCore.Options("Invented phone guide"), file)
        val opened = DatabaseReader.openFile(file)
        assertTrue(opened.chunks.all { it.page in listOf(1, 3) }); assertEquals(1, opened.sources.size)
        val review = File(root, "review")
        val prompt = context.assets.open("builder/LLM_PROMPT.txt").bufferedReader().use { it.readText() }
        BuilderReview.export(listOf(document), review, 1000, prompt)
        assertEquals(listOf(1, 3), BuilderReview.read(review).documents.first().pages.map { it.number })
        // Retain only invented interoperability outputs in the isolated test app.
        val export = File(context.filesDir, "builder-device-results").apply { mkdirs() }
        file.copyTo(File(export, "phone.dataeater"), overwrite = true)
        BuilderReview.zip(review, File(export, "phone-review.zip"))
    }
    @Test fun publisherVaultAndDeviceLicensingWorkWithNativeProviders() = synthetic { root, data ->
        val key = BuilderCrypto.createKey(); val vault = PublisherVault(context)
        val item = vault.save("Synthetic temporary key", "signing", key)
        try {
            assertEquals(key.getString("private_key"), vault.record(item).getString("private_key"))
            val stored = File(context.filesDir, "builder/keys/${item.id}.vault").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(stored.contains(key.getString("private_key")))
            val plain = File(root, "plain.dataeater").apply { writeBytes(Base64.getDecoder().decode(data.getString("plain"))) }
            val locked = File(root, "locked.dataeater")
            val secret = BuilderCrypto.encrypt(plain, locked, "Synthetic creator", "", key.getString("public_key"))
            val code = BuilderCrypto.licence(locked, secret, key, DeviceKeys.requestCode(), "never")
            val licence = JSONObject(Base64.getDecoder().decode(code.trim()).toString(Charsets.UTF_8))
            DeviceKeys.verifySignatureBytes(licence, key.getString("public_key"))
            val recovered = DeviceKeys.recoverContentKey(licence)
            assertArrayEquals(BuilderCrypto.secretKey(secret), recovered)
            val opened = DatabaseReader.openLocked(locked, recovered)
            assertTrue(opened.chunks.any { it.text.contains("380 bar") })
            // Linux keys can also protect and license on Android.
            BuilderCrypto.licence(File(root, "linux.dataeater").apply { writeBytes(Base64.getDecoder().decode(data.getString("locked"))) },
                data.getJSONObject("secret"), data.getJSONObject("signing"), DeviceKeys.requestCode(), "never")
        } finally { File(context.filesDir, "builder/keys/${item.id}.vault").delete() }
    }
    private fun await(model: BuilderViewModel) {
        val limit = android.os.SystemClock.elapsedRealtime() + 30000
        while (android.os.SystemClock.elapsedRealtime() < limit) {
            var busy = true
            instrument.runOnMainSync { busy = model.state.busy }
            if (!busy) return
            Thread.sleep(30)
        }
        fail("Builder operation did not finish")
    }
    @Test fun viewModelBuildsReviewsEditsChecksProtectsAndIssuesCodes() = synthetic { root, data ->
        lateinit var model: BuilderViewModel
        instrument.runOnMainSync { model = BuilderViewModel(context.applicationContext as Application) }
        val created = mutableListOf<String>(); val oldWorkspaces = File(context.filesDir, "builder/workspaces").listFiles().orEmpty().map { it.name }.toSet()
        val oldKeys = File(context.filesDir, "builder/keys").listFiles().orEmpty().map { it.name }.toSet()
        try {
            val txt = File(root, "invented.txt").apply { writeText("The invented pump runs at 380 bar. Stop before inspection. " .repeat(8)) }
            instrument.runOnMainSync { model.documents(listOf(Uri.fromFile(txt))) }; await(model)
            instrument.runOnMainSync { model.field("name", "Invented view model guide"); model.run() }; await(model)
            assertNotNull(model.state.result?.file)
            val plain = model.state.result!!.file!!; assertTrue(BuilderCore.inspect(plain).contains("380"))
            instrument.runOnMainSync { model.task(BuilderViewModel.Task.REVIEW); model.run() }; await(model)
            val review = model.state.result!!.review!!
            val name = model.batches(review.id).first(); val original = model.readBatch(review.id, name, true)
            instrument.runOnMainSync { model.saveBatch(review.id, name, original.replace("380", "381")) }; await(model)
            assertTrue(model.readBatch(review.id, name).contains("381"))
            instrument.runOnMainSync { model.review(review.id); model.task(BuilderViewModel.Task.REVIEWED); model.run() }; await(model)
            assertTrue(model.state.result!!.text.contains("Numeric text changed on 1"))
            instrument.runOnMainSync { model.task(BuilderViewModel.Task.KEY); model.run() }; await(model)
            assertNotNull(model.state.signingId)
            instrument.runOnMainSync { model.database(Uri.fromFile(plain)) }; await(model)
            instrument.runOnMainSync { model.task(BuilderViewModel.Task.INSPECT); model.run() }; await(model)
            assertTrue(model.state.result!!.text.contains("Fingerprints"))
            instrument.runOnMainSync { model.task(BuilderViewModel.Task.ENCRYPT); model.run() }; await(model)
            val encrypted = model.state.result!!.file!!; val secret = model.state.result!!.secret!!
            instrument.runOnMainSync { model.secret(secret.id); model.database(Uri.fromFile(encrypted)) }; await(model)
            instrument.runOnMainSync { model.task(BuilderViewModel.Task.LICENCE); model.field("request", DeviceKeys.requestCode()); model.run() }; await(model)
            assertTrue(model.state.result!!.file!!.extension == "lic")
            val previous = model.state.result!!.file!!.readBytes()
            instrument.runOnMainSync { model.task(BuilderViewModel.Task.BUILD); model.run(); model.cancel() }; await(model)
            assertTrue(model.state.status.contains("Cancelled")); assertFalse(previous.isEmpty())
            assertTrue(model.readBatch(review.id, name).contains("381"))
        } finally {
            for (file in File(context.filesDir, "builder/workspaces").listFiles().orEmpty()) if (file.name !in oldWorkspaces) file.deleteRecursively()
            for (file in File(context.filesDir, "builder/keys").listFiles().orEmpty()) if (file.name !in oldKeys) file.delete()
        }
    }
}
