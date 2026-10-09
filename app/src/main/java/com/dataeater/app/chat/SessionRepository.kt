package com.dataeater.app.chat

/**
 * Somewhere sessions can be kept.
 *
 * WHY AN INTERFACE
 * ----------------
 * [SessionStore] needs a real folder and a real `Context`, so it cannot run in
 * a plain unit test. [SessionController] holds all the *rules* — which session
 * is active, what a new one is called, when to write to disk — and those rules
 * are worth testing on their own.
 *
 * Two implementations exist: [SessionStore] for the phone, and an in-memory one
 * for tests. Neither the controller nor the rules know which they have.
 */
interface SessionRepository {
    fun all(): List<Session>
    fun find(id: String): Session?
    fun save(session: Session): Boolean
    fun delete(id: String): Boolean

    /** The next default name, given what already exists. */
    fun nextTitle(existing: List<Session>): String

    /** A fresh id. */
    fun newId(): String
}

/**
 * A repository that keeps everything in memory and forgets it all on exit.
 *
 * Used by tests. Never used by the app — losing a conversation silently is
 * exactly the bug the real store exists to avoid.
 */
class InMemorySessionRepository : SessionRepository {

    private val items = linkedMapOf<String, Session>()
    private var idCounter = 0

    override fun all(): List<Session> = items.values.toList()

    override fun find(id: String): Session? = items[id]

    override fun save(session: Session): Boolean {
        items[session.id] = session
        return true
    }

    override fun delete(id: String): Boolean = items.remove(id) != null

    override fun nextTitle(existing: List<Session>): String {
        val highest = existing.mapNotNull { session ->
            session.title.removePrefix("Session").trim().toIntOrNull()
        }.maxOrNull() ?: 0
        return "Session ${highest + 1}"
    }

    /** Distinct ids, so a test can tell two sessions apart. */
    override fun newId(): String = "test-${++idCounter}"
}
