package com.dataeater.app.benchmark

import com.dataeater.app.ai.ReplyBehavior
import java.util.Locale

/** A checkpoint is independent of normal chats and contains no unlock keys. */
data class BenchmarkRun(
    val id: String,
    val startedAt: String,
    val phone: Map<String, String>,
    val models: List<String>,
    val databases: List<String>,
    val questions: List<String>,
    val useDatabase: Boolean = true,
    val humanRating: Boolean = false,
    val preferGpu: Boolean = true,
    val behavior: ReplyBehavior = ReplyBehavior(),
    val status: String = "Running",
    val endedAt: String? = null,
    val loads: List<BenchmarkLoad> = emptyList(),
    val answers: List<BenchmarkAnswer> = emptyList(),
) {
    fun rated(ratings: Map<String, Pair<Int?, String>>): BenchmarkRun = copy(answers = answers.map { answer ->
        ratings[answer.id]?.let { answer.copy(stars = it.first, feedback = it.second) } ?: answer
    })
}
data class BenchmarkLoad(val model: String, val milliseconds: Long?, val backend: String, val status: String, val error: String = "")
data class BenchmarkAnswer(
    val id: String, val model: String, val questionIndex: Int,
    val status: String = "Running", val text: String = "", val sources: List<String> = emptyList(),
    val retrievalMs: Long? = null, val answerMs: Long? = null, val firstTextMs: Long? = null,
    val stars: Int? = null, val feedback: String = "", val error: String = "",
)

object BenchmarkReport {
    private fun seconds(ms: Long?): String = ms?.let { String.format(Locale.US, "%.3f", it / 1000.0) } ?: "—"
    private fun mean(values: List<Long>): String = if (values.isEmpty()) "—" else String.format(Locale.US, "%.3f", values.average() / 1000.0)
    fun text(run: BenchmarkRun): String = buildString {
        appendLine("DataEater benchmark ${run.id}")
        appendLine("Start: ${run.startedAt}"); appendLine("End: ${run.endedAt ?: "Not finished"}")
        appendLine("Status: ${run.status}")
        run.phone.forEach { (key, value) -> appendLine("$key: $value") }
        appendLine("Models: ${run.models.joinToString()}")
        appendLine("Databases: ${if (run.useDatabase) run.databases.joinToString() else "OFF — general knowledge, not checked against documents"}")
        appendLine("Requested processor: ${if (run.preferGpu) "GPU preferred, CPU fallback" else "CPU preferred, GPU fallback"}")
        appendLine("Reply preferences: ${run.behavior.strictness.label}, ${run.behavior.length.label}")
        appendLine("Human rating: ${run.humanRating}")
        appendLine("Method: sequential installed local models; load once per model; fresh conversation per question; no warm-up. Runtime caches may be warm. Input and output limits follow each model's profile. Selected databases form one search collection; fitted contexts can differ by model. Timings use a monotonic clock. Load includes backend fallback; answer includes generation/retries, excludes retrieval. Not a controlled accuracy or tokens/second test.")
        appendLine(); appendLine("QUESTIONS")
        run.questions.forEachIndexed { i, q -> appendLine("${i + 1}. $q") }
        appendLine(); appendLine("MODEL LOADS")
        run.loads.forEach { appendLine("${it.model}: ${it.status}, ${seconds(it.milliseconds)} s, ${it.backend}${if (it.error.isBlank()) "" else "; ${it.error}"}") }
        run.answers.forEach { a ->
            appendLine(); appendLine("${a.model} — question ${a.questionIndex + 1}")
            appendLine("Question: ${run.questions[a.questionIndex]}")
            appendLine("Status: ${a.status}; retrieval: ${seconds(a.retrievalMs)} s; answer: ${seconds(a.answerMs)} s; first text: ${seconds(a.firstTextMs)} s")
            appendLine("Answer:"); appendLine(a.text)
            if (a.error.isNotBlank()) appendLine("Error: ${a.error}")
            if (a.sources.isNotEmpty()) { appendLine("Retrieved sources (not an accuracy check):"); a.sources.forEach { appendLine("- $it") } }
            if (run.humanRating) { appendLine("Human rating: ${a.stars?.let { "$it/5" } ?: "Unrated"}"); appendLine("Feedback: ${a.feedback.ifBlank { "None" }}") }
        }
        appendLine(); appendLine("MODEL COMPARISON (seconds)")
        val rows = mutableListOf(listOf("Model", "Backend", "Load", "Completed", "No match", "Failed", "Cancelled", "Pending", "Avg answer", "Avg first text", "Avg chars" ) + if (run.humanRating) listOf("Rating", "Rated") else emptyList())
        run.models.forEach { model ->
            val answers = run.answers.filter { it.model == model }
            val completed = answers.filter { it.status == "Completed" }
            val load = run.loads.firstOrNull { it.model == model }
            val ratings = answers.mapNotNull { it.stars }
            rows.add(listOf(model, load?.backend ?: "—", seconds(load?.milliseconds), "${completed.size}/${run.questions.size}",
                answers.count { it.status == "No match" }.toString(), answers.count { it.status == "Failed" }.toString(),
                answers.count { it.status == "Cancelled" }.toString(), (run.questions.size - answers.count { it.status != "Running" }).toString(),
                mean(completed.mapNotNull { it.answerMs }), mean(completed.mapNotNull { it.firstTextMs }),
                if (completed.isEmpty()) "—" else String.format(Locale.US, "%.0f", completed.map { it.text.length }.average())) +
                if (run.humanRating) listOf(if (ratings.isEmpty()) "—" else String.format(Locale.US, "%.2f/5", ratings.average()), "${ratings.size}/${answers.size}") else emptyList())
        }
        val widths = rows.first().indices.map { c -> rows.maxOf { it[c].length } }
        rows.forEachIndexed { i, row ->
            appendLine(row.mapIndexed { c, value -> value.padEnd(widths[c]) }.joinToString(" | "))
            if (i == 0) appendLine(widths.joinToString("-+-") { "-".repeat(it) })
        }
        appendLine("Averages include completed answers only. Unrated answers are excluded from rating averages. No match means the model was not asked. Failed/cancelled answers never count as completed. Lower time and longer text do not prove a better answer.")
    }
}
