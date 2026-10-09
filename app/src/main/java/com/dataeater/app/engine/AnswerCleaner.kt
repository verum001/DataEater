package com.dataeater.app.engine

/**
 * Tidies up the text the AI model produced.
 *
 * Some models still print their internal reasoning as <think>...</think>
 * even when we ask them not to. That text is not an answer and only
 * confuses the user, so it is removed before the answer is shown.
 *
 * This is a safety net. The main fix is switching thinking mode off in the
 * AI engine.
 */
object AnswerCleaner {

    private val THINK_BLOCK = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
    private val THINK_TAGS = Regex("</?think>")

    /** Removes reasoning tags and tidies the whitespace. */
    fun clean(rawAnswer: String): String {
        var text = rawAnswer.replace(THINK_BLOCK, "")
        text = text.replace(THINK_TAGS, "")

        // Remove the blank lines the removal leaves behind, and any leading
        // whitespace, but keep the answer's own line breaks.
        return text.trim().trimStart('\n', ' ', '\t')
    }
}