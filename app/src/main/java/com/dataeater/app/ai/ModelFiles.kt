package com.dataeater.app.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Finds AI model files stored on the phone.
 *
 * Models live in a plain visible folder:
 *
 *     /sdcard/DataEater/
 *
 * We chose a normal folder on purpose. The Android-private folder
 * (/sdcard/Android/data/...) is hidden inside most file managers, which
 * makes it very hard for a user to copy a model in or see what they have.
 *
 * The cost of this choice is explained in SECURITY.md: reading this folder
 * needs the "all files access" permission on Android 11 and newer. A
 * knowledge tool needs to read whole files, so this is a reasonable use of
 * that permission - but it is a real permission and it is declared openly.
 */
object ModelFiles {

    /** Every LiteRT-LM model file ends with this. */
    const val MODEL_EXTENSION = ".litertlm"

    /** The folder the user sees, for example /sdcard/DataEater */
    const val FOLDER_NAME = "DataEater"

    /** One model file found on the phone. */
    data class ModelFile(val file: File) {
        val displayName: String get() = file.nameWithoutExtension
        val sizeInMegabytes: Long get() = file.length() / (1024 * 1024)
    }

    /** /sdcard/DataEater - shown to the user so they can find it easily. */
    fun dataEaterDirectory(): File =
        File(Environment.getExternalStorageDirectory(), FOLDER_NAME)

    /**
     * Are we allowed to read /sdcard/DataEater?
     *
     * Android 11 (API 30) and newer need a special "all files access"
     * permission. Older Android only needs the older read permission.
     */
    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Android 11+ special-access screen, with a fallback for vendor settings apps. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    fun accessSettingsIntent(context: Context): Intent {
        val appScreen = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        return if (appScreen.resolveActivity(context.packageManager) != null) appScreen
        else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    /**
     * All models currently in the folder, sorted by name.
     *
     * Returns an empty list when access has not been granted, or when the
     * folder does not exist yet.
     */
    fun findAll(context: Context): List<ModelFile> {
        if (!hasStorageAccess(context)) return emptyList()

        val directory = dataEaterDirectory()
        if (!directory.exists()) directory.mkdirs()

        val files = directory.listFiles() ?: return emptyList()

        return files
            .filter { it.isFile && it.name.endsWith(MODEL_EXTENSION) }
            .sortedBy { it.name }
            .map { ModelFile(it) }
    }
}