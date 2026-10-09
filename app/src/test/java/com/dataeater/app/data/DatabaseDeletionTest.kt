package com.dataeater.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class DatabaseDeletionTest {
    private fun inFolder(check: (File) -> Unit) {
        val folder = Files.createTempDirectory("database-delete-").toFile()
        try { check(folder) } finally { folder.deleteRecursively() }
    }

    @Test
    fun deletesOnlyTheChosenDatabase() = inFolder { folder ->
        val chosen = File(folder, "chosen.dataeater").apply { writeText("test") }
        val other = File(folder, "other.dataeater").apply { writeText("keep") }
        DatabaseDeletion.delete(chosen, folder)
        assertFalse(chosen.exists())
        assertTrue(other.exists())
    }

    @Test
    fun refusesNonDatabaseFilesAndDirectories() = inFolder { folder ->
        val database = File(folder, "keep.litertlm").apply { writeText("keep") }
        val directory = File(folder, "directory.dataeater").apply { mkdir() }
        for (file in listOf(database, directory)) {
            assertThrows(IOException::class.java) { DatabaseDeletion.delete(file, folder) }
            assertTrue(file.exists())
        }
    }

    @Test
    fun refusesFilesOutsideTheDatabaseFolder() = inFolder { folder ->
        val databases = File(folder, "databases").apply { mkdir() }
        val outside = File(folder, "outside.dataeater").apply { writeText("keep") }
        assertThrows(IOException::class.java) { DatabaseDeletion.delete(outside, databases) }
        assertTrue(outside.exists())
    }

    @Test
    fun refusesSymlinksPointingOutsideTheDatabaseFolder() = inFolder { folder ->
        val databases = File(folder, "databases").apply { mkdir() }
        val outside = File(folder, "outside.dataeater").apply { writeText("keep") }
        val link = File(databases, "linked.dataeater")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertThrows(IOException::class.java) { DatabaseDeletion.delete(link, databases) }
        assertTrue(outside.exists())
        assertTrue(link.exists())
    }

    @Test
    fun reportsAFileThatDisappearedRatherThanClaimingSuccess() = inFolder { folder ->
        assertThrows(IOException::class.java) {
            DatabaseDeletion.delete(File(folder, "missing.dataeater"), folder)
        }
    }
}
