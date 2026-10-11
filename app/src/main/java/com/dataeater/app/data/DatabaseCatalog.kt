package com.dataeater.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Only published, checksum-bearing database assets from the project's public repository. */
object DatabaseCatalog {
    const val CATALOG = "https://raw.githubusercontent.com/verum001/DataEater-Databases/main/catalog.json"
    const val API = "https://api.github.com/repos/verum001/DataEater-Databases/releases"
    data class Entry(val id: String, val name: String, val description: String, val version: String,
        val fileName: String, val url: String, val bytes: Long, val sha256: String) {
        fun json() = JSONObject().put("id", id).put("name", name).put("description", description)
            .put("version", version).put("fileName", fileName).put("url", url).put("bytes", bytes).put("sha256", sha256)
    }
    fun validate(entry: Entry): Entry {
        require(Regex("[0-9]+").matches(entry.id))
        require(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,180}\\.dataeater").matches(entry.fileName))
        val uri = URI(entry.url)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
            uri.path.startsWith("/verum001/DataEater-Databases/releases/download/") && uri.query == null && uri.fragment == null)
        require(entry.bytes in 1..64L * 1024 * 1024 && Regex("[a-f0-9]{64}").matches(entry.sha256))
        return entry
    }
    fun restore(text: String): Entry {
        val j = JSONObject(text)
        return validate(Entry(j.getString("id"), j.getString("name"), j.getString("description"), j.getString("version"),
            j.getString("fileName"), j.getString("url"), j.getLong("bytes"), j.getString("sha256")))
    }
    fun parse(releases: JSONArray): List<Entry> {
        val latest = linkedMapOf<String, Entry>()
        val sorted = (0 until releases.length()).map { releases.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
            .sortedByDescending { it.optString("published_at") }
        for (release in sorted) {
            val assets = release.optJSONArray("assets") ?: continue
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val filename = asset.optString("name")
                if (!filename.endsWith(".dataeater") || asset.optString("state") != "uploaded" || filename in latest) continue
                // Mark newest first: never silently offer an older version if the newest is invalid.
                val digest = asset.optString("digest").removePrefix("sha256:")
                val version = release.optString("tag_name")
                val safeVersion = version.removePrefix(filename.removeSuffix(".dataeater") + "-v").replace(Regex("[^A-Za-z0-9._-]"), "-").take(60)
                val target = filename.removeSuffix(".dataeater") + "-" + safeVersion + ".dataeater"
                latest[filename] = validate(Entry(asset.getLong("id").toString(),
                    release.optString("name").ifBlank { filename.removeSuffix(".dataeater") }.take(200),
                    release.optString("body").substringBefore("\n\n").take(1000), version.take(100), target,
                    asset.getString("browser_download_url"), asset.getLong("size"), digest))
            }
        }
        return latest.values.toList()
    }
}
