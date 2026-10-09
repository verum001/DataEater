package com.dataeater.app.builder

import com.dataeater.app.data.DatabaseIntegrity
import com.dataeater.app.data.DatabaseLimits
import com.dataeater.app.data.DatabaseReader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Portable builder operations. Android file picking and PDF extraction live separately. */
object BuilderCore {
    const val VERSION = "android-1.4.0"
    const val MAX_TEXT_BYTES = 12 * 1024 * 1024
    const val MAX_REVIEW_BYTES = 64L * 1024 * 1024
    data class Page(val number: Int, val text: String, val paths: Map<String, String> = emptyMap(), val context: String = "")
    data class Document(val title: String, val filename: String, val fingerprint: String, val totalPages: Int,
        val omitted: Int, val pages: List<Page>, val warning: String = "", val id: String = "")
    data class Options(val name: String, val description: String = "", val language: String = "en",
        val license: String = "unknown", val publisher: String = "", val targetChars: Int = 700)
    data class Passage(val text: String, val page: Int, val section: String, val context: String)
    fun checkpoint() { if (Thread.currentThread().isInterrupted) throw InterruptedException("Cancelled") }
    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun id(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "database" }
    fun bounded(stream: InputStream, max: Long = DatabaseLimits.MAX_ENTRY_BYTES): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        var total = 0L
        while (true) {
            checkpoint()
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= max) { "This file is too large to process safely. Use a smaller document collection." }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }
    fun heading(line: String): Boolean {
        val s = line.trim(); val words = s.split(Regex("\\s+"))
        if (s.isEmpty() || s.length > 90 || s.none { it.isLetter() } || words.size > 14) return false
        if (s.any { it in "=×÷Ω" } || s.lowercase() in setOf("if", "then", "where", "where:", "equation 1", "equation 2")) return false
        if (words[0].firstOrNull()?.isDigit() == true && words[0].length <= 8 && words[0].any { it in ".)-" }) return true
        if (s.filter { it.isLetter() }.length >= 4 && s.filter { it.isLetter() }.all { it.isUpperCase() }) return true
        val titleWords = Regex("\\p{L}+(?:[’']\\p{L}+)?").findAll(s).map { it.value }.toList()
        return titleWords.isNotEmpty() && titleWords.all { it.first().isUpperCase() && it.drop(1).isNotEmpty() && it.drop(1).all { c -> !c.isLetter() || c.isLowerCase() } } &&
            !s.endsWith('.') && !s.endsWith(':') && s.none { it.isDigit() } && (titleWords.size >= 2 || s.length >= 5)
    }
    fun passages(pages: List<Page>, target: Int = 700): List<Passage> {
        require(target >= 100) { "Passage size must be at least 100 characters." }
        val output = mutableListOf<Passage>(); val buffer = mutableListOf<String>()
        var section = ""; var context = ""; var number = 1
        fun flush() {
            if (buffer.isNotEmpty()) {
                val item = Passage(buffer.joinToString("\n\n").trim(), number, section, context)
                val previous = output.lastOrNull()
                if (previous != null && previous.text.length < 200 && previous.page == item.page && previous.section == item.section &&
                    previous.context == item.context && previous.text.length + item.text.length + 2 <= 1200) {
                    output[output.lastIndex] = previous.copy(text = previous.text + "\n\n" + item.text)
                } else if (item.text.isNotEmpty()) output.add(item)
                buffer.clear()
            }
        }
        fun add(block: String) {
            val units = if (block.length > 1200) block.split(Regex("(?<=[.!?])\\s+(?=[A-Z])|\\n")) else listOf(block)
            val pieces = mutableListOf<String>(); var current = ""
            for (unit in units.filter { it.isNotBlank() }) {
                if (current.isNotEmpty() && current.length + unit.length + 1 > 1200) { pieces.add(current); current = "" }
                current += (if (current.isEmpty()) "" else "\n") + unit
            }
            if (current.isNotBlank()) pieces.add(current)
            for (piece in pieces) {
                if (buffer.isNotEmpty() && buffer.sumOf { it.length } + piece.length + 2 * buffer.size > target) flush()
                buffer.add(piece)
            }
        }
        for (page in pages) {
            checkpoint(); flush(); number = page.number
            if (page.context.isNotEmpty()) { context = page.context; section = context.substringAfterLast(" > ") }
            for (block in page.text.replace("\r\n", "\n").replace('\r', '\n').split("\n\n").filter { it.isNotBlank() }) {
                val segment = mutableListOf<String>()
                for (line in block.trim().split('\n')) {
                    val normalized = line.trim().replace(Regex("\\s+"), " ")
                    if (page.paths.containsKey(normalized) || (page.paths.isEmpty() && page.context.isEmpty() && heading(line))) {
                        if (segment.isNotEmpty()) add(segment.joinToString("\n"))
                        flush(); section = line.trim(); context = page.paths[normalized] ?: context
                        segment.clear(); segment.add(line)
                    } else segment.add(line)
                }
                if (segment.isNotEmpty()) add(segment.joinToString("\n"))
            }
        }
        flush(); return output
    }
    fun writeDatabase(documents: List<Document>, options: Options, output: File): String {
        require(options.name.isNotBlank()) { "Enter a database name." }
        require(documents.sumOf { it.pages.sumOf { p -> p.text.toByteArray().size.toLong() } } <= MAX_TEXT_BYTES) { "This collection is too large to build on a phone. Split it into smaller databases." }
        val sources = JSONArray(); val text = StringBuilder(); var count = 0
        for ((index, document) in documents.withIndex()) {
            checkpoint(); if (document.pages.isEmpty()) continue
            val sid = document.id.ifBlank { "s${index + 1}" }
            sources.put(JSONObject().put("id", sid).put("title", document.title).put("publisher", options.publisher)
                .put("language", options.language).put("license", options.license)
                .put("year", Regex("(?:19|20)\\d{2}").find(document.filename)?.value?.toInt() ?: 0))
            for (passage in passages(document.pages, options.targetChars)) {
                count++
                text.append(JSONObject().put("id", "c%03d".format(count)).put("source_id", sid).put("section", passage.section)
                    .put("page", passage.page).put("lang", options.language).put("text", passage.text).put("search_context", passage.context)).append('\n')
                require(text.length <= DatabaseLimits.MAX_ENTRY_BYTES / 2) { "Database text is too large. Build a smaller collection." }
            }
        }
        require(count > 0) { "No usable text was found. Scanned PDFs need OCR outside this app." }
        val sourceBytes = (sources.toString(2) + "\n").toByteArray(); val chunkBytes = text.toString().toByteArray()
        require(chunkBytes.size <= DatabaseLimits.MAX_ENTRY_BYTES && sourceBytes.size <= DatabaseLimits.MAX_ENTRY_BYTES) { "Database text is too large." }
        val manifest = JSONObject().put("format", "dataeater").put("format_version", 1).put("database_id", id(options.name))
            .put("name", options.name).put("description", options.description).put("language", options.language).put("license", options.license)
            .put("created_utc", Instant.now().toString()).put("builder_version", VERSION).put("encryption", "none")
            .put("counts", JSONObject().put("documents", sources.length()).put("chunks", count))
            .put("files", JSONObject().put("sources.json", JSONObject().put("sha256", "sha256:" + sha(sourceBytes)))
                .put("chunks.jsonl", JSONObject().put("sha256", "sha256:" + sha(chunkBytes)).put("records", count)))
        writeZip(output, linkedMapOf("manifest.json" to (manifest.toString(2) + "\n").toByteArray(), "sources.json" to sourceBytes, "chunks.jsonl" to chunkBytes))
        val report = inspect(output)
        return "Built ${options.name}: ${sources.length()} documents, $count passages.\n$report\n" + documents.mapNotNull { it.warning.takeIf(String::isNotBlank) }.joinToString("\n")
    }
    fun writeZip(output: File, entries: Map<String, ByteArray>) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            for ((name, bytes) in entries) { checkpoint(); zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }
    fun entries(file: File): Map<String, ByteArray> = ZipFile(file).use { zip ->
        require(zip.size() <= DatabaseLimits.MAX_ENTRIES) { "This archive has too many entries." }
        val names = mutableSetOf<String>(); val result = linkedMapOf<String, ByteArray>(); var total = 0L
        val items = zip.entries()
        while (items.hasMoreElements()) {
            checkpoint(); val entry = items.nextElement()
            require(names.add(entry.name)) { "This archive contains duplicate entries." }
            if (!entry.isDirectory) {
                val bytes = zip.getInputStream(entry).use { bounded(it) }; total += bytes.size
                require(total <= 64L * 1024 * 1024) { "This archive is too large to process on a phone." }
                result[entry.name] = bytes
            }
        }
        result
    }
    fun inspect(file: File): String {
        val all = entries(file)
        require(all.keys.firstOrNull() == "manifest.json") { "The manifest must be first in the database." }
        val manifestText = all["manifest.json"]?.toString(Charsets.UTF_8) ?: error("Missing manifest")
        val manifest = JSONObject(manifestText)
        DatabaseReader.parseManifest(manifestText)
        val files = manifest.getJSONObject("files")
        for (name in files.keys()) DatabaseIntegrity.verify(manifestText, name, all[name] ?: error("Missing $name"))
        val counts = manifest.getJSONObject("counts")
        if (manifest.optString("encryption", "none") != "none") {
            require(manifest.optString("encryption") == "aes-256-gcm") { "Unsupported encryption." }
            DatabaseIntegrity.verify(manifestText, "payload.enc", all["payload.enc"] ?: error("Missing encrypted content"))
            return "Fingerprints verified. Locked database: ${manifest.getString("name")}. ${counts.getInt("chunks")} passages. Text is encrypted."
        }
        val sourceText = all["sources.json"]?.toString(Charsets.UTF_8) ?: error("Missing sources")
        val chunkText = all["chunks.jsonl"]?.toString(Charsets.UTF_8) ?: error("Missing passages")
        DatabaseIntegrity.verify(manifestText, "sources.json", all.getValue("sources.json"))
        DatabaseIntegrity.verify(manifestText, "chunks.jsonl", all.getValue("chunks.jsonl"))
        val sources = DatabaseReader.parseSources(sourceText); val chunks = DatabaseReader.parseChunks(chunkText)
        require(sources.size == counts.getInt("documents") && chunks.size == counts.getInt("chunks")) { "Database counts do not match its contents." }
        require(sources.map { it.id }.toSet().size == sources.size && chunks.map { it.id }.toSet().size == chunks.size) { "Duplicate document or passage IDs." }
        require(chunks.all { c -> sources.any { it.id == c.sourceId } }) { "A passage has no matching source." }
        return "Fingerprints and counts verified. ${sources.size} documents, ${chunks.size} passages.\n" +
            "Long passages: ${chunks.count { it.text.length > 1200 }}. Repeated passages: ${chunks.size - chunks.map { it.text }.toSet().size}.\n" +
            "This checks structure, not technical accuracy.\n" + sources.joinToString("\n") { it.title } +
            (chunks.firstOrNull()?.let { "\n\nFirst passage (page ${it.page}):\n${it.text}" } ?: "")
    }
}
