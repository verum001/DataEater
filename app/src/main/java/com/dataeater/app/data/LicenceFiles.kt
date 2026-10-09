package com.dataeater.app.data

import android.os.Environment
import java.io.File

/**
 * Finds Unlock Code files the customer has been sent.
 *
 * WHY A FILE INSTEAD OF A STRING
 * ------------------------------
 * An Unlock Code is around 850 characters on one line. Getting that from an
 * email into the app meant copying it by hand, and every attempt was a chance
 * to lose or swap two characters. A file can simply be attached to a message,
 * saved from one, or copied onto a memory card, and it arrives exactly as it
 * was written.
 *
 * So the code travels as a `.lic` file in a folder the user already has access
 * to, and the app reads it from there. The file contents are byte-for-byte the
 * same text as the code — nothing about the cryptography changes, and a code
 * pasted by hand still works.
 *
 * WHERE THE FILES GO
 * ------------------
 * `/sdcard/DataEater/Lic/`, alongside the databases and models. A subfolder
 * rather than the same directory, because a database, a gigabyte of model and
 * a licence file are three different kinds of thing and a customer should be
 * able to see which is which.
 *
 * NOTHING IN HERE IS TRUSTED
 * --------------------------
 * A `.lic` file is a file somebody emailed. Reading it to list it, and to show
 * what it *claims* to be, is safe: it is base64 JSON and displaying a string
 * cannot execute anything. Deciding whether it opens the database is a separate
 * step, in [com.dataeater.app.security.UnlockCode], which checks a signature.
 *
 * So the names and dates shown beside a file are a description of what the
 * sender claims, never a promise that it will work. [LicenceFile.claim] is
 * worded as a claim on purpose.
 */
object LicenceFiles {

    const val FOLDER_NAME = "Lic"

    /** The extension a licence file must have. Case is ignored on the SD card. */
    const val EXTENSION = ".lic"

    /**
     * Where licence files are looked for.
     *
     * Created on first use, so the user can be told where to put the file even
     * before they have any.
     */
    fun folder(): File {
        val directory = File(
            File(Environment.getExternalStorageDirectory(), DatabaseFiles.FOLDER_NAME),
            FOLDER_NAME,
        )
        if (!directory.exists()) directory.mkdirs()
        return directory
    }

    /** What the app knows about one licence file. */
    data class LicenceFile(
        val name: String,
        val path: File,

        /**
         * The database id the file says it is for, or null if it could not be
         * read. Never treated as authoritative — see the note on the class.
         */
        val claimedDatabaseId: String?,

        /** The date the file says it runs out, as written, or null. */
        val claimedExpiry: String?,

        /**
         * True when the text at least looks like a licence.
         *
         * A false here is not an error: the file may be something else entirely,
         * and the app would rather list it and let the customer try than hide it.
         */
        val looksValid: Boolean,
    ) {
        /**
         * A short description of what this file claims, for the list.
         *
         * "never expires" is said plainly rather than showing a dash, because a
         * dash reads as "unknown" and this is the one case where the answer is
         * definitely known.
         */
        val claim: String
            get() = when {
                !looksValid -> "Not a DataEater licence"
                claimedDatabaseId == null -> "Unreadable licence"
                claimedExpiry == null -> "never expires"
                // The difference matters more than it looks: "expires
                // 2020-01-01" describes a date, while "expired" says the
                // licence is already dead. Somebody reading the first has to
                // work out the second themselves, and it is the whole reason
                // they are on this screen.
                isAlreadyExpired() -> "expired on $claimedExpiry"
                else -> "expires $claimedExpiry"
            }

        /**
         * True when the date this file names is in the past.
         *
         * An unparseable date is not treated as expired. Refusing to say
         * anything about a file the app cannot read the date of would be
         * claiming more than it knows.
         */
        private fun isAlreadyExpired(): Boolean = try {
            java.time.LocalDate.parse(claimedExpiry!!).isBefore(java.time.LocalDate.now())
        } catch (problem: Exception) {
            false
        }

        val file: File get() = path
    }

    /**
     * Every licence file in the folder, newest first.
     *
     * Unreadable files are listed too. A file the customer cannot use is
     * exactly the one they will ask about, and hiding it would leave them
     * wondering where it went.
     */
    fun findAll(): List<LicenceFile> {
        val files = folder().listFiles() ?: return emptyList()

        return files
            .filter { it.isFile && it.name.endsWith(EXTENSION, ignoreCase = true) }
            .map { file ->
                val text = try {
                    file.readText()
                } catch (problem: Exception) {
                    null
                }
                describe(file, text)
            }
            // Newest first: a customer who has just been sent a renewal is
            // looking at the newest file, and it is the one they want.
            .sortedByDescending { it.path.lastModified() }
    }

    /**
     * Works out what a file claims, without deciding whether it is valid.
     *
     * Deliberately reads only. Parsing untrusted input to describe it is fine;
     * trusting it is not, and that happens elsewhere.
     */
    private fun describe(file: File, text: String?): LicenceFile {
        if (text == null) {
            return LicenceFile(file.name, file, null, null, false)
        }

        return try {
            // The same tolerant parse the pasted-code path uses: base64 with
            // line breaks is what every mail client produces.
            val licence = com.dataeater.app.security.UnlockCode.parse(text)
            LicenceFile(
                name = file.name,
                path = file,
                claimedDatabaseId = licence.optString("database_id").ifBlank { null },
                claimedExpiry = licence.optString("expiry").ifBlank { null },
                looksValid = true,
            )
        } catch (problem: Exception) {
            LicenceFile(file.name, file, null, null, false)
        }
    }

    /**
     * The text of one licence file, for checking.
     *
     * Returns null when the file has gone or cannot be read, which is a
     * different thing from being refused — the caller must not report a missing
     * file as a bad licence, or the customer will be told to ask for a new code
     * when the real problem is that they moved the file.
     */
    fun read(name: String): String? {
        val file = resolve(name) ?: return null
        return try {
            file.readText()
        } catch (problem: Exception) {
            null
        }
    }

    /**
     * The file behind a name, or null if the name could escape the folder.
     *
     * Names come from a directory listing, so this should never fire. It is
     * here because a file path built from anything outside the app is how a
     * program ends up reading somewhere it was never meant to.
     */
    fun resolve(name: String): File? {
        val safe = name.trim()
        if (safe.isEmpty()) return null
        if (!safe.endsWith(EXTENSION, ignoreCase = true)) return null
        if (!safe.matches(Regex("[A-Za-z0-9._-]+\\.lic", RegexOption.IGNORE_CASE))) return null
        if (safe.contains("..")) return null
        return File(folder(), safe).takeIf { it.isFile }
    }

    /** The place to show the user, as a path they can recognise. */
    fun folderPath(): String = folder().absolutePath
}
