package com.dataeater.app.data

import com.dataeater.app.builder.BuilderCore
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DatabaseInstallerTest {
    private fun entry(file: File) = DatabaseCatalog.Entry("1", "Synthetic", "", "v1", "synthetic-v1.dataeater",
        "https://github.com/verum001/DataEater-Databases/releases/download/v1/synthetic.dataeater", file.length(), BuilderCore.sha(file.readBytes()))
    @Test fun validPackagePublishesOnlyAfterVerificationAndExistingFileIsPreserved() {
        val root = java.nio.file.Files.createTempDirectory("database-install").toFile()
        try {
            val source = File(root, "download")
            BuilderCore.writeDatabase(listOf(BuilderCore.Document("Synthetic", "source.txt", "", 1, 0,
                listOf(BuilderCore.Page(1, "Keep this complete warning: never open a hot coolant cap.")))), BuilderCore.Options("Synthetic"), source)
            val directory = File(root, "installed")
            val e = entry(source)
            DatabaseInstaller.install(source, directory, e)
            val published = File(directory, e.fileName); val original = published.readBytes()
            assertEquals(1, DatabaseReader.openFile(published).sources.size)
            val second = File(root, "again").apply { writeBytes(original) }
            try { DatabaseInstaller.install(second, directory, e); fail("Must not overwrite") } catch (_: Exception) { }
            assertArrayEquals(original, published.readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun matchingHashCannotPublishAnInvalidDatabase() {
        val root = java.nio.file.Files.createTempDirectory("database-bad").toFile()
        try {
            val source = File(root, "invalid").apply { writeText("Not a database") }
            val e = entry(source); val directory = File(root, "installed")
            try { DatabaseInstaller.install(source, directory, e); fail("Must reject invalid package") } catch (_: Exception) { }
            assertFalse(File(directory, e.fileName).exists())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
