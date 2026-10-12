package com.dataeater.app.benchmark

import com.dataeater.app.ai.*
import com.dataeater.app.chat.ChatResponder
import com.dataeater.app.engine.RagEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Injectable engines and clock allow failures, refusals and cancellation to be tested. */
class BenchmarkRunner(
    private val engineFor: (String) -> LocalAiEngine,
    private val budgetFor: (String) -> Int,
    private val backend: (LocalAiEngine) -> String,
    private val clockMs: () -> Long,
    private val time: () -> String,
    private val answerTimeoutMs: Long = 300_000,
) {
    suspend fun execute(initial: BenchmarkRun, rag: RagEngine?,
        checkpoint: suspend (BenchmarkRun, String) -> Unit,
        stream: (BenchmarkRun) -> Unit,
    ): BenchmarkRun {
        var run = initial
        try {
            checkpoint(run, "Starting benchmark…")
            for ((modelIndex, model) in run.models.withIndex()) {
                var engine: LocalAiEngine? = null
                try {
                    checkpoint(run, "Loading ${modelIndex + 1}/${run.models.size}: $model")
                    val loadStart = clockMs()
                    try {
                        engine = engineFor(model)
                        engine.load()
                        run = run.copy(loads = run.loads + BenchmarkLoad(model, clockMs() - loadStart, backend(engine), "Loaded"))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        val reason = error.message ?: "Model load failed"
                        run = run.copy(loads = run.loads + BenchmarkLoad(model, clockMs() - loadStart, engine?.let(backend) ?: "Not loaded", "Failed", reason),
                            answers = run.answers + run.questions.indices.map { BenchmarkAnswer("$modelIndex-$it", model, it, "Failed", error = reason) })
                        checkpoint(run, "Could not load $model. Continuing…")
                        continue
                    }
                    checkpoint(run, "$model loaded")
                    for ((questionIndex, question) in run.questions.withIndex()) {
                        var answer = BenchmarkAnswer("$modelIndex-$questionIndex", model, questionIndex)
                        run = run.copy(answers = run.answers + answer)
                        checkpoint(run, "${modelIndex + 1}/${run.models.size} models · Question ${questionIndex + 1}/${run.questions.size}")
                        val retrievalStart = clockMs()
                        val budget = budgetFor(model)
                        fun decision(limit: Int) = ChatResponder(rag, run.databases.joinToString(), inputBudget = limit,
                            textCost = { it.toByteArray(Charsets.UTF_8).size },
                            instructionReserve = run.behavior.instruction(if (run.useDatabase) AnswerMode.DATABASE else AnswerMode.GENERAL_KNOWLEDGE).toByteArray(Charsets.UTF_8).size + 128,
                        ).decide(question, emptyList(), run.useDatabase, true)
                        var generationStart: Long? = null
                        val text = StringBuffer()
                        var lastStreamMs = Long.MIN_VALUE / 2
                        try {
                            val decided = decision(budget)
                            answer = answer.copy(retrievalMs = clockMs() - retrievalStart)
                            when (decided) {
                                is ChatResponder.Decision.NoPassages -> answer = answer.copy(status = "No match", text = "No matching database passage. The model was not asked.")
                                is ChatResponder.Decision.NoModel -> answer = answer.copy(status = "Failed", error = decided.reason)
                                else -> {
                                    generationStart = clockMs()
                                    withTimeout(answerTimeoutMs) {
                                        BoundedGeneration.generate(engine, BoundedGeneration.request(decided), budget, run.behavior,
                                            replan = { BoundedGeneration.request(decision(it)) },
                                            onSources = { answer = answer.copy(sources = it) }, onText = { delta ->
                                                if (answer.firstTextMs == null) answer = answer.copy(firstTextMs = clockMs() - generationStart)
                                                text.append(delta)
                                                val tick = clockMs()
                                                if (tick - lastStreamMs >= 100) {
                                                    lastStreamMs = tick
                                                    stream(run.copy(answers = run.answers.dropLast(1) + answer.copy(text = text.toString())))
                                                }
                                            })
                                    }
                                    answer = answer.copy(status = "Completed", text = text.toString(), answerMs = clockMs() - generationStart)
                                }
                            }
                        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                            answer = answer.copy(status = "Failed", text = text.toString(), error = "Answer exceeded five minutes", answerMs = generationStart?.let { clockMs() - it })
                        } catch (cancelled: CancellationException) {
                            answer = answer.copy(status = "Cancelled", text = text.toString(), answerMs = generationStart?.let { clockMs() - it })
                            run = run.copy(answers = run.answers.dropLast(1) + answer)
                            throw cancelled
                        } catch (error: Exception) {
                            answer = answer.copy(status = "Failed", text = text.toString(), error = error.message ?: "Answer failed", answerMs = generationStart?.let { clockMs() - it })
                        }
                        run = run.copy(answers = run.answers.dropLast(1) + answer)
                        checkpoint(run, "Answer ${questionIndex + 1}/${run.questions.size}: ${answer.status}")
                    }
                } finally { withContext(NonCancellable + kotlinx.coroutines.Dispatchers.IO) { engine?.close() } }
            }
            run = run.copy(status = "Finished", endedAt = time())
        } catch (cancelled: CancellationException) {
            run = run.copy(status = "Cancelled", endedAt = time(), answers = run.answers.map { if (it.status == "Running") it.copy(status = "Cancelled") else it })
        } catch (error: Exception) {
            run = run.copy(status = "Failed: ${error.message ?: "Could not finish"}", endedAt = time())
        }
        withContext(NonCancellable) { checkpoint(run, run.status) }
        return run
    }
}
