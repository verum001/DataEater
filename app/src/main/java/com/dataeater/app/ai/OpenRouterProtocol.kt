package com.dataeater.app.ai

import com.dataeater.app.chat.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.Reader

/** Wire parsing is independent of Android so malformed/partial streams can be tested. */
object OpenRouterProtocol {
    data class Model(val id: String, val name: String, val free: Boolean)
    fun request(model: String, prompt: String, mode: AnswerMode, history: List<ChatMessage>, behavior: ReplyBehavior = ReplyBehavior()): String {
        require(model.matches(Regex("[A-Za-z0-9_./:@+-]{1,200}"))) { "Choose a valid online model." }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", behavior.instruction(mode)))
        history.forEach { messages.put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text)) }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        return JSONObject().put("model", model).put("messages", messages).put("stream", true)
            .put("max_tokens", behavior.length.maxTokens).put("temperature", if (mode == AnswerMode.DATABASE) 0.1 else 0.4)
            .put("provider", JSONObject().put("data_collection", "deny")).toString()
    }
    fun models(json: String): List<Model> {
        val data = JSONObject(json).getJSONArray("data")
        return (0 until minOf(data.length(), 5000)).mapNotNull { i ->
            val m = data.optJSONObject(i) ?: return@mapNotNull null
            val id = m.optString("id")
            if (!id.matches(Regex("[A-Za-z0-9_./:@+-]{1,200}"))) return@mapNotNull null
            val architecture = m.optJSONObject("architecture")
            val inputs = architecture?.optJSONArray("input_modalities")
            val outputs = architecture?.optJSONArray("output_modalities")
            fun hasText(a: JSONArray?) = a == null || (0 until a.length()).any { a.optString(it) == "text" }
            if (!hasText(inputs) || !hasText(outputs) || m.optInt("context_length", 8192) < 8192) return@mapNotNull null
            val prices = m.optJSONObject("pricing")
            val free = prices?.optString("prompt")?.toDoubleOrNull() == 0.0 && prices.optString("completion").toDoubleOrNull() == 0.0
            Model(id, m.optString("name", id).filter { !it.isISOControl() }.take(160), free)
        }.distinctBy { it.id }.sortedWith(compareByDescending<Model> { it.free }.thenBy { it.name.lowercase() })
    }
    fun error(code: Int): String = when (code) {
        401, 403 -> "OpenRouter rejected the API key. Check it in Settings."
        402 -> "Your OpenRouter account needs more credits."
        429 -> "OpenRouter is busy or your request limit was reached. Try again later."
        400, 404 -> "This online model is unavailable or rejected the request. Choose another model."
        else -> "OpenRouter could not complete the request. Try again or choose another model."
    }
    fun stream(reader: Reader, onText: (String) -> Unit) {
        val input = reader.buffered()
        val event = StringBuilder()
        var output = 0
        var completed = false
        fun dispatch(): Boolean {
            if (event.isEmpty()) return false
            val data = event.toString(); event.setLength(0)
            if (data.trim() == "[DONE]") { completed = true; return true }
            val value = try { JSONObject(data) } catch (_: Exception) { throw IllegalStateException("OpenRouter returned an unreadable response.") }
            if (value.has("error")) throw IllegalStateException(error(value.optJSONObject("error")?.optInt("code", 500) ?: 500))
            val choice = value.optJSONArray("choices")?.optJSONObject(0)
            when (choice?.optString("finish_reason")) {
                "error" -> throw IllegalStateException(error(500))
                "length" -> throw IllegalStateException("The online answer reached its length limit. Choose a longer reply length or another model.")
                "content_filter" -> throw IllegalStateException("The online provider declined this request.")
            }
            val content = choice?.optJSONObject("delta")?.opt("content") as? String
            if (!content.isNullOrEmpty()) {
                output += content.length
                check(output <= 65536) { "The online answer was too long. Ask a narrower question." }
                onText(content)
            }
            return false
        }
        while (true) {
            val line = StringBuilder()
            while (true) {
                val c = input.read()
                if (c == -1 || c == 10) break
                if (c != 13) line.append(c.toChar())
                check(line.length <= 262144) { "OpenRouter returned an oversized response." }
            }
            if (line.isEmpty()) {
                if (dispatch()) break
            } else if (line.startsWith("data:")) {
                if (event.isNotEmpty()) event.append('\n')
                event.append(line.substring(5).removePrefix(" "))
                check(event.length <= 262144) { "OpenRouter returned an oversized response." }
            }
            // Detect EOF without relying on ready(), which also returns false during pauses.
            input.mark(1)
            val next = input.read()
            if (next == -1) { dispatch(); break }
            input.reset()
        }
        check(completed) { "The online connection ended before the answer finished. Please retry." }
        check(output > 0) { "This online model returned no answer. Choose another model." }
    }
}
