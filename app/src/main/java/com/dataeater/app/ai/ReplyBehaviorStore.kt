package com.dataeater.app.ai

import android.content.Context

class ReplyBehaviorStore(context: Context) {
    private val preferences = context.getSharedPreferences("reply-behavior", Context.MODE_PRIVATE)
    fun read(): ReplyBehavior = ReplyBehavior(
        DatabaseStrictness.entries.firstOrNull { it.name == preferences.getString("strictness", null) } ?: DatabaseStrictness.STRICT,
        ReplyLength.entries.firstOrNull { it.name == preferences.getString("length", null) } ?: ReplyLength.NORMAL,
    )
    fun write(value: ReplyBehavior) { preferences.edit().putString("strictness", value.strictness.name).putString("length", value.length.name).apply() }
}
