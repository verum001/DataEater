package com.dataeater.app.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Fixed service origin, no redirects, no prompt/key logging and no automatic retries. */
class OpenRouterClient internal constructor(private val baseUrl: String = "https://openrouter.ai/api/v1") : AutoCloseable {
    private val worker = Executors.newFixedThreadPool(4)
    private fun disconnectAsync(connection: HttpURLConnection?) {
        if (connection == null) return
        // Some URLConnection implementations wait for a blocking read while
        // disconnecting. Never perform that wait on the UI/cancellation thread.
        Thread({ connection.disconnect() }, "OpenRouter-disconnect").apply { isDaemon = true; start() }
    }
    private val active = AtomicReference<HttpURLConnection?>()
    private suspend fun <T> call(path: String, key: String?, body: String?, action: (HttpURLConnection) -> T): T = suspendCancellableCoroutine { continuation ->
        val future = worker.submit {
            var connection: HttpURLConnection? = null
            try {
                if (!continuation.isActive) return@submit
                connection = URL(baseUrl + path).openConnection() as HttpURLConnection
                active.set(connection)
                if (!continuation.isActive) return@submit
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.setRequestProperty("Accept", if (body == null) "application/json" else "text/event-stream")
                if (key != null) connection.setRequestProperty("Authorization", "Bearer $key")
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException(OpenRouterProtocol.error(code))
                val value = action(connection)
                if (continuation.isActive) continuation.resume(value)
            } catch (problem: Exception) {
                if (continuation.isActive) continuation.resumeWithException(
                    if (problem is IllegalStateException) problem else IllegalStateException("Could not connect to OpenRouter. Check your internet connection and try again.")
                )
            } finally {
                connection?.disconnect(); active.compareAndSet(connection, null)
            }
        }
        continuation.invokeOnCancellation { disconnectAsync(active.getAndSet(null)); future.cancel(false) }
    }
    suspend fun models(): List<OpenRouterProtocol.Model> = call("/models", null, null) { c ->
        val text = c.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder(); val buffer = CharArray(8192)
            while (true) { val n = reader.read(buffer); if (n < 0) break; result.append(buffer, 0, n); check(result.length <= 8_000_000) { "OpenRouter's model list is too large." } }
            result.toString()
        }
        OpenRouterProtocol.models(text)
    }
    suspend fun generate(key: String, body: String, onText: (String) -> Unit) = call("/chat/completions", key, body) { c ->
        check(c.contentType.orEmpty().substringBefore(';').trim().equals("text/event-stream", true)) { "OpenRouter returned an unexpected response. Please retry." }
        c.inputStream.reader(Charsets.UTF_8).use { reader ->
            OpenRouterProtocol.stream(reader) { text ->
                if (!Thread.currentThread().isInterrupted && active.get() === c) onText(text)
            }
        }
    }
    override fun close() { disconnectAsync(active.getAndSet(null)); worker.shutdown() }
}
class OpenRouterAiEngine(private var key: String, val modelId: String, label: String) : LocalAiEngine {
    override val modelName = "Online · $label"
    private val client = OpenRouterClient()
    override suspend fun load() { check(key.isNotBlank()) { "Add an OpenRouter API key in Settings." } }
    override suspend fun generate(prompt: String, mode: AnswerMode, history: List<com.dataeater.app.chat.ChatMessage>, behavior: ReplyBehavior, onNewText: (String) -> Unit) {
        client.generate(key, OpenRouterProtocol.request(modelId, prompt, mode, history, behavior), onNewText)
    }
    override fun close() { client.close(); key = "" }
}
