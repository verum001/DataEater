package com.dataeater.app.ai

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs a local AI model on the phone using Google LiteRT-LM.
 *
 * Inference happens on the device; this engine has no network calls.
 * The app's separate downloader only requests public model files.
 *
 * @param preferGpu try the phone's GPU first. The GPU is much faster, but
 *   some phone drivers do not work with it. The user can turn this off,
 *   and the CPU is always available as a fallback.
 */
class LiteRtAiEngine(
    private val modelFile: File,
    private val cacheDirectory: File?,
    private val preferGpu: Boolean,
) : LocalAiEngine {

    override val modelName: String = modelFile.nameWithoutExtension

    /** "none", "GPU" or "CPU" - shown in the interface so the user knows. */
    val backendInUse: String get() = backendInUseName
    private var backendInUseName = "none"

    /** Null until [load] has finished. */
    private var engine: Engine? = null

    override suspend fun load() {
        // Loading twice would waste memory, so we simply return.
        if (engine != null) return

        Log.i(LOG_TAG, "load() starting for ${modelFile.absolutePath}")

        // Everything in this block runs on a background thread. Loading a
        // model on the main thread would freeze the screen for several
        // seconds, and Android would eventually show "app not responding".
        withContext(Dispatchers.IO) {
            // We try the preferred backend first and the other one second.
            val profile = ModelCatalog.forFile(modelFile.name)
            val order = if (profile?.cpuOnly == true) {
                listOf(Backend.CPU() to "CPU")
            } else if (preferGpu) {
                listOf(Backend.GPU() to "GPU", Backend.CPU() to "CPU")
            } else {
                listOf(Backend.CPU() to "CPU", Backend.GPU() to "GPU")
            }

            val problems = StringBuilder()
            for ((backend, backendName) in order) {
                Log.i(LOG_TAG, "trying backend $backendName")
                val problem = tryLoadWith(backend, backendName)
                if (problem == null) {
                    Log.i(LOG_TAG, "load() succeeded with $backendName")
                    return@withContext
                }
                Log.w(LOG_TAG, "backend $backendName failed: $problem")
                problems.append("$backendName said: $problem. ")
            }

            throw IllegalStateException(
                "Could not load the model. $problems"
            )
        }
    }

    /**
     * Tries to load the model with one backend.
     *
     * @return null on success, or a short description of what went wrong.
     */
    private fun tryLoadWith(backend: Backend, backendName: String): String? {
        var candidate: Engine? = null
        return try {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                // A writable folder that lets the runtime cache the model
                // between runs, which makes the second load much faster.
                cacheDir = cacheDirectory?.absolutePath,
            )

            val newEngine = Engine(config)
            candidate = newEngine
            newEngine.initialize() // Can take several seconds.
            engine = newEngine
            backendInUseName = backendName
            null
        } catch (problem: Exception) {
            runCatching { candidate?.close() }
            problem.message ?: problem.javaClass.simpleName
        }
    }

    override suspend fun generate(
        prompt: String,
        mode: AnswerMode,
        history: List<com.dataeater.app.chat.ChatMessage>,
        behavior: ReplyBehavior,
        onNewText: (String) -> Unit,
    ) {
        val activeEngine = engine
            ?: error("The model is not loaded yet. Press Load model first.")

        withContext(Dispatchers.IO) {
            Log.i(LOG_TAG, "generate() starting, prompt length ${prompt.length}")
            // Each question gets a brand new conversation. That way the model
            // can never remember an earlier, unrelated question and mix the
            // two answers together.
            val inlineMemory = ModelCatalog.forFile(modelFile.name)?.inlineHistory == true
            val conversation = activeEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(behavior.instruction(mode)),
                    initialMessages = (if (inlineMemory) emptyList() else history).map { if (it.fromUser) Message.user(it.text) else Message.model(it.text) },
                    // A low temperature makes the model stick closely to the
                    // facts we give it instead of inventing something more
                    // interesting. For technical answers, boring is correct.
                    samplerConfig = SamplerConfig(
                        topK = 20,
                        topP = 0.9,
                        temperature = if (mode == AnswerMode.DATABASE) 0.1 else 0.4,
                    ),
                )
            )
            Log.i(LOG_TAG, "conversation created")

            conversation.use { activeConversation ->

                // finished is how we know the answer is complete.
                val finished = CompletableDeferred<Unit>()

                // LiteRT-LM 0.17 callback messages contain new text, not a cumulative answer.
                var callbackCount = 0
                var receivedText = false

                Log.i(LOG_TAG, "calling sendMessageAsync()")
                activeConversation.sendMessageAsync(
                    text = if (inlineMemory && history.isNotEmpty()) buildString {
                        appendLine("Earlier conversation (context, not reference evidence):")
                        history.forEach { appendLine("${if (it.fromUser) "User" else "Assistant"}: ${it.text}") }
                        appendLine()
                        append(prompt)
                    } else prompt,
                    // A cap, so one badly chosen question cannot make the
                    // phone generate text for an hour.
                    maxOutputToken = ModelCatalog.forFile(modelFile.name)?.maxOutputTokens ?: MAX_OUTPUT_TOKENS,
                    // New reasoning bundles need a bounded thought budget. Their
                    // declared thought channel is separate from Message.contents;
                    // only the answer is streamed. Existing models keep thinking off.
                    thinkingConfig = ThinkingConfig(
                        enableThinking = (ModelCatalog.forFile(modelFile.name)?.thinkingBudget ?: 0) > 0,
                        thinkingTokenBudget = ModelCatalog.forFile(modelFile.name)?.thinkingBudget ?: 0,
                    ),
                    callback = object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val fullText = message.toString()
                            callbackCount++
                            if (callbackCount <= 3 || callbackCount % 20 == 0) {
                                Log.i(
                                    LOG_TAG,
                                    "onMessage #$callbackCount, ${fullText.length} chars",
                                )
                            }
                            if (fullText.isNotEmpty() && !finished.isCompleted) { receivedText = true; onNewText(fullText) }
                        }

                        override fun onDone() {
                            Log.i(LOG_TAG, "onDone() after $callbackCount callbacks")
                            finished.complete(Unit)
                        }

                        override fun onError(throwable: Throwable) {
                            Log.w(LOG_TAG, "onError()", throwable)
                            finished.completeExceptionally(throwable)
                        }
                    },
                )

                // Wait until the model says it is finished.
                try {
                    finished.await()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    finished.cancel()
                    runCatching { activeConversation.cancelProcess() }
                    throw cancelled
                }
                check(receivedText) { "This model returned no answer. Please choose another model." }
                Log.i(LOG_TAG, "generate() finished")
            }
        }
    }

    override fun close() {
        engine?.close()
        engine = null
        backendInUseName = "none"
    }

    private companion object {
        const val LOG_TAG = "DataEaterAI"

        const val MAX_OUTPUT_TOKENS = 512

    }
}
