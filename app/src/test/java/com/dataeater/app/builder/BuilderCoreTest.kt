package com.dataeater.app.builder

import com.dataeater.app.data.DatabaseReader
import com.dataeater.app.security.DeviceKeys
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BuilderCoreTest {
    private fun workspace(block: (File) -> Unit) {
        val root = java.nio.file.Files.createTempDirectory("builder-test-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun document() = BuilderCore.Document("Invented guide", "guide-2026.txt", "synthetic", 3, 1, listOf(
        BuilderCore.Page(1, "1. Pump Pressure\nThe invented pump uses 380 bar at 12.5 volts. Never clean it with compressed air."),
        BuilderCore.Page(3, "2. Cooling\nStop the pump before inspection. Never open the coolant cap while hot.")))
    private fun refused(block: () -> Unit) { try { block(); fail("Expected refusal") } catch (_: IllegalArgumentException) { } }
    @Test fun pageBoundariesValuesWarningsAndHeadingsSurvive() {
        val passages = BuilderCore.passages(document().pages)
        assertEquals(listOf(1, 3), passages.map { it.page })
        assertTrue(passages.first().text.contains("380 bar")); assertTrue(passages.first().text.contains("12.5 volts"))
        assertTrue(passages.last().text.contains("Never open")); assertTrue(passages.first().text.contains("1. Pump Pressure"))
        assertFalse(BuilderCore.heading("0.625")); assertFalse(BuilderCore.heading("V = 12"))
    }
    @Test fun longSentencesAndTableRowsAreRetainedRatherThanTruncated() {
        val row = "X | 0.625 | 380 | " + "specific warning " .repeat(150)
        val passages = BuilderCore.passages(listOf(BuilderCore.Page(1, row)), 200)
        assertEquals(row.trim(), passages.joinToString("\n") { it.text })
    }
    @Test fun databaseWriterMatchesTheAndroidReaderAndIdsStayUnique() = workspace { root ->
        val file = File(root, "example.dataeater")
        BuilderCore.writeDatabase(listOf(document(), document().copy(title = "Second guide")), BuilderCore.Options("Invented guide", language = "fr"), file)
        val db = DatabaseReader.openFile(file)
        assertEquals(2, db.sources.size); assertEquals(4, db.chunks.size)
        assertEquals(db.chunks.size, db.chunks.map { it.id }.toSet().size)
        assertEquals("fr", JSONObject(BuilderCore.entries(file).getValue("chunks.jsonl").toString(Charsets.UTF_8).lineSequence().first()).getString("lang"))
    }
    @Test fun fingerprintsAndCountsAreVerifiedBeforeInspection() = workspace { root ->
        val file = File(root, "example.dataeater"); BuilderCore.writeDatabase(listOf(document()), BuilderCore.Options("Guide"), file)
        val entries = BuilderCore.entries(file).toMutableMap(); entries["chunks.jsonl"] = "changed".toByteArray()
        BuilderCore.writeZip(file, entries)
        try { BuilderCore.inspect(file); fail("Tampered text accepted") } catch (_: DatabaseReader.IntegrityException) { }
    }
    @Test fun reviewRoundTripKeepsBlankPageGapAndReportsNumericEdits() = workspace { root ->
        val folder = File(root, "review"); BuilderReview.export(listOf(document()), folder, 1000, "prompt")
        val edited = File(folder, "reviewed").listFiles()!!.first()
        edited.writeText(edited.readText().replace("380 bar", "381 bar"))
        val read = BuilderReview.read(folder)
        assertEquals(listOf(1, 3), read.documents.first().pages.map { it.number }); assertEquals(1, read.numericChanges)
        val zip = File(root, "review.zip"); BuilderReview.zip(folder, zip)
        val copy = File(root, "copy"); BuilderReview.unzip(zip, copy)
        assertEquals(read.documents, BuilderReview.read(copy).documents)
    }
    @Test fun modifiedOriginalAndMissingReorderedOrEmptyPagesAreRefused() = workspace { root ->
        val folder = File(root, "review"); BuilderReview.export(listOf(document()), folder, 1000, "prompt")
        val original = File(folder, "original").listFiles()!!.first(); val reviewed = File(folder, "reviewed/${original.name}"); val saved = reviewed.readText()
        for (bad in listOf(saved.replace("=== PAGE 3 ===", "=== PAGE 4 ==="), saved.replace("=== PAGE 3 ===", "=== PAGE 1 ==="), "=== PAGE 1 ===\n\n=== PAGE 3 ===\n")) {
            reviewed.writeText(bad); refused { BuilderReview.read(folder) }
        }
        reviewed.writeText(saved); original.appendText("Changed original"); refused { BuilderReview.read(folder) }
    }
    @Test fun malformedPublisherContextsAndPathTraversalAreRefused() = workspace { root ->
        val folder = File(root, "review"); BuilderReview.export(listOf(document()), folder, 1000, "prompt")
        val file = File(folder, "review.json"); val manifest = JSONObject(file.readText())
        manifest.getJSONArray("documents").getJSONObject(0).put("page_contexts", "bad")
        file.writeText(manifest.toString()); refused { BuilderReview.read(folder) }
        val zip = File(root, "bad.zip"); ZipOutputStream(zip.outputStream()).use { stream ->
            for (name in listOf("review.json", "../outside.txt")) { stream.putNextEntry(ZipEntry(name)); stream.write("{}".toByteArray()); stream.closeEntry() }
        }
        refused { BuilderReview.unzip(zip, File(root, "bad")) }; assertFalse(File(root, "outside.txt").exists())
    }
    @Test fun signingEncryptionDeviceWrappingExpiryAndKeyReuseWorkTogether() = workspace { root ->
        val plain = File(root, "plain.dataeater"); BuilderCore.writeDatabase(listOf(document()), BuilderCore.Options("Guide"), plain)
        val signing = BuilderCrypto.createKey(); val locked = File(root, "locked.dataeater")
        val secret = BuilderCrypto.encrypt(plain, locked, "Creator", "Contact", signing.getString("public_key"))
        val again = BuilderCrypto.encrypt(plain, locked, "Creator", "Contact", signing.getString("public_key"), secret)
        assertArrayEquals(BuilderCrypto.secretKey(secret), BuilderCrypto.secretKey(again))
        val device = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val request = Base64.getEncoder().encodeToString(device.public.encoded)
        val code = BuilderCrypto.licence(locked, secret, signing, request, "30d")
        val licence = JSONObject(Base64.getDecoder().decode(code.trim()).toString(Charsets.UTF_8))
        DeviceKeys.verifySignatureBytes(licence, signing.getString("public_key"))
        assertArrayEquals(BuilderCrypto.secretKey(secret), DeviceKeys.recoverContentKeyWith(licence.getJSONObject("wrap"), device.private))
        val wrong = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        try { DeviceKeys.recoverContentKeyWith(licence.getJSONObject("wrap"), wrong.private); fail("Wrong device accepted") } catch (_: Exception) { }
        assertEquals(java.time.LocalDate.of(2026, 11, 7).toString(), BuilderCrypto.expiry("30d", java.time.LocalDate.of(2026, 10, 8)))
        assertNull(BuilderCrypto.expiry("never"))
        assertEquals("{\"expiry\":null}", DeviceKeys.signingBytes(JSONObject().put("expiry", JSONObject.NULL)).toString(Charsets.UTF_8))
    }
    @Test fun mismatchedContentOrSigningKeysAndMalformedRequestsAreRefused() = workspace { root ->
        val plain = File(root, "plain.dataeater"); BuilderCore.writeDatabase(listOf(document()), BuilderCore.Options("Guide"), plain)
        val signing = BuilderCrypto.createKey(); val locked = File(root, "locked.dataeater")
        val secret = BuilderCrypto.encrypt(plain, locked, "Creator", "", signing.getString("public_key"))
        refused { BuilderCrypto.licence(locked, secret, BuilderCrypto.createKey(), signing.getString("public_key"), "never") }
        refused { BuilderCrypto.licence(locked, secret, signing, "not a request", "never") }
        val invalid = JSONObject(secret.toString()).put("content_key", Base64.getEncoder().encodeToString(ByteArray(32)))
        try { BuilderCrypto.licence(locked, invalid, signing, signing.getString("public_key"), "never"); fail("Wrong content key accepted") } catch (_: javax.crypto.AEADBadTagException) { }
    }
    @Test fun linuxFixturesCanBeReadAndReissuedByTheAndroidBuilder() = workspace { root ->
        val fixture = JSONObject(javaClass.getResourceAsStream("/builder-interop.json")!!.bufferedReader().readText())
        val plain = File(root, "linux.dataeater").apply { writeBytes(Base64.getDecoder().decode(fixture.getString("plain"))) }
        val locked = File(root, "linux-locked.dataeater").apply { writeBytes(Base64.getDecoder().decode(fixture.getString("locked"))) }
        assertTrue(BuilderCore.inspect(plain).contains("380")); BuilderCore.inspect(locked)
        val secret = fixture.getJSONObject("secret"); val signing = fixture.getJSONObject("signing")
        val code = BuilderCrypto.licence(locked, secret, signing, fixture.getString("request"), "never")
        val licence = JSONObject(Base64.getDecoder().decode(code.trim()).toString(Charsets.UTF_8))
        DeviceKeys.verifySignatureBytes(licence, signing.getString("public_key"))
        val reviewZip = File(root, "linux-review.zip").apply { writeBytes(Base64.getDecoder().decode(fixture.getString("review_zip"))) }
        val folder = File(root, "review"); BuilderReview.unzip(reviewZip, folder)
        assertEquals(listOf(1, 3), BuilderReview.read(folder).documents.first().pages.map { it.number })
        // The outputs are strictly invented fixtures for Python compatibility checking.
        val export = File("build/builder-interop").apply { mkdirs() }
        val android = File(export, "android.dataeater")
        BuilderCore.writeDatabase(listOf(document()), BuilderCore.Options("Android invented guide"), android)
        val androidSigning = BuilderCrypto.createKey(); val androidLocked = File(export, "android-locked.dataeater")
        val androidSecret = BuilderCrypto.encrypt(android, androidLocked, "Synthetic creator", "", androidSigning.getString("public_key"))
        File(export, "android-signing.json").writeText(androidSigning.toString())
        File(export, "android-secret.json").writeText(androidSecret.toString())
        File(export, "android-code.lic").writeText(BuilderCrypto.licence(androidLocked, androidSecret, androidSigning, fixture.getString("request"), "never"))
        val review = File(export, "review"); review.deleteRecursively(); BuilderReview.export(listOf(document()), review, 1000, "Synthetic prompt")
    }
    @Test fun cancellationAndByteLimitsRefuseBeforePublishing() {
        try {
            Thread.currentThread().interrupt()
            try { BuilderCore.checkpoint(); fail("Cancellation ignored") } catch (_: InterruptedException) { }
        } finally { Thread.interrupted() }
        refused { BuilderCore.bounded(java.io.ByteArrayInputStream(ByteArray(100)), 50) }
    }
}
