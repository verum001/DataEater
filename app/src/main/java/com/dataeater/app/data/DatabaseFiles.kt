package com.dataeater.app.data

import android.os.Environment
import java.io.File

/**
 * Finds DataEater database files stored on the phone.
 *
 * The user puts `.dataeater` files in one visible folder:
 *
 *     /sdcard/DataEater/
 *
 * A visible folder was chosen on purpose. Android's private folder
 * (/sdcard/Android/data/...) is hidden in most file managers, so the user
 * cannot easily copy a database in or see which ones they already have.
 *
 * There is no file picker. The user copies a file in with any file
 * manager, restarts or refreshes the app, and the database is there.
 *
 * The folder is created automatically the first time the app runs, so the
 * user never has to make an empty folder by hand.
 */
object DatabaseFiles {

    /** Every DataEater database file ends with this. */
    const val DATABASE_EXTENSION = ".dataeater"

    /** The visible folder, for example /storage/emulated/0/DataEater */
    const val FOLDER_NAME = "DataEater"

    /** One database file found on the phone. */
    data class DatabaseFile(val file: File) {
        val displayName: String get() = file.nameWithoutExtension
        val sizeInKilobytes: Long get() = file.length() / 1024
    }

    /** The folder where databases live. Created if it does not exist yet. */
    fun folder(): File {
        val folder = File(Environment.getExternalStorageDirectory(), FOLDER_NAME)
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    /** All databases currently in the folder, sorted by name. */
    fun findAll(): List<DatabaseFile> {
        val files = folder().listFiles() ?: return emptyList()

        return files
            .filter { it.isFile && it.name.endsWith(DATABASE_EXTENSION) }
            .sortedBy { it.name.lowercase() }
            .map { DatabaseFile(it) }
    }
}