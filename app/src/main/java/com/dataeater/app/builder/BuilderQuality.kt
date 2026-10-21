package com.dataeater.app.builder

import org.json.JSONArray
import org.json.JSONObject

/** Review hints only; report contains counts/flags and hashes, never source text or keys. */
object BuilderQuality {
    const val LIMITATION = "Automatic flags are review hints, not technical approval. Check the original pages. Reading order, component ownership and table relationships are not verified. Restriction hints cover English/Russian; other text changes still need review. No AI is required."
    data class Metric(val page: Int, val characters: Int, val bytes: Int)
    data class Change(val page: Int, val flags: List<String>)
    private const val START = "(?<![\\p{L}\\p{M}\\p{N}_])"
    private const val END = "(?![\\p{L}\\p{M}\\p{N}_])"
    private val patterns = linkedMapOf(
        "numbers" to Regex("\\d+(?:[.,]\\d+)*"),
        "restrictions" to Regex(START + "(?:not|no|never|cannot|must|shall|prohibited|нельзя|не|нет|запрещено|должен|должны)" + END, RegexOption.IGNORE_CASE),
        "units" to Regex(START + "(?:bar|kPa|MPa|Pa|psi|mm|cm|km|m|kg|mg|g|Nm|rpm|V|mV|A|mA|W|kW|Hz|kHz|MHz|ohm|бар|кПа|МПа|мм|см|кг|Ом)" + END + "|[°Ωµμ%]"),
        "identifiers" to Regex(START + "(?:[a-zа-я]+[-_]?\\d[\\p{L}\\p{M}\\p{N}_-]*|\\d+[a-zа-я][\\p{L}\\p{M}\\p{N}_-]*)" + END, RegexOption.IGNORE_CASE),
        "numbered_steps" to Regex("^\\s*(\\d+(?:\\.\\d+)*)[.)]\\s+", RegexOption.MULTILINE))
    fun changes(old: String, new: String): List<String> {
        if (old == new) return emptyList()
        return listOf("text_changed") + patterns.mapNotNull { (name, pattern) ->
            fun matches(text: String) = pattern.findAll(text).map {
                val value = if (name == "numbered_steps") it.groupValues[1] else it.value
                if (name == "restrictions") value.lowercase() else value
            }.toList()
            if (matches(old) != matches(new)) "${name}_changed" else null
        }
    }
    fun source(doc: BuilderCore.Document, sid: String, passages: List<BuilderCore.Passage>): JSONObject {
        require(doc.totalPages in 0..10000 && doc.pages.all { it.number in 1..10000 }) { "Quality reports support at most 10000 original pages per document." }
        val pdf = doc.filename.endsWith(".pdf", true)
        val included = doc.pages.associateBy { it.number }; val measured = doc.pageMetrics.associateBy { it.page }
        val changes = doc.reviewChanges.associateBy { it.page }; val pages = JSONArray()
        for (number in 1..maxOf(doc.totalPages, included.keys.maxOrNull() ?: 0)) {
            BuilderCore.checkpoint()
            val page = included[number]; val prior = measured[number]; val flags = mutableListOf<String>()
            if (page == null) flags.add("omitted_page") else {
                if (page.text.length < 200) flags.add("sparse_text")
                if (pdf) flags.add("pdf_layout_not_verified")
                flags.addAll(changes[number]?.flags.orEmpty())
            }
            pages.put(JSONObject().put("page", number).put("included", page != null)
                .put("characters", page?.text?.length ?: prior?.characters ?: JSONObject.NULL)
                .put("utf8_bytes", page?.text?.toByteArray(Charsets.UTF_8)?.size ?: prior?.bytes ?: JSONObject.NULL)
                .put("reason", if (page != null) "included" else if (prior != null) "below_100_characters" else "not_in_extracted_text")
                .put("reading_order", if (pdf) "not_verified" else "not_assessed").put("flags", JSONArray(flags)))
        }
        return JSONObject().put("format", "dataeater-source-quality").put("version", 1).put("source_id", sid)
            .put("source_sha256", doc.fingerprint).put("total_pages", pages.length()).put("pages", pages)
            .put("oversized_passages", JSONArray(passages.filter { it.text.length > 1200 }.map {
                JSONObject().put("page", it.page).put("characters", it.text.length) }))
            .put("technical_accuracy", "not_verified").put("limitations", LIMITATION)
    }
    fun report(documents: List<BuilderCore.Document>, options: BuilderCore.Options): JSONObject {
        require(documents.sumOf { maxOf(it.totalPages, it.pages.maxOfOrNull { p -> p.number } ?: 0).toLong() } <= 20000L) { "This quality report is too large. Split the document collection." }
        val reports = documents.mapIndexed { index, doc -> source(doc, doc.id.ifBlank { "s${index + 1}" }, BuilderCore.passages(doc.pages, options.targetChars)) }
        return JSONObject().put("format", "dataeater-preparation-quality").put("version", 1)
            .put("documents", JSONArray(reports)).put("limitations", LIMITATION)
    }
    fun summary(report: JSONObject): String {
        var omitted = 0; var changed = 0; var pdf = 0
        val docs = report.getJSONArray("documents")
        for (i in 0 until docs.length()) {
            val pages = docs.getJSONObject(i).getJSONArray("pages")
            for (j in 0 until pages.length()) {
                val p = pages.getJSONObject(j); if (!p.getBoolean("included")) omitted++
                val flags = p.getJSONArray("flags").let { a -> (0 until a.length()).map { a.getString(it) } }
                if ("text_changed" in flags) changed++
                if ("pdf_layout_not_verified" in flags) pdf++
            }
        }
        return "Preparation quality: $omitted omitted pages; $changed changed pages; $pdf PDF pages need layout review. Automatic checks do not verify technical accuracy."
    }
}
