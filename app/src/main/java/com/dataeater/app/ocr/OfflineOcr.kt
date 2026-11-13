package com.dataeater.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

/** CPU-only OCR. Use on a worker thread; each session owns its native instance. */
class OfflineOcr private constructor(private val api: TessBaseAPI) : Closeable {
    data class Result(val text: String, val confidence: Int, val truncated: Boolean)
    fun recognise(bitmap: Bitmap): String = recognisePage(bitmap).let { OcrPolicy.usableText(it.text, it.confidence) }

    /** Full-page extraction has a separate bound from the short naming excerpt. */
    fun recognisePage(bitmap: Bitmap): Result {
        require(!bitmap.isRecycled && bitmap.width.toLong() * bitmap.height <= OcrPolicy.MAX_PIXELS && maxOf(bitmap.width, bitmap.height) <= OcrPolicy.MAX_EDGE)
        return try {
            api.setImage(bitmap)
            val text = api.getUTF8Text().orEmpty()
            val confidence = api.meanConfidence()
            val clean = text.filter { it == '\n' || it == '\t' || !it.isISOControl() }.trim()
            Result(if (confidence in OcrPolicy.MIN_CONFIDENCE..100) clean.take(MAX_PAGE_TEXT) else "",
                confidence.coerceIn(0, 100), clean.length > MAX_PAGE_TEXT)
        } finally { api.clear() }
    }
    override fun close() { api.recycle() }

    companion object {
        const val MAX_PAGE_TEXT = 50_000
        private val hashes = mapOf(
            "eng" to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
            "rus" to "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225",
        )
        private fun digest(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
            while (true) { val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        @Synchronized private fun prepare(context: Context, language: OcrLanguage): File {
            val root = File(context.filesDir,"ocr");val data=File(root,"tessdata")
            require(data.isDirectory || data.mkdirs()) { "Could not prepare offline OCR." }
            for (code in language.files) {
                val target=File(data,"$code.traineddata");val expected=hashes.getValue(code)
                if(target.isFile && target.length() in 1..8_000_000 && digest(target)==expected) continue
                val atomic=AtomicFile(target);val output=atomic.startWrite()
                try {
                    context.assets.open("ocr/$code.traineddata").use { input ->
                        val hash=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);var total=0
                        while(true) { val n=input.read(buffer);if(n<0) break;total+=n;require(total<=8_000_000);output.write(buffer,0,n);hash.update(buffer,0,n) }
                        require(hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }==expected) { "Offline OCR data verification failed." }
                    }
                    atomic.finishWrite(output)
                } catch(problem: Throwable) { atomic.failWrite(output);throw problem }
            }
            return root
        }
        fun open(context: Context, language: OcrLanguage): OfflineOcr {
            val root=prepare(context,language);val api=TessBaseAPI()
            try {
                require(api.init(root.absolutePath,language.code,TessBaseAPI.OEM_LSTM_ONLY)) { "Could not start offline OCR." }
                api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                api.setVariable("debug_file","/dev/null")
                return OfflineOcr(api)
            } catch(problem: Throwable) { api.recycle();throw problem }
        }
    }
}
