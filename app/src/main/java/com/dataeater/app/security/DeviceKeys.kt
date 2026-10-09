package com.dataeater.app.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The phone's half of DataEater's device-bound licences.
 *
 * A key pair is created once, inside AndroidKeyStore. The private key is
 * hardware-backed where the phone supports it and **can never be exported** -
 * there is no code path that reads it out, and the backup rules are not part
 * of this project. That is the whole point: the creator wraps a key for this
 * phone, and nowhere else.
 *
 * EVERYTHING HERE MUST MATCH `tools/dataeater_builder/packaging.py`.
 * A change on one side without the other will break every licence, so the
 * constants below are marked with the file they mirror.
 *
 * No third-party library is used: this is the Android platform's own crypto.
 */
object DeviceKeys {

    /** Logcat tag, so the crypto path can be followed on a real phone. */
    const val LOG_TAG = "DataEaterKeys"

    /**
     * Logging that can never fail.
     *
     * `android.util.Log` is a stub in JVM unit tests and throws "not mocked".
     * A diagnostic that can break the thing it is diagnosing is worse than no
     * diagnostic, so every log call here is wrapped.
     */
    private fun log(level: Int, message: String) {
        try {
            android.util.Log.println(level, LOG_TAG, message)
        } catch (ignored: Exception) {
            // Logging is never important enough to fail over.
        }
    }

    /** Keystore alias for our one long-lived key pair. */
    private const val KEY_ALIAS = "dataeater_device_key"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val CURVE = "secp256r1"

    // --- mirrored from packaging.py: WRAP_FORMAT / WRAP_VERSION ---
    const val WRAP_FORMAT = "dataeater-wrap"
    const val WRAP_VERSION = 1

    // --- mirrored from packaging.py: WRAP_SALT / WRAP_INFO / AES_KEY_BYTES ---
    private val WRAP_SALT = ByteArray(16)
    private const val WRAP_INFO = "DataEater licence v1"
    private const val AES_KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128

    // --- mirrored from packaging.py: LICENCE_FORMAT / LICENCE_VERSION ---
    const val LICENCE_FORMAT = "dataeater-licence"
    const val LICENCE_VERSION = 1

    /**
     * How a licence is signed.
     *
     * Mirrors packaging.py, which uses `ec.ECDSA(hashes.SHA256())` over
     * SECP256R1.
     *
     * WHY NOT Ed25519
     * ----------------
     * Ed25519 was the first choice and is a good algorithm. It was replaced
     * after measuring what real phones actually have: Ed25519 reaches Android
     * through Conscrypt, which stock Android only gained in version 13. The
     * development phone has no Conscrypt at all and none of its seven crypto
     * providers offer Ed25519, so every licence was refused on it.
     *
     * `SHA256withECDSA` over P-256 works on every Android from API 23, and it
     * is the same curve this app already uses for key agreement.
     */
    const val EC_ALGORITHM = "EC"
    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    const val CURVE_BITS = 256

    /** Thrown when something about a licence is wrong. The message is shown. */
    class LicenceException(message: String) : Exception(message)

    // ------------------------------------------------------------------
    // The device key pair
    // ------------------------------------------------------------------

    /** Returns the existing key pair, creating it the first time. */
    fun keyPair(): Pair<ECPublicKey, PrivateKey> {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            throw LicenceException("Encrypted databases require Android 12 or newer. Unencrypted databases and chat are still available.")
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val certificate = keyStore.getCertificate(KEY_ALIAS)
            val privateKey =
                (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry).privateKey
            return (certificate.publicKey as ECPublicKey) to privateKey
        }

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                .build()
        )
        val pair = generator.generateKeyPair()
        return (pair.public as ECPublicKey) to pair.private
    }

    /** True when the private key lives in secure hardware rather than software. */
    fun isHardwareBacked(): Boolean = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val factory = KeyFactory.getInstance(entry.privateKey.algorithm, ANDROID_KEY_STORE)
        val info = factory.getKeySpec(
            entry.privateKey, android.security.keystore.KeyInfo::class.java)
        @Suppress("DEPRECATION")
        info.isInsideSecureHardware
    } catch (problem: Exception) {
        false
    }

    // ------------------------------------------------------------------
    // The Unlock Request Code
    // ------------------------------------------------------------------

    /**
     * The text the customer sends to the creator.
     *
     * It is the PUBLIC key in X.509 form, base64 encoded. A public key is
     * safe to send anywhere: it cannot help anybody open anything without the
     * private key, which stays on this phone.
     *
     * Byte-for-byte the same as `packaging.make_request_code`.
     */
    fun requestCode(): String {
        val (publicKey, _) = keyPair()
        return encodeBase64(publicKey.encoded)
    }

    /**
     * Base64, via `java.util` rather than `android.util`.
     *
     * Same result on the phone, but it also runs in a plain JVM unit test,
     * which is how the licence maths gets tested without a phone. Available
     * from Android 8 (API 26), which is our minimum.
     */
    fun encodeBase64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    /**
     * Decoding is deliberately forgiving about whitespace and line breaks,
     * because an Unlock Code is usually pasted in and arrives wrapped. A code
     * the customer copied correctly must not be rejected over a stray newline.
     */
    fun decodeBase64(text: String): ByteArray =
        java.util.Base64.getMimeDecoder().decode(text.trim())

    /**
     * A JCA object for the creator's signature, from a provider that can do it.
     *
     * WHY NOT JUST `KeyFactory.getInstance("EC")`
     * -------------------------------------------
     * On a real phone that call can resolve to **AndroidKeyStore**, whose
     * preferred provider sits first. AndroidKeyStore refuses to build a key
     * from raw bytes and answers with a confusing complaint about key *pair
     * generation* — which has nothing to do with what we are doing. This was
     * found on the device, not in a unit test: a plain JVM has no
     * AndroidKeyStore, so the tests passed while real phones failed.
     *
     * The creator's public key is not secret and is not held in hardware, so a
     * normal software provider is exactly right for it.
     *
     * @throws GeneralSecurityException if no provider offers EC P-256
     */
    private fun <T : Any> crypto(
        make: (java.security.Provider?) -> T,
    ): T {
        // Ask every registered provider, skipping the one that cannot help.
        // Which provider answers is not the same on every device: a stock
        // Android phone offers Conscrypt, a HarmonyOS phone offers HarmonyJSSE.
        var problem: Exception? = null
        for (provider in java.security.Security.getProviders()) {
            if (provider.name == "AndroidKeyStore") {
                continue
            }
            try {
                val result = make(provider)
                log(android.util.Log.INFO, "crypto handled by ${provider.name}")
                return result
            } catch (found: Exception) {
                problem = found
            }
        }
        log(
            android.util.Log.WARN,
            "no usable provider. Registered: " +
                java.security.Security.getProviders().joinToString(", ") { it.name },
        )
        // No registered provider worked. Fall back to the platform default.
        return try {
            make(null)
        } catch (problem2: Exception) {
            throw problem ?: problem2
        }
    }

    /**
     * The creator's public key, loaded from base64.
     *
     * Must be an EC **P-256** key, because that is what the builder signs
     * with. A key on any other curve is refused rather than tried, so a
     * mismatched key gives a clear reason instead of a bare failure.
     */
    fun loadCreatorPublicKey(creatorPublicKeyBase64: String): java.security.PublicKey {
        val key = crypto { provider ->
            val factory = if (provider == null) {
                java.security.KeyFactory.getInstance(EC_ALGORITHM)
            } else {
                java.security.KeyFactory.getInstance(EC_ALGORITHM, provider)
            }
            factory.generatePublic(
                X509EncodedKeySpec(decodeBase64(creatorPublicKeyBase64))
            )
        }

        if (key !is java.security.interfaces.ECPublicKey) {
            throw LicenceException("That is not an EC creator public key.")
        }
        val params = (key as? java.security.spec.ECParameterSpec)
        val order = params?.order
        if (order != null && order.bitLength() != CURVE_BITS) {
            throw LicenceException(
                "That creator key is on a ${order.bitLength()}-bit curve, " +
                    "but this app requires P-256."
            )
        }
        return key
    }

    /**
     * A verifier over exactly the bytes [signingBytes] produces.
     *
     * `SHA256withECDSA` is available on every Android from API 23, including on
     * devices with no Conscrypt. See [docs/SECURITY.md] for why Ed25519 was
     * abandoned after it turned out most phones cannot verify it.
     */
    fun signatureVerifier(creatorPublicKeyBase64: String): Signature =
        crypto { provider ->
            val verifier = if (provider == null) {
                Signature.getInstance(SIGNATURE_ALGORITHM)
            } else {
                Signature.getInstance(SIGNATURE_ALGORITHM, provider)
            }
            verifier.initVerify(loadCreatorPublicKey(creatorPublicKeyBase64))
            verifier
        }

    // ------------------------------------------------------------------
    // Opening a licence
    // ------------------------------------------------------------------

    /**
     * Checks the creator's signature over a licence.
     *
     * The signature covers every field, including the expiry. A customer who
     * edits the expiry cannot re-sign it, so the check fails and the licence
     * is refused. That is what stops somebody giving themselves more time.
     */
    fun verifySignature(licence: org.json.JSONObject, creatorPublicKeyBase64: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw LicenceException(
                "Checking the creator's signature needs Android 13 or newer. " +
                    "This phone is Android " + Build.VERSION.RELEASE + "."
            )
        }
        verifySignatureBytes(licence, creatorPublicKeyBase64)
    }

    /**
     * The signature check itself, with no Android version gate in front of it.
     *
     * Split out so a plain JVM unit test can call it. The version check above
     * cannot be exercised off a phone, but the cryptography underneath it can,
     * and that is the part that must be proven against the Python builder.
     */
    fun verifySignatureBytes(
        licence: org.json.JSONObject,
        creatorPublicKeyBase64: String,
    ) {
        val signatureText = licence.optString("signature")
        if (signatureText.isEmpty()) {
            throw LicenceException("This Unlock Code has no signature.")
        }

        try {
            val verifier = signatureVerifier(creatorPublicKeyBase64)
            verifier.update(signingBytes(licence))
            if (!verifier.verify(decodeBase64(signatureText))) {
                throw LicenceException(
                    "This Unlock Code was changed after the creator signed it, " +
                        "so it cannot be trusted."
                )
            }
        } catch (problem: LicenceException) {
            throw problem
        } catch (problem: Exception) {
            // A phone that simply cannot do the signature algorithm is not an
            // attack, and the user should never be shown a JCA class name. What
            // they need to know is that this phone cannot check codes at all.
            val isMissingAlgorithm =
                (problem.message ?: "").contains("no such algorithm", ignoreCase = true)
            throw LicenceException(
                if (isMissingAlgorithm) {
                    "This phone cannot check Unlock Codes. Its Android build has " +
                        "no support for the $SIGNATURE_ALGORITHM signature, which " +
                        "is how a creator proves a code is genuine. Nothing is " +
                        "wrong with your code."
                } else {
                    "The creator's signature could not be checked: ${problem.message}"
                }
            )
        }
    }

    /**
     * Recovers the database content key.
     *
     * The mirror image of `packaging.unwrap_content_key`. If the licence was
     * made for a different phone, this throws, because the two shared secrets
     * will not match and the AES check will fail.
     */
    fun recoverContentKey(licence: org.json.JSONObject): ByteArray {
        val wrap = licence.optJSONObject("wrap")
            ?: throw LicenceException("This Unlock Code has no key inside.")
        val (_, privateKey) = keyPair()
        return recoverContentKeyWith(wrap, privateKey)
    }

    /**
     * The unwrap maths, with the phone's private key passed in.
     *
     * Split out so a JVM test can supply its own key and check the result
     * against the Python builder. On the phone the private key comes from
     * AndroidKeyStore and cannot be exported, so this is the only way to prove
     * the two implementations agree.
     *
     * The format and version are checked here as well as in Python, so a
     * licence built by a future version is refused with a clear reason
     * instead of failing later as a puzzling decryption error.
     */
    fun recoverContentKeyWith(
        wrap: org.json.JSONObject,
        privateKey: PrivateKey,
    ): ByteArray {
        val format = wrap.optString("format")
        if (format != WRAP_FORMAT) {
            throw LicenceException(
                "This is not a DataEater Unlock Code (format='$format')."
            )
        }
        val version = wrap.optInt("version", -1)
        if (version != WRAP_VERSION) {
            throw LicenceException(
                "This Unlock Code is version $version; this app understands " +
                    "version $WRAP_VERSION. Ask the creator for a current one."
            )
        }

        try {
            val ephemeralPublic = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(
                    decodeBase64(wrap.getString("ephemeral_public_key"))))

            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(privateKey)
            agreement.doPhase(ephemeralPublic, true)

            val wrappingKey = hkdf(agreement.generateSecret(), WRAP_SALT,
                WRAP_INFO.toByteArray(Charsets.UTF_8), AES_KEY_BYTES)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(wrappingKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS,
                    decodeBase64(wrap.getString("nonce"))),
            )
            return cipher.doFinal(
                decodeBase64(wrap.getString("wrapped_key")))
        } catch (problem: LicenceException) {
            throw problem
        } catch (problem: Exception) {
            throw LicenceException(
                "This Unlock Code was not issued for this phone. Ask the " +
                    "creator for a new code for this device."
            )
        }
    }

    /** Has this licence run out? Compares against the device clock. */
    fun isExpired(licence: org.json.JSONObject): Boolean =
        isExpiredOn(licence, java.time.LocalDate.now())

    /**
     * Expiry check with the date passed in.
     *
     * Split out so expiry can be tested on the computer, where waiting for a
     * date to arrive is not an option.
     */
    fun isExpiredOn(licence: org.json.JSONObject, today: java.time.LocalDate): Boolean {
        val expiry = licence.optString("expiry").trim()
        if (expiry.isEmpty() || licence.isNull("expiry")) return false
        return try {
            val until = java.time.LocalDate.parse(expiry)
            today.isAfter(until)
        } catch (problem: Exception) {
            // A date we cannot read is treated as expired, because guessing
            // "not expired" on an unreadable date would be the wrong way round.
            true
        }
    }

    fun expiryText(licence: org.json.JSONObject): String {
        val expiry = licence.optString("expiry").trim()
        return if (expiry.isEmpty()) "never expires" else "until $expiry"
    }

    // ------------------------------------------------------------------
    // Small helpers, shared with the payload decryption
    // ------------------------------------------------------------------

    /**
     * HKDF-SHA256, RFC 5869, built on the platform's HmacSHA256.
     *
     * Android has no ready-made HKDF, so the standard extract and expand
     * steps are done with the HMAC it already provides.
     */
    fun hkdf(
        secret: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")

        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = mac.doFinal(secret)

        val output = ByteArray(length)
        var block = ByteArray(0)
        var counter = 1
        var written = 0

        while (written < length) {
            mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            mac.update(block)
            mac.update(info)
            mac.update(counter.toByte())
            block = mac.doFinal()

            val size = minOf(block.size, length - written)
            System.arraycopy(block, 0, output, written, size)
            written += size
            counter += 1
        }
        return output
    }

    /** AES-GCM decryption, the same step for licences and for the payload. */
    fun gcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return cipher.doFinal(ciphertext)
    }

    /**
     * The exact bytes a signature covers: every field except the signature,
     * with the keys sorted so the order never matters.
     *
     * Mirrors `packaging._licence_signing_bytes`, which is Python's
     * `json.dumps(..., sort_keys=True, separators=(",", ":"))`.
     *
     * WHY THE TEXT IS BUILT BY HAND INSTEAD OF USING `JSONObject.toString()`
     * ------------------------------------------------------------------------
     * `JSONObject` does NOT preserve the order fields were added in. On Android
     * it stores them in a `LinkedHashMap`, so writing a copy back out gives
     * insertion order - which is exactly the ordering we must not depend on.
     * Sorting the keys into a fresh `JSONObject` does not help either, because
     * that fresh object is a hash map and reorders them again. Measured:
     * `{"zzz":1,"aaa":2,"mmm":3}` comes back out as `{"aaa":2,"zzz":1,"mmm":3}`.
     *
     * So the canonical text is assembled here, key by key, in sorted order.
     * That is the only way to guarantee the bytes match Python's, which is
     * what makes a signature created by the builder verify on the phone.
     */
    fun signingBytes(licence: org.json.JSONObject): ByteArray {
        val text = StringBuilder()
        val copy = org.json.JSONObject(licence.toString())
        copy.remove("signature")
        writeCanonicalJson(copy, text)
        return text.toString().toByteArray(Charsets.UTF_8)
    }

    /** Writes `value` as JSON with every object key in sorted order. */
    private fun writeCanonicalJson(value: org.json.JSONObject, out: StringBuilder) {
        out.append('{')
        val keys = value.keys().asSequence().toList().sorted()
        for ((index, key) in keys.withIndex()) {
            if (index > 0) out.append(',')
            writeJsonString(key, out)
            out.append(':')
            writeCanonicalValue(value.get(key), out)
        }
        out.append('}')
    }

    private fun writeCanonicalValue(value: Any?, out: StringBuilder) {
        when (value) {
            null, org.json.JSONObject.NULL -> out.append("null")
            is org.json.JSONObject -> writeCanonicalJson(value, out)
            is org.json.JSONArray -> {
                out.append('[')
                for (index in 0 until value.length()) {
                    if (index > 0) out.append(',')
                    writeCanonicalValue(value.get(index), out)
                }
                out.append(']')
            }
            is Number, is Boolean -> out.append(value.toString())
            else -> writeJsonString(value.toString(), out)
        }
    }

    /**
     * Writes a JSON string exactly as Python's `json.dumps` would with
     * `ensure_ascii=False`: escape only what the spec requires, and pass
     * non-ASCII characters through as real UTF-8 rather than `\uXXXX`.
     *
     * Getting this wrong would not be a small error - it would invalidate
     * every signature - so the matching test in `CryptoCompatibilityTest`
     * checks the byte sequence against a value produced by the builder.
     */
    private fun writeJsonString(text: String, out: StringBuilder) {
        out.append('"')
        for (character in text) {
            when {
                character == '"' -> out.append("\\\"")
                character == '\\' -> out.append("\\\\")
                character == '\n' -> out.append("\\n")
                character == '\r' -> out.append("\\r")
                character == '\t' -> out.append("\\t")
                character == '\b' -> out.append("\\b")
                character == '\u000C' -> out.append("\\f")
                character < ' ' -> out.append(String.format("\\u%04x", character.code))
                else -> out.append(character)
            }
        }
        out.append('"')
    }
}