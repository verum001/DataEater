package com.dataeater.app.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns sessions to and from JSON.
 *
 * SPLIT FROM THE FILE HANDLING ON PURPOSE
 * --------------------------------------
 * `SessionStore` needs a real file system and a real `Context`, so it cannot be
 * tested on a plain JVM. This half is pure and can, which is where almost all
 * the risk lives: a field that is silently dropped on save, or read back as
 * the wrong type, loses a user's conversation without any error at all.
 *
 * Nothing is written unless it can be read back. The version number exists so
 * that a future change can tell an old file from a new one rather than guessing.
 */
object SessionCodec {

    /** Bumped if the shape ever changes incompatibly. */
    const val VERSION = 1

    fun encodeSession(session: Session): String =
        JSONObject().apply {
            put("version", VERSION)
            put("id", session.id)
            put("title", session.title)
            put("model_name", session.modelName ?: JSONObject.NULL)
            put("database_name", session.databaseName)
            put("use_database", session.useDatabase)
            put("prefer_gpu", session.preferGpu)
            put("updated_at", session.updatedAt)
            put("messages", JSONArray().apply {
                session.messages.forEach { put(encodeMessage(it)) }
            })
        }.toString()

    fun decodeSession(text: String): Session? = try {
        val json = JSONObject(text)
        // A file from a future version is refused rather than half-read.
        if (json.optInt("version", -1) > VERSION) {
            null
        } else {
            Session(
                id = json.getString("id"),
                title = json.optString("title", "Session"),
                messages = json.optJSONArray("messages").decodeMessages(),
                modelName = if (json.isNull("model_name")) null else json.optString("model_name"),
                databaseName = json.optString("database_name", ""),
                useDatabase = json.optBoolean("use_database", true),
                preferGpu = json.optBoolean("prefer_gpu", true),
                // Files written before this field existed read as 0, which
                // sorts them last. Losing drawer order is a far better outcome
                // than refusing to open an older conversation at all.
                updatedAt = json.optLong("updated_at", 0L),
            )
        }
    } catch (problem: Exception) {
        // A damaged file must not take the whole session list down with it.
        null
    }

    private fun encodeMessage(message: ChatMessage): JSONObject =
        JSONObject().apply {
            put("id", message.id)
            put("from_user", message.fromUser)
            put("text", message.text)
            put("sources", JSONArray(message.sources))
            put("no_answer", message.databaseHadNoAnswer)
            put("without_database", message.answeredWithoutDatabase)
            put("general_background", message.mayIncludeGeneralKnowledge)
            put("incomplete", message.incomplete)
            put("context_key", message.contextKey ?: JSONObject.NULL)
        }

    private fun JSONArray?.decodeMessages(): List<ChatMessage> {
        if (this == null) return emptyList()
        val result = ArrayList<ChatMessage>(length())
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            result.add(
                ChatMessage(
                    id = item.optString("id", "m$index"),
                    fromUser = item.optBoolean("from_user", false),
                    text = item.optString("text", ""),
                    sources = item.optJSONArray("sources").decodeStrings(),
                    databaseHadNoAnswer = item.optBoolean("no_answer", false),
                    answeredWithoutDatabase = item.optBoolean("without_database", false),
                    mayIncludeGeneralKnowledge = item.optBoolean("general_background", false),
                    incomplete = item.optBoolean("incomplete", false),
                    contextKey = if (item.isNull("context_key")) null else item.optString("context_key"),
                )
            )
        }
        return result
    }

    private fun JSONArray?.decodeStrings(): List<String> {
        if (this == null) return emptyList()
        val result = ArrayList<String>(length())
        for (index in 0 until length()) {
            optString(index, "").takeIf { it.isNotEmpty() }?.let { result.add(it) }
        }
        return result
    }
}