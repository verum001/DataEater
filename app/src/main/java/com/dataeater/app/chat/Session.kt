package com.dataeater.app.chat

/**
 * One conversation, and the settings it was had.
 *
 * WHY A SESSION REMEMBERS ITS OWN SETTINGS
 * -----------------------------------------
 * The mockups show a session restoring "its saved model selection, database
 * selection, and database on/off state". That is not a small thing to copy by
 * hand after every question, and it is not something to guess at either.
 *
 * So a session owns those three choices. Switching to a past session puts the
 * app back exactly as it was, including which database and whether the
 * database was switched on.
 *
 * The practical consequence worth stating: a conversation recorded with the
 * database off can never be mistaken later for one that was checked against
 * the documents.
 */
data class Session(
    /** Stable id. Never shown to the user. */
    val id: String,

    /**
     * What the user sees: "Session 1", "Session 2", and so on.
     *
     * Deliberately not the first question. A name made from the question is
     * friendlier, but it is long, it wraps, and it changes. The mockups use
     * short stable names and so does this.
     */
    val title: String,

    /** The conversation, oldest first. */
    val messages: List<ChatMessage> = emptyList(),

    // --- the settings this session was had -------------------------------

    /** File name of the chosen model, or null for "none yet". */
    val modelName: String? = null,

    /**
     * Chosen database.
     *
     * A *name*, not a path, because the file may move and the app never
     * stores where a database lives — it looks in the one visible folder.
     */
    val databaseName: String = "",

    /**
     * Whether answers are looked up in the database at all.
     *
     * When false the model answers from its own knowledge, which is a genuine
     * feature for ordinary questions and a genuine hazard for technical ones.
     * The interface warns about exactly that, on the toggle itself.
     */
    val useDatabase: Boolean = true,

    /** True for the GPU. Kept per session, like everything else here. */
    val preferGpu: Boolean = true,

    /**
     * When this session was last changed, in milliseconds since 1970.
     *
     * The drawer sorts by this so the conversation you were just using is at
     * the top. Sorting by message count instead would look plausible and be
     * wrong: a long conversation from last week would sit above a short one
     * you used a minute ago.
     *
     * Written on every change, so it survives a restart.
     */
    val updatedAt: Long = 0L,
) {
    /** True when there is nothing to show yet. */
    val isEmpty: Boolean get() = messages.isEmpty()

    /**
     * The one-line description under the title in the session list.
     *
     * This is what makes the drawer useful rather than decorative: it says
     * which model, which database, and whether the database was on — the three
     * things that change what an answer means.
     */
    val subtitle: String
        get() {
            val parts = mutableListOf<String>()
            parts += modelName ?: "No model"
            parts += if (useDatabase) {
                databaseName.ifBlank { "No database" }
            } else {
                "Database off"
            }
            return parts.joinToString(" \u00B7 ")
        }

    /**
     * Adds a message and returns a new session. Sessions are immutable.
     *
     * Stamps [updatedAt] so the drawer puts this conversation at the top.
     */
    fun withMessage(message: ChatMessage, now: Long = System.currentTimeMillis()): Session =
        copy(messages = messages + message, updatedAt = now)
}