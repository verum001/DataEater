package com.dataeater.app.data

import com.dataeater.app.security.DeviceKeys
import org.json.JSONObject
import java.security.MessageDigest

/** V1 keeps readable metadata unsigned. This identity binds new licences to encrypted bytes. */
object DatabaseIdentity {
    fun validId(value: Any?): Boolean = value is String && value.isNotBlank() && value.length <= 256 &&
        Charsets.UTF_8.newEncoder().canEncode(value) && value == value.trim() && value != "." && value != ".." && value.none { it.isISOControl() || it == '/' || it == '\\' }

    fun requireId(value: Any?): String {
        if (!validId(value)) throw DatabaseReader.DataEaterException("The database identifier is missing or ambiguous. Ask the creator for a corrected database.")
        return value as String
    }

    fun fingerprint(manifest: JSONObject): String {
        val id = requireId(manifest.opt("database_id"))
        require(manifest.opt("format") == "dataeater" && manifest.opt("format_version") == 1 && manifest.opt("encryption") == "aes-256-gcm") { "Unsupported encrypted database format." }
        val hash = manifest.getJSONObject("files").getJSONObject("payload.enc").get("sha256") as? String
        require(hash != null && Regex("sha256:[0-9a-f]{64}").matches(hash)) { "Missing encrypted payload fingerprint." }
        val nonceText = manifest.opt("payload_nonce") as? String ?: throw DatabaseReader.DataEaterException("Invalid nonce field."); require(nonceText.length <= 128) { "Invalid nonce encoding." }
        val nonce = DeviceKeys.decodeBase64(nonceText); require(nonce.size == 12) { "Invalid database nonce." }
        val public = manifest.opt("creator_public_key") as? String ?: ""
        require(public.length <= 2048) { "Invalid creator public key encoding." }
        val canonical = JSONObject().put("database_id", id).put("format_version", 1).put("encryption", "aes-256-gcm")
            .put("creator_public_key", DeviceKeys.encodeBase64(DeviceKeys.decodeBase64(public)))
            .put("payload_nonce", DeviceKeys.encodeBase64(nonce)).put("payload_sha256", hash)
        return "sha256:" + MessageDigest.getInstance("SHA-256").digest(DeviceKeys.signingBytes(canonical)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
