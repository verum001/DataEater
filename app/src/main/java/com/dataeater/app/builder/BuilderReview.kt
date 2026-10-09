package com.dataeater.app.builder

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

object BuilderReview {
    private val marker = Regex("^=== PAGE ([1-9][0-9]*) ===[ \\t]*$", RegexOption.MULTILINE)
    fun parse(text: String): List<BuilderCore.Page> {
        val matches = marker.findAll(text).toList()
        require(matches.isNotEmpty() && text.substring(0, matches.first().range.first).isBlank()) { "Keep the original page markers; remove commentary and Markdown fences." }
        val pages = matches.mapIndexed { index, match ->
            BuilderCore.Page(match.groupValues[1].toInt(), text.substring(match.range.last + 1, matches.getOrNull(index + 1)?.range?.first ?: text.length).trim())
        }
        require(pages.map { it.number } == pages.map { it.number }.distinct().sorted()) { "Page markers are duplicated or out of order." }
        return pages
    }
    fun render(pages: List<BuilderCore.Page>): String = pages.joinToString("") { "=== PAGE ${it.number} ===\n${it.text}\n\n" }
    fun safe(folder: File, name: String): File {
        require(name.isNotBlank() && name != "." && name != ".." && File(name).name == name && !name.contains('\\')) { "Invalid review filename." }
        return File(folder, name).also { require(it.canonicalFile.parentFile == folder.canonicalFile) { "Review files must stay in their folder." } }
    }
    fun export(documents: List<BuilderCore.Document>, folder: File, batchChars: Int, prompt: String) {
        require(!folder.exists()) { "Choose a new review workspace." }
        require(batchChars >= 1000) { "Batch size must be at least 1000 characters." }
        require(documents.isNotEmpty() && documents.all { it.pages.isNotEmpty() && !it.warning.contains("OCR") }) { "Scanned or empty documents need OCR outside the app before review." }
        require(documents.sumOf { it.pages.sumOf { p -> p.text.toByteArray().size.toLong() } } <= BuilderCore.MAX_TEXT_BYTES) { "Export a smaller document collection." }
        folder.mkdirs(); for (name in listOf("original", "reviewed", "notes")) File(folder, name).mkdir()
        val items = JSONArray()
        for ((index, document) in documents.withIndex()) {
            BuilderCore.checkpoint()
            val batches = mutableListOf<List<BuilderCore.Page>>(); var current = mutableListOf<BuilderCore.Page>(); var count = 0
            for (page in document.pages) {
                val length = render(listOf(page)).length
                if (current.isNotEmpty() && count + length > batchChars) { batches.add(current); current = mutableListOf(); count = 0 }
                current.add(page); count += length
            }
            if (current.isNotEmpty()) batches.add(current)
            val contexts = JSONObject()
            for (page in document.pages) if (page.paths.isNotEmpty() || page.context.isNotEmpty()) {
                contexts.put(page.number.toString(), JSONObject().put("paths", JSONObject(page.paths)).put("default", page.context))
            }
            val parts = JSONArray()
            for ((number, batch) in batches.withIndex()) {
                BuilderCore.checkpoint()
                val name = "document-%03d-part-%03d.txt".format(index + 1, number + 1); val text = render(batch)
                for (dir in listOf("original", "reviewed")) safe(File(folder, dir), name).writeText(text)
                parts.put(JSONObject().put("filename", name).put("pages", JSONArray(batch.map { it.number }))
                    .put("original_sha256", BuilderCore.sha(text.toByteArray())).put("characters", text.length))
            }
            items.put(JSONObject().put("id", "s${index + 1}").put("title", document.title).put("filename", document.filename)
                .put("source_sha256", document.fingerprint).put("total_pdf_pages", document.totalPages).put("omitted_minimal_text_pages", document.omitted)
                .put("page_contexts", contexts).put("parts", parts))
        }
        File(folder, "review.json").writeText(JSONObject().put("format", "dataeater-review").put("version", 1).put("documents", items).toString(2) + "\n")
        File(folder, "LLM_PROMPT.txt").writeText(prompt)
        File(folder, "README.md").writeText("# Review extracted text\n\nKeep original/ and review.json unchanged. Give LLM_PROMPT.txt and one original batch to a reviewer. Save complete checked text in the matching reviewed/ file, and uncertainties in notes/. Preserve all page markers. Verify technical corrections against the PDF. Build using Build reviewed text in DataEater, or dataeater-builder build-reviewed THIS_FOLDER -o result.dataeater on Linux. No upload or OCR is performed.\n")
    }
    data class Imported(val documents: List<BuilderCore.Document>, val numericChanges: Int)
    fun read(folder: File): Imported {
        val manifest = JSONObject(File(folder, "review.json").inputStream().use { BuilderCore.bounded(it, 1024 * 1024) }.toString(Charsets.UTF_8))
        require(manifest.optString("format") == "dataeater-review" && manifest.optInt("version") == 1) { "Unsupported review package." }
        val sources = manifest.getJSONArray("documents")
        require(sources.length() in 1..1000) { "Invalid review document count." }
        val ids = mutableSetOf<String>(); val files = mutableSetOf<String>(); val documents = mutableListOf<BuilderCore.Document>()
        var changes = 0; var bytes = 0L
        val numbers = Regex("\\d+(?:[.,]\\d+)*")
        for (index in 0 until sources.length()) {
            BuilderCore.checkpoint(); val source = sources.getJSONObject(index); val sid = source.getString("id")
            require(ids.add(sid)) { "Duplicate review document ID." }
            val parts = source.getJSONArray("parts"); val pages = mutableListOf<BuilderCore.Page>()
            require(parts.length() in 1..10000) { "Invalid review batch count." }
            for (partIndex in 0 until parts.length()) {
                BuilderCore.checkpoint(); val part = parts.getJSONObject(partIndex); val name = part.getString("filename")
                require(files.add(name)) { "Duplicate review batch." }
                val originalBytes = safe(File(folder, "original"), name).inputStream().use { BuilderCore.bounded(it) }.toString(Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n').toByteArray()
                require(BuilderCore.sha(originalBytes) == part.getString("original_sha256")) { "An original batch was modified: $name" }
                val reviewedBytes = safe(File(folder, "reviewed"), name).inputStream().use { BuilderCore.bounded(it) }
                bytes += originalBytes.size + reviewedBytes.size
                require(bytes <= BuilderCore.MAX_REVIEW_BYTES) { "The review package is too large. Split the collection." }
                val original = parse(originalBytes.toString(Charsets.UTF_8)); val reviewed = parse(reviewedBytes.toString(Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n'))
                val expected = part.getJSONArray("pages").let { list -> (0 until list.length()).map { list.getInt(it) } }
                require(original.map { it.number } == expected && reviewed.map { it.number } == expected) { "Missing, added or changed page marker in $name" }
                for ((old, new) in original.zip(reviewed)) {
                    require(old.text.isEmpty() || new.text.isNotEmpty()) { "A reviewed page is empty in $name" }
                    if (numbers.findAll(old.text).map { it.value }.toList() != numbers.findAll(new.text).map { it.value }.toList()) changes++
                }
                pages.addAll(reviewed)
            }
            val positions = pages.map { it.number }; val total = source.getInt("total_pdf_pages")
            require(positions.isNotEmpty() && positions == positions.distinct().sorted() && positions.last() <= total) { "Invalid page ranges across review batches." }
            require(!source.has("page_contexts") || source.get("page_contexts") is JSONObject) { "Invalid publisher metadata." }
            val contexts = source.optJSONObject("page_contexts") ?: JSONObject()
            val contextual = pages.map { page ->
                require(!contexts.has(page.number.toString()) || contexts.get(page.number.toString()) is JSONObject) { "Invalid publisher metadata." }
                val item = contexts.optJSONObject(page.number.toString()) ?: JSONObject()
                require(!item.has("paths") || item.get("paths") is JSONObject) { "Invalid publisher metadata." }
                require(!item.has("default") || item.get("default") is String) { "Invalid publisher metadata." }
                val paths = item.optJSONObject("paths") ?: JSONObject()
                val default = item.optString("default", ""); require(default.length <= 2000 && paths.length() <= 500) { "Invalid publisher metadata." }
                val mapping = paths.keys().asSequence().associateWith { key ->
                    val value = paths.get(key); require(key.length <= 200 && value is String && value.length <= 2000) { "Invalid publisher metadata." }; value as String
                }
                page.copy(paths = mapping, context = default)
            }
            documents.add(BuilderCore.Document(source.getString("title"), source.optString("filename"), source.optString("source_sha256"),
                total, source.optInt("omitted_minimal_text_pages"), contextual, id = sid))
        }
        return Imported(documents, changes)
    }
    fun zip(folder: File, output: File) {
        read(folder) // Never export a misleading review package that fails validation.
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            for (file in folder.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(folder).path }) {
                BuilderCore.checkpoint(); val name = file.relativeTo(folder).invariantSeparatorsPath
                require(file.canonicalPath.startsWith(folder.canonicalPath + File.separator)) { "Invalid review path." }
                zip.putNextEntry(ZipEntry(name)); file.inputStream().use { input ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) { BuilderCore.checkpoint(); val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 32L * 1024 * 1024); zip.write(buffer, 0, count) }
                }; zip.closeEntry()
            }
        }
    }
    fun unzip(input: File, folder: File) {
        require(!folder.exists()) { "Choose a new review workspace." }; folder.mkdirs()
        ZipFile(input).use { zip ->
            require(zip.size() <= 10000) { "Too many review files." }
            val manifestNames = zip.entries().asSequence().map { it.name }.filter { it == "review.json" || (it.endsWith("/review.json") && it.split('/').size == 2) }.toList()
            require(manifestNames.size == 1) { "The ZIP must contain one review workspace." }
            val prefix = manifestNames.single().removeSuffix("review.json")
            val entries = zip.entries(); val names = mutableSetOf<String>(); var total = 0L
            while (entries.hasMoreElements()) {
                BuilderCore.checkpoint(); val entry = entries.nextElement()
                if (entry.isDirectory && entry.name == prefix) continue
                require(entry.name.startsWith(prefix)) { "The ZIP contains files outside its review workspace." }
                val name = entry.name.removePrefix(prefix)
                require(names.add(name) && !name.contains('\\') && !name.startsWith('/') && name.split('/').none { it == ".." || it == "." }) { "Invalid review archive path." }
                val target = File(folder, name)
                require(target.canonicalPath.startsWith(folder.canonicalPath + File.separator)) { "Invalid review archive path." }
                if (entry.isDirectory) target.mkdirs() else {
                    require(name == "review.json" || name == "LLM_PROMPT.txt" || name == "README.md" ||
                        ((name.startsWith("original/") || name.startsWith("reviewed/") || name.startsWith("notes/")) && name.split('/').size == 2)) { "Unexpected file in review archive." }
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { stream -> target.outputStream().use { output ->
                        val buffer = ByteArray(65536); var countForFile = 0L
                        while (true) { BuilderCore.checkpoint(); val count = stream.read(buffer); if (count < 0) break
                            total += count; countForFile += count; require(total <= BuilderCore.MAX_REVIEW_BYTES && countForFile <= 32L * 1024 * 1024) { "Review archive is too large." }; output.write(buffer, 0, count) }
                    } }
                }
            }
        }
        read(folder)
    }
}
