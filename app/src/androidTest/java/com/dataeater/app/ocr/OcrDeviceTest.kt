package com.dataeater.app.ocr

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.googlecode.tesseract.android.TessBaseAPI
import com.dataeater.app.ocr.OfflineOcr
import com.dataeater.app.ocr.OcrLanguage
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/** Synthetic scanned pages only; no LLM or shared files. */
class OcrDeviceTest {
    @Test fun russianLanguageRecognisesCyrillic() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val native=TessBaseAPI()
        try { assertTrue(native.getVersion().startsWith("5.5.3")) } finally { native.recycle() }
        val bitmap=instrument.context.assets.open("pdf-names/russian-title.png").use {BitmapFactory.decodeStream(it)}!!
        try {
            OfflineOcr.open(instrument.targetContext,OcrLanguage.RUSSIAN).use {ocr ->
                val text=ocr.recognise(bitmap).uppercase(Locale.ROOT)
                assertTrue(text.contains("РУКОВОДСТВО"));assertTrue(text.contains("РЕМОНТУ"))
            }
        } finally {bitmap.recycle()}
    }
}
