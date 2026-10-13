package com.dataeater.app.security

import org.json.JSONObject
import com.dataeater.app.data.DatabaseIdentity
import java.time.LocalDate

/**
 * Everything that can be checked about an Unlock Code *before* the phone's
 * private key is touched.
 *
 * WHY THIS IS A SEPARATE FILE
 * ---------------------------
 * The dangerous part of unlocking is the private key: it lives in
 * AndroidKeyStore, cannot be exported, and cannot be reached from a plain
 * JVM test. So everything around it is pulled out here, where it *can* be
 * tested on the computer in milliseconds.
 *
 * The order of the checks is deliberate and is the whole security property:
 *
 *     1. is it a licence at all?          cheap, safe
 *     2. is it one THIS creator signed?    proves who issued it
 *     3. has it expired?                   the signed expiry, not a guess
 *     4. is it for THIS database?          stops a code for one manual
 *                                         unlocking another
 *     5. only now, touch the private key
 *
 * Steps 1–4 all happen with no secret involved, so a malformed or hostile
 * code can never reach the crypto at all.
 *
 * A note on what is NOT here: no licence is ever written to a log, and none
 * is ever put on the clipboard. An Unlock Code contains the wrapped content
 * key, so unlike a Request Code it is a secret.
 */
object UnlockCode {

    /** Mirrors `packaging.LICENCE_FORMAT` / `LICENCE_VERSION`. */
    const val FORMAT = "dataeater-licence"
    const val VERSION = 1
    const val MAX_CODE_BYTES = 64 * 1024


    /** Thrown for anything the user needs to be told about. */
    class Refused(message: String) : Exception(message)

    /**
     * The outcome of checking a code. Either it is sound, or there is a
     * specific reason it is not.
     */
    sealed class Checked {
        /** Every check passed. The licence may now be used to recover the key. */
        data class Accepted(val licence: JSONObject) : Checked()

        /** Refused, with a message fit to show the user. */
        data class Rejected(val reason: String) : Checked()
    }

    /**
     * Turns pasted text into a licence object.
     *
     * Deliberately forgiving about whitespace and line breaks: an Unlock Code
     * is 500 characters on one line, and it is almost always pasted from an
     * email or a chat app that wrapped it. Rejecting it for that would be a
     * support call every single time.
     *
     * @throws Refused if the text is empty, is not base64, or is not a licence
     */
    fun parse(text: String): JSONObject {
        if (text.length > MAX_CODE_BYTES * 2) throw Refused("This Unlock Code is too large.")
        val cleaned = text.filterNot { it.isWhitespace() }
        if (cleaned.length > MAX_CODE_BYTES) throw Refused("This Unlock Code is too large.")
        if (cleaned.isEmpty()) {
            throw Refused("No Unlock Code was entered.")
        }

        val decoded = try {
            java.util.Base64.getDecoder().decode(cleaned)
        } catch (problem: IllegalArgumentException) {
            throw Refused(
                "That is not a DataEater Unlock Code. Check that you copied " +
                    "all of it, including the beginning and the end."
            )
        }

        val licence = try {
            val json = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(decoded)).toString()
            com.dataeater.app.data.JsonSafety.check(json)
            JSONObject(json)
        } catch (problem: Exception) {
            throw Refused("The Unlock Code could not be read.")
        }

        if (licence.optString("format") != FORMAT) {
            throw Refused("This is not a DataEater Unlock Code.")
        }
        if (licence.optInt("version", -1) != VERSION) {
            throw Refused(
                "This Unlock Code is version ${licence.optInt("version", -1)}; " +
                    "this app understands version $VERSION. Ask the creator " +
                    "for a current one."
            )
        }
        if (licence.optJSONObject("wrap") == null) {
            throw Refused("This Unlock Code has no key inside.")
        }
        return licence
    }

    /**
     * Runs every check that does not need the private key.
     *
     * @param today passed in rather than read from the clock, so expiry can
     *   be tested without waiting for a date.
     * @param expectedDatabaseId the manifest's `database_id`, so a code for
     *   one manual cannot be used to open another
     */
    fun check(
        licence: JSONObject,
        creatorPublicKeyBase64: String,
        expectedDatabaseId: String,
        today: LocalDate = LocalDate.now(),
        expectedDatabaseFingerprint: String? = null,
    ): Checked {
        // The signature must be checked BEFORE the expiry. An unsigned code
        // claiming to be expired is not worth reading the date from; an
        // expired code is still a real code and still deserves a clear reason.
        if (creatorPublicKeyBase64.isBlank()) {
            return Checked.Rejected(
                "This database does not say who issued it, so an Unlock Code " +
                    "cannot be trusted. Ask the creator for a database built " +
                    "with --creator-public-key."
            )
        }

        try {
            DeviceKeys.verifySignatureBytes(licence, creatorPublicKeyBase64)
        } catch (problem: DeviceKeys.LicenceException) {
            return Checked.Rejected(problem.message ?: "The Unlock Code was refused.")
        }

        // Only now is the expiry worth reading: we know the creator signed it.
        if (DeviceKeys.isExpiredOn(licence, today)) {
            // The raw date, not expiryText(). expiryText() says "until ..." for
            // a code that is still valid, which reads as nonsense in a sentence
            // about one that has already run out.
            return Checked.Rejected(
                "This Unlock Code expired on ${licence.optString("expiry").trim()}. " +
                    "Ask the creator for a new one."
            )
        }

        val codeDatabaseId = licence.opt("database_id")
        if (!DatabaseIdentity.validId(expectedDatabaseId) || !DatabaseIdentity.validId(codeDatabaseId)) {
            return Checked.Rejected("This Unlock Code or database has a missing or ambiguous identifier. Ask the creator to correct it.")
        }
        if (codeDatabaseId != expectedDatabaseId) {
            return Checked.Rejected("This Unlock Code is for a different database ($codeDatabaseId), not for this one.")
        }
        if (licence.has("database_fingerprint")) {
            val binding = licence.opt("database_fingerprint") as? String
            if (binding == null || !Regex("sha256:[0-9a-f]{64}").matches(binding) || binding != expectedDatabaseFingerprint) {
                return Checked.Rejected("This Unlock Code is for a different encrypted database file. Ask the creator for a matching code.")
            }
        }
        if (licence.opt("format") != FORMAT || licence.opt("version") != VERSION || licence.optJSONObject("wrap") == null) {
            return Checked.Rejected("This is not a supported DataEater Unlock Code.")
        }

        return Checked.Accepted(licence)
    }

    /**
     * The one place the licence meets the phone's private key.
     *
     * Kept separate because this is the only part that cannot be unit tested:
     * the private key cannot be read out of AndroidKeyStore by anything,
     * including this app.
     *
     * @throws DeviceKeys.LicenceException when the code was not made for this
     *   phone, which is a completely normal situation rather than an error to
     *   explain at length
     */
    fun recoverContentKey(licence: JSONObject): ByteArray =
        DeviceKeys.recoverContentKey(licence)
}