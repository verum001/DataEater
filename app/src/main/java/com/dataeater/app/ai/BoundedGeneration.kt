package com.dataeater.app.ai

import com.dataeater.app.chat.ChatMessage
import com.dataeater.app.chat.ChatResponder
import kotlinx.coroutines.CancellationException

/** Retry rejected local input before any answer is streamed; never cut reference text. */
object BoundedGeneration {
    data class Request(val prompt: String, val mode: AnswerMode, val history: List<ChatMessage>, val sources: List<String>)
    data class Overflow(val used: Int, val limit: Int)
    fun overflow(error: Throwable): Overflow? {
        val messages = generateSequence(error) { it.cause?.takeUnless { cause -> cause === it } }.take(8).mapNotNull { it.message }.joinToString("\n")
        if (!messages.contains("Input token ids are too long", ignoreCase = true)) return null
        val match = Regex("(\\d+)\\s*>=\\s*(\\d+)").find(messages) ?: return null
        val used = match.groupValues[1].toIntOrNull() ?: return null
        val limit = match.groupValues[2].toIntOrNull() ?: return null
        return Overflow(used, limit).takeIf { used >= limit && limit > 0 }
    }
    fun nextBudget(current: Int, overflow: Overflow): Int {
        val room = (overflow.limit - minOf(512, overflow.limit / 3)).coerceAtLeast(1)
        return minOf(current - 1, (current.toLong() * room * 85 / overflow.used / 100).toInt()).coerceAtLeast(1)
    }
    fun request(decision: ChatResponder.Decision): Request = when (decision) {
        is ChatResponder.Decision.FromDatabase -> Request(decision.prompt, AnswerMode.DATABASE, decision.history, decision.sources)
        is ChatResponder.Decision.WithoutDatabase -> Request(decision.prompt, AnswerMode.GENERAL_KNOWLEDGE, decision.history, emptyList())
        is ChatResponder.Decision.NoModel -> throw IllegalArgumentException(decision.reason)
        is ChatResponder.Decision.NoPassages -> throw IllegalArgumentException("The matching database text does not fit this model. Try a shorter question or another model.")
    }
    suspend fun generate(
        engine: LocalAiEngine, initial: Request, initialBudget: Int, behavior: ReplyBehavior,
        replan: ((Int) -> Request)? = null, onSources: (List<String>) -> Unit = {}, onText: (String) -> Unit,
    ) {
        var request = initial; var budget = initialBudget; var retries = 0; var streamed = false
        while (true) {
            onSources(request.sources)
            try {
                engine.generate(request.prompt, request.mode, request.history, behavior) { text ->
                    if (text.isNotEmpty()) streamed = true
                    onText(text)
                }
                return
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val overflow = if (replan != null && !streamed) overflow(error) else null
                if (overflow == null) throw error
                if (retries++ >= 3) {
                    onSources(emptyList())
                    throw IllegalArgumentException("This question and its reference text are too long for this model. Try a shorter question or another model.")
                }
                budget = nextBudget(budget, overflow)
                val smaller = try { replan!!(budget) } catch (error: Exception) { onSources(emptyList()); throw error }
                if (smaller == request) {
                    onSources(emptyList())
                    throw IllegalArgumentException("The matching text is too long for this model. Choose another model or review the database passages in the builder.")
                }
                request = smaller
            }
        }
    }
}
