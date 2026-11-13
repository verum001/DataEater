package com.dataeater.app.builder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.dataeater.app.ocr.OfflineOcr
import com.dataeater.app.ocr.OcrLanguage
import com.dataeater.app.ocr.OcrPolicy
import java.io.Closeable
import java.io.File

/** Reads only a private source copy after the builder has obtained document access. */
internal class BuilderPdfOcr(private val context: Context, private val source: File,
    private val language: OcrLanguage) : Closeable {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var engine: OfflineOcr? = null

    fun page(number: Int): OfflineOcr.Result {
        BuilderCore.checkpoint()
        if (renderer == null) {
            descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor!!)
        }
        if (engine == null) engine = OfflineOcr.open(context, language)
        return renderer!!.openPage(number - 1).use { page ->
            val size = OcrPolicy.renderSize(page.width.toDouble(), page.height.toDouble())
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                BuilderCore.checkpoint()
                engine!!.recognisePage(bitmap).also {
                    BuilderCore.checkpoint()
                    require(!it.truncated) { "OCR text on page $number exceeds the safe page limit. Split or simplify this PDF." }
                }
            } finally { bitmap.recycle() }
        }
    }
    override fun close() {
        try { engine?.close() } finally {
            try { renderer?.close() } finally { descriptor?.close() }
        }
    }
}
