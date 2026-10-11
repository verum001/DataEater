package com.dataeater.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dataeater.app.data.DatabaseCollection
import com.dataeater.app.data.DatabaseReader
import com.dataeater.app.engine.SearchEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseCollectionDeviceTest {
    @Test fun actualSurvivalPackageAndCrossFileCitations() {
        val db = DatabaseReader.openFile(File("/sdcard/DataEater/survival-0.1.0.dataeater"))
        assertEquals(3, db.sources.size)
        assertEquals(2635, db.chunks.size)
        for (source in db.sources) {
            assertTrue(source.title, db.chunks.any { it.sourceId == source.id && it.text.isNotBlank() })
            val hit = SearchEngine(db.chunks.filter { it.sourceId == source.id }).search("water", 5)
            assertFalse(source.title, hit.isEmpty())
        }
        val combined = DatabaseCollection.combine(listOf(db, db.copy(fileName = "second-copy.dataeater")))
        assertEquals(db.chunks.size * 2, combined.chunks.map { it.id }.toSet().size)
        val hits = SearchEngine(combined.chunks).search("water", 100)
        assertTrue(hits.any { it.chunk.sourceId.startsWith("0:") })
        assertTrue(hits.any { it.chunk.sourceId.startsWith("1:") })
        assertTrue(combined.titleOfSource("1:" + db.sources.first().id).contains("second-copy.dataeater"))
    }
}
