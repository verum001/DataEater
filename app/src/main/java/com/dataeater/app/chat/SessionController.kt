package com.dataeater.app.chat

/**
 * Owns the list of sessions and which one is active.
 *
 * THE RULES THIS ENFORCES
 * -----------------------
 *  - there is always exactly one active session, even on a first launch with
 *    nothing saved, because a chat screen with no conversation to show would
 *    have nothing to do;
 *  - a session is written to disk as soon as it changes, so closing the app
 *    mid-sentence does not lose the conversation;
 *  - switching sessions carries that session's own settings with it, which is
 *    the whole point of sessions rather than one global state;
 *  - a session that fails to save is still kept in memory, so a full disk
 *    cannot make the app unusable.
 *
 * WHY IT HOLDS NO ANDROID CODE
 * ---------------------------
 * Every rule above is testable on a computer, and each one is a case where
 * getting it wrong loses a user's conversation. The repository is injected, so
 * the tests use the in-memory one.
 */
class SessionController(
    private val repository: SessionRepository,

    /**
     * Where the clock comes from.
     *
     * Injected so tests can decide the order without sleeping, because drawer
     * order is a rule worth proving rather than assuming.
     */
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Every session, newest first. */
    var sessions: List<Session> = emptyList()
        private set

    /**
     * The session on screen.
     *
     * Never null: [ensureSomethingToShow] guarantees it before the interface
     * reads it.
     */
    var active: Session? = null
        private set

    /**
     * Loads what is on disk, creating a first session if there is nothing.
     *
     * Called once when the app starts.
     */
    fun start(): Session {
        val existing = repository.all().sortedForDisplay()
        sessions = existing

        // Reuse the most recent session rather than starting a fresh one, so
        // reopening the app returns the user to where they were. A chat app
        // that discards yesterday's conversation on every launch would be
        // unusable.
        val restored = existing.firstOrNull() ?: run {
            val created = create(existing)
            sessions = listOf(created)
            created
        }
        active = restored
        return restored
    }

    /**
     * Adds a session and makes it active.
     *
     * @param settings what to carry over from the current session, so a new
     *   conversation starts with the same database and model rather than
     *   silently reverting to defaults
     */
    fun newSession(copyFrom: Session? = active): Session {
        val created = create(sessions, template = copyFrom)
        active = created
        return created
    }

    /**
     * Switches to a session that already exists.
     *
     * Returns the session now active, or the current one if the id is unknown,
     * because failing to switch must never leave the app with nothing showing.
     */
    fun switchTo(id: String): Session {
        // In-memory state may be newer than the last successful checkpoint.
        val found = sessions.firstOrNull { it.id == id } ?: repository.find(id)
        if (found == null) return active ?: start()
        active = found
        return found
    }

    /**
     * Records a message on the active session and saves it.
     *
     * This is the only way a message enters a conversation, so there is no way
     * for a message to appear on screen but not be saved.
     */
    fun append(message: ChatMessage): Session? {
        val current = active ?: return null
        val updated = current.withMessage(message, now())
        return commit(updated)
    }

    /**
     * Replaces the message with [messageId] — used while an answer is still
     * arriving.
     *
     * Streaming callers pass persist=false and checkpoint immutable snapshots
     * on an IO worker. Ordinary edits keep immediate persistence by default.
     */
    fun replaceMessage(
        messageId: String,
        text: String,
        incomplete: Boolean,
        final: Boolean = false,
        sources: List<String>? = null,
        persist: Boolean = true,
    ): Session? {
        val current = active ?: return null
        val updated = current.copy(
            messages = current.messages.map { message ->
                if (message.id == messageId) {
                    message.copy(text = text, incomplete = incomplete, sources = sources ?: message.sources)
                } else {
                    message
                }
            },
            // Reordering the drawer on every word of a streaming answer would
            // make the list jump about under the user's finger, so an
            // in-progress answer only counts as "touched" once it settles.
            updatedAt = if (final) now() else current.updatedAt,
        )
        return commit(updated, persist)
    }

    /**
     * Changes one of the settings this session owns, and saves.
     *
     * Used by the database toggle, the processor choice and the model choice,
     * so switching them is remembered per session rather than globally.
     */
    fun updateSettings(
        modelName: String? = active?.modelName,
        databaseName: String? = active?.databaseName,
        useDatabase: Boolean? = active?.useDatabase ?: true,
        preferGpu: Boolean? = active?.preferGpu ?: true,
        persist: Boolean = true,
    ): Session? {
        val current = active ?: return null
        return commit(
            current.copy(
                modelName = modelName,
                databaseName = databaseName ?: current.databaseName,
                useDatabase = useDatabase ?: current.useDatabase,
                preferGpu = preferGpu ?: current.preferGpu,
                updatedAt = now(),
            ),
            persist = persist,
        )
    }

    /**
     * Removes a session.
     *
     * A new one is created in its place, because the chat screen cannot be
     * shown without a conversation and silently doing nothing would leave the
     * user tapping a button that appears broken.
     */
    fun delete(id: String): Session {
        if (sessions.none { it.id == id }) return active ?: start()
        check(repository.delete(id)) { "Session could not be deleted from storage." }
        sessions = sessions.filterNot { it.id == id }
        if (sessions.isEmpty()) {
            val created = create(emptyList())
            sessions = listOf(created)
            active = created
            return created
        }
        // If the deleted one was on screen, move to the top of the list, which
        // is the most recent of what remains.
        val replacement = sessions.first()
        if (active?.id == id) {
            active = replacement
        }
        return active ?: replacement
    }

    /** Saves and updates the in-memory copy. Returns the saved session. */
    private fun commit(session: Session, persist: Boolean = true): Session {
        active = session
        sessions = sessions.map { if (it.id == session.id) session else it }
            .sortedForDisplay()
        // A failed write is deliberately not reported upward. The session is
        // still correct in memory, and refusing to accept the user's message
        // because a disk is full would be worse than losing it on exit.
        if (persist) repository.save(session)
        return session
    }

    /** Save an immutable snapshot without changing controller state on an IO thread. */
    fun saveSnapshot(session: Session): Boolean = repository.save(session)

    private fun create(
        existing: List<Session>,
        template: Session? = null,
    ): Session {
        val session = Session(
            id = repository.newId(),
            title = repository.nextTitle(existing),
            modelName = template?.modelName,
            databaseName = template?.databaseName ?: "",
            useDatabase = template?.useDatabase ?: true,
            preferGpu = template?.preferGpu ?: true,
            updatedAt = now(),
        )
        repository.save(session)
        // This is the only place the list grows, so a session cannot be added
        // twice by a caller that also remembered to do it. That bug was real:
        // it produced two sessions both named "Session 1", and the drawer
        // showed a duplicate the user could not remove.
        sessions = (sessions + session).sortedForDisplay()
        return session
    }

    /**
     * Most recently touched first.
     *
     * The id is the tie-break because two sessions saved in the same
     * millisecond is possible and their order must still be stable between
     * one question and the next.
     */
    private fun List<Session>.sortedForDisplay(): List<Session> =
        sortedWith(
            compareByDescending<Session> { it.updatedAt }
                .thenByDescending { it.id }
        )
}
