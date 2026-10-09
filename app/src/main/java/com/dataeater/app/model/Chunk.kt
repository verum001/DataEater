package com.dataeater.app.model

/**
 * One small piece of text from a database, together with where it came from.
 *
 * The source information (sourceId, section, page) is the reason a chunk
 * is more than a string. When the AI answers a question later we can show
 * the real document, section and page instead of inventing a citation.
 */
data class Chunk(
    /** Unique identifier inside the database, for example "c003". */
    val id: String,

    /** Which source document this text came from, matches a SourceInfo id. */
    val sourceId: String,

    /** Chapter or heading, for example "2.1 Routine Service". */
    val section: String,

    /** Page number in the original document. 0 if the source has no pages. */
    val page: Int,

    /** The actual text of this piece. */
    val text: String,

    /** Optional publisher section ancestry for search; not answer evidence. */
    val searchContext: String = "",
)

/** One document that the chunks came from. */
data class SourceInfo(
    val id: String,
    val title: String,
    val publisher: String,
    val year: Int,
)