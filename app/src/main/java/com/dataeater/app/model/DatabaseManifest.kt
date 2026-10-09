package com.dataeater.app.model

/**
 * Information about a DataEater database.
 *
 * This is read from `manifest.json`, the first file inside a
 * `.dataeater` package. It tells us what the database is, and it
 * lets us refuse to open a database we do not understand.
 */
data class DatabaseManifest(
    /** Short unique identifier, for example "demo". */
    val databaseId: String,

    /** Human readable name shown in the interface. */
    val name: String,

    /** One or two sentences about what the database contains. */
    val description: String,

    /**
     * Version of the DataEater file format used by this database.
     * This app understands version 1. If a future database says
     * version 2, we refuse to open it instead of misreading it.
     */
    val formatVersion: Int,

    /** Main language of the text inside, for example "en". */
    val language: String,

    /** Licence of the content, for example "CC0-1.0". */
    val license: String,

    /** "none" for an open database, or e.g. "aes-256-gcm" for a locked one. */
    val encryption: String,

    /**
     * Name of whoever produced the database.
     * Public, so it is safe to show before a licence is entered.
     */
    val creator: String = "",

    /**
     * How to reach the creator, usually an email.
     * Public, so the app can tell a locked customer who to ask.
     */
    val contact: String = "",

    /**
     * The creator's ECDSA P-256 PUBLIC key, base64.
     *
     * The app needs this to check that an Unlock Code really came from the
     * creator of *this* database, rather than from somebody who merely
     * happens to have a signing key of their own.
     *
     * Public keys are safe to publish — that is what they are for. The private
     * half never leaves the creator's computer and is never in this file.
     */
    val creatorPublicKey: String = "",

    /** How many source documents this database contains. */
    val documentCount: Int,

    /** How many text pieces the database contains. */
    val chunkCount: Int,
)