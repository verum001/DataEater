package com.dataeater.app.engine
import com.dataeater.app.model.Chunk
import kotlin.math.ln

/** Cached lexical index. Rare terms and matching headings outrank common repeated words. */
class SearchEngine(private val chunks: List<Chunk>) {
    val chunkCount: Int get() = chunks.size
    data class SearchResult(val chunk: Chunk, val score: Double, val matchedTerms: List<String>)
    private data class Indexed(val chunk: Chunk, val terms: Map<String, Int>, val headings: Set<String>, val length: Int)
    // Contents pages list topics, not facts. Preserve them in the database,
    // but keep dotted-leader lists out of question-answer evidence.
    private val index = chunks.filterNot { CONTENTS_LINE.findAll(it.text).count() >= 2 }.map { chunk ->
        val words = tokenize(chunk.text)
        Indexed(chunk, words.groupingBy { it }.eachCount(), tokenize(chunk.section + " " + chunk.searchContext).toSet(), words.size)
    }
    private val postings: Map<String, List<Int>> = buildMap {
        val mutable = mutableMapOf<String, MutableList<Int>>()
        index.forEachIndexed { id, row -> (row.terms.keys + row.headings).forEach { word -> mutable.getOrPut(word) { mutableListOf() }.add(id) } }
        putAll(mutable)
    }
    private val averageLength = index.map { it.length }.average().takeIf { it.isFinite() && it > 0 } ?: 1.0
    fun search(query: String, limit: Int = 10): List<SearchResult> {
        if (limit <= 0) return emptyList()
        val terms = tokenize(query).distinct()
        if (terms.isEmpty()) return emptyList()
        val scores = mutableMapOf<Int, Double>()
        val matched = mutableMapOf<Int, MutableList<String>>()
        for (term in terms) {
            val words = if (postings.containsKey(term)) listOf(term) else if (term.length >= 4 && term.all { it.isLetter() }) postings.keys.filter { it.startsWith(term) } else emptyList()
            val candidates = words.flatMap { postings[it].orEmpty() }.distinct()
            val rarity = ln(1.0 + (index.size - candidates.size + 0.5) / (candidates.size + 0.5))
            for (id in candidates) {
                val row = index[id]
                val frequency = words.sumOf { row.terms[it] ?: 0 }.toDouble()
                val norm = 1.2 * (0.25 + 0.75 * row.length / averageLength)
                val body = if (frequency > 0) frequency * 2.2 / (frequency + norm) else 0.0
                val heading = if (words.any { it in row.headings }) 0.65 else 0.0
                scores[id] = (scores[id] ?: 0.0) + rarity * (body + heading)
                matched.getOrPut(id) { mutableListOf() }.add(term)
            }
        }
        return scores.map { (id, score) ->
            val coverage = matched.getValue(id).size.toDouble() / terms.size
            val phrase = if (terms.size > 1 && index[id].chunk.text.lowercase().contains(query.trim().lowercase())) 1.25 else 1.0
            SearchResult(index[id].chunk, score * (0.5 + coverage) * phrase, matched.getValue(id))
        }.sortedByDescending { it.score }.take(limit)
    }
    private fun tokenize(text: String): List<String> = text.lowercase().split(WORD_SEPARATOR)
        .filter { it.length >= 2 && it !in STOP_WORDS }
        .map { if (it.length >= 5 && it.endsWith("s") && !it.endsWith("ss") && !it.endsWith("us") && !it.endsWith("is") && it.all { c -> c in 'a'..'z' }) it.dropLast(1) else it }
    private companion object {
        val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
        val CONTENTS_LINE = Regex("[.·…]{3,}\\s*\\d+(?:-\\d+)?\\s*(?:\\n|$)")
        val STOP_WORDS = setOf(
            // articles, pronouns and the verb "to be"
            "the", "a", "an", "it", "its", "they", "them", "their", "we", "us",
            "you", "your", "i", "he", "she", "his", "her",
            "is", "are", "was", "were", "be", "been", "being", "am",
            // prepositions and conjunctions
            "of", "to", "in", "on", "at", "by", "for", "with", "from", "as",
            "and", "or", "but", "if", "so", "than", "then", "that", "this",
            "these", "those", "there", "here", "into", "about", "after",
            "before", "over", "under", "up", "down", "out", "off",
            // question words - very common in our search box
            "what", "which", "when", "where", "how", "why", "who", "whose",
            "does", "do", "did", "can", "will", "would", "should", "could",
            // very common verbs and filler words
            "have", "has", "had", "not", "no", "yes", "get", "got", "make",
            "use", "using", "used", "one", "two", "may", "must", "shall",
            "all", "any", "some", "more", "most", "such", "also", "just",
            "very", "more", "most", "many", "much", "such",
        )
    }
}
