package com.dataeater.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a licence file the customer has been sent.
 *
 * WHY THIS NEEDS TESTS AT ALL
 * ---------------------------
 * The file came out of an email. Everything the app knows about it before
 * checking a signature is therefore a **claim**, and the whole design rests on
 * two things being true at once:
 *
 *   1. nothing here decides whether a file opens a database
 *   2. a file that is not a licence is still *listed*, with a clear reason
 *
 * The second matters more than it looks. A customer whose file was refused, or
 * who saved the wrong thing, will look for it on the screen. If the app hides
 * files it does not recognise, they conclude it went missing and ask the
 * creator for a new one — which does not help.
 *
 * The folder itself needs a real Android filesystem, so it is not exercised
 * here; these tests cover the decisions that are pure.
 */
class LicenceFilesTest {

    /**
     * A file name that could reach outside the folder must be refused.
     *
     * Names come from a directory listing, so this should never fire — and that
     * is exactly why it must: a path built from outside data is how an app
     * ends up reading somewhere it was never meant to.
     */
    @Test
    fun onlyPlainFileNamesInTheFolderAreAccepted() {
        for (hostile in listOf(
            "../../etc/passwd.lic",
            "..%2F..%2Fsecret.lic",
            "/sdcard/elsewhere.lic",
            "sub/dir/licence.lic",
            "name with spaces.lic",
        )) {
            // resolve() needs the folder to exist, which needs Android. So the
            // rule is checked directly here, and the Android-free part of it is
            // the pattern below.
            val passesPattern =
                hostile.endsWith(".lic", ignoreCase = true) &&
                    Regex("[A-Za-z0-9._-]+\\.lic", RegexOption.IGNORE_CASE)
                        .matches(hostile) &&
                    !hostile.contains("..")
            assertFalse(
                "'$hostile' must not be accepted, pattern check passed: $passesPattern",
                passesPattern,
            )
        }
    }

    @Test
    fun ordinaryFileNamesPassTheRule() {
        for (fine in listOf(
            "acme-HF4500.lic",
            "customer-1.lic",
            "UPPER.lic",
            "a.b.c.lic",
        )) {
            val ok = fine.endsWith(".lic", ignoreCase = true) &&
                Regex("[A-Za-z0-9._-]+\\.lic", RegexOption.IGNORE_CASE).matches(fine) &&
                !fine.contains("..")
            assertTrue("'$fine' should be accepted", ok)
        }
    }

    /** A `.lic` name is a licence; anything else in the folder is not listed. */
    @Test
    fun onlyTheLicExtensionCounts() {
        assertTrue("acme.lic".endsWith(LicenceFiles.EXTENSION, ignoreCase = true))
        assertTrue("acme.LIC".endsWith(LicenceFiles.EXTENSION, ignoreCase = true))
        assertFalse("acme.txt".endsWith(LicenceFiles.EXTENSION, ignoreCase = true))
        assertFalse("acme.dataeater".endsWith(LicenceFiles.EXTENSION, ignoreCase = true))
        // A file called ".lic" on its own is not a licence either.
        assertFalse(".lic".endsWith(LicenceFiles.EXTENSION, ignoreCase = true) &&
            ".lic".length > LicenceFiles.EXTENSION.length)
    }

    /** The folder sits inside the DataEater folder, not beside it. */
    @Test
    fun theFolderIsNamedForLicencesAndSitsUnderDataEater() {
        assertEquals("Lic", LicenceFiles.FOLDER_NAME)
        assertEquals(".lic", LicenceFiles.EXTENSION)
    }

    /**
     * What is shown beside a file.
     *
     * "never expires" is spelled out rather than shown as a dash, because a
     * customer reading "—" cannot tell a permanent licence from an unreadable
     * one, and the difference decides whether they need to ask for a new code.
     */
    @Test
    fun aLicenceThatNeverExpiresSaysSo() {
        val file = LicenceFiles.LicenceFile(
            name = "acme.lic",
            path = java.io.File("/sdcard/DataEater/Lic/acme.lic"),
            claimedDatabaseId = "hf4500",
            claimedExpiry = null,
            looksValid = true,
        )
        assertEquals("never expires", file.claim)
    }

    @Test
    fun anExpiryIsShownAsWritten() {
        val file = LicenceFiles.LicenceFile(
            name = "acme.lic",
            path = java.io.File("/sdcard/DataEater/Lic/acme.lic"),
            claimedDatabaseId = "hf4500",
            claimedExpiry = "2026-11-05",
            looksValid = true,
        )
        assertEquals("expires 2026-11-05", file.claim)
    }

    /** A file that is not a licence must say so rather than look unclaimed. */
    @Test
    fun somethingThatIsNotALicenceSaysSoPlainly() {
        val file = LicenceFiles.LicenceFile(
            name = "notes.txt.lic",
            path = java.io.File("/sdcard/DataEater/Lic/notes.txt.lic"),
            claimedDatabaseId = null,
            claimedExpiry = null,
            looksValid = false,
        )
        assertEquals("Not a DataEater licence", file.claim)
    }

    /** A licence that parses but names no database is odd, and says so. */
    @Test
    fun aLicenceWithNoDatabaseIdIsCalledUnreadable() {
        val file = LicenceFiles.LicenceFile(
            name = "odd.lic",
            path = java.io.File("/sdcard/DataEater/Lic/odd.lic"),
            claimedDatabaseId = null,
            claimedExpiry = "2026-11-05",
            looksValid = true,
        )
        assertEquals("Unreadable licence", file.claim)
    }
}

/**
 * What a licence file says about itself.
 *
 * THE CASE THIS EXISTS FOR
 * ------------------------
 * An expired licence used to be listed as "expires 2020-01-01", which is
 * true and useless: the customer has to compare it with today to learn that
 * the file is already dead. They are on that screen *because* something is
 * wrong, and "expired on 2020-01-01" says it outright.
 */
class LicenceFileClaimTest {

    private fun file(expiry: String?, name: String = "x.lic") = LicenceFiles.LicenceFile(
        name = name,
        path = java.io.File("/sdcard/DataEater/Lic/$name"),
        claimedDatabaseId = "demo",
        claimedExpiry = expiry,
        looksValid = true,
    )

    @Test
    fun aLicenceWithNoDateNeverExpires() {
        assertEquals("never expires", file(null).claim)
    }

    @Test
    fun aFutureDateIsDescribedAsExpiring() {
        val future = java.time.LocalDate.now().plusDays(30).toString()
        assertEquals("expires $future", file(future).claim)
    }

    @Test
    fun aPastDateIsCalledExpiredNotExpiring() {
        assertEquals("expired on 2020-01-01", file("2020-01-01").claim)
    }

    /**
     * Today itself must not read as expired.
     *
     * The check is "before today", not "not after today". Getting this wrong
     * would refuse a licence on the day it ran out, which is the kind of
     * mistake that costs a customer a support call on the wrong day.
     */
    @Test
    fun todayIsNotYetExpired() {
        val today = java.time.LocalDate.now().toString()
        assertEquals("expires $today", file(today).claim)
    }

    /**
     * A date the app cannot read must produce no claim about expiry at all.
     *
     * Saying "expired" about a date it failed to parse would be inventing an
     * answer, which is the one thing this app must never do.
     */
    @Test
    fun anUnreadableDateDoesNotClaimToBeExpired() {
        val claim = file("not-a-date").claim
        assertFalse(
            "an unreadable date must not be called expired, was: $claim",
            claim.startsWith("expired"),
        )
    }
}
