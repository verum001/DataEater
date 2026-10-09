package com.dataeater.app.ai

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Android can mount app storage and shared storage separately, even on the same card.
 * Copy into a hidden partial file beside the destination, verify, then rename there.
 * A partial or incorrect download must never become a loadable model.
 */
object ModelInstaller {
    fun install(source: File, directory: File, entry: ModelCatalog.Entry,
        cancelled: () -> Boolean = { false },
        onStaged: (File) -> Unit = {},
        publish: (() -> Unit) -> Unit = { it() },
    ) {
        require(File(entry.fileName).name == entry.fileName && entry.fileName.endsWith(".litertlm"))
        if (source.length() != entry.bytes) throw IOException("Incomplete model file")
        if (cancelled()) throw InterruptedException()
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Storage unavailable")
        val target = File(directory, entry.fileName)
        if (target.exists()) throw IOException("Model already exists")
        val temporary = File.createTempFile(".dataeater-model-", ".part", directory)
        try {
            onStaged(temporary)
            val hash = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            FileOutputStream(temporary).use { output ->
                source.inputStream().buffered().use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        if (cancelled()) throw InterruptedException()
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > entry.bytes) throw IOException("Model size changed")
                        output.write(buffer, 0, n)
                        hash.update(buffer, 0, n)
                    }
                }
                output.fd.sync()
            }
            val actual = hash.digest().joinToString("") { "%02x".format(it) }
            if (copied != entry.bytes || actual != entry.sha256) throw IOException("Model verification failed")
            if (cancelled()) throw InterruptedException()
            publish {
                if (target.exists()) throw IOException("Model already exists")
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            source.delete()
        } finally { temporary.delete() }
    }
}
