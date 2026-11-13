package com.dataeater.app.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrPolicyTest {
    @Test fun pageRasterAlwaysFitsPixelAndEdgeBudgets() {
        for (w in listOf(1.0,72.0,595.0,842.0,10000.0,20000.0)) for(h in listOf(1.0,72.0,595.0,842.0,10000.0,20000.0)) {
            val s=OcrPolicy.renderSize(w,h)
            assertTrue(s.width>0 && s.height>0);assertTrue(s.width.toLong()*s.height<=OcrPolicy.MAX_PIXELS)
            assertTrue(maxOf(s.width,s.height)<=OcrPolicy.MAX_EDGE)
        }
    }
    @Test fun malformedPageDimensionsRejectedBeforeAllocation() {
        for (bad in listOf(0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY,20001.0)) {
            try { OcrPolicy.renderSize(bad,842.0);fail("Invalid width") } catch (_:IllegalArgumentException) {}
            try { OcrPolicy.renderSize(595.0,bad);fail("Invalid height") } catch (_:IllegalArgumentException) {}
        }
    }
    @Test fun meaningfulNativeTextDoesNotNeedOcr() {
        assertFalse(OcrPolicy.needsOcr("ISUZU TROOPER 1994 SERVICE MANUAL"))
        assertTrue(OcrPolicy.needsOcr(" "));assertTrue(OcrPolicy.needsOcr("Digitised"))
    }
    @Test fun weakAndInvalidConfidenceProducesNoEvidence() {
        for (confidence in listOf(-1,0,39,101)) assertEquals("",OcrPolicy.usableText("ISUZU TROOPER",confidence))
        assertEquals("ISUZU TROOPER",OcrPolicy.usableText(" ISUZU TROOPER ",40))
    }
    @Test fun outputIsBoundedAndControlsRemoved() {
        assertEquals(OcrPolicy.MAX_TEXT,OcrPolicy.usableText("x".repeat(20000),90).length)
        assertEquals("a\nb",OcrPolicy.usableText("a\u0000\nb",90))
    }
    @Test fun languageCodesAndFilesAreFixedNotPaths() {
        assertEquals(listOf("eng","rus"),OcrLanguage.BOTH.files)
        assertEquals("eng+rus",OcrLanguage.BOTH.code)
        assertTrue(OcrLanguage.entries.flatMap { it.files }.all { it in setOf("eng","rus") })
    }
}
