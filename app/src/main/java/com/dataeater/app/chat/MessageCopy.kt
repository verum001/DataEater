package com.dataeater.app.chat

/**
 * What gets put on the clipboard when a message is copied.
 *
 * WHY THIS IS A SEPARATE, TESTED FUNCTION
 * ---------------------------------------
 * Because the copied text usually leaves the phone. It goes into a message to
 * a colleague, a work order, a fault report — places where nobody can see the
 * little "3 sources" line that was beside the answer.
 *
 * So the text has to carry its own evidence. An answer copied without its
 * citation becomes an unsourced claim somewhere else, which is the exact thing
 * this app exists to prevent, and it would happen silently.
 *
 * And an answer given with the database switched off has to say so *in the
 * copied text*. The control row said "Database off" and the session list will
 * still say it tomorrow, but the pasted text arrives in someone else's chat
 * with none of that around it.
 */
object MessageCopy {

    /**
     * The text for one message.
     *
     * The question is copied on its own — it is the user's own words and they
     * are not making a claim about anything.
     */
    fun forMessage(message: ChatMessage): String = buildString {
        append(message.text.trimEnd())

        if (message.fromUser) return@buildString
        if (message.mayIncludeGeneralKnowledge) {
            appendLine(); appendLine(); append("May include general knowledge outside the database. Check the labelled sections.")
        }

        // The safety line, in the pasted text, for exactly the case where it
        // matters and will be invisible.
        when {
            message.answeredWithoutDatabase -> {
                appendLine()
                appendLine()
                append(DATABASE_OFF_FOOTER)
            }

            message.sources.isNotEmpty() -> {
                appendLine()
                appendLine()
                // The newline after the heading is not optional. Without it the
                // first citation is pasted on the same line as the words
                // "From your documents:", which is both ugly and reads as if
                // the heading were part of the citation.
                append(SOURCES_HEADING).appendLine()
                message.sources.forEach { append("• ").appendLine(it) }
            }

            // A refusal is worth keeping with the answer: "the database does
            // not contain this" is a real finding and the colleague should not
            // have to ask what an empty answer meant.
            message.databaseHadNoAnswer -> {
                appendLine()
                appendLine()
                append(NO_ANSWER_FOOTER)
            }

            // A stopped answer must not travel looking finished.
            //
            // This was missing entirely at first, and it is the quiet version of
            // the same problem: the half-answer sat complete on screen, marked
            // only in the session record, so a copy made later would paste a
            // sentence ending in nothing and no sign that anything was wrong.
            message.incomplete -> {
                appendLine()
                appendLine()
                append(INCOMPLETE_FOOTER)
            }
        }
    }

    /**
     * States that the answer was not checked, for text pasted elsewhere.
     *
     * Deliberately not toned down to something polite like "from memory". The
     * whole point is that the reader of the pasted text cannot see this app and
     * must be told in the text itself.
     */
    const val DATABASE_OFF_FOOTER =
        "(Given without the database. Not checked against any document, and " +
            "it may be wrong.)"

    /** Introduces the citations. */
    const val SOURCES_HEADING = "From your documents:"

    /** Explains an empty answer, so it is not mistaken for a broken app. */
    const val NO_ANSWER_FOOTER =
        "(The database was searched and does not contain this.)"

    /**
     * Marks an answer that was cut short.
     *
     * Wording matters here: a half answer pasted into a fault report reads as a
     * complete instruction, and somebody could act on it.
     */
    const val INCOMPLETE_FOOTER =
        "(This answer was stopped before it finished, so it is incomplete.)"
}
