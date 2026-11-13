package com.dataeater.app.builder

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.data.DatabaseReader
import com.dataeater.app.ocr.OcrLanguage
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Entirely synthetic sources, in the app's private cache. No models or shared manuals. */
class BuilderOcrDeviceTest {
    private fun workspace(block: (File, File) -> Unit) {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val root = File(instrument.targetContext.cacheDir, "builder-ocr-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val original = File(root, "scanned.pdf")
            instrument.context.assets.open("pdf-names/scanned-title.pdf").use { input -> original.outputStream().use { output -> input.copyTo(output) } }
            val mixed = File(root, "mixed.pdf")
            ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val bitmap = Bitmap.createBitmap(1500, 2000, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        renderer.openPage(0).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        val pdf = PdfDocument()
                        try {
                            for (number in 1..4) {
                                val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, number).create())
                                page.canvas.drawColor(Color.WHITE)
                                if (number == 1 || number == 4) page.canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, 600, 800), null)
                                if (number == 2) {
                                    val paint = Paint().apply { color = Color.BLACK; textSize = 15f }
                                    listOf("Native selectable text must remain unchanged.", "Do not remove PX-17 at 12.5 MPa.",
                                        "1. Stop the engine before inspection.", "2. Check the original manual and all warnings.").forEachIndexed { index, line ->
                                        page.canvas.drawText(line, 30f, 70f + index * 30, paint)
                                    }
                                }
                                pdf.finishPage(page)
                            }
                            mixed.outputStream().use { output -> pdf.writeTo(output) }
                        } finally { pdf.close() }
                    } finally { bitmap.recycle() }
                }
            }
            block(root, mixed)
        } finally { Thread.interrupted(); root.deleteRecursively() }
    }
    @Test fun mixedPdfBuildsAllPagesAndPreservesReviewLineage() = workspace { root, source ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BuilderFiles(context)
        val input = BuilderFiles.Input(Uri.fromFile(source), "mixed.pdf")
        val temp = File(root, "extract").apply { mkdirs() }
        val fingerprint = BuilderReview.fingerprint(source)
        val plain = files.extract(input, temp)
        assertEquals(listOf(2), plain.pages.map { it.number })
        assertTrue(plain.pageMetrics.all { it.ocr == null })
        val attempts = mutableListOf<Int>()
        val recognised = files.extract(input, temp, OcrLanguage.ENGLISH) { page, total, ocr ->
            assertEquals(4, total); if (ocr) attempts += page
        }
        assertEquals(listOf(1, 3, 4), attempts)
        assertEquals(listOf(1, 2, 4), recognised.pages.map { it.number })
        assertEquals(plain.pages.single().text, recognised.pages.single { it.number == 2 }.text)
        assertTrue(recognised.pages.first().text.contains("TROOPER"))
        assertEquals("used", recognised.pageMetrics[3].ocr!!.status)
        assertEquals("no_usable_text", recognised.pageMetrics[2].ocr!!.status)
        assertEquals(1, recognised.omitted)
        assertEquals(fingerprint, recognised.fingerprint)
        assertEquals(fingerprint, BuilderReview.fingerprint(source))
        assertTrue(temp.listFiles().isNullOrEmpty())
        val db = File(root, "ocr.dataeater")
        BuilderCore.writeDatabase(listOf(recognised), BuilderCore.Options("OCR synthetic"), db)
        assertEquals(listOf(1, 2, 4), DatabaseReader.openFile(db).chunks.map { it.page }.distinct())
        val pages = JSONArray(BuilderCore.entries(db).getValue("sources.json").toString(Charsets.UTF_8))
            .getJSONObject(0).getJSONObject("preparation_quality").getJSONArray("pages")
        assertEquals("eng", pages.getJSONObject(3).getJSONObject("ocr").getString("language"))
        assertFalse(pages.getJSONObject(2).getBoolean("included"))
        val review = File(root, "review")
        BuilderReview.export(listOf(recognised), review, 30000, "Synthetic review prompt")
        val imported = BuilderReview.read(review).documents.single()
        assertEquals(recognised.pageMetrics, imported.pageMetrics)
        assertEquals(recognised.pages.map { it.text }, imported.pages.map { it.text })
        val rebuilt = File(root, "reviewed.dataeater")
        BuilderCore.writeDatabase(listOf(imported), BuilderCore.Options("Reviewed OCR synthetic"), rebuilt)
        assertEquals(listOf(1, 2, 4), DatabaseReader.openFile(rebuilt).chunks.map { it.page }.distinct())
    }
    @Test fun cancellationBeforeRecognitionCleansPrivateCopies() = workspace { root, source ->
        val temp = File(root, "extract").apply { mkdirs() }
        val fingerprint = BuilderReview.fingerprint(source)
        try {
            BuilderFiles(InstrumentationRegistry.getInstrumentation().targetContext).extract(
                BuilderFiles.Input(Uri.fromFile(source), "mixed.pdf"), temp, OcrLanguage.ENGLISH
            ) { _, _, ocr -> if (ocr) Thread.currentThread().interrupt() }
            fail("Ignored interruption")
        } catch (_: InterruptedException) { }
        finally { Thread.interrupted() }
        assertTrue(temp.listFiles().isNullOrEmpty())
        assertEquals(fingerprint, BuilderReview.fingerprint(source))
    }

    @Test fun backgroundBuilderCreatesAndContinuesAnOcrOptimisationZip() = workspace { _, source ->
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val component = context.packageName + "/com.dataeater.app.MainActivity"
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("am start -W -n $component")).use { it.readBytes() }
        lateinit var model: BuilderViewModel
        fun main(action: () -> Unit) = instrument.runOnMainSync(action)
        fun await() {
            val limit = android.os.SystemClock.elapsedRealtime() + 90_000
            while (android.os.SystemClock.elapsedRealtime() < limit) {
                var busy = true
                main { busy = model.state.busy }
                if (!busy) { assertEquals(model.state.status, "Finished.", model.state.status); return }
                Thread.sleep(50)
            }
            model.cancel(); fail("OCR builder did not finish")
        }
        val visible = mutableListOf<File>()
        val ownedWorkspaces = mutableSetOf<File>()
        val workspaces = File(context.filesDir, "builder/workspaces")
        fun ownResult() {
            val result = model.state.result!!
            result.file?.let { visible += it }
            result.quality?.parentFile?.let {
                val workspace = if (it.name == "review") it.parentFile!! else it
                assertEquals(workspaces.canonicalFile, workspace.canonicalFile.parentFile)
                ownedWorkspaces += workspace
            }
        }
        try {
            main {
                model = BuilderViewModel(context.applicationContext as android.app.Application)
                model.documents(listOf(Uri.fromFile(source)))
            }
            await()
            main { model.field("name", "ocr-verification-${UUID.randomUUID()}"); model.ocr(OcrLanguage.ENGLISH); model.createSimple(false, false) }
            await(); ownResult()
            assertEquals(listOf(1, 2, 4), DatabaseReader.openFile(model.state.result!!.file!!).chunks.map { it.page }.distinct())
            main { model.createSimple(false, true) }
            await(); ownResult()
            val zip = model.state.result!!.file!!
            java.util.zip.ZipFile(zip).use { archive ->
                assertArrayEquals(source.readBytes(), archive.getInputStream(archive.getEntry("sources/source-1.pdf")).use { it.readBytes() })
                val manifest = org.json.JSONObject(archive.getInputStream(archive.getEntry("review.json")).bufferedReader().use { it.readText() })
                assertEquals("used", manifest.getJSONArray("documents").getJSONObject(0).getJSONArray("page_metrics").getJSONObject(3).getJSONObject("ocr").getString("status"))
            }
            main { model.importReview(Uri.fromFile(zip)) }
            await()
            ownedWorkspaces += model.reviewFolder(model.state.reviewId!!).parentFile!!
            // The reviewed continuation uses the checked extraction, even with OCR disabled.
            main { model.ocr(null); model.createSimple(false, true, true) }
            await(); ownResult()
            assertEquals(listOf(1, 2, 4), DatabaseReader.openFile(model.state.result!!.file!!).chunks.map { it.page }.distinct())
            val report = org.json.JSONObject(model.state.result!!.quality!!.readText())
            assertEquals("used", report.getJSONArray("documents").getJSONObject(0).getJSONArray("pages").getJSONObject(3).getJSONObject("ocr").getString("status"))
        } finally {
            visible.forEach { it.delete() }
            ownedWorkspaces.forEach { it.deleteRecursively() }
        }
    }
}
