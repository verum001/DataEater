package com.dataeater.app.security

import android.content.Context
import com.dataeater.app.data.DatabaseIdentity
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Wrapped licences stay private; never store an unwrapped database key. */
object LicenceStore {
    private fun dir(context: Context) = File(context.filesDir, "licences").apply { mkdirs() }
    internal fun storageName(id: String, creatorPublicKey: String? = null): String {
        DatabaseIdentity.requireId(id)
        val identity = if (creatorPublicKey == null) id else id + ":" + MessageDigest.getInstance("SHA-256").digest(DeviceKeys.decodeBase64(creatorPublicKey)).joinToString("") { "%02x".format(it.toInt() and 255) }
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) } + ".lic"
    }
    fun save(context: Context, databaseId: String, unlockCode: ByteArray, creatorPublicKey: String? = null): Boolean = try {
        require(unlockCode.size <= UnlockCode.MAX_CODE_BYTES)
        val target = File(dir(context), storageName(databaseId, creatorPublicKey)); val temp = File.createTempFile(".licence-", ".tmp", target.parentFile)
        try {
            FileOutputStream(temp).use { it.write(unlockCode); it.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
        true
    } catch (_: Exception) { false }

    fun get(context: Context, databaseId: String, creatorPublicKey: String? = null): ByteArray? {
        val folder = dir(context)
        val current = File(folder, storageName(databaseId, creatorPublicKey))
        val oldHashed = File(folder, storageName(databaseId))
        // Legacy names are read only. A stored code is still reverified against the database.
        val legacy = File(folder, databaseId.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val file = if (current.isFile) current else if (oldHashed.isFile) oldHashed else legacy
        if (!file.isFile || file.canonicalFile.parentFile != folder.canonicalFile || file.length() > UnlockCode.MAX_CODE_BYTES) return null
        return file.inputStream().use { stream ->
            val result = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) { val n = stream.read(buffer); if (n < 0) break; if (result.size() + n > UnlockCode.MAX_CODE_BYTES) return null; result.write(buffer, 0, n) }
            result.toByteArray()
        }
    }
}
