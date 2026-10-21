package com.dataeater.app.builder

import com.dataeater.app.data.DatabaseReader
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BuilderQualityTest {
    private fun workspace(block: (File) -> Unit) {
        val root = Files.createTempDirectory("quality-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun document() = BuilderCore.Document("Synthetic", "synthetic.pdf", "a".repeat(64), 3, 1,
        listOf(BuilderCore.Page(1, "1. Do not remove PX-17 at 12.5 MPa.\n2. Check the valve."), BuilderCore.Page(3, "X".repeat(1301))),
        pageMetrics = listOf(BuilderQuality.Metric(1, 55, 55), BuilderQuality.Metric(2, 12, 12), BuilderQuality.Metric(3, 1301, 1301)))
    @Test fun flagsAreNonBlockingAndFormattingIsNotASemanticChange() {
        val old = "1. Do not remove PX-17 at 12.5 MPa.\n2. Check unit."
        val flags = BuilderQuality.changes(old, "2. Do remove PX-18 at 13.5 bar.\n3. Check unit.")
        for (name in listOf("numbers", "restrictions", "units", "identifiers", "numbered_steps")) assertTrue(name, "${name}_changed" in flags)
        assertEquals(listOf("text_changed"), BuilderQuality.changes("1. Do not use 12.5 MPa.", "1.  Do not use 12.5  MPa."))
        assertTrue("restrictions_changed" in BuilderQuality.changes("Не снимать деталь.", "Снимать деталь."))
    }
    @Test fun reportRecordsOmittedPagesAndNeverCertifiesLayout() = workspace { root ->
        val doc = document(); val file = File(root, "guide.dataeater")
        BuilderCore.writeDatabase(listOf(doc), BuilderCore.Options("Guide"), file)
        val sources = JSONArray(BuilderCore.entries(file).getValue("sources.json").toString(Charsets.UTF_8))
        val report = sources.getJSONObject(0).getJSONObject("preparation_quality")
        assertFalse(report.getJSONArray("pages").getJSONObject(1).getBoolean("included"))
        assertEquals("below_100_characters", report.getJSONArray("pages").getJSONObject(1).getString("reason"))
        assertEquals(12, report.getJSONArray("pages").getJSONObject(1).getInt("characters"))
        assertEquals("not_verified", report.getString("technical_accuracy"))
        assertTrue(report.getJSONArray("oversized_passages").length() > 0)
        assertFalse(report.toString().contains("Do not remove"))
        assertTrue(DatabaseReader.openFile(file).chunks.isNotEmpty())
    }
    @Test fun reviewRoundTripRecomputesFlagsAndKeepsMarkers() = workspace { root ->
        val folder = File(root, "review"); BuilderReview.export(listOf(document()), folder, 30000, "prompt")
        assertTrue(File(folder, "quality_report.json").isFile)
        val batch = File(folder, "reviewed").listFiles()!!.first()
        batch.writeText(batch.readText().replace("Do not", "Do").replace("MPa", "bar"))
        val zip = File(root, "review.zip"); BuilderReview.zip(folder, zip)
        val imported = File(root, "imported"); BuilderReview.unzip(zip, imported)
        val doc = BuilderReview.read(imported).documents.single()
        assertEquals(listOf(1, 3), doc.pages.map { it.number })
        assertEquals(3, doc.pageMetrics.size)
        assertTrue(doc.reviewChanges.single().flags.containsAll(listOf("restrictions_changed", "units_changed")))
        val file = File(root, "reviewed.dataeater"); BuilderCore.writeDatabase(listOf(doc), BuilderCore.Options("Guide"), file)
        assertTrue(BuilderCore.inspect(file).contains("Fingerprints"))
        batch.writeText(batch.readText().replace("=== PAGE 3 ===", "=== PAGE 4 ==="))
        try { BuilderReview.read(folder); fail("Changed marker accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun cancelledBuildPreservesPreviousOutput() = workspace { root ->
        val file = File(root, "guide.dataeater"); file.writeText("existing output")
        Thread.currentThread().interrupt()
        try {
            try { BuilderCore.writeDatabase(listOf(document()), BuilderCore.Options("Guide"), file); fail("Ignored interruption") }
            catch (_: InterruptedException) { }
        } finally { Thread.interrupted() }
        assertEquals("existing output", file.readText())
        assertFalse(root.listFiles()!!.any { it.name.startsWith(".dataeater-build-") })
    }
    @Test fun pythonReportDatabaseOpensInAndroidWithoutFormatChange() = workspace { root ->
        val fixture = org.json.JSONObject(javaClass.getResourceAsStream("/quality_python.json")!!.bufferedReader().use { it.readText() })
        val entries = fixture.getJSONObject("entries")
        val file = File(root, "python.dataeater")
        BuilderCore.writeZip(file, linkedMapOf("manifest.json" to entries.getString("manifest.json").toByteArray(), "sources.json" to entries.getString("sources.json").toByteArray(), "chunks.jsonl" to entries.getString("chunks.jsonl").toByteArray()))
        assertEquals(2, DatabaseReader.openFile(file).chunks.size)
        val expected = fixture.getJSONArray("expected_flags").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertEquals(expected, BuilderQuality.changes("1. Do not remove PX-17 at 12.5 MPa.\n2. Check unit.", "2. Do remove PX-18 at 13.5 bar.\n3. Check unit."))
        assertTrue(BuilderCore.inspect(file).contains("Fingerprints"))
    }

    @Test fun reportRemainsInsideEncryptedSourcesAndExportsSyntheticCompatibilityFile() = workspace { root ->
        val plain = File(root, "plain.dataeater"); BuilderCore.writeDatabase(listOf(document()), BuilderCore.Options("Guide"), plain)
        val key = BuilderCrypto.createKey(); val locked = File(root, "private.dataeater")
        val secret = BuilderCrypto.encrypt(plain, locked, "Synthetic", "", key.getString("public_key"))
        val entries = BuilderCore.entries(locked)
        assertEquals(setOf("manifest.json", "payload.enc"), entries.keys)
        assertFalse(entries.getValue("manifest.json").toString(Charsets.UTF_8).contains("source_sha256"))
        val manifest = org.json.JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        val payload = com.dataeater.app.security.DeviceKeys.gcmDecrypt(BuilderCrypto.secretKey(secret), java.util.Base64.getDecoder().decode(manifest.getString("payload_nonce")), entries.getValue("payload.enc"))
        assertTrue(org.json.JSONObject(payload.toString(Charsets.UTF_8)).getString("sources").contains("preparation_quality"))
        val compat = File("build/quality-compat/android.dataeater")
        compat.parentFile.mkdirs(); plain.copyTo(compat, overwrite = true)
        val review = File("build/quality-compat/android-review")
        if (review.exists()) review.deleteRecursively()
        BuilderReview.export(listOf(document()), review, 30000, "Synthetic compatibility fixture")
    }

}
