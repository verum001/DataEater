package com.dataeater.app.data

import android.content.Context
import android.content.SharedPreferences

/** Remembers an existing external database; an unavailable choice means none selected. */
object DatabaseChoice {

    private const val PREFS_NAME = "dataeater-settings"
    private const val KEY_LAST_DATABASE = "last-database"

    /** A missing, legacy built-in, or blank choice never silently opens another file. */
    fun restore(stored: String?, available: Collection<String>): String {
        val wanted = stored?.trim().orEmpty()
        return if (wanted in available) wanted else ""
    }

    /** Reads the remembered choice, or null on a first run. */
    fun stored(context: Context): String? =
        prefs(context).getString(KEY_LAST_DATABASE, null)

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

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}