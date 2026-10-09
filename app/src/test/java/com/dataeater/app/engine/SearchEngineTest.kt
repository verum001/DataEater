package com.dataeater.app.engine

import com.dataeater.app.model.Chunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the keyword search.
 *
 * Run them from the terminal with:  ./gradlew testDebugUnitTest
 *
 * These are real tests: they check that the search finds the right text and
 * puts it in the right order. That matters because a diagnostic tool that
 * shows the wrong passage first is worse than one that shows nothing.
 */
class SearchEngineTest {

    // A miniature version of the demo database, so the tests are easy to read.
    private val chunks = listOf(
        Chunk(
            id = "c005", sourceId = "s1", section = "3.1 Fuel Pressure Testing", page = 11,
            text = "Connect gauge GT-2000 to test port P1 on the high-pressure pipe. " +
                "Start the engine and allow the rail pressure to stabilise. " +
                "Idle rail pressure must be 380 bar at 850 rpm."
        ),
        Chunk(
            id = "c101", sourceId = "s2", section = "P0087 Fuel Rail Pressure Too Low", page = 2,
            text = "Fault code P0087 means the fuel rail pressure is below the threshold " +
                "set by the engine control unit. Common causes: empty fuel tank, " +
                "blocked primary filter, air in the system, or a failed supply pump."
        ),
        Chunk(
            id = "c008", sourceId = "s1", section = "3.2 Injector Testing", page = 15,
            text = "Cylinder balance is measured with gauge BT-900. " +
                "A difference above 8 percent between cylinders indicates " +
                "a stuck or leaking injector."
        ),
        Chunk(
            id = "c104", sourceId = "s2", section = "P0201 Injector Circuit Cylinder 1", page = 8,
            text = "Fault code P0201 reports a fault in the injector circuit for cylinder 1. " +
                "Measure resistance of the injector coil: a healthy coil measures 0.9 to 1.3 ohm."
        ),
        Chunk(
            id = "c003", sourceId = "s1", section = "2.1 Routine Service", page = 7,
            text = "Replace the primary fuel filter every 30,000 km or every 12 months, " +
                "whichever comes first. Replace the secondary filter every 60,000 km."
        ),
    )

    private val engine = SearchEngine(chunks)

    @Test
    fun `finds a chunk by a unique word`() {
        val results = engine.search("P0087")
        assertTrue("should return at least one result", results.isNotEmpty())
        assertEquals("c101", results.first().chunk.id)
    }

    @Test
    fun `ranks the best matching chunk first`() {
        val results = engine.search("injector")
        assertTrue("should return at least one result", results.isNotEmpty())
        assertTrue(
            "an injector chunk should come first, got ${results.first().chunk.id}",
            results.first().chunk.id == "c104" || results.first().chunk.id == "c008",
        )
    }

    @Test
    fun `finds every relevant chunk for a topic`() {
        val results = engine.search("injector")
        val ids = results.map { it.chunk.id }.toSet()
        assertTrue("both injector chunks should be found, got $ids",
            ids.contains("c104") && ids.contains("c008"))
    }

    @Test
    fun `matches the start of a word`() {
        // Searching a prefix must find the full word inside the chunk.
        val results = engine.search("press")
        val ids = results.map { it.chunk.id }.toSet()
        assertTrue("prefix 'press' should find pressure chunks, got $ids",
            ids.contains("c005") && ids.contains("c101"))
    }

    @Test
    fun `ignores very common words`() {
        // "the" and "what" are stop words and must not match anything.
        val results = engine.search("what is the")
        assertTrue("a query of only common words should return nothing", results.isEmpty())
    }

    @Test
    fun `returns nothing when there is no match`() {
        val results = engine.search("helicopter")
        assertTrue("there is no mention of helicopters", results.isEmpty())
    }

    @Test
    fun `keeps every result above zero score`() {
        val results = engine.search("filter")
        assertTrue(results.isNotEmpty())
        for (result in results) {
            assertTrue("score must be positive", result.score > 0.0)
        }
    }

    @Test
    fun `respects the result limit`() {
        val results = engine.search("fuel", limit = 2)
        assertTrue("limit should be respected", results.size <= 2)
    }

    @Test
    fun `reports which words actually matched`() {
        val results = engine.search("fuel filter")
        assertTrue(results.isNotEmpty())
        val matched = results.first().matchedTerms
        assertTrue("matched terms should be reported, got $matched",
            matched.contains("filter") || matched.contains("fuel"))
    }
    @Test fun `rare fault code outranks common fuel words`() {
        val common=(1..100).map { Chunk("common$it","s","Fuel pressure",1,"Fuel fuel pressure fuel filter.") }
        val exact=Chunk("fault","s","Code AZ17",2,"AZ17 identifies an open sensor circuit in the fuel system.")
        assertEquals("fault",SearchEngine(common+exact).search("fuel AZ17").first().chunk.id)
    }
    @Test fun `non Latin words and heading only matches are indexed`() {
        val chunk=Chunk("ru","s","Давление масла",7,"380 бар.")
        assertEquals("ru",SearchEngine(listOf(chunk)).search("давление").first().chunk.id)
    }
    @Test fun `whole passage budgeting never clips a warning`() {
        val rag=RagEngine(chunks,listOf(com.dataeater.app.model.SourceInfo("s1","Manual","Jack",2026)))
        val found=rag.retrieve("pressure")
        assertTrue(rag.fitContext(found,10).isEmpty())
        val selected=rag.fitContext(found,1000)
        assertTrue(selected.all { item -> found.any { it.chunk.text == item.chunk.text } })
    }
    @Test fun `contents listings never displace answer evidence`() {
        val toc=Chunk("toc","s","Contents",1,"Ohm law electrical resistance............17\nElectricity current voltage..............18")
        val body=Chunk("body","s","Ohm's law",17,"Ohm's law relates voltage, current and resistance.")
        val result=SearchEngine(listOf(toc,body)).search("Ohm law electrical resistance")
        assertEquals(listOf("body"),result.map { it.chunk.id })
    }
    @Test fun `plural question matches singular technical headings`() {
        val chunk=Chunk("stroke","s","Intake Stroke",2,"Air enters the cylinder.")
        assertEquals("stroke",SearchEngine(listOf(chunk)).search("strokes").first().chunk.id)
    }
    @Test fun `publisher ancestry finds a subsection without adding answer facts`() {
        val chunk=Chunk("intake","s","Intake Stroke",47,"The piston moves downward.","Aircraft Engines > Four-Stroke Cycle > Intake Stroke")
        assertEquals("intake",SearchEngine(listOf(chunk)).search("four strokes engine").first().chunk.id)
        assertEquals("The piston moves downward.",chunk.text)
    }
}