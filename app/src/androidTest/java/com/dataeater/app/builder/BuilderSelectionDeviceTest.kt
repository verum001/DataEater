package com.dataeater.app.builder

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.data.DatabaseReader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import java.io.File
import java.util.Base64
import java.util.UUID

/** Fast selection used to stop the foreground service before Android started it. */
class BuilderSelectionDeviceTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context = instrument.targetContext
    @Before fun openApp() {
        val component = context.packageName + "/com.dataeater.app.MainActivity"
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrument.uiAutomation.executeShellCommand("am start -W -n $component")
        ).use { it.readBytes() }
    }
    private fun await(model: BuilderViewModel) {
        val deadline = SystemClock.elapsedRealtime() + 30000
        while (SystemClock.elapsedRealtime() < deadline) {
            var busy = true
            instrument.runOnMainSync { busy = model.state.busy }
            if (!busy) return
            Thread.sleep(20)
        }
        fail("Document selection did not finish")
    }
    @Test fun expiredMediaDocumentAccessGivesRecoveryMessage() {
        val root = File(context.cacheDir, "expired-access-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val uri = Uri.parse("content://com.android.providers.media.documents/document/document:99999999")
            val target = File(root, "partial.pdf")
            try {
                BuilderFiles(context).copy(uri, target)
                fail("An ungranted media document must be refused")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("choose it again"))
                assertTrue(error.cause is SecurityException)
                assertFalse(target.exists())
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun rapidPdfSelectionsCancellationAndRejectionDoNotCrash() {
        val root = File(context.cacheDir, "benchmark-share/selection-test-${UUID.randomUUID()}").apply { mkdirs() }
        lateinit var model: BuilderViewModel
        instrument.runOnMainSync { model = BuilderViewModel(context.applicationContext as Application) }
        try {
            val fixture = JSONObject(instrument.context.assets.open("builder-interop.json").bufferedReader().use { it.readText() })
            val pdf = File(root, "invented.pdf").apply { writeBytes(Base64.getDecoder().decode(fixture.getString("pdf"))) }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".benchmark-files", pdf)
            repeat(10) {
                instrument.runOnMainSync { model.documents(listOf(uri)) }
                await(model)
                instrument.runOnMainSync { assertEquals(listOf(uri), model.state.inputs.map { it.uri }); assertEquals("Finished.", model.state.status) }
            }
            instrument.runOnMainSync { model.documents(listOf(Uri.fromFile(File(root, "unsupported.bin")))) }
            await(model)
            instrument.runOnMainSync { assertTrue(model.state.status.contains("Choose PDF")) }
            instrument.runOnMainSync { model.documents(listOf(uri)); model.cancel() }
            await(model)
            instrument.runOnMainSync { assertTrue(model.state.status.contains("Cancelled")) }
            instrument.runOnMainSync { model.documents(listOf(uri)) }
            await(model)
            // Wait beyond Android's foreground-start deadline: the old crash was delayed.
            Thread.sleep(12000)
            instrument.runOnMainSync { assertFalse(model.state.busy); assertEquals("Finished.", model.state.status) }
            val document = BuilderFiles(context).extract(BuilderFiles.Input(uri, pdf.name), root)
            val database = File(root, "synthetic.dataeater")
            BuilderCore.writeDatabase(listOf(document), BuilderCore.Options("Synthetic PDF selection"), database)
            assertTrue(DatabaseReader.openFile(database).chunks.any { it.text.contains("380 bar") })
        } finally { root.deleteRecursively() }
    }
}
