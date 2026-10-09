package com.dataeater.app.data

import org.json.JSONObject
import java.security.MessageDigest

/** Detects damage relative to the manifest; this does not authenticate a publisher. */
object DatabaseIntegrity {
    fun verify(manifestText: String, entryName: String, bytes: ByteArray) {
        val expected = JSONObject(manifestText)
            .optJSONObject("files")
            ?.optJSONObject(entryName)
            ?.opt("sha256") as? String
        if (expected == null || !Regex("sha256:[0-9a-f]{64}").matches(expected)) {
            throw DatabaseReader.IntegrityException(
                "This database has no valid SHA-256 fingerprint for '$entryName'. " +
                    "It has not been opened. Ask the creator for a complete database file."
            )
        }
        val actual = "sha256:" + MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (actual != expected) {
            throw DatabaseReader.IntegrityException(
                "The integrity check failed for '$entryName'. This database has been " +
                    "changed or damaged. It has not been opened. Get a fresh copy from the creator."
            )
        }
    }
}
