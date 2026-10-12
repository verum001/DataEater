package com.dataeater.app.benchmark

import com.dataeater.app.ai.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Strict generated filenames and canonical containment keep exports/deletion scoped. */
class BenchmarkStore(val directory: File) {
    companion object { val ID = Regex("benchmark-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}") }
    fun file(id: String, extension: String = "txt"): File {
        require(ID.matches(id) && extension in listOf("txt", "json")) { "Invalid benchmark file." }
        val target = File(directory, "$id.$extension")
        require(target.canonicalFile.parentFile == directory.canonicalFile) { "Invalid benchmark path." }
        return target
    }
    fun list(): List<File> = directory.listFiles().orEmpty().filter { f ->
        f.isFile && f.extension == "txt" && ID.matches(f.nameWithoutExtension) &&
            runCatching { f.canonicalFile.parentFile == directory.canonicalFile }.getOrDefault(false)
    }.sortedByDescending { it.name }
    private fun atomic(target: File, text: String) {
        require(directory.isDirectory || directory.mkdirs()) { "Could not create DataEater/benchmark. Check storage access." }
        val temp = File.createTempFile(".benchmark-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { temp.delete() }
    }
    fun save(run: BenchmarkRun) {
        // JSON is the checkpoint authority. TXT is a readable export regenerated on open.
        atomic(file(run.id, "json"), encode(run).toString(2))
        atomic(file(run.id), BenchmarkReport.text(run))
    }
    fun read(id: String): BenchmarkRun {
        val source = file(id, "json")
        require(source.length() <= 32L * 1024 * 1024) { "This result is too large to open." }
        val run = decode(JSONObject(source.readText()))
        require(run.id == id) { "Benchmark identity mismatch." }
        return run
    }
    fun delete(id: String) {
        val targets = listOf(file(id), file(id, "json"))
        targets.forEach { require(!it.exists() || it.delete()) { "Could not delete ${it.name}." } }
    }
    private fun strings(array: JSONArray) = List(array.length()) { array.getString(it) }
    private fun longs(o: JSONObject, k: String): Long? = if (o.isNull(k)) null else o.getLong(k)
    fun encode(r: BenchmarkRun): JSONObject = JSONObject().apply {
        put("format", 1); put("id", r.id); put("startedAt", r.startedAt); put("endedAt", r.endedAt ?: JSONObject.NULL)
        put("phone", JSONObject(r.phone)); put("models", JSONArray(r.models)); put("databases", JSONArray(r.databases)); put("questions", JSONArray(r.questions))
        put("useDatabase", r.useDatabase); put("humanRating", r.humanRating); put("preferGpu", r.preferGpu)
        put("strictness", r.behavior.strictness.name); put("length", r.behavior.length.name); put("status", r.status)
        put("loads", JSONArray().apply { r.loads.forEach { l -> put(JSONObject().apply {
            put("model", l.model); put("milliseconds", l.milliseconds ?: JSONObject.NULL); put("backend", l.backend); put("status", l.status); put("error", l.error)
        }) } })
        put("answers", JSONArray().apply { r.answers.forEach { a -> put(JSONObject().apply {
            put("id", a.id); put("model", a.model); put("questionIndex", a.questionIndex); put("status", a.status); put("text", a.text); put("sources", JSONArray(a.sources))
            put("retrievalMs", a.retrievalMs ?: JSONObject.NULL); put("answerMs", a.answerMs ?: JSONObject.NULL); put("firstTextMs", a.firstTextMs ?: JSONObject.NULL)
            put("stars", a.stars ?: JSONObject.NULL); put("feedback", a.feedback); put("error", a.error)
        }) } })
    }
    fun decode(o: JSONObject): BenchmarkRun {
        require(o.getInt("format") == 1)
        val questions = strings(o.getJSONArray("questions")); require(questions.size in 1..50)
        val models = strings(o.getJSONArray("models")); require(models.size in 1..20)
        val p = o.getJSONObject("phone")
        val aa = o.getJSONArray("answers"); require(aa.length() <= 1000)
        val ll = o.getJSONArray("loads"); require(ll.length() <= 20)
        return BenchmarkRun(o.getString("id"), o.getString("startedAt"), p.keys().asSequence().associateWith { p.getString(it) },
            models, strings(o.getJSONArray("databases")), questions, o.getBoolean("useDatabase"), o.getBoolean("humanRating"), o.getBoolean("preferGpu"),
            ReplyBehavior(DatabaseStrictness.valueOf(o.getString("strictness")), ReplyLength.valueOf(o.getString("length"))), o.getString("status"),
            if (o.isNull("endedAt")) null else o.getString("endedAt"),
            List(ll.length()) { i -> ll.getJSONObject(i).let { BenchmarkLoad(it.getString("model"), longs(it, "milliseconds"), it.getString("backend"), it.getString("status"), it.getString("error")) } },
            List(aa.length()) { i -> aa.getJSONObject(i).let {
                val index = it.getInt("questionIndex"); require(index in questions.indices)
                val stars = if (it.isNull("stars")) null else it.getInt("stars").also { n -> require(n in 1..5) }
                BenchmarkAnswer(it.getString("id"), it.getString("model"), index, it.getString("status"), it.getString("text"), strings(it.getJSONArray("sources")),
                    longs(it, "retrievalMs"), longs(it, "answerMs"), longs(it, "firstTextMs"), stars, it.getString("feedback"), it.getString("error"))
            } })
    }
}
