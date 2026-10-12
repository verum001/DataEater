package com.dataeater.app.data

import android.content.Context
import android.content.SharedPreferences

/** Remembers an existing external database; an unavailable choice means none selected. */
object DatabaseChoice {

    const val ALL = "Search in several databases"
    private const val LEGACY_ALL = "All unlocked databases"

    fun choices(available: Collection<String>): List<String> =
        if (available.isEmpty()) emptyList() else listOf(ALL) + available.filter { it != ALL }

    private const val PREFS_NAME = "dataeater-settings"
    private const val KEY_LAST_DATABASE = "last-database"

    /** A missing, legacy built-in, or blank choice never silently opens another file. */
    fun restore(stored: String?, available: Collection<String>): String {
        val wanted = stored?.trim().orEmpty().let { if (it == LEGACY_ALL) ALL else it }
        return if (wanted in choices(available)) wanted else ""
    }

    /** Reads the remembered choice, or null on a first run. */
    fun stored(context: Context): String? =
        prefs(context).getString(KEY_LAST_DATABASE, null)?.let { if (it.trim() == LEGACY_ALL) ALL else it }

    /** Remembers a choice. An empty or blank name forgets it entirely. */
    fun remember(context: Context, name: String) {
        val value = name.trim()
        prefs(context).edit().apply {
            if (value.isEmpty()) remove(KEY_LAST_DATABASE) else putString(KEY_LAST_DATABASE, value)
        }.apply()
    }

    /** Forgets the choice, so no database is selected next launch. */
    fun forget(context: Context) {
        prefs(context).edit().remove(KEY_LAST_DATABASE).apply()
    }

    fun selection(context: Context, sessionId: String? = null): Set<String> =
        prefs(context).getStringSet(sessionId?.let { "databases:$it" } ?: "multiple-databases", emptySet()).orEmpty().toSet()

    fun rememberSelection(context: Context, names: Set<String>, sessionId: String?) {
        prefs(context).edit().putStringSet("multiple-databases", names.toSet()).apply {
            if (sessionId != null) putStringSet("databases:$sessionId", names.toSet())
        }.apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}