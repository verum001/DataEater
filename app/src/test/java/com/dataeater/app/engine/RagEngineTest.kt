package com.dataeater.app.engine

import com.dataeater.app.model.Chunk
import com.dataeater.app.model.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the RAG step: retrieval plus prompt building.
 *
 * This is the part that stops the AI from inventing facts, so it is worth
 * testing carefully on the computer before trusting it on a phone.
 */
class RagEngineTest {

    private val sources = listOf(
        SourceInfo(
            id = "s1",
            title = "HF-4500 Service Manual",
            publisher = "DataEater",
            year = 2026,
        )
    )

    private val chunks = listOf(
        Chunk(
            id = "c005", sourceId = "s1", section = "3.1 Fuel Pressure Testing", page = 11,
            text = "Idle rail pressure must be 380 bar at 850 rpm."
        ),
        Chunk(
            id = "c007", sourceId = "s1", section = "3.2 Injector Testing", page = 14,
            text = "Tighten injector hold-down bolts to a torque of 45 Nm."
        ),
        Chunk(
            id = "c101", sourceId = "s1", section = "P0087 Fuel Rail Pressure Too Low", page = 2,
            text = "Fault code P0087 means the fuel rail pressure is below the threshold."
        ),
    )

    private val rag = RagEngine(chunks, sources)

    @Test
    fun `finds the chunk that holds the answer`() {
        val found = rag.retrieve("what is the idle rail pressure")
        assertTrue("should find something", found.isNotEmpty())
        assertEquals("c005", found.first().chunk.id)
    }

    @Test
    fun `finds fault codes by their exact code`() {
        val found = rag.retrieve("P0087")
        assertTrue(found.isNotEmpty())
        assertEquals("c101", found.first().chunk.id)
    }

    @Test
    fun `finds torque values`() {
        val found = rag.retrieve("what torque for injector bolts")
        assertTrue(found.isNotEmpty())
        assertEquals("c007", found.first().chunk.id)
    }

    @Test
    fun `returns nothing when the database does not mention it`() {
        val found = rag.retrieve("how do I change the tyres")
        assertTrue("nothing about tyres exists", found.isEmpty())
    }

    @Test
    fun `citation contains document section and page`() {
        val found = rag.retrieve("idle rail pressure")
        val citation = found.first().citation
        assertTrue("document title missing in: $citation",
            citation.contains("HF-4500 Service Manual"))
        assertTrue("section missing in: $citation",
            citation.contains("3.1 Fuel Pressure Testing"))
        assertTrue("page missing in: $citation", citation.contains("page 11"))
    }

    @Test
    fun `prompt contains the retrieved text and the question`() {
        val found = rag.retrieve("idle rail pressure")
        val prompt = rag.buildPrompt("what is the idle rail pressure", found)

        assertTrue("question must be in the prompt",
            prompt.contains("QUESTION: what is the idle rail pressure"))
        assertTrue("source text must be in the prompt",
            prompt.contains("380 bar at 850 rpm"))
        assertTrue("page reference must be in the prompt", prompt.contains("page 11"))
    }

    @Test fun `native system instruction requires grounded answers`() {
        val system = com.dataeater.app.ai.AnswerMode.DATABASE.systemInstruction
        assertTrue(system.contains("only the current REFERENCE TEXT"))
        assertTrue(system.contains("The database does not contain this information"))
    }

    @Test
    fun `prompt numbers every passage`() {
        val found = rag.retrieve("pressure", limit = 3)
        val prompt = rag.buildPrompt("question", found)
        assertTrue("first passage must be numbered", prompt.contains("[1]"))
    }

    @Test
    fun `prompt places question after the reference text`() {
        // Small models ground far better when the question is repeated
        // immediately before the answer is written.
        val found = rag.retrieve("idle rail pressure")
        val prompt = rag.buildPrompt("what is the idle rail pressure", found)

        val occurrences = Regex("QUESTION: what is the idle rail pressure")
            .findAll(prompt).count()
        assertEquals("question must appear once", 1, occurrences)

        assertTrue("the question must follow the reference text",
            prompt.indexOf("QUESTION:") > prompt.indexOf("REFERENCE TEXT:"))
    }

    @Test
    fun `prompt ends with the user question`() {
        val found = rag.retrieve("idle rail pressure")
        val prompt = rag.buildPrompt("q", found)
        assertTrue("prompt must end with the question", prompt.trimEnd().endsWith("QUESTION: q"))
    }

    @Test
    fun `at most three passages are given to the model`() {
        // Extra irrelevant text confuses a small model.
        val found = rag.retrieve("pressure")
        assertTrue("no more than three passages", found.size <= 3)
    }
}