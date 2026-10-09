package com.dataeater.app.chat

/** Serializes background text callbacks and rejects callbacks after completion or stop. */
class StreamingAnswer {
    private val text = StringBuilder()
    private var finished = false

    @Synchronized fun append(value: String): Boolean {
        if (finished) return false
        text.append(value)
        return true
    }

    @Synchronized fun pending(): String? = if (finished) null else text.toString()

    @Synchronized fun finish(suffix: String = ""): String {
        if (!finished) text.append(suffix)
        finished = true
        return text.toString()
    }
}
