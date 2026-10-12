package com.dataeater.app.benchmark

import com.dataeater.app.ai.*
import com.dataeater.app.chat.ChatMessage
import com.dataeater.app.engine.RagEngine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BenchmarkTest {
    private fun run(db: Boolean = false) = BenchmarkRun("benchmark-20261012-120000-abcdef12", "2026-10-12T12:00:00+03:00", mapOf("Phone" to "Test phone"), listOf("a", "b"), if (db) listOf("test.dataeater") else emptyList(), listOf("question one", "question two"), db, true)
    private class Engine(override val modelName: String, val calls: MutableList<String>, val loadFail: Boolean = false, val genFail: Boolean = false) : LocalAiEngine {
        var closed = false
        override suspend fun load() { calls.add("load:$modelName"); if (loadFail) error("bad load") }
        override suspend fun generate(prompt: String, mode: AnswerMode, history: List<ChatMessage>, behavior: ReplyBehavior, onNewText: (String) -> Unit) {
            assertTrue(history.isEmpty()); calls.add("$modelName:$prompt")
            onNewText("first "); if (genFail) error("bad answer"); onNewText("second")
        }
        override fun close() { closed = true; calls.add("close:$modelName") }
    }
    @Test fun sequentialFreshAnswersAndLoadOnce() = runBlocking {
        val calls = mutableListOf<String>(); var tick = 0L
        val runner = BenchmarkRunner({ Engine(it, calls) }, { 6000 }, { "CPU" }, { tick += 100; tick }, { "end" })
        val result = runner.execute(run(), null, { _, _ -> }, {})
        assertEquals(4, result.answers.size); assertTrue(result.answers.all { it.status == "Completed" && it.text == "first second" && it.answerMs!! > 0 && it.firstTextMs!! > 0 })
        assertEquals(listOf("load:a", "a:QUESTION: question one", "a:QUESTION: question two", "close:a", "load:b", "b:QUESTION: question one", "b:QUESTION: question two", "close:b"), calls)
        assertEquals("Finished", result.status)
    }
    @Test fun loadFactoryFailureDoesNotSkipOtherModels() = runBlocking {
        val calls = mutableListOf<String>()
        val runner = BenchmarkRunner({ if (it == "a") error("memory") else Engine(it, calls) }, { 6000 }, { "CPU" }, { 1 }, { "end" })
        val result = runner.execute(run(), null, { _, _ -> }, {})
        assertEquals(listOf("Failed", "Failed", "Completed", "Completed"), result.answers.map { it.status })
        assertNull(result.answers.first().answerMs); assertEquals("Not loaded", result.loads.first().backend)
    }
    @Test fun noPassagesNeverCallsModel() = runBlocking {
        val calls = mutableListOf<String>()
        val runner = BenchmarkRunner({ Engine(it, calls) }, { 6000 }, { "CPU" }, { 10 }, { "end" })
        val result = runner.execute(run(true), RagEngine(emptyList(), emptyList()), { _, _ -> }, {})
        assertTrue(result.answers.all { it.status == "No match" && it.answerMs == null })
        assertEquals(listOf("load:a", "close:a", "load:b", "close:b"), calls)
    }
    @Test fun failedPartialAnswersExcludedFromAverages() = runBlocking {
        val calls = mutableListOf<String>(); var tick = 0L
        val runner = BenchmarkRunner({ Engine(it, calls, genFail = it == "a") }, { 6000 }, { "CPU" }, { tick += 100; tick }, { "end" })
        val result = runner.execute(run(), null, { _, _ -> }, {})
        assertEquals("first ", result.answers.first().text)
        val row = BenchmarkReport.text(result).lines().first { it.startsWith("a ") && it.contains(" | ") }
        assertTrue(row.contains("0/2")); assertTrue(row.contains("—"))
    }
    @Test fun cancellationClosesEngineAndRetainsCompletedAnswer() = runBlocking {
        val ready = CompletableDeferred<Unit>(); var closed = false; var calls = 0; var last: BenchmarkRun? = null
        val fake = object : LocalAiEngine {
            override val modelName = "a"
            override suspend fun load() {}
            override suspend fun generate(prompt: String, mode: AnswerMode, history: List<ChatMessage>, behavior: ReplyBehavior, onNewText: (String) -> Unit) {
                calls++; onNewText("partial")
                if (calls == 2) { ready.complete(Unit); awaitCancellation() }
            }
            override fun close() { closed = true }
        }
        val runner = BenchmarkRunner({ fake }, { 6000 }, { "CPU" }, { 100 }, { "end" })
        val task = launch { runner.execute(run().copy(models = listOf("a")), null, { value, _ -> last = value }, {}) }
        ready.await(); task.cancelAndJoin()
        assertTrue(closed); assertEquals("Cancelled", last!!.status)
        assertEquals(listOf("Completed", "Cancelled"), last!!.answers.map { it.status })
    }
    @Test fun timeoutRecordedAsFailureAndNextQuestionRuns() = runBlocking {
        var calls = 0
        val fake = object : LocalAiEngine {
            override val modelName = "a"
            override suspend fun load() {}
            override suspend fun generate(prompt: String, mode: AnswerMode, history: List<ChatMessage>, behavior: ReplyBehavior, onNewText: (String) -> Unit) { calls++; if (calls == 1) awaitCancellation() else onNewText("done") }
            override fun close() {}
        }
        val result = BenchmarkRunner({ fake }, { 6000 }, { "CPU" }, { 10 }, { "end" }, answerTimeoutMs = 30).execute(run().copy(models = listOf("a")), null, { _, _ -> }, {})
        assertEquals(listOf("Failed", "Completed"), result.answers.map { it.status })
    }
    @Test fun ratingAveragesExcludeUnratedAndJsonPreservesEverything() {
        val dir = Files.createTempDirectory("bench").toFile()
        try {
            val store = BenchmarkStore(dir)
            val value = run().copy(status = "Finished", endedAt = "end", loads = listOf(BenchmarkLoad("a", 1234, "CPU", "Loaded")), answers = listOf(
                BenchmarkAnswer("0-0", "a", 0, "Completed", "hello\nworld", sources = listOf("source page 4"), retrievalMs = 50, answerMs = 2000, firstTextMs = 500, stars = 4, feedback = "clear"),
                BenchmarkAnswer("0-1", "a", 1, "Completed", "answer", answerMs = 4000)))
            store.save(value); assertEquals(value, store.read(value.id))
            val text = store.file(value.id).readText(); assertTrue(text.contains("3.000")); assertTrue(text.contains("4.00/5")); assertTrue(text.contains("Unrated"))
            store.save(value.rated(mapOf("0-0" to (2 to "changed")))); assertTrue(store.file(value.id).readText().contains("2.00/5"))
            assertEquals(1, store.list().size); store.delete(value.id); assertTrue(store.list().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun pathsCannotEscapeAndSymlinksCannotBeSharedOrDeleted() {
        val root = Files.createTempDirectory("bench").toFile(); val outside = File(root, "outside.txt").apply { writeText("keep") }
        val dir = File(root, "results").apply { mkdirs() }; val store = BenchmarkStore(dir)
        try {
            try { store.file("../outside"); fail() } catch (_: IllegalArgumentException) {}
            Files.createSymbolicLink(File(dir, "benchmark-20261012-120000-abcdef12.txt").toPath(), outside.toPath())
            assertTrue(store.list().isEmpty())
            try { store.delete("benchmark-20261012-120000-abcdef12"); fail() } catch (_: IllegalArgumentException) {}
            assertEquals("keep", outside.readText())
        } finally { root.deleteRecursively() }
    }
}
