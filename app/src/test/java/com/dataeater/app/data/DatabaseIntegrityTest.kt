package com.dataeater.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class DatabaseIntegrityTest {
    private val demo = File("../dataeater/databases/demo.dataeater")

    private fun entries(): MutableMap<String, ByteArray> = ZipFile(demo).use { zip ->
        zip.entries().asSequence().associate { entry ->
            entry.name to zip.getInputStream(entry).use { it.readBytes() }
        }.toMutableMap()
    }

    private fun withPackage(entries: Map<String, ByteArray>, check: (File) -> Unit) {
        val file = File.createTempFile("integrity-", ".dataeater")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            check(file)
        } finally {
            file.delete()
        }
    }

    @Test
    fun realBuilderDemoOpensWithMatchingHashes() {
        val opened = DatabaseReader.openFile(demo)
        assertEquals(21, opened.chunks.size)
        assertEquals(2, opened.sources.size)
    }

    @Test
    fun changedKnowledgeAndCitationsAreBothRefused() {
        for (name in listOf("chunks.jsonl", "sources.json")) {
            val entries = entries()
            // Still valid JSON: parsing alone cannot detect this change.
            entries[name] = entries.getValue(name) + " ".toByteArray()
            withPackage(entries) { file ->
                val error = assertThrows(DatabaseReader.IntegrityException::class.java) {
                    DatabaseReader.openFile(file)
                }
                assertTrue(error.message!!.contains(name))
                assertTrue(error.message!!.contains("changed or damaged"))
            }
        }
    }

    @Test
    fun missingOrMalformedHashCannotDisableVerification() {
        for (badHash in listOf(null, "", "sha256:1234", "md5:" + "0".repeat(64), 123)) {
            val entries = entries()
            val manifest = JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8))
            val fileInfo = manifest.getJSONObject("files").getJSONObject("chunks.jsonl")
            if (badHash == null) fileInfo.remove("sha256") else fileInfo.put("sha256", badHash)
            entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
            withPackage(entries) { file ->
                assertThrows(DatabaseReader.IntegrityException::class.java) {
                    DatabaseReader.openFile(file)
                }
            }
        }
    }

    @Test
    fun missingFileTableOrRequiredEntryIsRefused() {
        for (removeTable in listOf(true, false)) {
            val entries = entries()
            val manifest = JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8))
            if (removeTable) manifest.remove("files")
            else manifest.getJSONObject("files").remove("sources.json")
            entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
            withPackage(entries) { file ->
                assertThrows(DatabaseReader.IntegrityException::class.java) {
                    DatabaseReader.openFile(file)
                }
            }
        }
    }

    @Test
    fun changedEncryptedPayloadIsRefusedBeforeDecryption() {
        val manifest = """{"format":"dataeater","format_version":1,
            "encryption":"aes-256-gcm","payload_nonce":"unused",
            "files":{"payload.enc":{"sha256":"sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}}} """
        withPackage(mapOf("manifest.json" to manifest.toByteArray(), "payload.enc" to byteArrayOf(1))) { file ->
            val error = assertThrows(DatabaseReader.IntegrityException::class.java) {
                DatabaseReader.openLocked(file, ByteArray(32))
            }
            assertTrue(error.message!!.contains("payload.enc"))
        }
    }

    @Test
    fun hashesCoverExactUtf8BytesIncludingLineEndings() {
        // SHA-256 of the UTF-8 bytes for "hello\n", independently pinned.
        val manifest = """{"files":{"payload.enc":{"sha256":"sha256:5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03"}}}"""
        DatabaseIntegrity.verify(manifest, "payload.enc", "hello\n".toByteArray(Charsets.UTF_8))
        assertThrows(DatabaseReader.IntegrityException::class.java) {
            DatabaseIntegrity.verify(manifest, "payload.enc", "hello\r\n".toByteArray(Charsets.UTF_8))
        }
    }
}
