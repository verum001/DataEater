package com.dataeater.app.builder

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONObject
import java.io.File

class BuilderFiles(private val context: Context) {
    data class Input(val uri: Uri, val name: String)
    fun input(uri: Uri): Input {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document"
        return Input(uri, name)
    }
    fun folder(uri: Uri): List<Input> {
        val result = mutableListOf<Input>(); val visited = mutableSetOf<String>()
        fun visit(id: String, depth: Int) {
            BuilderCore.checkpoint(); require(depth <= 24 && visited.size < 10000) { "The folder has too many nested items." }
            if (!visited.add(id)) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id)
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    BuilderCore.checkpoint(); val child = cursor.getString(0); val name = cursor.getString(1)
                    if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) visit(child, depth + 1)
                    else if (supported(name)) {
                        result.add(Input(DocumentsContract.buildDocumentUriUsingTree(uri, child), name))
                        require(result.size <= 1000) { "Build a smaller document collection." }
                    }
                }
            } ?: error("The folder could not be read. Choose it again.")
        }
        visit(DocumentsContract.getTreeDocumentId(uri), 0)
        require(result.isNotEmpty()) { "No PDF, text or Markdown documents were found." }
        return result.sortedBy { it.name.lowercase() }
    }
    fun copyReviewFolder(uri: Uri, destination: File) {
        require(!destination.exists()); destination.mkdirs()
        var count = 0; var total = 0L
        fun visit(id: String, parent: File, depth: Int) {
            require(depth <= 1) { "Unexpected nested folder in review." }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id)
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    BuilderCore.checkpoint(); count++; require(count <= 10000) { "Too many review files." }
                    val child = cursor.getString(0); val name = cursor.getString(1); val directory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    val target = BuilderReview.safe(parent, name)
                    if (directory) {
                        require(depth == 0 && name in setOf("original", "reviewed", "notes")) { "Choose a review folder containing review.json, original and reviewed." }
                        require(!target.exists()); target.mkdir(); visit(child, target, depth + 1)
                    } else {
                        require(depth == 1 || name in setOf("review.json", "LLM_PROMPT.txt", "README.md")) { "Unexpected file in review folder." }
                        require(!target.exists()) { "Duplicate review filename." }
                        copy(DocumentsContract.buildDocumentUriUsingTree(uri, child), target, 32L * 1024 * 1024)
                        total += target.length(); require(total <= BuilderCore.MAX_REVIEW_BYTES) { "The review is too large for the phone." }
                    }
                }
            } ?: error("The review folder could not be read.")
        }
        visit(DocumentsContract.getTreeDocumentId(uri), destination, 0)
    }
    fun supported(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("pdf", "txt", "md", "markdown")
    fun copy(uri: Uri, target: File, limit: Long = 256L * 1024 * 1024) {
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output ->
            val buffer = ByteArray(65536); var total = 0L
            while (true) { BuilderCore.checkpoint(); val n = input.read(buffer); if (n < 0) break
                total += n; require(total <= limit) { "This document is too large to process on the phone. Use a smaller file." }; output.write(buffer, 0, n) }
        } } ?: error("The selected file could not be read. Choose it again.")
    }
    fun record(uri: Uri): JSONObject = context.contentResolver.openInputStream(uri)?.use {
        JSONObject(BuilderCore.bounded(it, 65536).toString(Charsets.UTF_8))
    } ?: error("The key file could not be read.")
    fun extract(input: Input, temporary: File): BuilderCore.Document {
        require(supported(input.name)) { "Choose PDF, TXT or Markdown documents." }
        val source = File(temporary, "source-${java.util.UUID.randomUUID()}.${input.name.substringAfterLast('.')}")
        copy(input.uri, source)
        try {
            val fingerprint = java.security.MessageDigest.getInstance("SHA-256").let { digest ->
                source.inputStream().use { stream -> val buffer = ByteArray(65536); while (true) { BuilderCore.checkpoint(); val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
                digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            }
            val title = input.name.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
            if (input.name.endsWith(".pdf", true)) {
                PDFBoxResourceLoader.init(context.applicationContext)
                val memory = MemoryUsageSetting.setupMixed(16L * 1024 * 1024).setTempDir(temporary)
                return PDDocument.load(source, memory).use { document ->
                    require(document.currentAccessPermission.canExtractContent()) { "This PDF does not permit text extraction." }
                    require(document.numberOfPages in 1..10000) { "The PDF has too many pages. Split it into smaller documents." }
                    val headings = sortedMapOf<Int, String>(); var outlineCount = 0
                    fun outline(first: PDOutlineItem?, parents: List<String>, depth: Int) {
                        require(depth <= 24) { "The PDF outline is too deeply nested." }
                        var item = first
                        while (item != null) {
                            BuilderCore.checkpoint(); outlineCount++; require(outlineCount <= 10000) { "The PDF has too many bookmarks." }
                            val label = item.title.orEmpty().trim().take(200); val chain = if (label.isEmpty()) parents else parents + label
                            try {
                                val page = item.findDestinationPage(document)
                                if (page != null) {
                                    val index = document.pages.indexOf(page) + 1
                                    if (index > 0) headings[index] = chain.joinToString(" > ").take(2000)
                                }
                            } catch (_: java.io.IOException) { /* A broken bookmark must not change extracted facts. */ }
                            outline(item.firstChild, chain, depth + 1)
                            item = item.nextSibling
                        }
                    }
                    outline(document.documentCatalog.documentOutline?.firstChild, emptyList(), 0)
                    val stripper = PDFTextStripper().apply { sortByPosition = true; paragraphEnd = "\n\n" }
                    val pages = mutableListOf<BuilderCore.Page>(); var omitted = 0; var textBytes = 0L; var contextPath = ""
                    for (index in 1..document.numberOfPages) {
                        BuilderCore.checkpoint(); if (headings.containsKey(index)) contextPath = headings.getValue(index)
                        stripper.startPage = index; stripper.endPage = index
                        val text = clean(stripper.getText(document)); textBytes += text.toByteArray().size
                        require(textBytes <= BuilderCore.MAX_TEXT_BYTES) { "This PDF has too much text to build safely on the phone. Split it into smaller documents." }
                        if (text.length >= 100) pages.add(BuilderCore.Page(index, text, context = contextPath)) else omitted++
                    }
                    val sparse = pages.isEmpty() || pages.sumOf { it.text.length }.toLong() / document.numberOfPages < 200
                    val warning = if (sparse) "${input.name}: little usable text. Scanned pages need OCR outside this app; missing content is not reconstructed." else if (omitted > 0) "${input.name}: $omitted pages had very little extractable text and were omitted. Compare them against the original." else ""
                    val metadata = document.documentInformation.title.orEmpty().trim()
                    val chosen = metadata.takeIf { it.length > 6 && it.any(Char::isLowerCase) } ?: title
                    BuilderCore.Document(chosen, input.name, fingerprint, document.numberOfPages, omitted, pages, warning)
                }
            }
            require(source.length() <= BuilderCore.MAX_TEXT_BYTES) { "This text document is too large. Split it into smaller files." }
            var text = clean(source.readText())
            if (input.name.substringAfterLast('.').lowercase() in setOf("md", "markdown")) text = text.lines().joinToString("\n") {
                if (it.trimStart().startsWith('#')) "\n" + it.trimStart().trimStart('#').trim() + "\n" else it
            }.trim()
            val pages = if (text.contains("=== PAGE ")) BuilderReview.parse(text) else if (text.isNotEmpty()) listOf(BuilderCore.Page(1, text)) else emptyList()
            return BuilderCore.Document(title, input.name, fingerprint, pages.lastOrNull()?.number ?: 1, 0, pages)
        } finally { source.delete() }
    }
    private fun clean(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')
        .filter { it == '\n' || it == '\t' || it.code >= 32 }.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
}
