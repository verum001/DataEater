package com.dataeater.app.builder

import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.data.DatabaseReader
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Android's ICU regex runtime differs from the desktop JVM; only synthetic text is used. */
class BuilderQualityNativeDeviceTest {
    @Test fun reviewHintsCompileAndMatchOnAndroid() {
        val flags = BuilderQuality.changes("5. Не снимать PX-17 при 12.5 МПа.", "6. Снимать PX-18 при 13.5 бар.")
        for (name in listOf("numbers", "restrictions", "units", "identifiers", "numbered_steps")) {
            assertTrue(name, "${name}_changed" in flags)
        }
        assertEquals(listOf("text_changed"), BuilderQuality.changes("1. Do not use 12.5 MPa.", "1.  Do not use 12.5  MPa."))
    }
    @Test fun sourceReportAndReaderWorkOnNativeRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "quality-native-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val doc = BuilderCore.Document("Synthetic report", "synthetic.pdf", "a".repeat(64), 3, 1,
                listOf(BuilderCore.Page(1, "1. Do not remove PX-17 at 12.5 MPa."), BuilderCore.Page(3, "Stop before inspection.")),
                pageMetrics = listOf(BuilderQuality.Metric(2, 12, 12)))
            val file = File(root, "synthetic.dataeater")
            BuilderCore.writeDatabase(listOf(doc), BuilderCore.Options("Synthetic report"), file)
            assertEquals(listOf(1, 3), DatabaseReader.openFile(file).chunks.map { it.page }.distinct())
            val sources = JSONArray(BuilderCore.entries(file).getValue("sources.json").toString(Charsets.UTF_8))
            val pages = sources.getJSONObject(0).getJSONObject("preparation_quality").getJSONArray("pages")
            assertFalse(pages.getJSONObject(1).getBoolean("included"))
            assertEquals(12, pages.getJSONObject(1).getInt("characters"))
            assertEquals("not_verified", pages.getJSONObject(0).getString("reading_order"))
        } finally { root.deleteRecursively() }
    }
}
