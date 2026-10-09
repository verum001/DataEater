package com.dataeater.app.ai

import org.junit.Assert.*
import org.junit.Test

class ModelCatalogProfilesTest {
    @Test fun nativeCrashProneNemotronNeverUsesGpu() {
        val model = ModelCatalog.forFile("Nemotron-3-Nano-4B_int8.litertlm")!!
        assertTrue(model.cpuOnly)
        assertEquals(3200, model.maxOutputTokens)
        assertEquals(2048, model.thinkingBudget)
    }
    @Test fun reasoningHasRoomForFinalAnswer() {
        ModelCatalog.entries.filter { it.thinkingBudget > 0 }.forEach {
            assertTrue(it.maxOutputTokens > it.thinkingBudget)
            assertSame(it, ModelCatalog.forFile(it.fileName))
        }
    }
    @Test fun legacySmallProfilesRemainRecognisedWithoutOfferingFailedDownloads() {
        val old = ModelCatalog.forFile("qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm")!!
        assertEquals(0, old.thinkingBudget)
        assertTrue(old.inlineHistory)
        assertFalse(ModelCatalog.entries.any { it.id in setOf("qwen05", "qwen06", "smol036", "lfm12") })
        assertNull(ModelCatalog.forFile("unknown-user-model.litertlm"))
    }
}
