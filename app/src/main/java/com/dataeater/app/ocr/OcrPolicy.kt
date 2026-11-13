package com.dataeater.app.ocr

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

enum class OcrLanguage(val label: String, val code: String, val files: List<String>) {
    ENGLISH("English", "eng", listOf("eng")),
    RUSSIAN("Russian", "rus", listOf("rus")),
    BOTH("English + Russian", "eng+rus", listOf("eng", "rus")),
}

/** Shared OCR resource and quality limits, independent of any file-write capability. */
object OcrPolicy {
    const val MAX_PIXELS = 3_000_000L
    const val MAX_EDGE = 2200
    const val MIN_CONFIDENCE = 40
    const val MAX_TEXT = 6000
    data class Size(val width: Int, val height: Int)
    fun needsOcr(text: String) = text.count(Char::isLetterOrDigit) < 24
    fun renderSize(width: Double, height: Double): Size {
        require(width.isFinite() && height.isFinite() && width in 1.0..20000.0 && height in 1.0..20000.0) { "Unsupported PDF page dimensions." }
        val scale = min(2.5, min(MAX_EDGE / maxOf(width, height), sqrt(MAX_PIXELS / (width * height))))
        val size = Size(maxOf(1, floor(width * scale).toInt()), maxOf(1, floor(height * scale).toInt()))
        require(size.width.toLong() * size.height <= MAX_PIXELS && maxOf(size.width, size.height) <= MAX_EDGE)
        return size
    }
    fun usableText(text: String, confidence: Int): String = if (confidence !in MIN_CONFIDENCE..100) "" else
        text.filter { it == '\n' || it == '\t' || !it.isISOControl() }.trim().take(MAX_TEXT)
}
