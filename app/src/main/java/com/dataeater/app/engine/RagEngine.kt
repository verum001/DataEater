package com.dataeater.app.engine

import com.dataeater.app.model.Chunk
import com.dataeater.app.model.SourceInfo

/**
 * Joins the database to the AI: finds the relevant text, then builds a prompt
 * that tells the model to answer only from that text.
 *
 * This is the "RAG" step - Retrieval Augmented Generation.
 *
 * Why this is the most important class in the app:
 *
 * A language model on its own will happily invent a plausible number. For a
 * diagnostic tool that is dangerous - a wrong pressure or a wrong torque
 * value destroys hardware. So we never let the model answer from memory.
 * We look up the text first, and we tell the model to use nothing else.
 *
 * This file is deliberately free of Android code, so it can be tested on the
 * computer without a phone. That is where its behaviour is proven.
 */
class RagEngine(
    chunks: List<Chunk>,
    private val sources: List<SourceInfo>,
) {

    private val searchEngine = SearchEngine(chunks)
    private val titlesById = sources.associate { it.id to it.title }

    /**
     * How many pieces of text this database holds.
     *
     * Needed when a question matches nothing: the honest answer is "nothing
     * here matches", but the useful answer also says what *was* searched, or
     * the user cannot tell a missing answer from the wrong database.
     */
    val chunkCount: Int get() = searchEngine.chunkCount

    /** How many documents the text came from. */
    val documentCount: Int get() = sources.size

    /** One chunk that was found in the database, with its citation ready. */
    data class RetrievedChunk(
        val chunk: Chunk,
        val sourceTitle: String,
        val score: Double,
    ) {
        /** For example: "HF-4500 Service Manual - 3.1 Fuel Pressure Testing - page 11" */
        val citation: String
            get() = "$sourceTitle — ${chunk.section} — page ${chunk.page}"
    }

    /**
     * Finds the passages in the database that best answer [question].
     * An empty result means the database has nothing about this question.
     */
    fun retrieve(question: String, limit: Int = DEFAULT_CHUNK_COUNT): List<RetrievedChunk> =
        searchEngine.search(question, limit = if (limit == DEFAULT_CHUNK_COUNT && Regex("(?i)\\b(strokes|stages|steps|components|types)\\b").containsMatchIn(question)) 6 else limit).map { result ->
            RetrievedChunk(
                chunk = result.chunk,
                sourceTitle = result.chunk.sourceId.let { titlesById[it] ?: it },
                score = result.score,
            )
        }

    /** Include whole passages within the model budget, without cutting numbers or warnings. */
    fun fitContext(retrieved: List<RetrievedChunk>, budget: Int, textCost: (String) -> Int = { it.length }): List<RetrievedChunk> {
        val first = retrieved.firstOrNull() ?: return emptyList()
        if (textCost(first.chunk.text) + textCost(first.sourceTitle) + textCost(first.chunk.section) + 60 > budget) return emptyList()
        var remaining = budget
        return retrieved.distinctBy { it.chunk.text }.filter {
            val size = textCost(it.chunk.text) + textCost(it.sourceTitle) + textCost(it.chunk.section) + 60
            if (size <= remaining) { remaining -= size; true } else false
        }
    }

    /** Reference passages precede the question; grounding instructions use the native system role. */
    fun buildPrompt(question: String, retrieved: List<RetrievedChunk>, previousQuestion: String? = null): String =
        buildString {
            appendLine("REFERENCE TEXT:")
            retrieved.forEachIndexed { index, item ->
                appendLine()
                appendLine("[${index + 1}] ${item.sourceTitle}")
                appendLine("    Section ${item.chunk.section}, page ${item.chunk.page}")
                appendLine("    ${item.chunk.text}")
            }
            appendLine()
            if (previousQuestion != null) appendLine("Previous user question (subject only): $previousQuestion")
            appendLine("QUESTION: $question")

        }

    private companion object {
        /**
         * Three passages, not five. A small model gets confused by extra
         * text that does not help, so we give it only what is most useful.
         */
        const val DEFAULT_CHUNK_COUNT = 3

    }
}
