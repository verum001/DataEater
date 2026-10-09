package com.dataeater.app.ai

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest
import java.io.IOException

class ModelInstallerTest {
    private fun entry(data: ByteArray) = ModelCatalog.entries.first().copy(fileName = "model.litertlm", bytes = data.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) })
    private fun runCase(block: (java.io.File, java.io.File, ModelCatalog.Entry) -> Unit) {
        val root = Files.createTempDirectory("model-install").toFile()
        try {
            val data = "verified model payload".toByteArray()
            val source = java.io.File(root, "download.part").apply { writeBytes(data) }
            block(source, java.io.File(root, "models"), entry(data))
        } finally { root.deleteRecursively() }
    }
    @Test fun verifiedModelMovesIntoList() = runCase { source, dir, entry ->
        ModelInstaller.install(source, dir, entry)
        assertFalse(source.exists()); assertEquals(entry.bytes, java.io.File(dir, entry.fileName).length())
    }
    @Test fun wrongHashNeverPublishes() = runCase { source, dir, entry ->
        source.writeBytes(source.readBytes().apply { this[0] = (this[0].toInt() xor 1).toByte() })
        try { ModelInstaller.install(source, dir, entry); fail() } catch (_: IOException) { }
        assertTrue(source.exists()); assertFalse(java.io.File(dir, entry.fileName).exists())
        assertTrue(dir.listFiles().isNullOrEmpty())
    }
    @Test fun truncatedDownloadNeverPublishes() = runCase { source, dir, entry ->
        source.writeText("partial")
        try { ModelInstaller.install(source, dir, entry); fail() } catch (_: IOException) { }
        assertFalse(dir.exists())
    }
    @Test fun existingUserModelIsNotOverwritten() = runCase { source, dir, entry ->
        dir.mkdirs(); val existing = java.io.File(dir, entry.fileName).apply { writeText("user file") }
        try { ModelInstaller.install(source, dir, entry); fail() } catch (_: IOException) { }
        assertEquals("user file", existing.readText()); assertTrue(source.exists())
    }
    @Test fun cancelledVerificationNeverPublishes() = runCase { source, dir, entry ->
        try { ModelInstaller.install(source, dir, entry, { true }); fail() } catch (_: InterruptedException) { }
        assertFalse(dir.exists())
    }
    @Test fun cancellationAtPublishBoundaryLeavesNoModel() = runCase { source, dir, entry ->
        try {
            ModelInstaller.install(source, dir, entry, publish = { throw InterruptedException() })
            fail()
        } catch (_: InterruptedException) { }
        assertTrue(source.exists()); assertFalse(java.io.File(dir, entry.fileName).exists())
        assertTrue(dir.listFiles().isNullOrEmpty())
    }
    @Test fun traversalRejected() = runCase { source, dir, entry ->
        try { ModelInstaller.install(source, dir, entry.copy(fileName = "../escape.litertlm")); fail() } catch (_: IllegalArgumentException) { }
        assertFalse(dir.exists())
    }
    @Test fun catalogueIsPinnedPublicHttpsAndHasUniqueFiles() {
        assertEquals(ModelCatalog.entries.size, ModelCatalog.entries.map { it.fileName }.distinct().size)
        ModelCatalog.entries.forEach {
            assertTrue(it.url.startsWith("https://huggingface.co/litert-community/"))
            assertTrue(it.revision.matches(Regex("[a-f0-9]{40}")))
            assertTrue(it.sha256.matches(Regex("[a-f0-9]{64}")))
            assertTrue(it.bytes > 0)
        }
    }
}
