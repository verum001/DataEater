package com.dataeater.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ModelDeletionTest {
    private fun inFolder(check: (File) -> Unit) {
        val folder = Files.createTempDirectory("model-delete-").toFile()
        try { check(folder) } finally { folder.deleteRecursively() }
    }

    @Test
    fun deletesOnlyTheChosenModel() = inFolder { folder ->
        val chosen = File(folder, "chosen.litertlm").apply { writeText("test") }
        val other = File(folder, "other.litertlm").apply { writeText("keep") }
        ModelDeletion.delete(chosen, folder)
        assertFalse(chosen.exists())
        assertTrue(other.exists())
    }

    @Test
    fun refusesNonModelFilesAndDirectories() = inFolder { folder ->
        val database = File(folder, "keep.dataeater").apply { writeText("keep") }
        val directory = File(folder, "keep.litertlm").apply { mkdir() }
        for (file in listOf(database, directory)) {
            assertThrows(IOException::class.java) { ModelDeletion.delete(file, folder) }
            assertTrue(file.exists())
        }
    }

    @Test
    fun refusesFilesOutsideTheModelFolder() = inFolder { folder ->
        val models = File(folder, "models").apply { mkdir() }
        val outside = File(folder, "outside.litertlm").apply { writeText("keep") }
        assertThrows(IOException::class.java) { ModelDeletion.delete(outside, models) }
        assertTrue(outside.exists())
    }

    @Test
    fun refusesSymlinksPointingOutsideTheModelFolder() = inFolder { folder ->
        val models = File(folder, "models").apply { mkdir() }
        val outside = File(folder, "outside.litertlm").apply { writeText("keep") }
        val link = File(models, "linked.litertlm")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertThrows(IOException::class.java) { ModelDeletion.delete(link, models) }
        assertTrue(outside.exists())
        assertTrue(link.exists())
    }

    @Test
    fun reportsAFileThatDisappearedRatherThanClaimingSuccess() = inFolder { folder ->
        assertThrows(IOException::class.java) {
            ModelDeletion.delete(File(folder, "missing.litertlm"), folder)
        }
    }
}
