package com.dataeater.app.data

import java.io.File
import java.io.IOException

/** Removes only a database file directly inside the visible database folder. */
object DatabaseDeletion {
    fun delete(file: File, directory: File) {
        if (file.absoluteFile.parentFile != directory.absoluteFile ||
            file.canonicalFile.parentFile != directory.canonicalFile ||
            !file.name.endsWith(DatabaseFiles.DATABASE_EXTENSION) || !file.isFile
        ) {
            throw IOException("The database file is missing or is outside the database folder.")
        }
        if (!file.delete()) {
            throw IOException("The file could not be removed. Check storage access and try again.")
        }
    }
}
