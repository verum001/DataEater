package com.dataeater.app.chat

/** Bounded replay of complete turns. Saved chat remains intact; only model input is bounded. */
object ConversationMemory {
    fun select(history: List<ChatMessage>, database: Boolean, contextKey: String?, budget: Int, textCost: (String) -> Int = { it.length }): List<ChatMessage> {
        val turns = mutableListOf<List<ChatMessage>>()
        var user: ChatMessage? = null
        for (message in history) {
            if (message.fromUser) { user = message; continue }
            val question = user ?: continue
            user = null
            if (message.incomplete || message.databaseHadNoAnswer || message.text.isBlank()) continue
            val legacyGeneral = !database && (contextKey == null || contextKey == "general") && question.contextKey == null && message.contextKey == null && message.answeredWithoutDatabase
            if (contextKey != null && !legacyGeneral && (question.contextKey != contextKey || message.contextKey != contextKey)) continue
            if (database && (message.answeredWithoutDatabase || message.sources.isEmpty())) continue
            if (!database && !message.answeredWithoutDatabase && message.sources.isNotEmpty()) continue
            turns += listOf(question, message)
        }
        val selected = mutableListOf<List<ChatMessage>>()
        var remaining = budget
        // Keep at most two short explicit user declarations from earlier turns.
        // These are verbatim conversation context, never generated database facts.
        val declaration = Regex("(?i)\\b(my|i am|i'm|call me|remember|мой|моя|меня зовут)\\b")
        val anchors = turns.filter { it[0].text.length <= 250 && it[1].text.length <= 350 && declaration.containsMatchIn(it[0].text) }.take(2)
        val pinned = anchors.filter { turn ->
            val cost = turn.sumOf { textCost(it.text) + 24 }
            if (cost <= remaining / 2) { remaining -= cost; true } else false
        }
        for (turn in turns.asReversed().filter { it !in pinned }.take(4)) {
            // Never cut a prior answer into an apparently complete statement.
            val cost = turn.sumOf { textCost(it.text) + 24 }
            if (cost > remaining) break
            selected.add(0, turn)
            remaining -= cost
        }
        val chosen = (selected + pinned).toSet()
        return turns.filter { it in chosen }.flatten()
    }

    fun retrievalQuestion(question: String, memory: List<ChatMessage>): String {
        val followup = Regex("(?i)\\b(it|its|they|them|that|those|this|same|other|above)\\b|^(and|what about|how often)\\b|(?i)\\b(это|его|ее|её|их|этот|этого)\\b")
        val previous = memory.lastOrNull { it.fromUser }?.text ?: return question
        return if (followup.containsMatchIn(question)) "$previous\n$question" else question
    }
}
