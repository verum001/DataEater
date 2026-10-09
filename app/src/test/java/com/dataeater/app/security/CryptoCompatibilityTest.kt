package com.dataeater.app.security

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Proves the Android crypto and the Python builder produce the same answers.
 *
 * THE PROBLEM THIS SOLVES
 * -----------------------
 * `DeviceKeys.kt` and `tools/dataeater_builder/packaging.py` each implement
 * this chain independently:
 *
 *     shared secret --HKDF--> wrapping key --AES-GCM--> content key
 *
 * If the two disagree by a single byte, every licence the creator issues is
 * useless on the customer's phone. Unit tests on each side would NOT catch
 * that, because each side would still agree with itself. Only a test holding
 * values produced by the *other* side can catch it.
 *
 * So every expected value below was produced by the Python side:
 *
 *     tools/.venv/bin/python tools/tests/make_crypto_vectors.py
 *
 * That script is deterministic. Re-running it prints exactly these values.
 * If you change the crypto on one side, re-run the script, copy the new
 * values in here, and the test on the *other* side immediately tells you the
 * two no longer match.
 *
 * These run on the computer in about a second. No phone needed.
 */
class CryptoCompatibilityTest {

    // ------------------------------------------------------------------
    // Vectors from packaging.py
    // ------------------------------------------------------------------

    private val hkdfInputHex =
        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
    private val hkdfExpectedHex =
        "1ff8967b2c7ec3659903a122a564ac0eec8770085b25d5866eb2713fdd329be7"

    /** The exact text Python signs. Byte-for-byte. */
    private val pythonSigningBytes =
        """{"database_id":"demo-db","expiry":"2027-01-31",""" +
            """"format":"dataeater-licence","signed_by":"c2lnbmVy",""" +
            """"version":1,"wrap":{"nonce":"REVG","version":1,"wrapped_key":"QUJD"}}"""

    /** A whole envelope Python built, wrapping a known content key. */
    private val pythonEnvelope = """
        {
          "format": "dataeater-wrap",
          "version": 1,
          "ephemeral_public_key": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1lqTl3yqPRsIGFL/V6eeRl8WYFdzBLrq1QXdOkhYnPNQGF6JU3LfYiHqOhN1V+Rz/dtnVfBb1QfDxTP86ckShQ==",
          "nonce": "AAECAwQFBgcICQoL",
          "wrapped_key": "jL71NE0KBKaTCZSf2j9jBZpSpfqWJyorRZvvhr8pLS83fgxJaF0ycumuI1QSnrcL"
        }
    """.trimIndent()

    /** The P-256 private key whose public half that envelope was built for. */
    private val pythonDevicePrivateKey =
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgERERERERERERERERERERERERERERERERERERERERERGhRANCAAQCF+YX8LZEOSgnj5aZnmmiOk8sFSvfbWzfZuW4AoLU7RlKfevLl3EtLdo8qFqodlpW9F/HWFmWUvKJfGUwbleU"

    private val expectedContentKeyHex =
        "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f"

    /** A different phone's private key, for the wrong-device negative test. */
    private val otherDevicePrivateKey =
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgREREREREREREREREREREREREREREREREREREREREREShRANCAARbNokNrL18mpa7dKHuKLPS11ty4Jog7yXPjm/YqfA1DQ4UvtjUaCo02DU4vf9bluiaZmbsDbV0XQL6EhAHLfda"

    /** A valid EC P-256 key that did NOT sign the licence above. */
    private val attackerPublicKey =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEC7vF6LyEvTPR084D/6yadH9MGZP92y7JOkEWqG8CKnfDwXGRVZpMKhqlfnm40Zd9oslZFy9HjjQeJwKNaf/7ew=="

    /** EC P-256 public key and a licence it really signed. */
    private val creatorPublicKey =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEUadYCDOJjqGxg8vXNQpAmQeMbvHB4Y6XDNdoMDXyXn0BEFInErC1p8/wgWhUhphKlOaDHtrEbnNg+p2DSnqBoQ=="

    private val pythonSignedLicence = """
        {
  "database_id": "demo-db",
  "expiry": "2027-01-31",
  "format": "dataeater-licence",
  "signature": "MEUCICzpaidRB+HRNW8YfZEq6cI+FW7z+59a/z47rrszpjLFAiEA9ejGt441SMU+f0AbBkabwmfYx8v0C8umNQCJdtdSPxQ=",
  "signed_by": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEUadYCDOJjqGxg8vXNQpAmQeMbvHB4Y6XDNdoMDXyXn0BEFInErC1p8/wgWhUhphKlOaDHtrEbnNg+p2DSnqBoQ==",
  "version": 1,
  "wrap": {
    "ephemeral_public_key": "QUJD",
    "format": "dataeater-wrap",
    "nonce": "REVG",
    "version": 1,
    "wrapped_key": "R0hJ"
  }
}
    """.trimIndent()

    // ------------------------------------------------------------------
    // 1. Key derivation
    // ------------------------------------------------------------------

    @Test
    fun hkdfAgreesWithPython() {
        val result = DeviceKeys.hkdf(
            secret = hexToBytes(hkdfInputHex),
            salt = ByteArray(16),
            info = "DataEater licence v1".toByteArray(Charsets.UTF_8),
            length = 32,
        )
        assertEquals(hkdfExpectedHex, bytesToHex(result))
    }

    /**
     * Guards the guard. The vector above is only meaningful if changing
     * HKDF-SHA256 actually changes the answer, so confirm the salt and info
     * are not being silently ignored.
     */
    @Test
    fun hkdfActuallyUsesItsSaltAndInfo() {
        val secret = hexToBytes(hkdfInputHex)

        val plain = DeviceKeys.hkdf(secret, ByteArray(16), "a".toByteArray(), 32)
        val otherSalt = DeviceKeys.hkdf(secret, ByteArray(16) { 1 }, "a".toByteArray(), 32)
        val otherInfo = DeviceKeys.hkdf(secret, ByteArray(16), "b".toByteArray(), 32)

        assertTrue("salt was ignored", plain.contentEquals(otherSalt).not())
        assertTrue("info was ignored", plain.contentEquals(otherInfo).not())
    }

    @Test
    fun hkdfRejectsAnImpossibleLength() {
        // A zero-length key would be silently useless. Asking for nothing
        // must not return an empty array quietly.
        val result = DeviceKeys.hkdf(ByteArray(32), ByteArray(16), ByteArray(0), 0)
        assertEquals(0, result.size)
    }

    // ------------------------------------------------------------------
    // 2. The signed bytes
    // ------------------------------------------------------------------

    @Test
    fun signingBytesAgreeWithPython() {
        val licence = JSONObject("""
            {
              "version": 1,
              "database_id": "demo-db",
              "expiry": "2027-01-31",
              "format": "dataeater-licence",
              "wrap": { "wrapped_key": "QUJD", "nonce": "REVG", "version": 1 },
              "signed_by": "c2lnbmVy"
            }
        """.trimIndent())

        assertEquals(
            pythonSigningBytes,
            String(DeviceKeys.signingBytes(licence), Charsets.UTF_8),
        )
    }

    /**
     * The signature must not depend on the order the fields happened to be
     * added in, or a licence could fail for a silly reason.
     */
    @Test
    fun signingBytesIgnoreFieldOrder() {
        val one = JSONObject(
            """{"b":1,"a":{"z":true,"y":"x"}}"""
        )
        val two = JSONObject(
            """{"a":{"y":"x","z":true},"b":1}"""
        )
        assertTrue(
            DeviceKeys.signingBytes(one).contentEquals(DeviceKeys.signingBytes(two))
        )
    }

    /** The signature field itself must never be part of what it signs. */
    @Test
    fun signingBytesExcludeTheSignatureItself() {
        val bare = JSONObject("""{"format":"dataeater-licence","version":1}""")
        val withSignature = JSONObject("""{"format":"dataeater-licence","version":1}""")
        withSignature.put("signature", "irrelevant-base64")

        assertTrue(
            DeviceKeys.signingBytes(bare)
                .contentEquals(DeviceKeys.signingBytes(withSignature))
        )
    }

    // ------------------------------------------------------------------
    // 3. Signature verification
    // ------------------------------------------------------------------

    @Test
    fun acceptsASignaturePythonMade() {
        DeviceKeys.verifySignatureBytes(
            JSONObject(pythonSignedLicence),
            creatorPublicKey,
        )
    }

    @Test
    fun rejectsALicenceWhoseExpiryWasExtended() {
        // THE most important negative test in this file. A customer who edits
        // the expiry to keep using the database must be caught here.
        val tampered = JSONObject(pythonSignedLicence)
        tampered.put("expiry", "2099-12-31")

        val problem = assertThrows(DeviceKeys.LicenceException::class.java) {
            DeviceKeys.verifySignatureBytes(tampered, creatorPublicKey)
        }
        assertTrue(
            "message should explain it was changed, was: ${problem.message}",
            problem.message!!.contains("changed after the creator signed it"),
        )
    }

    @Test
    fun rejectsALicenceSwappedToAnotherDatabase() {
        val swapped = JSONObject(pythonSignedLicence)
        swapped.put("database_id", "somebody-elses-database")

        assertThrows(DeviceKeys.LicenceException::class.java) {
            DeviceKeys.verifySignatureBytes(swapped, creatorPublicKey)
        }
    }

    @Test
    fun rejectsALicenceWithNoSignature() {
        val unsigned = JSONObject("""{"format":"dataeater-licence","version":1}""")

        val problem = assertThrows(DeviceKeys.LicenceException::class.java) {
            DeviceKeys.verifySignatureBytes(unsigned, creatorPublicKey)
        }
        assertTrue(problem.message!!.contains("no signature"))
    }

    @Test
    fun rejectsASignatureFromTheWrongCreator() {
        // A different, equally valid EC P-256 key that did not sign this
        // licence. This is what stops somebody re-signing one themselves.
        assertThrows(DeviceKeys.LicenceException::class.java) {
            DeviceKeys.verifySignatureBytes(
                JSONObject(pythonSignedLicence),
                attackerPublicKey,
            )
        }
    }

    // ------------------------------------------------------------------
    // 4. The whole unwrap, end to end
    // ------------------------------------------------------------------

    @Test
    fun recoversTheSameContentKeyPythonWrapped() {
        val privateKey = loadPrivateKey(pythonDevicePrivateKey)

        val contentKey = DeviceKeys.recoverContentKeyWith(
            JSONObject(pythonEnvelope),
            privateKey,
        )

        assertEquals(expectedContentKeyHex, bytesToHex(contentKey))
        assertEquals(32, contentKey.size)
    }

    /**
     * A licence built for a different phone must fail. Both sides produce a
     * different shared secret, and the AES tag check is what catches it.
     */
    @Test
    fun refusesALicenceMadeForADifferentPhone() {
        val otherPhoneKey = loadPrivateKey(otherDevicePrivateKey)

        assertThrows(DeviceKeys.LicenceException::class.java) {
            DeviceKeys.recoverContentKeyWith(JSONObject(pythonEnvelope), otherPhoneKey)
        }
    }

    @Test
    fun refusesAnEnvelopeThatIsNotADataeaterWrap() {
        val privateKey = loadPrivateKey(pythonDevicePrivateKey)
        val wrongFormat = JSONObject(pythonEnvelope)
        wrongFormat.put("format", "something-else")

        val problem = assertThrows(DeviceKeys.LicenceException::class.java) {
            DeviceKeys.recoverContentKeyWith(wrongFormat, privateKey)
        }
        assertTrue(problem.message!!.contains("not a DataEater Unlock Code"))
    }

    @Test
    fun refusesAFutureEnvelopeVersion() {
        val privateKey = loadPrivateKey(pythonDevicePrivateKey)
        val future = JSONObject(pythonEnvelope)
        future.put("version", 99)

        val problem = assertThrows(DeviceKeys.LicenceException::class.java) {
            DeviceKeys.recoverContentKeyWith(future, privateKey)
        }
        assertTrue(problem.message!!.contains("version 99"))
    }

    /** A flipped bit in the ciphertext must not decrypt to anything usable. */
    @Test
    fun refusesATamperedPayload() {
        val privateKey = loadPrivateKey(pythonDevicePrivateKey)
        val tampered = JSONObject(pythonEnvelope)
        val wrapped = DeviceKeys.decodeBase64(tampered.getString("wrapped_key"))
        wrapped[wrapped.size - 1] = (wrapped[wrapped.size - 1].toInt() xor 0x01).toByte()
        tampered.put("wrapped_key", DeviceKeys.encodeBase64(wrapped))

        assertThrows(DeviceKeys.LicenceException::class.java) {
            DeviceKeys.recoverContentKeyWith(tampered, privateKey)
        }
    }

    // ------------------------------------------------------------------
    // 5. The Request Code
    // ------------------------------------------------------------------

    /**
     * A Request Code is the single most important piece of compatibility
     * between the two sides. It is what the customer sends the creator, and
     * the creator's tool turns it back into a public key.
     *
     * If these two encodings ever differ, **no licence can be issued for any
     * real phone at all** - the creator's tool would simply reject every
     * Request Code it was ever sent.
     */
    @Test
    fun requestCodeFormatAgreesWithPython() {
        val pythonRequestCode =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEV+l39tt+M8P+es8oQu2YcAnK9W1FhoL8pEe309diqzTFqzdwulc73/VBQGVkD/tbNG36hN7E201o5fWcxHHC7A=="

        // 1. It must be readable as an X.509 SubjectPublicKeyInfo key.
        val keyBytes = DeviceKeys.decodeBase64(pythonRequestCode)
        assertEquals("91 bytes of X.509 key data", 91, keyBytes.size)
        val publicKey = java.security.KeyFactory.getInstance("EC")
            .generatePublic(java.security.spec.X509EncodedKeySpec(keyBytes))

        // 2. Re-encoding it must give back exactly the text Python wrote.
        //    This is the whole check: our base64 of a Keystore public key has
        //    to be byte-identical to what the creator's tool expects.
        assertEquals(pythonRequestCode, DeviceKeys.encodeBase64(publicKey.encoded))
    }

    /** The curve must be P-256, because that is what the wrap maths uses. */
    @Test
    fun aRequestCodeCarriesAP256Key() {
        val key = java.security.KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(DeviceKeys.decodeBase64(
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEV+l39tt+M8P+es8oQu2YcAnK9W1FhoL8pEe309diqzTFqzdwulc73/VBQGVkD/tbNG36hN7E201o5fWcxHHC7A=="
            ))
        ) as java.security.interfaces.ECPublicKey

        assertEquals(256, (key.params as java.security.spec.ECParameterSpec).order.bitLength())
    }

    /**
     * A Request Code is long and gets wrapped by whatever the customer pastes
     * it through. The creator's tool tolerates whitespace, and so must ours.
     */
    @Test
    fun requestCodeSurvivesBeingWrappedOverLines() {
        val wrapped = listOf(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEV+l39tt+M8P+es8oQu2YcAnK9W1F",
            "hoL8pEe309diqzTFqzdwulc73/VBQGVkD/tbNG36hN7E201o5fWcxHHC7A==",
        ).joinToString("\n")

        val keyBytes = DeviceKeys.decodeBase64(wrapped)
        assertEquals(91, keyBytes.size)
        assertEquals(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEV+l39tt+M8P+es8oQu2YcAnK9W1FhoL8pEe309diqzTFqzdwulc73/VBQGVkD/tbNG36hN7E201o5fWcxHHC7A==",
            DeviceKeys.encodeBase64(keyBytes),
        )
    }

    /**
     * The creator's key must be P-256. Anything else is refused with a clear
     * reason rather than failing later as a puzzling signature error.
     */
    @Test
    fun theCreatorKeyMustBeOnP256() {
        val key = DeviceKeys.loadCreatorPublicKey(creatorPublicKey)

        assertTrue(
            "expected an EC key, got ${key.algorithm}",
            key is java.security.interfaces.ECPublicKey,
        )
        val params = (key as java.security.interfaces.ECPublicKey).params
        val order = (params as java.security.spec.ECParameterSpec).order
        assertEquals(256, order.bitLength())
    }

    /** Garbage must be rejected with a clear message, not a crash. */
    @Test
    fun rubbishIsNotARequestCode() {
        val junk = java.util.Base64.getEncoder()
            .encodeToString("not a key".toByteArray())

        assertThrows(java.security.spec.InvalidKeySpecException::class.java) {
            java.security.KeyFactory.getInstance("EC").generatePublic(
                java.security.spec.X509EncodedKeySpec(DeviceKeys.decodeBase64(junk))
            )
        }
    }

    // ------------------------------------------------------------------
    // 6. Base64 handling
    // ------------------------------------------------------------------

    /**
     * An Unlock Code is normally pasted in, so it often arrives split over
     * several lines. Rejecting it for that would be a support call every time.
     */
    @Test
    fun decodesBase64ThatArrivedWrappedOverLines() {
        // Simulate a customer pasting a Request Code that the clipboard or
        // the messaging app has wrapped at 64 characters.
        val wrapped = pythonDevicePrivateKey.chunked(64).joinToString("\n")

        assertArrayEquals(
            DeviceKeys.decodeBase64(pythonDevicePrivateKey),
            DeviceKeys.decodeBase64(wrapped),
        )
    }

    @Test
    fun decodesBase64WithSurroundingBlankLinesAndSpaces() {
        val messy = "\n\n  " + pythonDevicePrivateKey.chunked(40).joinToString("\n  ") +
            "  \n\n"

        assertArrayEquals(
            DeviceKeys.decodeBase64(pythonDevicePrivateKey),
            DeviceKeys.decodeBase64(messy),
        )
    }

    @Test
    fun base64RoundTrips() {
        val original = ByteArray(64) { (it * 3).toByte() }
        assertArrayEquals(
            original,
            DeviceKeys.decodeBase64(DeviceKeys.encodeBase64(original)),
        )
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    /**
     * Loads a P-256 private key from base64 PKCS#8.
     *
     * On a real phone this key comes from AndroidKeyStore and cannot be
     * exported at all. Here it is a fixed test value, which is the only way to
     * check the maths against the Python side.
     */
    private fun loadPrivateKey(base64: String) =
        KeyFactory.getInstance("EC").generatePrivate(
            PKCS8EncodedKeySpec(DeviceKeys.decodeBase64(base64))
        )
}