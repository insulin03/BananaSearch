package com.banana.search.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.banana.search.core.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class OcrResult(val fullText: String, val lines: List<String>) {
    companion object {
        val EMPTY = OcrResult("", emptyList())
    }
}

object OcrEngine {

    private const val TAG = "Ocr"

    private val client by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    @Volatile
    private var failed = false

    val available: Boolean get() = !failed

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        if (failed) return OcrResult.EMPTY
        return try {
            suspendCancellableCoroutine { cont ->
                val image = InputImage.fromBitmap(bitmap, 0)
                client.process(image)
                    .addOnSuccessListener { result ->
                        if (!cont.isActive) return@addOnSuccessListener
                        val lines = ArrayList<String>()
                        for (block in result.textBlocks) {
                            for (line in block.lines) {
                                val t = line.text.trim()
                                if (t.isNotEmpty()) lines.add(t)
                            }
                        }
                        cont.resume(OcrResult(result.text.trim(), lines))
                    }
                    .addOnFailureListener { t ->
                        AppLog.w(TAG, "识别失败: ${t.message}")
                        if (cont.isActive) cont.resume(OcrResult.EMPTY)
                    }
            }
        } catch (t: Throwable) {

            failed = true
            AppLog.e(TAG, "OCR 不可用，降级为纯云端视觉", t)
            OcrResult.EMPTY
        }
    }
}
