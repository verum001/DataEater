package com.dataeater.app.data

import com.dataeater.app.model.DatabaseManifest
import java.io.File

/**
 * Decides whether a `.dataeater` file is an open database or a locked one.
 *
 * WHY THIS IS ITS OWN FILE
 * ------------------------
 * A locked database has no readable `chunks.jsonl` and no `sources.json` -
 * the text is inside `payload.enc`. So when the app tries to open one
 * normally it fails, and the message the user sees is
 * *"The file 'chunks.jsonl' is missing. The database is damaged."*
 *
 * That is a lie, and a confusing one. The database is not damaged, it is
 * encrypted on purpose, and the user needs to be told that so they can go and
 * get an Unlock Code.
 *
 * The only honest way to tell the difference is to read `manifest.json`,
 * which is never encrypted, and look at the `encryption` field. That is all
 * this file does.
 *
 * The decision itself is a pure function of the manifest text, so it can be
 * tested on the computer without a phone.
 */
object DatabaseLock {

    /** What we know about a database file before trying to open it. */
    sealed class Status {
        /** Plain text database. It can be opened directly. */
        data class Open(val manifest: DatabaseManifest) : Status()

        /**
         * Encrypted database. The name, description and creator are readable;
         * the knowledge inside is not.
         */
        data class Locked(
            val manifest: DatabaseManifest,
            /** e.g. "aes-256-gcm" */
            val scheme: String,
        ) : Status() {
            /** True when the creator left a way to contact them. */
            val hasContact: Boolean get() = manifest.contact.isNotBlank()

            /** True when a creator name was recorded at all. */
            val hasCreator: Boolean get() = manifest.creator.isNotBlank()
        }

        /**
         * The file could not be read at all: not a ZIP, no manifest, or a
         * format version this app does not understand.
         *
         * Kept separate from Open and Locked so the app never shows "locked"
         * for a file that is merely broken.
         */
        data class Unreadable(val reason: String) : Status()
    }

    /**
     * Reads the manifest of a real file on the phone and classifies it.
     *
     * Never throws. A damaged file is a result, not an error, because the
     * interface has to be able to show the problem to the user.
     */
    fun inspect(file: File): Status {
        if (!file.exists()) {
            return Status.Unreadable("The file ${file.name} is not there any more.")
        }
        return try {
            statusOf(DatabaseReader.manifestTextOf(file))
        } catch (problem: Exception) {
            Status.Unreadable(problem.message ?: "The file could not be read.")
        }
    }

    /**
     * The decision itself: manifest text in, [Status] out.
     *
     * Separated from [inspect] so it can be tested with no file and no phone.
     */
    fun statusOf(manifestText: String): Status {
        // parseManifest refuses a wrong format, a missing version, and a version
        // newer than this app understands. It also lets org.json's own
        // JSONException through when the text is not JSON at all, so this
        // catches broadly on purpose: a damaged file is a result to show the
        // user, never an exception to crash on.
        val manifest = try {
            DatabaseReader.parseManifest(manifestText)
        } catch (problem: Exception) {
            return Status.Unreadable(
                problem.message ?: "The manifest could not be read.",
            )
        }

        val scheme = manifest.encryption.trim()
        return if (scheme.isEmpty() || scheme.equals("none", ignoreCase = true)) {
            Status.Open(manifest)
        } else {
            Status.Locked(manifest, scheme)
        }
    }

    }