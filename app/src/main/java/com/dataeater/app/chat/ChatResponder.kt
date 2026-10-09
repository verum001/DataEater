package com.dataeater.app.chat

import com.dataeater.app.engine.RagEngine

/**
 * Turns a typed question into something the model can be asked, or into an
 * honest explanation of why the model should not be asked at all.
 *
 * WHY THIS IS NOT IN THE INTERFACE
 * --------------------------------
 * Every decision that changes what an answer *means* is here, away from
 * Compose, so it can be tested on a computer with no phone and no model file.
 * Those decisions are:
 *
 *   - there is no model loaded
 *   - the database is switched on, and it has nothing about this question
 *   - the database is switched on and has something
 *   - the database is switched **off**, so the model answers from memory
 *
 * That last one is the dangerous one. This app exists partly to stop a model
 * inventing a torque value, so the database-off case must be a *deliberate*
 * act that is labelled on the answer afterwards — never a silent fallback.
 *
 * WHY A DECISION IS RETURNED RATHER THAN A STRING
 * ----------------------------------------------
 * Because the interface has to say different things in each case. "Load a
 * model first", "nothing in this database matches", and "this answer is not
 * checked against any document" are three different sentences, and collapsing
 * them into one string would throw away the distinction.
 */
class ChatResponder(
    private val ragEngine: RagEngine?,
    private val databaseName: String,
    private val contextKey: String? = null,
    private val inputBudget: Int = 6000,
    private val textCost: (String) -> Int = { it.length },
    private val instructionReserve: Int = 700,
) {

    /** What should happen for one question. */
    sealed class Decision {

        /**
         * No model is loaded, so there is nothing to ask.
         * The conversation must not advance.
         */
        data class NoModel(val reason: String) : Decision()

        /**
         * The database is on but holds nothing about this question.
         *
         * The model is deliberately **not** asked. If it were, it would answer
         * from memory and there would be no way for the user to tell, because
         * the answer would look exactly like a sourced one.
         *
         * This is also fast: retrieval is a fraction of a millisecond, so an
         * unmatched question costs nothing.
         */
        data class NoPassages(
            val databaseName: String,
            val passageCount: Int,
            val documentCount: Int,
        ) : Decision()

        /**
         * The model should be asked, quoting the database.
         *
         * [sources] are carried alongside so the answer can be shown with its
         * citations. They are decided *before* generation starts, so a citation
         * is never lost because generation was stopped halfway.
         */
        data class FromDatabase(
            val prompt: String,
            val sources: List<String>,
            val history: List<ChatMessage> = emptyList(),
        ) : Decision()

        /**
         * The database is off, so the model answers from its own knowledge.
         *
         * The prompt says so explicitly. A model asked a bare question will
         * happily answer as though it were reading a manual.
         */
        data class WithoutDatabase(
            val prompt: String,
            val warning: String,
            val history: List<ChatMessage> = emptyList(),
        ) : Decision()
    }

    /**
     * Works out what to do.
     *
     * @param question      what the user typed
     * @param history       earlier turns of this conversation, oldest first
     * @param useDatabase   the database on/off toggle
     * @param hasModel      whether a model is loaded
     */
    fun decide(
        question: String,
        history: List<ChatMessage>,
        useDatabase: Boolean,
        hasModel: Boolean,
    ): Decision {
        val asked = question.trim()

        if (!hasModel) {
            return Decision.NoModel("Load a model first, in Settings.")
        }
        if (asked.isEmpty()) {
            return Decision.NoModel("Type a question first.")
        }

        val memory = ConversationMemory.select(history, useDatabase, contextKey, if (useDatabase) inputBudget / 5 else inputBudget / 3, textCost)

        if (textCost(asked) > inputBudget / 2) return Decision.NoModel("This question is too long for the model. Please ask a shorter question.")

        if (!useDatabase) {
            return Decision.WithoutDatabase(
                prompt = "QUESTION: $asked",
                history = memory,
                warning = NO_DATABASE_WARNING,
            )
        }

        val engine = ragEngine
            ?: return Decision.NoModel(
                "The database is not open yet. Choose one in Settings."
            )

        val searchQuestion = ConversationMemory.retrievalQuestion(asked, memory)
        val candidates = engine.retrieve(searchQuestion)
        val retrieved = engine.fitContext(candidates, (inputBudget - textCost(asked) * 2 - memory.sumOf { textCost(it.text) } - instructionReserve).coerceAtLeast(1), textCost)
        if (candidates.isNotEmpty() && retrieved.isEmpty()) return Decision.NoModel("The matching passage is too long for this model. Choose a larger model, or review the database text in the builder.")
        if (retrieved.isEmpty()) {
            return Decision.NoPassages(
                databaseName = databaseName,
                passageCount = engine.chunkCount,
                documentCount = engine.documentCount,
            )
        }

        return Decision.FromDatabase(
            prompt = engine.buildPrompt(asked, retrieved, if (searchQuestion != asked) memory.lastOrNull { it.fromUser }?.text else null),
            sources = retrieved.map { it.citation },
            history = memory,
        )
    }

    companion object {
        /**
         * Shown on every answer given with the database off.
         *
         * Deliberately blunt. A user who has just turned off the only thing
         * making these answers trustworthy deserves to be told, in the answer
         * itself, that nobody checked it.
         */
        const val NO_DATABASE_WARNING =
            "Answered without the database, from the model's own memory. " +
                "It has not been checked against any document and may be wrong."

        /**
         * Says plainly that there is no source, and asks for uncertainty rather
         * than a guess.
         *
         * A bare question gets a confident answer out of a small model. Naming
         * the lack of a document, and asking it to say when it is unsure, is
         * what turns that into something a technician can judge.
         */
        const val MEMORY_INSTRUCTIONS =
            "Answer from your general knowledge. You do NOT have any document " +
                "in front of you for this question, so you cannot look up a " +
                "specific value, part number or procedure.\n" +
                "If you are not sure of a specific value, say that you are not " +
                "sure instead of guessing. Keep it to one or two sentences."

        /** Keeps one earlier turn from filling the whole prompt. */
        const val MAX_HISTORY_CHARACTERS = 300
    }
}
