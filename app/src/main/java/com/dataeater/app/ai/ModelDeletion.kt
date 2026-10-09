package com.dataeater.app.ai

import java.io.File
import java.io.IOException

/** Deletes only a model file directly inside the app's visible model folder. */
object ModelDeletion {
    fun validate(file: File, directory: File) {
        if (file.absoluteFile.parentFile != directory.absoluteFile ||
            file.canonicalFile.parentFile != directory.canonicalFile ||
            !file.name.endsWith(ModelFiles.MODEL_EXTENSION) || !file.isFile
        ) {
            throw IOException("The model file is missing or is outside the model folder.")
        }
    }

    fun delete(file: File, directory: File) {
        validate(file, directory)
        if (!file.delete()) {
            throw IOException("The file could not be removed. Check storage access and try again.")
        }
    }
}
