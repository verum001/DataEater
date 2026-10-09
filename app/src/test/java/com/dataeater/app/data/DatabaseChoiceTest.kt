package com.dataeater.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Restoring a missing choice must never silently open another database. */
class DatabaseChoiceTest {

    private val present = listOf("FH_EX165W", "Kobelco", "paid-manual")

    // ------------------------------------------------------------------
    // First run
    // ------------------------------------------------------------------

    @Test
    fun withNothingRememberedNoDatabaseIsSelected() {
        assertEquals(
            "",
            DatabaseChoice.restore(null, present),
        )
    }

    @Test
    fun aBlankNameIsTreatedAsNothingRemembered() {
        for (blank in listOf("", "   ", "\n", "\t ")) {
            assertEquals(
                "blank should leave no database selected",
                "",
                DatabaseChoice.restore(blank, present),
            )
        }
    }

    // ------------------------------------------------------------------
    // A remembered choice that is still there
    // ------------------------------------------------------------------

    @Test
    fun aRememberedDatabaseIsReopened() {
        assertEquals(
            "Kobelco",
            DatabaseChoice.restore("Kobelco", present),
        )
    }

    /** Existing installs may still remember the removed built-in database. */
    @Test
    fun aLegacyDemoChoiceRestoresNoSelection() {
        assertEquals(
            "",
            DatabaseChoice.restore("bundled-demo", present),
        )
    }

    /** Surrounding whitespace must not lose the user's database. */
    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(
            "Kobelco",
            DatabaseChoice.restore("  Kobelco \n", present),
        )
    }

    // ------------------------------------------------------------------
    // A remembered choice that is no longer there
    // ------------------------------------------------------------------

    /**
     * The file was deleted: no database is selected and no fallback is opened.
     */
    @Test
    fun aDeletedDatabaseRestoresNoSelection() {
        assertEquals(
            "",
            DatabaseChoice.restore("DeletedManual", present),
        )
    }

    /** The file was renamed. Same reason to fall back. */
    @Test
    fun aRenamedDatabaseRestoresNoSelection() {
        assertEquals(
            "",
            DatabaseChoice.restore("old-name", listOf("new-name")),
        )
    }

    /** Nothing on the phone at all, and something remembered. */
    @Test
    fun withNoFilesAtAllItRestoresNoSelection() {
        assertEquals(
            "",
            DatabaseChoice.restore("Kobelco", emptyList()),
        )
    }

    /**
     * A name is only honoured on an exact match. "Kob" must not be treated as
     * "Kobelco", or the app could open a different database than the user
     * chose.
     *
     * Case matters, because filenames on the phone's storage are case
     * sensitive: a file really named `kobelco` is a different file. Trailing
     * whitespace is deliberately *not* in this list, because it is trimmed and
     * covered by `surroundingWhitespaceIsIgnored`.
     */
    @Test
    fun onlyAnExactNameIsRestored() {
        for (wrong in listOf("Kob", "Kobelco2", "kobelco", "KOBELCO", "Kobelc0")) {
            assertEquals(
                "'$wrong' should not match Kobelco",
                "",
                DatabaseChoice.restore(wrong, listOf("Kobelco")),
            )
        }
    }

    // ------------------------------------------------------------------
    // The property that matters
    // ------------------------------------------------------------------

    /**
     * A nonempty result must be the remembered file, never an automatic fallback.
     */
    @Test
    fun aRestoredChoiceIsEitherEmptyOrAnExistingFile() {
        val remembered = listOf(
            null, "", "  ", "Kobelco", "DeletedManual", "bundled-demo", "random",
        )

        for (rememberedName in remembered) {
            val chosen = DatabaseChoice.restore(rememberedName, present)
            val openable = chosen == "" || chosen in present
            assertTrue(
                "'$rememberedName' produced '$chosen', which cannot be opened",
                openable,
            )
        }
    }

    private fun assertTrue(message: String, condition: Boolean) =
        org.junit.Assert.assertTrue(message, condition)
}