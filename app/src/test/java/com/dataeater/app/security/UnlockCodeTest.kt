package com.dataeater.app.security

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Checking an Unlock Code before any secret is touched.
 *
 * WHY THESE MATTER
 * ----------------
 * An Unlock Code is what a customer pastes in after paying. Four things must
 * never happen, and each has a test here:
 *
 *   1. a code somebody edited (an expiry stretched out) must be refused
 *   2. an expired code must be refused, even though it is genuinely signed
 *   3. a code for one database must not open a different one
 *   4. a code signed by somebody else's key must not be honoured
 *
 * Every expected value below was produced by the Python builder:
 *
 *     tools/.venv/bin/python tools/tests/make_crypto_vectors.py
 *
 * That script is deterministic, so these strings never change unless the
 * builder's format genuinely changes — at which point they should.
 */
class UnlockCodeTest {

    /** A real Unlock Code, exactly as `encode_licence` writes it. */
    private val validUnlockCode =
                    "eyJkYXRhYmFzZV9pZCI6ImRlbW8tZGIiLCJleHBpcnkiOiIyMDI3LTAxLTMxIiwiZm9ybWF0IjoiZGF0YWVhdGVyLWxpY2VuY2UiLCJzaWduYXR1cmUiOiJNRVVDSUN6cGFpZFJCK0hSTlc4WWZaRXE2Y0krRlc3eis1OWEvejQ3cnJzenBqTEZBaUVBOWVqR3Q0NDFTTVUrZjBBYkJrYWJ3bWZZeDh2MEM4dW1OUUNKZHRkU1B4UT0iLCJzaWduZWRfYnkiOiJNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUVVYWRZQ0RPSmpxR3hnOHZYTlFwQW1RZU1idkhCNFk2WEROZG9NRFh5WG4wQkVGSW5FckMxcDgvd2dXaFVocGhLbE9hREh0ckVibk5nK3AyRFNucUJvUT09IiwidmVyc2lvbiI6MSwid3JhcCI6eyJlcGhlbWVyYWxfcHVibGljX2tleSI6IlFVSkQiLCJmb3JtYXQiOiJkYXRhZWF0ZXItd3JhcCIsIm5vbmNlIjoiUkVWRyIsInZlcnNpb24iOjEsIndyYXBwZWRfa2V5IjoiUjBoSiJ9fQ==\n"

    /** The same code as it realistically arrives: eight lines, not one. */
    private val wrappedUnlockCode =
                    "eyJkYXRhYmFzZV9pZCI6ImRlbW8tZGIiLCJleHBpcnkiOiIyMDI3LTAxLTMxIiwi\n" +
            "Zm9ybWF0IjoiZGF0YWVhdGVyLWxpY2VuY2UiLCJzaWduYXR1cmUiOiJNRVVDSUN6\n" +
            "cGFpZFJCK0hSTlc4WWZaRXE2Y0krRlc3eis1OWEvejQ3cnJzenBqTEZBaUVBOWVq\n" +
            "R3Q0NDFTTVUrZjBBYkJrYWJ3bWZZeDh2MEM4dW1OUUNKZHRkU1B4UT0iLCJzaWdu\n" +
            "ZWRfYnkiOiJNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUVVYWRZ\n" +
            "Q0RPSmpxR3hnOHZYTlFwQW1RZU1idkhCNFk2WEROZG9NRFh5WG4wQkVGSW5FckMx\n" +
            "cDgvd2dXaFVocGhLbE9hREh0ckVibk5nK3AyRFNucUJvUT09IiwidmVyc2lvbiI6\n" +
            "MSwid3JhcCI6eyJlcGhlbWVyYWxfcHVibGljX2tleSI6IlFVSkQiLCJmb3JtYXQi\n" +
            "OiJkYXRhZWF0ZXItd3JhcCIsIm5vbmNlIjoiUkVWRyIsInZlcnNpb24iOjEsIndy\n" +
            "YXBwZWRfa2V5IjoiUjBoSiJ9fQ==\n"

    /** Genuinely signed by the same key, but expired in 2020. */
    private val expiredUnlockCode =
                    "eyJkYXRhYmFzZV9pZCI6ImRlbW8iLCJleHBpcnkiOiIyMDIwLTAxLTAxIiwiZm9ybWF0IjoiZGF0YWVhdGVyLWxpY2VuY2UiLCJzaWduYXR1cmUiOiJNRVVDSVFDbkRxRDFTakRQaWdQYkhLZittVnF1emx5TUFoVkxpTHRGaUMxeXlwSjZYZ0lnY1hPM3pOYU5MV2dPQnBISExEMTlBMzFlcFpicjh2bGxHVmVZTU5hOEVHUT0iLCJzaWduZWRfYnkiOiJNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUVVYWRZQ0RPSmpxR3hnOHZYTlFwQW1RZU1idkhCNFk2WEROZG9NRFh5WG4wQkVGSW5FckMxcDgvd2dXaFVocGhLbE9hREh0ckVibk5nK3AyRFNucUJvUT09IiwidmVyc2lvbiI6MSwid3JhcCI6eyJlcGhlbWVyYWxfcHVibGljX2tleSI6IlFVSkQiLCJmb3JtYXQiOiJkYXRhZWF0ZXItd3JhcCIsIm5vbmNlIjoiUkVWRyIsInZlcnNpb24iOjEsIndyYXBwZWRfa2V5IjoiUjBoSiJ9fQ==\n"

    /**
     * Genuinely signed by the genuine creator, and genuinely issued for a
     * different database. It cannot be produced by editing a code, because the
     * signature check runs first and would catch that.
     */
    private val wrongDatabaseUnlockCode =
                    "eyJkYXRhYmFzZV9pZCI6ImEtY29tcGxldGVseS1kaWZmZXJlbnQtbWFudWFsIiwiZXhwaXJ5IjoiMjAyNy0wMS0zMSIsImZvcm1hdCI6ImRhdGFlYXRlci1saWNlbmNlIiwic2lnbmF0dXJlIjoiTUVRQ0lGUVd0QlpTL0Z3a05UMmtuaWg1SGNBYmdjc3lOMjZjR0RkdWVIdStWaDl5QWlCSzloY3ZNSnlrZjRjbUtuQXU4aUNPVm5sQkFub1ZLSjY5dEwwT1VNMGRMUT09Iiwic2lnbmVkX2J5IjoiTUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFVWFkWUNET0pqcUd4Zzh2WE5RcEFtUWVNYnZIQjRZNlhETmRvTURYeVhuMEJFRkluRXJDMXA4L3dnV2hVaHBoS2xPYURIdHJFYm5OZytwMkRTbnFCb1E9PSIsInZlcnNpb24iOjEsIndyYXAiOnsiZXBoZW1lcmFsX3B1YmxpY19rZXkiOiJRVUpEIiwiZm9ybWF0IjoiZGF0YWVhdGVyLXdyYXAiLCJub25jZSI6IlJFVkciLCJ2ZXJzaW9uIjoxLCJ3cmFwcGVkX2tleSI6IlIwaEoifX0=\n"

    private val creatorPublicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEUadYCDOJjqGxg8vXNQpAmQeMbvHB4Y6XDNdoMDXyXn0BEFInErC1p8/wgWhUhphKlOaDHtrEbnNg+p2DSnqBoQ=="

    /** A valid EC P-256 key that signed nothing. */
    private val attackerPublicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEC7vF6LyEvTPR084D/6yadH9MGZP92y7JOkEWqG8CKnfDwXGRVZpMKhqlfnm40Zd9oslZFy9HjjQeJwKNaf/7ew=="

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    @Test
    fun acceptsARealUnlockCode() {
        val licence = UnlockCode.parse(validUnlockCode)

        assertEquals("dataeater-licence", licence.getString("format"))
        assertEquals(1, licence.getInt("version"))
        assertEquals("demo-db", licence.getString("database_id"))
        assertEquals("2027-01-31", licence.getString("expiry"))
    }

    /**
     * A 500-character code pasted from an email is always wrapped. Refusing it
     * for that would mean a support call every single time.
     */
    @Test
    fun acceptsAnUnlockCodeThatArrivedWrappedOverLines() {
        assertEquals(
            UnlockCode.parse(validUnlockCode).getString("database_id"),
            UnlockCode.parse(wrappedUnlockCode).getString("database_id"),
        )
    }

    @Test
    fun refusesAnEmptyUnlockCode() {
        for (empty in listOf("", "   ", "\n\n  \t")) {
            val problem = assertThrows(UnlockCode.Refused::class.java) {
                UnlockCode.parse(empty)
            }
            assertTrue(
                "message should say nothing was entered, was: ${problem.message}",
                problem.message!!.contains("No Unlock Code"),
            )
        }
    }

    @Test
    fun refusesTextThatIsNotEvenJson() {
        val rubbish = java.util.Base64.getEncoder()
            .encodeToString("this is not a licence".toByteArray())

        val problem = assertThrows(UnlockCode.Refused::class.java) {
            UnlockCode.parse(rubbish)
        }
        assertTrue(
            "message was: ${problem.message}",
            problem.message!!.contains("could not be read"),
        )
    }

    /** Right shape, wrong format. Another product's code must be refused. */
    @Test
    fun refusesSomethingThatIsNotAnUnlockCode() {
        val notOurs = java.util.Base64.getEncoder().encodeToString(
            """{"format":"some-other-product","version":1}""".toByteArray()
        )

        val problem = assertThrows(UnlockCode.Refused::class.java) {
            UnlockCode.parse(notOurs)
        }
        assertTrue(problem.message!!.contains("not a DataEater Unlock Code"))
    }

    @Test
    fun refusesTextThatIsNotEvenBase64() {
        assertThrows(UnlockCode.Refused::class.java) {
            UnlockCode.parse("!!! this is not base64 at all !!!")
        }
    }

    @Test
    fun refusesALicenceWithNoKeyInside() {
        val json = JSONObject("""{"format":"dataeater-licence","version":1}""")
        val encoded = java.util.Base64.getEncoder()
            .encodeToString(json.toString().toByteArray())

        val problem = assertThrows(UnlockCode.Refused::class.java) {
            UnlockCode.parse(encoded)
        }
        assertTrue(problem.message!!.contains("no key inside"))
    }

    /** A licence from a future version must be refused, not guessed at. */
    @Test
    fun refusesAFutureLicenceVersion() {
        val future = java.util.Base64.getEncoder().encodeToString(
            """{"format":"dataeater-licence","version":99}""".toByteArray()
        )

        val problem = assertThrows(UnlockCode.Refused::class.java) {
            UnlockCode.parse(future)
        }
        assertTrue(problem.message!!.contains("version 99"))
    }

    // ------------------------------------------------------------------
    // The checks that decide whether a code is honoured
    // ------------------------------------------------------------------

    @Test
    fun acceptsACorrectUnlockCode() {
        val result = UnlockCode.check(
            UnlockCode.parse(validUnlockCode),
            creatorPublicKey,
            expectedDatabaseId = "demo-db",
            today = LocalDate.of(2026, 10, 6),
        )

        assertTrue(
            "should have been accepted, was $result",
            result is UnlockCode.Checked.Accepted,
        )
    }

    /** The most important refusal: somebody stretched the expiry out. */
    @Test
    fun refusesACodeWhoseExpiryWasEdited() {
        val tampered = JSONObject(UnlockCode.parse(validUnlockCode).toString())
        tampered.put("expiry", "2099-12-31")

        val result = UnlockCode.check(
            tampered,
            creatorPublicKey,
            expectedDatabaseId = "demo-db",
            today = LocalDate.of(2026, 10, 6),
        )

        assertTrue(
            "an edited expiry must be refused, was $result",
            result is UnlockCode.Checked.Rejected,
        )
        assertTrue(
            (result as UnlockCode.Checked.Rejected).reason
                .contains("changed after the creator signed")
        )
    }

    /**
     * This code is genuinely signed by the genuine creator. The only thing
     * wrong with it is the date, and it must still be refused.
     */
    @Test
    fun refusesAnExpiredCode() {
        val result = UnlockCode.check(
            UnlockCode.parse(expiredUnlockCode),
            creatorPublicKey,
            expectedDatabaseId = "demo-db",
            today = LocalDate.of(2026, 10, 6),
        )

        assertTrue(
            "an expired code must be refused, was $result",
            result is UnlockCode.Checked.Rejected,
        )
        assertTrue(
            (result as UnlockCode.Checked.Rejected).reason
                .contains("expired on 2020-01-01")
        )
    }

    /**
     * The signature is checked BEFORE the expiry. This test pins that order:
     * an unsigned code claiming to be expired must never be believed about its
     * own date.
     */
    @Test
    fun theSignatureIsCheckedBeforeTheDate() {
        val licence = JSONObject(UnlockCode.parse(validUnlockCode).toString())
        licence.put("expiry", "2020-01-01")

        val result = UnlockCode.check(
            licence,
            creatorPublicKey,
            "demo-db",
            LocalDate.of(2026, 10, 6),
        )

        assertTrue(result is UnlockCode.Checked.Rejected)
        val reason = (result as UnlockCode.Checked.Rejected).reason
        assertTrue(
            "the refusal should be about the signature, was: $reason",
            reason.contains("changed after the creator signed"),
        )
    }

    /** One manual's licence must not open a different manual. */
    @Test
    fun refusesACodeForADifferentDatabase() {
        val result = UnlockCode.check(
            UnlockCode.parse(wrongDatabaseUnlockCode),
            creatorPublicKey,
            expectedDatabaseId = "demo-db",
            today = LocalDate.of(2026, 10, 6),
        )

        assertTrue(
            "a code for another database must be refused, was $result",
            result is UnlockCode.Checked.Rejected,
        )
        assertTrue(
            (result as UnlockCode.Checked.Rejected).reason
                .contains("different database")
        )
    }

    /**
     * Without the creator's public key the app cannot tell a real code from a
     * forged one, so it must refuse rather than try and hope.
     */
    @Test
    fun refusesAnyCodeWhenTheDatabaseNamesNoCreator() {
        val result = UnlockCode.check(
            UnlockCode.parse(validUnlockCode),
            "",
            expectedDatabaseId = "demo-db",
            today = LocalDate.of(2026, 10, 6),
        )

        assertTrue(result is UnlockCode.Checked.Rejected)
        assertTrue(
            (result as UnlockCode.Checked.Rejected).reason
                .contains("does not say who issued it")
        )
    }

    /** A code signed by someone else's key must not be honoured. */
    @Test
    fun refusesACodeSignedByTheWrongCreator() {
        val result = UnlockCode.check(
            UnlockCode.parse(validUnlockCode),
            attackerPublicKey,
            expectedDatabaseId = "demo-db",
            today = LocalDate.of(2026, 10, 6),
        )

        assertTrue(
            "a forged code must be refused, was $result",
            result is UnlockCode.Checked.Rejected,
        )
    }

    // ------------------------------------------------------------------
    // Expiry, on its own
    // ------------------------------------------------------------------

    @Test
    fun aLicenceWithNoExpiryNeverExpires() {
        val licence = JSONObject("""{"expiry":null}""")
        assertEquals(false, DeviceKeys.isExpiredOn(licence, LocalDate.of(2030, 1, 1)))
    }

    /**
     * An unreadable date is treated as expired. Guessing "not expired" on a
     * date we cannot parse is the wrong way round to fail.
     */
    @Test
    fun anUnreadableDateCountsAsExpired() {
        for (bad in listOf("not-a-date", "31/01/2027", "2027-13-45")) {
            val licence = JSONObject("""{"expiry":"$bad"}""")
            assertEquals(
                "'$bad' should count as expired",
                true,
                DeviceKeys.isExpiredOn(licence, LocalDate.of(2026, 10, 6)),
            )
        }
    }

    @Test
    fun aFutureDateIsNotExpired() {
        val licence = JSONObject("""{"expiry":"2099-12-31"}""")
        assertEquals(false, DeviceKeys.isExpiredOn(licence, LocalDate.of(2026, 10, 6)))
    }
}