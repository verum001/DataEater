package com.dataeater.app.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Public model metadata only. Never downloads or executes repository code. */
object HuggingFaceModels {
    data class Result(val entries: List<ModelCatalog.Entry>, val incomplete: Boolean)
    private val repoPattern = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
    fun valid(entry: ModelCatalog.Entry): Boolean =
        entry.id.matches(Regex("[a-zA-Z0-9_-]{1,80}")) && repoPattern.matches(entry.repository) && entry.repository.split("/").none { it == "." || it == ".." } &&
            entry.revision.matches(Regex("[0-9a-f]{40}")) && entry.sha256.matches(Regex("[0-9a-f]{64}")) &&
            entry.bytes in 1..32_000_000_000L && entry.fileName.matches(Regex("[a-zA-Z0-9_.-]+\\.litertlm")) &&
            entry.remoteFileName.endsWith(".litertlm") && entry.remoteFileName.split('/').all {
                it.isNotBlank() && it != "." && it != ".." && it.none { c -> c == '\\' || c.code < 32 || c == '?' || c == '#' }
            }

    fun parseRepository(text: String): List<ModelCatalog.Entry> {
        val repo = JSONObject(text)
        if (repo.optBoolean("private") || (repo.has("gated") && repo.opt("gated") != false && repo.opt("gated") != JSONObject.NULL)) return emptyList()
        val id = repo.optString("id"); val revision = repo.optString("sha")
        if (!repoPattern.matches(id) || !revision.matches(Regex("[0-9a-f]{40}"))) return emptyList()
        val siblings = repo.optJSONArray("siblings") ?: return emptyList()
        return (0 until minOf(siblings.length(), 1000)).mapNotNull { index ->
            val file = siblings.optJSONObject(index) ?: return@mapNotNull null
            val path = file.optString("rfilename")
            if (!path.endsWith(".litertlm")) return@mapNotNull null
            val lfs = file.optJSONObject("lfs") ?: return@mapNotNull null
            val hash = lfs.optString("sha256"); val bytes = lfs.optLong("size", file.optLong("size"))
            val known = ModelCatalog.forFile(path.substringAfterLast('/'))?.takeIf { it.repository == id && it.sha256 == hash }
            val token = MessageDigest.getInstance("SHA-256").digest("$id/$path:$hash".toByteArray())
                .joinToString("") { "%02x".format(it) }.take(16)
            val tested = ModelCatalog.entries.any { it.repository == id && it.remoteFileName == path && it.sha256 == hash }
            val localName = known?.fileName ?: path.substringAfterLast('/').removeSuffix(".litertlm")
                .replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(110) + "--$token.litertlm"
            val entry = ModelCatalog.Entry("hf-$token", id.substringAfter('/') + " · " + path.substringAfterLast('/').removeSuffix(".litertlm"),
                if (tested) "Tested for document answers" else "Not yet tested for document answers", id, revision, localName, bytes, hash,
                repo.optJSONObject("cardData")?.optString("license")?.takeIf { it.isNotBlank() } ?: "See publisher licence",
                maxOutputTokens = known?.maxOutputTokens ?: 512, thinkingBudget = known?.thinkingBudget ?: 0,
                cpuOnly = known?.cpuOnly ?: false, inputChars = known?.inputChars ?: 4000,
                inlineHistory = known?.inlineHistory ?: false, remoteFileName = path,
                updatedAt = repo.optString("lastModified"), documentTested = tested)
            entry.takeIf(::valid)
        }
    }

    suspend fun search(query: String): Result = withTimeout(60_000) {
        coroutineScope {
            val term = query.trim().take(120)
            val encoded = URLEncoder.encode(term, "UTF-8")
            val requests = listOf("https://huggingface.co/api/models?filter=litertlm&sort=lastModified&direction=-1&limit=30&search=$encoded",
                "https://huggingface.co/api/models?sort=lastModified&direction=-1&limit=30&search=" + URLEncoder.encode(if (term.isBlank()) "litertlm" else term, "UTF-8"))
            val listings = requests.map { url -> async(Dispatchers.IO) { runCatching { JSONArray(read(url)) } } }.awaitAll()
            if (listings.all { it.isFailure }) error("Could not reach Hugging Face. Check your connection and retry.")
            val ids = listings.flatMap { result -> result.getOrNull()?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id")?.takeIf(repoPattern::matches) }
            }.orEmpty() }.distinct().take(40)
            val permits = Semaphore(4)
            val details = ids.map { id -> async(Dispatchers.IO) { permits.withPermit {
                runCatching { parseRepository(read("https://huggingface.co/api/models/$id?blobs=true")) }
            } } }.awaitAll()
            Result(details.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.id }.sortedByDescending { it.updatedAt },
                listings.any { it.isFailure } || details.any { it.isFailure } || ids.size == 40)
        }
    }

    /** Refresh reviewed files directly, so a bounded recent search cannot hide them. */
    suspend fun recommended(query: String): Result = withTimeout(30_000) {
        coroutineScope {
            val reviewed = ModelCatalog.recommended
            val checked = reviewed.map { profile -> async(Dispatchers.IO) {
                runCatching { parseRepository(read("https://huggingface.co/api/models/${profile.repository}?blobs=true")) }
            } }.awaitAll()
            val term = query.trim().take(120)
            val entries = reviewed.mapIndexed { index, profile ->
                checked[index].getOrNull()?.firstOrNull {
                    it.repository == profile.repository && it.remoteFileName == profile.remoteFileName &&
                        it.sha256 == profile.sha256 && it.bytes == profile.bytes
                }?.copy(name = profile.name, description = profile.description, documentTested = true) ?: profile
            }.filter { term.isBlank() || listOf(it.name, it.repository, it.remoteFileName).any { s -> s.contains(term, ignoreCase = true) } }
            Result(entries, checked.any { it.isFailure })
        }
    }

    /** Publisher text is displayed as plain data; no code, HTML or remote images run. */
    suspend fun description(entry: ModelCatalog.Entry): String = withTimeout(15_000) {
        require(valid(entry))
        withContext(Dispatchers.IO) {
            descriptionPreview(read("https://huggingface.co/${entry.repository}/raw/${entry.revision}/README.md", 128_000))
        }
    }

    internal fun descriptionPreview(markdown: String): String {
        val lines = markdown.removePrefix("\uFEFF").take(128_000)
            .replace(Regex("<!--[\\s\\S]*?-->"), "").lines()
        var frontMatter = lines.firstOrNull()?.trim() == "---"
        var fence: String? = null
        val body = mutableListOf<String>()
        for ((index, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (frontMatter) {
                if (index > 0 && line == "---") frontMatter = false
                continue
            }
            if (line.startsWith("```") || line.startsWith("~~~")) {
                val marker = line.take(3)
                if (fence == null) fence = marker else if (fence == marker) fence = null
                continue
            }
            if (fence != null || line.startsWith('#') || line.startsWith('|') || line.matches(Regex("[-=]{3,}"))) continue
            val text = line.replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
                .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
                .replace(Regex("<[^>]*>"), "").replace("**", "").replace("__", "").replace("`", "")
            body.add(text)
        }
        val paragraphs = body.joinToString("\n").split(Regex("\n\\s*\n"))
            .map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotBlank() }.take(3)
        return paragraphs.joinToString("\n\n").take(1800)
    }

    private fun read(url: String, maximumBytes: Int = 4_000_000): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000; connection.readTimeout = 10000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "DataEater/0.1.7.1")
        try {
            check(connection.responseCode == 200) { "Hugging Face request failed" }
            return connection.inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream(); val bytes = ByteArray(8192)
                while (true) { val n = stream.read(bytes); if (n < 0) break; check(buffer.size() + n <= maximumBytes); buffer.write(bytes, 0, n) }
                buffer.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }

    fun encode(entry: ModelCatalog.Entry): String = JSONObject().apply {
        put("id", entry.id); put("name", entry.name); put("description", entry.description); put("repository", entry.repository)
        put("revision", entry.revision); put("fileName", entry.fileName); put("bytes", entry.bytes); put("sha256", entry.sha256)
        put("license", entry.license); put("remoteFileName", entry.remoteFileName); put("updatedAt", entry.updatedAt)
        put("documentTested", entry.documentTested); put("maxOutputTokens", entry.maxOutputTokens); put("thinkingBudget", entry.thinkingBudget)
        put("cpuOnly", entry.cpuOnly); put("inputChars", entry.inputChars); put("inlineHistory", entry.inlineHistory)
    }.toString()

    fun decode(text: String): ModelCatalog.Entry? = runCatching {
        val j = JSONObject(text)
        ModelCatalog.Entry(j.getString("id"), j.getString("name"), j.getString("description"), j.getString("repository"),
            j.getString("revision"), j.getString("fileName"), j.getLong("bytes"), j.getString("sha256"), j.getString("license"),
            maxOutputTokens = j.optInt("maxOutputTokens", 512).coerceIn(128, 4096), thinkingBudget = j.optInt("thinkingBudget", 0).coerceIn(0, 2048),
            cpuOnly = j.optBoolean("cpuOnly"), inputChars = j.optInt("inputChars", 4000).coerceIn(1500, 16000), inlineHistory = j.optBoolean("inlineHistory"),
            remoteFileName = j.getString("remoteFileName"), updatedAt = j.optString("updatedAt"), documentTested = j.optBoolean("documentTested"))
            .takeIf(::valid)
    }.getOrNull()
}
