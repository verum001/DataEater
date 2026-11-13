package com.dataeater.app.builder

/** Preserve useful native text and never concatenate two readings of the same page. */
object BuilderOcrPolicy {
    fun needsOcr(native: String) = native.length < 100
    data class Choice(val text: String, val status: String) {
        val used get() = status == "used"
    }
    fun choose(native: String, recognised: String): Choice {
        val letters = recognised.count(Char::isLetterOrDigit)
        return when {
            letters < 24 -> Choice(native, "no_usable_text")
            letters <= native.count(Char::isLetterOrDigit) -> Choice(native, "not_improved")
            else -> Choice(recognised, "used")
        }
    }
}
