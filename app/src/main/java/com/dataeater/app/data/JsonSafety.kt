package com.dataeater.app.data

import org.json.JSONTokener

/** Bound parser recursion/allocation and reject duplicate (including escaped) field names. */
object JsonSafety {
    fun check(text: String) {
        val frames = mutableListOf<MutableSet<String>?>()
        var quoted = false; var escaped = false; var start = 0; var structure = 0; var roots = 0
        for (i in text.indices) {
            val c = text[i]
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') {
                    quoted = false
                    var next = i + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':' && frames.lastOrNull() != null) {
                        val key = JSONTokener(text.substring(start, i + 1)).nextValue() as? String
                            ?: throw DatabaseReader.DataEaterException("Invalid JSON field name.")
                        if (!frames.last()!!.add(key)) throw DatabaseReader.DataEaterException("Duplicate JSON field names are not permitted.")
                    }
                }
            } else when (c) {
                '"' -> { quoted = true; start = i }
                '{', '[' -> {
                    if (frames.isEmpty()) { roots++; if (roots > 1) throw DatabaseReader.DataEaterException("Multiple JSON roots are not permitted.") }
                    frames.add(if (c == '{') mutableSetOf() else null)
                    if (frames.size > 32) throw DatabaseReader.DataEaterException("The JSON structure is too deeply nested.")
                    structure++
                }
                '}', ']' -> { if (frames.isEmpty()) throw DatabaseReader.DataEaterException("Invalid JSON structure."); frames.removeAt(frames.lastIndex); structure++ }
                ',', ':' -> structure++
            }
            if (structure > 100000) throw DatabaseReader.DataEaterException("The JSON structure is too complex.")
        }
        if (quoted || frames.isNotEmpty() || roots != 1) throw DatabaseReader.DataEaterException("Invalid JSON structure.")
    }
}
