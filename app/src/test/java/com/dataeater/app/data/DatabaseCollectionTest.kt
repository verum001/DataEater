package com.dataeater.app.data

import com.dataeater.app.engine.SearchEngine
import com.dataeater.app.model.Chunk
import com.dataeater.app.model.SourceInfo
import org.junit.Assert.*
import org.junit.Test

class DatabaseCollectionTest {
    private fun database(name: String, text: String) = DatabaseReader.OpenedDatabase(
        DatabaseReader.parseManifest("""{"format":"dataeater","format_version":1,"database_id":"$name","name":"$name"}"""),
        listOf(Chunk("c001", "s001", "Water", 12, text)),
        listOf(SourceInfo("s001", "Manual", "Publisher", 2002)), "$name.dataeater",
    )

    @Test fun overlappingIdsKeepBothResultsAndCorrectSources() {
        val first = database("Army", "Water procurement in the desert")
        val second = database("AirForce", "Water procurement in the forest")
        val combined = DatabaseCollection.combine(listOf(first, second), listOf("Locked manual"))
        val hits = SearchEngine(combined.chunks).search("water procurement", limit = 5)
        assertEquals(2, hits.size)
        assertEquals(2, combined.chunks.map { it.id }.toSet().size)
        assertTrue(combined.titleOfSource(hits.first { it.chunk.text.contains("desert") }.chunk.sourceId).contains("Army.dataeater"))
        assertTrue(combined.titleOfSource(hits.first { it.chunk.text.contains("forest") }.chunk.sourceId).contains("AirForce.dataeater"))
        assertTrue(combined.manifest.description.contains("Locked manual"))
        assertEquals("c001", first.chunks.single().id)
        assertEquals(12, combined.chunks.first().page)
    }

    @Test fun collectionChoiceRestoresOnlyWhenFilesExist() {
        assertEquals(DatabaseChoice.ALL, DatabaseChoice.restore(DatabaseChoice.ALL, listOf("Army")))
        assertEquals("", DatabaseChoice.restore(DatabaseChoice.ALL, emptyList()))
    }

    @Test(expected = DatabaseReader.DataEaterException::class)
    fun oversizedCollectionIsRefused() {
        val small = database("Army", "Water")
        DatabaseCollection.combine(listOf(small.copy(chunks = List(100_001) { small.chunks.single() })))
    }
    @Test fun differentSelectedManualsHaveSeparateChatMemoryScopes() {
        val first = database("Army", "Water")
        val second = database("AirForce", "Shelter")
        val third = database("Other", "Food")
        val selected = DatabaseCollection.combine(listOf(first, second))
        val changed = DatabaseCollection.combine(listOf(first, third))
        assertNotEquals(selected.manifest.databaseId, changed.manifest.databaseId)
        assertEquals(selected.manifest.databaseId, DatabaseCollection.combine(listOf(first, second)).manifest.databaseId)
        assertFalse(selected.chunks.any { it.text == "Food" })
        assertEquals(DatabaseChoice.ALL, DatabaseChoice.restore("All unlocked databases", listOf("Army")))
    }

}
