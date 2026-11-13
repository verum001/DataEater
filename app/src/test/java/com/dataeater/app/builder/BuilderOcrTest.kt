package com.dataeater.app.builder

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BuilderOcrTest {
    @Test fun thinPagesUseOnlyAnImprovedReading() {
        assertTrue(BuilderOcrPolicy.needsOcr("Short native caption"))
        assertFalse(BuilderOcrPolicy.needsOcr("Normal native text. ".repeat(10)))
        val title = "ISUZU TROOPER SERVICE MANUAL 1994"
        assertTrue(BuilderOcrPolicy.choose("", title).used)
        assertEquals(title, BuilderOcrPolicy.choose("", title).text)
        assertEquals("caption", BuilderOcrPolicy.choose("caption", "xx").text)
        assertEquals("no_usable_text", BuilderOcrPolicy.choose("caption", "xx").status)
        assertEquals("not_improved", BuilderOcrPolicy.choose("Native words with useful numbers 1994", "Native words with useful 1994").status)
    }
    @Test fun qualityAndReviewKeepOcrLineageWithoutSourceText() {
        val root = Files.createTempDirectory("ocr-review-").toFile()
        try {
            val reading = BuilderQuality.Ocr("eng", "used", 91)
            val skipped = BuilderQuality.Ocr("eng", "no_usable_text", 0)
            val doc = BuilderCore.Document("Scan", "scan.pdf", "a".repeat(64), 2, 1,
                listOf(BuilderCore.Page(1, "Do not remove PX-17 at 12.5 MPa.")),
                pageMetrics = listOf(BuilderQuality.Metric(1, 33, 33, reading), BuilderQuality.Metric(2, 0, 0, skipped)))
            val review = File(root, "review")
            BuilderReview.export(listOf(doc), review, 30000, "prompt")
            val imported = BuilderReview.read(review).documents.single()
            assertEquals(doc.pageMetrics, imported.pageMetrics)
            val report = BuilderQuality.report(listOf(imported), BuilderCore.Options("Scan"))
            val pages = report.getJSONArray("documents").getJSONObject(0).getJSONArray("pages")
            assertEquals("used", pages.getJSONObject(0).getJSONObject("ocr").getString("status"))
            assertEquals("no_usable_text", pages.getJSONObject(1).getJSONObject("ocr").getString("status"))
            assertFalse(pages.getJSONObject(1).getBoolean("included"))
            assertTrue(pages.getJSONObject(0).getJSONArray("flags").toString().contains("ocr_accuracy_not_verified"))
            assertFalse(report.toString().contains("Do not remove"))
            assertTrue(BuilderQuality.summary(report).contains("Recognised text was used on 1 page;"))
            val plain = File(root, "ocr.dataeater")
            BuilderCore.writeDatabase(listOf(imported), BuilderCore.Options("OCR"), plain)
            val locked = File(root, "ocr-priv.dataeater")
            val key = BuilderCrypto.createKey()
            val secret = BuilderCrypto.encrypt(plain, locked, "Synthetic", "", key.getString("public_key"))
            val entries = BuilderCore.entries(locked)
            assertEquals(setOf("manifest.json", "payload.enc"), entries.keys)
            val header = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
            assertFalse(header.toString().contains("mean_confidence"))
            val decrypted = com.dataeater.app.security.DeviceKeys.gcmDecrypt(BuilderCrypto.secretKey(secret),
                java.util.Base64.getDecoder().decode(header.getString("payload_nonce")), entries.getValue("payload.enc"))
            assertTrue(JSONObject(decrypted.toString(Charsets.UTF_8)).getString("sources").contains("ocr_accuracy_not_verified"))
            val manifest = File(review, "review.json")
            val json = JSONObject(manifest.readText())
            json.getJSONArray("documents").getJSONObject(0).getJSONArray("page_metrics").getJSONObject(0).getJSONObject("ocr").put("language", "../../data")
            manifest.writeText(json.toString())
            try { BuilderReview.read(review); fail("Unsafe language accepted") } catch (_: IllegalArgumentException) { }
        } finally { root.deleteRecursively() }
    }
    @Test fun invalidRecognitionMetadataIsRejected() {
        for (args in listOf(Triple("eng", "used", -1), Triple("rus", "used", 101), Triple("eng", "approved", 90))) {
            try { BuilderQuality.Ocr(args.first, args.second, args.third); fail("Invalid OCR inventory") } catch (_: IllegalArgumentException) { }
        }
    }
}
