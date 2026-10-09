package com.dataeater.app.ai

/**
 * Shared streaming interface for on-device and explicitly selected online AI.
 *
 * This interface is the reason the app is not tied to one particular
 * library. Today [LiteRtAiEngine] is the only implementation, but we can
 * add llama.cpp or anything else later without changing the rest of the app.
 *
 * The interface deliberately has only one real operation: "here is some
 * text, give me an answer, and tell me as the words appear".
 */
interface LocalAiEngine {

    /** Name of the model, shown in the interface so the user always knows. */
    val modelName: String

    /**
     * Puts the model into memory.
     *
     * This is slow - on a phone it can take several seconds - so it is a
     * separate step that the user can trigger once and reuse many times.
     */
    suspend fun load()

    /**
     * Asks the model a question.
     *
     * [onNewText] is called repeatedly as the answer is produced, so the
     * interface can show the answer appearing word by word. The pieces arrive
     * in order as new text; the caller only has to append them.
     */
    suspend fun generate(
        prompt: String,
        mode: AnswerMode = AnswerMode.DATABASE,
        history: List<com.dataeater.app.chat.ChatMessage> = emptyList(),
        behavior: ReplyBehavior = ReplyBehavior(),
        onNewText: (String) -> Unit,
    )

    /** Frees the model from memory. */
    fun close()
}