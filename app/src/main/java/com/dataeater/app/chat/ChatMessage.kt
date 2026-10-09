package com.dataeater.app.chat

/**
 * One line in a conversation.
 *
 * WHY A MESSAGELIST AND NOT JUST A STRING
 * ---------------------------------------
 * A session has to survive the app being closed, so the conversation cannot
 * live only in the interface. It is written to disk, and a written format needs
 * a decided shape rather than whatever a text field happened to contain.
 *
 * WHY A MESSAGE REMEMBERS ITS OWN SOURCES
 * ----------------------------------------
 * When a database was switched off and the model answered from its own
 * knowledge, there is no citation. A message that did not record that would
 * look identical, months later, to one that was properly sourced. That
 * difference is the whole safety story of this app, so it is stored, not
 * inferred.
 */
data class ChatMessage(
    /** Stable id, so the interface can tell two identical messages apart. */
    val id: String,

    /** True for the person, false for the model. */
    val fromUser: Boolean,

    /** What was said. */
    val text: String,

    /**
     * Where the answer came from: document, section, page.
     *
     * Empty when there was no database, or when the database had nothing to
     * say and the model said so instead.
     */
    val sources: List<String> = emptyList(),

    /**
     * True when the model declined because the database did not contain the
     * answer.
     *
     * This is a *good* outcome and the interface says so. "I don't know" must
     * never look like a failure, or the user will stop believing it.
     */
    val databaseHadNoAnswer: Boolean = false,

    /**
     * True when the answer came from the model's own memory, with the database
     * switched off.
     *
     * Flagged separately from [sources] being empty, because "no database" and
     * "database had nothing to say" mean very different things to somebody
     * relying on the answer.
     */
    val answeredWithoutDatabase: Boolean = false,

    /** Flexible database replies may include clearly labelled outside background. */
    val mayIncludeGeneralKnowledge: Boolean = false,

    /**
     * True when the model failed or the answer was stopped early.
     *
     * Kept in the record so a saved conversation does not show a half-written
     * answer as though it were complete.
     */
    val incomplete: Boolean = false,

    /** Database identity or general chat; prevents replay across unrelated contexts. */
    val contextKey: String? = null,
)