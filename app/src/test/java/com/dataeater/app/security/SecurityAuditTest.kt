package com.dataeater.app.security

import com.dataeater.app.data.*
import com.dataeater.app.builder.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.LocalDate
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SecurityAuditTest {
    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val public = DeviceKeys.encodeBase64(pair.public.encoded)
    private val today = LocalDate.of(2026, 10, 13)
    private fun signed(id: Any? = "audit-db", binding: Any? = null): JSONObject {
        val code = JSONObject().put("format", "dataeater-licence").put("version", 1).put("database_id", id ?: JSONObject.NULL)
            .put("expiry", "2026-10-31").put("wrap", JSONObject().put("format", "dataeater-wrap").put("version", 1))
        if (binding != null) code.put("database_fingerprint", binding)
        code.put("signature", DeviceKeys.encodeBase64(Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(DeviceKeys.signingBytes(code)); sign() }))
        return code
    }
    @Test fun genuinelySignedEmptyMissingAmbiguousIdsAreRefused() {
        for (id in listOf(null,""," "," db","db ",".","..","a/b",12,JSONObject())) {
            assertTrue("$id", UnlockCode.check(signed(id),public,"audit-db",today) is UnlockCode.Checked.Rejected)
            assertFalse(DatabaseIdentity.validId(id))
        }
        assertTrue(UnlockCode.check(signed("audit-db"),public,"",today) is UnlockCode.Checked.Rejected)
    }
    @Test fun invalidUnicodeCannotCollideWithSignedQuestionMark() {
        val forged = signed("?").put("database_id", "\uD800")
        assertTrue(UnlockCode.check(forged, public, "\uD800", today) is UnlockCode.Checked.Rejected)
    }
    @Test fun unicodeKeysAreSortedByCodePointAndFloatValuesAreNotRoundTripped() {
        val o = JSONObject().put("extra", JSONObject().put("\uD800\uDC00", 1.0).put("\uE000", "x"))
        assertEquals("{\"extra\":{\"\uE000\":\"x\",\"\uD800\uDC00\":1.0}}", DeviceKeys.signingBytes(o).toString(Charsets.UTF_8))
    }
    @Test fun exactCaseAndLegacyValidBindingRemainSupported() {
        assertTrue(UnlockCode.check(signed(),public,"audit-db",today) is UnlockCode.Checked.Accepted)
        assertTrue(UnlockCode.check(signed("AUDIT-DB"),public,"audit-db",today) is UnlockCode.Checked.Rejected)
        val changed = signed().put("database_id","other")
        assertTrue(UnlockCode.check(changed,public,"other",today) is UnlockCode.Checked.Rejected)
    }
    @Test fun newFingerprintsAreRequiredToMatchAndCannotBeRemoved() {
        val digest = "sha256:"+"a".repeat(64); val code=signed(binding=digest)
        assertTrue(UnlockCode.check(code,public,"audit-db",today,digest) is UnlockCode.Checked.Accepted)
        assertTrue(UnlockCode.check(code,public,"audit-db",today) is UnlockCode.Checked.Rejected)
        assertTrue(UnlockCode.check(code,public,"audit-db",today,"sha256:"+"b".repeat(64)) is UnlockCode.Checked.Rejected)
        code.remove("database_fingerprint");assertTrue(UnlockCode.check(code,public,"audit-db",today,digest) is UnlockCode.Checked.Rejected)
    }
    @Test fun missingMalformedManifestIdsAndUnsupportedEncryptionRejected() {
        for (value in listOf(JSONObject.NULL,"",42,"a/b")) {
            assertThrows(DatabaseReader.DataEaterException::class.java) { DatabaseReader.parseManifest(JSONObject().put("format","dataeater").put("format_version",1).put("database_id",value).toString()) }
        }
        assertThrows(DatabaseReader.DataEaterException::class.java) { DatabaseReader.parseManifest("""{"format":"dataeater","format_version":1,"database_id":"db","encryption":"rot13"}""") }
    }
    @Test fun nonP256KeyAndMalformedCryptoInputsRejected() {
        val p384=KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp384r1")) }.generateKeyPair()
        assertThrows(DeviceKeys.LicenceException::class.java) { DeviceKeys.loadCreatorPublicKey(DeviceKeys.encodeBase64(p384.public.encoded)) }
        assertThrows(IllegalArgumentException::class.java) { DeviceKeys.gcmDecrypt(ByteArray(16),ByteArray(12),ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { DeviceKeys.gcmDecrypt(ByteArray(32),ByteArray(8),ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { DeviceKeys.hkdf(ByteArray(32),ByteArray(16),ByteArray(0),8161) }
    }
    @Test fun clockRollbackLimitIsDemonstratedNotClaimedPrevented() {
        val code=signed();assertTrue(UnlockCode.check(code,public,"audit-db",LocalDate.of(2026,11,1)) is UnlockCode.Checked.Rejected)
        assertTrue(UnlockCode.check(code,public,"audit-db",LocalDate.of(2026,10,1)) is UnlockCode.Checked.Accepted)
    }
    @Test fun storedLicenceNamesDoNotCollideOrTraverse() {
        assertNotEquals(LicenceStore.storageName("a b"),LicenceStore.storageName("a_b"))
        assertNotEquals(LicenceStore.storageName("DB"),LicenceStore.storageName("db"))
        assertNotEquals(LicenceStore.storageName("db", public),LicenceStore.storageName("db", DeviceKeys.encodeBase64(KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair().public.encoded)))
        assertThrows(DatabaseReader.DataEaterException::class.java) { LicenceStore.storageName("..") }
    }
    @Test fun excessiveNestingSizeAndInvalidBase64AreRejectedBeforeParserOrCrypto() {
        val deep="[".repeat(10000)+"]".repeat(10000)
        assertThrows(UnlockCode.Refused::class.java) { UnlockCode.parse(Base64.getEncoder().encodeToString(deep.toByteArray())) }
        assertThrows(UnlockCode.Refused::class.java) { UnlockCode.parse("A".repeat(65537)) }
        assertThrows(UnlockCode.Refused::class.java) { UnlockCode.parse("!!!"+Base64.getEncoder().encodeToString(signed().toString().toByteArray())) }
    }
    @Test fun duplicateEscapedKeysAndDeepDatabaseJsonAreRefused() {
        assertThrows(DatabaseReader.DataEaterException::class.java) { DatabaseReader.parseManifest("{\"format\":\"dataeater\",\"database_id\":\"a\",\"database_\\u0069d\":\"b\",\"format_version\":1}") }
        assertThrows(DatabaseReader.DataEaterException::class.java) { DatabaseReader.parseSources("[".repeat(10000)+"]".repeat(10000)) }
        assertThrows(DatabaseReader.DataEaterException::class.java) { JsonSafety.check("["+"0,".repeat(100001)+"0]") }
    }
    @Test fun invalidUtf8AndNumericContentIdentifiersAreRefused() {
        assertThrows(DatabaseReader.DataEaterException::class.java) { DatabaseReader.decodeText(byteArrayOf(0xC3.toByte(), 0x28)) }
        assertThrows(DatabaseReader.DataEaterException::class.java) { DatabaseReader.parseSources("[{\"id\":42}]") }
        assertThrows(DatabaseReader.DataEaterException::class.java) { DatabaseReader.parseChunks("{\"id\":42,\"source_id\":\"s\",\"text\":\"bad\"}") }
    }
    @Test fun externalLicencePreviewIsBoundedBeforeParsing() {
        val file = File.createTempFile("licence-audit", ".lic")
        try {
            file.writeBytes(ByteArray(UnlockCode.MAX_CODE_BYTES + 1))
            assertThrows(IllegalArgumentException::class.java) { LicenceFiles.readFileCapped(file) }
            file.writeText("short")
            assertEquals("short", LicenceFiles.readFileCapped(file))
        } finally { file.delete() }
    }
    @Test fun zipSuffixLimitsAndDuplicateNamesApplyAfterWantedEntry() {
        val file=File.createTempFile("audit-zip", ".dataeater")
        fun zip(entries: Map<String,ByteArray>) { ZipOutputStream(file.outputStream()).use { z -> entries.forEach { (n,v) -> z.putNextEntry(ZipEntry(n));z.write(v);z.closeEntry() } } }
        try {
            zip(linkedMapOf("manifest.json" to "{}".toByteArray(),"suffix" to ByteArray(2048)))
            assertThrows(DatabaseReader.DataEaterException::class.java) { DatabaseReader.readBytesFromPackage({file.inputStream()},"manifest.json",1024,10) }
            zip(linkedMapOf("manifest.json" to "{}".toByteArray(),"manifesz.json" to "{}".toByteArray()))
            val bytes=file.readBytes();val needle="manifesz.json".toByteArray();val replacement="manifest.json".toByteArray()
            for(i in 0..bytes.size-needle.size) if(needle.indices.all { bytes[i+it]==needle[it] }) replacement.copyInto(bytes,i)
            file.writeBytes(bytes)
            assertThrows(DatabaseReader.DataEaterException::class.java) { DatabaseReader.readBytesFromPackage({file.inputStream()},"manifest.json",1024,10) }
        } finally { file.delete() }
    }
    @Test fun pythonPackageNewAndLegacyLicencesDecryptOnAndroidReader() {
        val stream=javaClass.classLoader!!.getResourceAsStream("security_interop_v1.json")!!
        val fixture=JSONObject(stream.bufferedReader().use { it.readText() })
        val file=File.createTempFile("audit-interop", ".dataeater")
        try {
            file.writeBytes(Base64.getDecoder().decode(fixture.getString("package_base64")))
            val manifest=JSONObject(DatabaseReader.manifestTextOf(file));val identity=DatabaseIdentity.fingerprint(manifest)
            assertEquals(fixture.getString("database_fingerprint"),identity)
            val privateKey=KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(fixture.getString("test_device_private_key"))))
            for (field in listOf("new_licence","legacy_licence")) {
                val code=UnlockCode.parse(fixture.getString(field))
                assertTrue(UnlockCode.check(code,manifest.getString("creator_public_key"),"interop-db",today,identity) is UnlockCode.Checked.Accepted)
                val key=DeviceKeys.recoverContentKeyWith(code.getJSONObject("wrap"),privateKey)
                val opened=DatabaseReader.openLocked(file,key,identity)
                assertTrue(opened.chunks.single().text.contains("blue"))
                assertThrows(DatabaseReader.IntegrityException::class.java) { DatabaseReader.openLocked(file,key,"sha256:"+"0".repeat(64)) }
            }
            val code=UnlockCode.parse(fixture.getString("new_licence"))
            val changed=JSONObject(manifest.toString()).put("payload_nonce","AAAAAAAAAAAAAAAA")
            assertTrue(UnlockCode.check(code,manifest.getString("creator_public_key"),"interop-db",today,DatabaseIdentity.fingerprint(changed)) is UnlockCode.Checked.Rejected)
        } finally { file.delete() }
    }
}
