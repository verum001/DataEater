package com.dataeater.app.security

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object LicenceStore {
    private const val SUBDIR = "licences"

    private fun dir(context: Context): File = File(context.filesDir, SUBDIR).apply { mkdirs() }

    fun save(context: Context, databaseId: String, unlockCode: ByteArray): Boolean {
        return try {
            val file = File(dir(context), sanitize(databaseId))
            FileOutputStream(file).use { it.write(unlockCode) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun get(context: Context, databaseId: String): ByteArray? {
        val file = File(dir(context), sanitize(databaseId))
        return if (file.exists()) file.readBytes() else null
    }

    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
