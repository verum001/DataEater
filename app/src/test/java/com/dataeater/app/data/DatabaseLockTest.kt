package com.dataeater.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for telling a locked database from an open one.
 *
 * This is the decision the whole licence flow rests on. If a locked database
 * is mistaken for an open one, the user gets "the database is damaged". If an
 * open database is mistaken for a locked one, the user is told to go and buy
 * something they already own.
 *
 * Pure manifest text in, decision out. No phone needed.
 */
class DatabaseLockTest {

    /**
     * Builds a manifest that looks like the real ones the builder writes.
     *
     * Fields are assembled in a deliberately jumbled order, because a real
     * manifest's field order depends on how the builder was written, and
     * nothing here may depend on it.
     */
    private fun openManifest(
        encryption: String = "none",
        creator: String = "",
        contact: String = "",
        version: Int = 1,
    ): String {
        val extras = buildList {
            if (creator.isNotEmpty()) add("\"creator\": \"$creator\",")
            if (contact.isNotEmpty()) add("\"contact\": \"$contact\",")
        }.joinToString("\n  ")

        return """
            {
              "counts": { "chunks": 21, "documents": 2 },
              "name": "HF-4500 manual",
              "description": "Synthetic demo",
              "license": "CC0-1.0",
              "language": "en",
              "encryption": "$encryption",
              $extras
              "format_version": $version,
              "database_id": "demo",
              "format": "dataeater"
            }
        """.trimIndent()
    }

    // ------------------------------------------------------------------
    // Open databases
    // ------------------------------------------------------------------

    @Test
    fun encryptionNoneIsOpen() {
        val status = DatabaseLock.statusOf(openManifest())
        assertTrue("expected Open, got $status", status is DatabaseLock.Status.Open)
    }

    /**
     * A manifest written before the `encryption` field existed must not be
     * treated as locked. The field was added deliberately so old databases
     * would keep working, and this is what keeps that promise.
     */
    @Test
    fun aManifestWithNoEncryptionFieldIsOpen() {
        val noField = """
            {
              "format": "dataeater",
              "format_version": 1,
              "database_id": "old",
              "name": "Built by an older builder"
            }
        """.trimIndent()

        assertTrue(DatabaseLock.statusOf(noField) is DatabaseLock.Status.Open)
    }

    @Test
    fun anEmptyEncryptionFieldIsOpen() {
        assertTrue(
            DatabaseLock.statusOf(openManifest(encryption = "  "))
                is DatabaseLock.Status.Open,
        )
    }

    @Test
    fun theWordNoneInAnyCaseIsOpen() {
        for (spelling in listOf("none", "None", "NONE", "NoNe")) {
            assertTrue(
                "spelling '$spelling' should be open",
                DatabaseLock.statusOf(openManifest(encryption = spelling))
                    is DatabaseLock.Status.Open,
            )
        }
    }

    // ------------------------------------------------------------------
    // Locked databases
    // ------------------------------------------------------------------

    @Test
    fun aesIsReportedAsLocked() {
        val status = DatabaseLock.statusOf(openManifest(encryption = "aes-256-gcm"))

        assertTrue("expected Locked, got $status", status is DatabaseLock.Status.Locked)
        assertEquals("aes-256-gcm", (status as DatabaseLock.Status.Locked).scheme)
    }

    /** The name and description are public, so the app can still show them. */
    @Test
    fun aLockedDatabaseStillHasItsReadableDetails() {
        val status = DatabaseLock.statusOf(
            openManifest(encryption = "aes-256-gcm", creator = "A. Mechanic", contact = "a@b.c")
        ) as DatabaseLock.Status.Locked

        assertEquals("HF-4500 manual", status.manifest.name)
        assertEquals("Synthetic demo", status.manifest.description)
        assertTrue(status.hasCreator)
        assertTrue(status.hasContact)
    }

    /**
     * Some databases will be sold without contact details. The app must not
     * assume they exist, or it will print an empty email address.
     */
    @Test
    fun aLockedDatabaseMayHaveNoCreatorOrContact() {
        val status = DatabaseLock.statusOf(
            openManifest(encryption = "aes-256-gcm")
        ) as DatabaseLock.Status.Locked

        assertFalse(status.hasCreator)
        assertFalse(status.hasContact)
        assertEquals("", status.manifest.creator)
    }

    @Test
    fun anUnknownSchemeIsStillTreatedAsLocked() {
        // Not "aes-256-gcm", but encrypted by something. Refusing to open it
        // is the safe answer; we cannot know what it needs.
        val status = DatabaseLock.statusOf(openManifest(encryption = "rot13-from-1970"))
        assertTrue(status is DatabaseLock.Status.Locked)
    }

    // ------------------------------------------------------------------
    // Broken files: never reported as "locked"
    // ------------------------------------------------------------------

    /**
     * The important one. A damaged file must not be reported as locked,
     * because that sends the user off to buy a licence they do not need.
     */
    @Test
    fun aDamagedFileIsUnreadableNotLocked() {
        val status = DatabaseLock.statusOf("this is not json at all {{{")

        assertTrue("expected Unreadable, got $status", status is DatabaseLock.Status.Unreadable)
    }

    @Test
    fun aFileThatIsNotADataeaterDatabaseIsUnreadable() {
        val status = DatabaseLock.statusOf("""{"format":"something-else","version":1}""")

        assertTrue(status is DatabaseLock.Status.Unreadable)
        assertTrue(
            (status as DatabaseLock.Status.Unreadable).reason.contains("not a DataEater database"),
        )
    }

    /**
     * A future format must be refused, not guessed at. Reporting it as
     * "locked" would be actively misleading.
     */
    @Test
    fun aFutureFormatVersionIsUnreadableNotLocked() {
        val status = DatabaseLock.statusOf(
            openManifest(encryption = "aes-256-gcm", version = 99)
        )

        assertTrue("expected Unreadable, got $status", status is DatabaseLock.Status.Unreadable)
        assertTrue(
            (status as DatabaseLock.Status.Unreadable).reason.contains("version 99"),
        )
    }

    @Test
    fun aManifestWithNoVersionIsUnreadable() {
        val status = DatabaseLock.statusOf("""{"format":"dataeater","name":"No version"}""")
        assertTrue(status is DatabaseLock.Status.Unreadable)
    }

    @Test
    fun anEmptyManifestIsUnreadable() {
        assertTrue(DatabaseLock.statusOf("") is DatabaseLock.Status.Unreadable)
    }

    // ------------------------------------------------------------------
    // Real files
    // ------------------------------------------------------------------

    /** A file that is not there must be a result, never an exception. */
    @Test
    fun aMissingFileIsUnreadable() {
        val status = DatabaseLock.inspect(java.io.File("/tmp/definitely-not-here.dataeater"))

        assertTrue(status is DatabaseLock.Status.Unreadable)
        assertTrue(
            (status as DatabaseLock.Status.Unreadable).reason.isNotBlank(),
        )
    }

    @Test
    fun aFileOfNonsenseIsUnreadableRatherThanCrashing() {
        val junk = java.io.File.createTempFile("dataeater-test", ".dataeater")
        try {
            junk.writeBytes("this is not a ZIP archive".toByteArray())
            assertTrue(DatabaseLock.inspect(junk) is DatabaseLock.Status.Unreadable)
        } finally {
            junk.delete()
        }
    }

    /** The real bundled demo must never be reported as locked. */
    @Test
    fun theBundledDemoIsOpen() {
        val status = DatabaseLock.inspect(java.io.File("../dataeater/databases/demo.dataeater"))

        assertTrue("expected Open, got $status", status is DatabaseLock.Status.Open)
        assertEquals("demo", (status as DatabaseLock.Status.Open).manifest.databaseId)
    }
}