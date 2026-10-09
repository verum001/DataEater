package com.dataeater.app.builder

import com.dataeater.app.security.DeviceKeys
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object BuilderCrypto {
    private val random = SecureRandom()
    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String) = Base64.getDecoder().decode(text.filterNot(Char::isWhitespace))
    private fun <T> software(operation: (java.security.Provider) -> T): T {
        var last: Exception? = null
        for (provider in java.security.Security.getProviders().filterNot { it.name.startsWith("AndroidKeyStore") }) {
            try { return operation(provider) } catch (error: Exception) { last = error }
        }
        throw last ?: IllegalStateException("This phone does not provide the required cryptography.")
    }
    private fun pair() = software { provider ->
        KeyPairGenerator.getInstance("EC", provider).apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    }
    private fun publicKey(text: String): ECPublicKey {
        val encoded = decode(text)
        val key = try { software { provider -> KeyFactory.getInstance("EC", provider).generatePublic(X509EncodedKeySpec(encoded)) } }
            catch (error: Exception) { throw IllegalArgumentException("Invalid P-256 public key or device request.", error) }
        require(key is ECPublicKey) { "The key must use P-256." }
        val expected = pair().public as ECPublicKey
        require(key.params.order == expected.params.order && key.params.curve == expected.params.curve && key.params.generator == expected.params.generator) { "The key must use P-256." }
        return key
    }
    fun createKey(): JSONObject {
        val pair = pair()
        return JSONObject().put("format", "dataeater-creator-key").put("version", 1).put("algorithm", "ecdsa-p256-sha256")
            .put("private_key", b64(pair.private.encoded)).put("public_key", b64(pair.public.encoded))
            .put("warning", "Keep this private signing key backed up. Never send it to customers or publish it.")
    }
    fun validateKey(record: JSONObject): Pair<ECPrivateKey, ECPublicKey> {
        require(record.optString("format") == "dataeater-creator-key" && record.optInt("version") == 1) { "Not a DataEater signing key." }
        val privateKey = software { provider -> KeyFactory.getInstance("EC", provider).generatePrivate(PKCS8EncodedKeySpec(decode(record.getString("private_key")))) } as? ECPrivateKey ?: error("Not an EC signing key")
        val public = publicKey(record.getString("public_key"))
        require(privateKey.params.order == public.params.order) { "The signing key must use P-256." }
        val check = "DataEater key pair check".toByteArray()
        val signed = Signature.getInstance("SHA256withECDSA").run { initSign(privateKey); update(check); sign() }
        require(Signature.getInstance("SHA256withECDSA").run { initVerify(public); update(check); verify(signed) }) { "The private and public keys do not match." }
        return privateKey to public
    }
    fun secretKey(record: JSONObject): ByteArray {
        require(record.optString("format") == "dataeater-secret" && record.optInt("version") == 1) { "Not a DataEater content key." }
        return decode(record.getString("content_key")).also { require(it.size == 32) { "The content key must be 32 bytes." } }
    }
    fun gcmEncrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); doFinal(plain)
    }
    fun encrypt(input: File, output: File, creator: String, contact: String, signingPublic: String,
        existingSecret: JSONObject? = null, replace: Boolean = false): JSONObject {
        require(signingPublic.isNotBlank()) { "Create or import a signing key first." }; publicKey(signingPublic)
        BuilderCore.inspect(input)
        val entries = BuilderCore.entries(input); val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        require(manifest.optString("encryption", "none") == "none") { "This database is already locked." }
        val key = if (existingSecret != null && !replace) secretKey(existingSecret) else ByteArray(32).also(random::nextBytes)
        val envelope = JSONObject().put("format", "dataeater-payload").put("version", 1)
            .put("sources", entries.getValue("sources.json").toString(Charsets.UTF_8)).put("chunks", entries.getValue("chunks.jsonl").toString(Charsets.UTF_8)).toString().toByteArray()
        val nonce = ByteArray(12).also(random::nextBytes); val ciphertext = gcmEncrypt(key, nonce, envelope)
        require(ciphertext.size <= 32 * 1024 * 1024) { "The encrypted database is too large to open safely." }
        manifest.put("encryption", "aes-256-gcm").put("payload_nonce", b64(nonce)).put("creator", creator).put("contact", contact)
            .put("creator_public_key", signingPublic).put("files", JSONObject().put("payload.enc", JSONObject().put("sha256", "sha256:" + BuilderCore.sha(ciphertext)).put("bytes", ciphertext.size)))
        BuilderCore.writeZip(output, linkedMapOf("manifest.json" to (manifest.toString(2) + "\n").toByteArray(), "payload.enc" to ciphertext))
        BuilderCore.inspect(output)
        return JSONObject().put("format", "dataeater-secret").put("version", 1).put("database", output.name).put("content_key", b64(key))
            .put("warning", "Keep this key private and backed up. It decrypts the database.")
    }
    fun expiry(text: String, today: LocalDate = LocalDate.now()): String? {
        val value = text.trim().lowercase()
        if (value.isBlank() || value == "never") return null
        val relative = Regex("([1-9][0-9]*)([dy])").matchEntire(value)
        return if (relative != null) today.plusDays(Math.multiplyExact(relative.groupValues[1].toLong(), if (relative.groupValues[2] == "y") 365L else 1L)).toString()
            else LocalDate.parse(value).toString()
    }
    fun licence(database: File, secret: JSONObject, signingKey: JSONObject, request: String, until: String): String {
        BuilderCore.inspect(database)
        val entries = BuilderCore.entries(database); val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        require(manifest.optString("encryption") == "aes-256-gcm") { "This database is not locked and needs no access code." }
        val key = secretKey(secret)
        // Prove that both supplied keys actually belong to this database before issuing a code.
        val payload = DeviceKeys.gcmDecrypt(key, decode(manifest.getString("payload_nonce")), entries.getValue("payload.enc"))
        require(JSONObject(payload.toString(Charsets.UTF_8)).optString("format") == "dataeater-payload") { "The content key does not belong to this database." }
        val (private, public) = validateKey(signingKey)
        require(public.encoded.contentEquals(publicKey(manifest.getString("creator_public_key")).encoded)) { "This signing key does not match the database's public key." }
        val device = publicKey(request)
        val ephemeral = pair()
        val shared = KeyAgreement.getInstance("ECDH").run { init(ephemeral.private); doPhase(device, true); generateSecret() }
        val wrapping = DeviceKeys.hkdf(shared, ByteArray(16), "DataEater licence v1".toByteArray(), 32)
        val nonce = ByteArray(12).also(random::nextBytes)
        val wrap = JSONObject().put("format", "dataeater-wrap").put("version", 1).put("ephemeral_public_key", b64(ephemeral.public.encoded))
            .put("nonce", b64(nonce)).put("wrapped_key", b64(gcmEncrypt(wrapping, nonce, key)))
        val licence = JSONObject().put("format", "dataeater-licence").put("version", 1).put("database_id", manifest.getString("database_id"))
            .put("expiry", expiry(until) ?: JSONObject.NULL).put("wrap", wrap).put("signed_by", b64(public.encoded))
        val signature = Signature.getInstance("SHA256withECDSA").run { initSign(private); update(DeviceKeys.signingBytes(licence)); sign() }
        licence.put("signature", b64(signature))
        DeviceKeys.verifySignatureBytes(licence, b64(public.encoded))
        return b64(licence.toString().toByteArray()) + "\n"
    }
}
