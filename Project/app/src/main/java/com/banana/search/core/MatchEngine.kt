package com.banana.search.core

import android.content.Context
import android.graphics.Bitmap
import com.banana.search.capture.ImagePreprocess
import com.banana.search.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecognizeOutcome(
    val tier: Tier,
    val ok: Boolean = true,

    val ocrText: String = "",

    val matchedQuery: String = "",
    val top: List<ScoredItem> = emptyList(),
    val cloudQuestion: String = "",
    val cloudAnswer: String = "",
    val cloudExplanation: String = "",
    val usage: DeepSeekUsage? = null,
    val elapsedMs: Long = 0L,
    val message: String = "",

    val ambiguous: Boolean = false
) {
    enum class Tier(val label: String) {
        LOCAL("一档·本地题库"),

        APPROX("本地近似"),
        TEXT("二档·纯文字"),
        VISION("三档·截图视觉"),
        FAILED("失败")
    }
}

class MatchEngine(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    private val settings: Settings,
    private val client: DeepSeekClient
) {

    suspend fun recognizeFromText(
        fullText: String,
        lines: List<String>,
        questionImage: Bitmap? = null
    ): RecognizeOutcome =
        withContext(Dispatchers.Default) {
            val started = System.currentTimeMillis()
            if (lines.isEmpty() && fullText.isBlank()) {
                return@withContext RecognizeOutcome(
                    tier = RecognizeOutcome.Tier.FAILED,
                    ok = false,
                    elapsedMs = System.currentTimeMillis() - started,
                    message = "无障碍没有读到任何文字：这个界面可能是图片渲染的。" +
                        "可在「启动」页把取词方式切换为「截屏 OCR」。"
                )
            }
            decide(lines, fullText, cropped = questionImage, forceVision = false, started = started)
        }

    suspend fun recognize(screen: Bitmap, region: android.graphics.Rect? = null): RecognizeOutcome =
        withContext(Dispatchers.Default) {
        val started = System.currentTimeMillis()
        var cropped: Bitmap? = null
        var ocrInput: Bitmap? = null
        try {
            cropped = ImagePreprocess.cropQuestion(screen, settings.cropTop, settings.cropBottom, region)

            var ocrText = ""
            var lines: List<String> = emptyList()
            if (settings.ocrEnabled && OcrEngine.available) {
                ocrInput = ImagePreprocess.prepareForOcr(cropped)
                val ocr = OcrEngine.recognize(ocrInput)
                ocrText = ocr.fullText
                lines = ocr.lines
            }

            val noText = lines.isEmpty() && ocrText.isBlank()
            decide(lines, ocrText, cropped, forceVision = noText, started = started)
        } catch (t: Throwable) {
            AppLog.e(TAG, "识别流程异常", t)
            RecognizeOutcome(
                tier = RecognizeOutcome.Tier.FAILED,
                ok = false,
                elapsedMs = System.currentTimeMillis() - started,
                message = "识别异常：${t.javaClass.simpleName} ${t.message.orEmpty()}"
            )
        } finally {
            if (ocrInput != null && ocrInput !== cropped) runCatching { ocrInput.recycle() }
            if (cropped != null && cropped !== screen) runCatching { cropped.recycle() }
        }
    }

    private suspend fun decide(
        lines: List<String>,
        fullText: String,
        cropped: Bitmap?,
        forceVision: Boolean,
        started: Long
    ): RecognizeOutcome {
        val mode = settings.searchMode

        var top: List<ScoredItem> = emptyList()
        var matched = ""
        if (mode != Settings.MODE_DEEPSEEK && QuestionBank.isLoaded) {
            if (lines.isNotEmpty()) {
                val lineMatch = QuestionBank.rankLines(lines, settings.topK)
                top = lineMatch.top
                matched = lineMatch.query
            }
            if (top.isEmpty() && fullText.isNotBlank()) {
                top = QuestionBank.rank(fullText, settings.topK)
                matched = fullText
            }
        }

        val queryForModel = matched.ifBlank { fullText }
        val best = top.firstOrNull()?.score ?: 0.0
        val second = top.getOrNull(1)?.score ?: 0.0
        val high = settings.highThreshold.toDouble()
        val low = settings.lowThreshold.toDouble()

        val ambiguous = best > 0.0 && best < high && second > best * AMBIGUOUS_RATIO

        if (TierPolicy.isConfidentLocal(best, second, high)) {
            return RecognizeOutcome(
                tier = RecognizeOutcome.Tier.LOCAL,
                ocrText = fullText,
                matchedQuery = matched,
                top = top,
                elapsedMs = System.currentTimeMillis() - started,
                message = TierPolicy.describe(best, second, high)
            )
        }

        if (mode == Settings.MODE_LOCAL) {

            return RecognizeOutcome(
                tier = if (top.isEmpty()) RecognizeOutcome.Tier.FAILED else RecognizeOutcome.Tier.APPROX,
                ocrText = fullText,
                matchedQuery = matched,
                top = top,
                elapsedMs = System.currentTimeMillis() - started,
                ambiguous = ambiguous,
                message = if (top.isEmpty()) {
                    "纯本地题库模式：没有匹配到题目"
                } else {
                    "纯本地题库模式：下面是本地最接近的候选，仅供参考（未消耗 Token）"
                }
            )
        }

        if (!client.hasKey()) {
            return RecognizeOutcome(
                tier = if (top.isEmpty()) RecognizeOutcome.Tier.FAILED else RecognizeOutcome.Tier.APPROX,
                ocrText = fullText,
                matchedQuery = matched,
                top = top,
                elapsedMs = System.currentTimeMillis() - started,
                ambiguous = ambiguous,
                message = if (top.isEmpty()) {
                    "未配置 API Key，且本地题库无匹配"
                } else {
                    "未配置 API Key：下面是本地最接近的候选，仅供参考"
                }
            )
        }

        val maySendImage = settings.sendImageToModel
        val useVision = maySendImage &&
            cropped != null &&
            (mode == Settings.MODE_DEEPSEEK || forceVision || top.isEmpty() || best < low)
        val jpeg = if (useVision) {
            ImagePreprocess.toJpeg(ImagePreprocess.scaleToMaxSide(cropped!!, VISION_MAX_SIDE), 78)
        } else {
            null
        }

        if (!useVision && queryForModel.isBlank() && top.isEmpty()) {
            return RecognizeOutcome(
                tier = RecognizeOutcome.Tier.FAILED,
                ok = false,
                ocrText = fullText,
                matchedQuery = matched,
                top = top,
                elapsedMs = System.currentTimeMillis() - started,
                message = "没有读到可用的题干文字，而「发送内容」设为「只发文字」，没有可发送的内容。" +
                    "请到启动页把它改成「文字 + 截图」，或把取词方式换成「截屏 OCR」。"
            )
        }

        val candidates = top.take(3).map { it.item.question to it.item.answers.joinToString("；") }

        return when (val result = client.ask(queryForModel.ifBlank { null }, jpeg, candidates)) {
            is DsResult.Ok -> {
                val data = result.data
                var merged = top

                if (data.question.isNotBlank() && QuestionBank.isLoaded) {
                    val reRank = QuestionBank.rank(data.question, settings.topK)
                    if (reRank.isNotEmpty() && (merged.isEmpty() || reRank.first().score > merged.first().score)) {
                        merged = reRank
                    }
                }
                val tier = if (useVision) RecognizeOutcome.Tier.VISION else RecognizeOutcome.Tier.TEXT
                val tierLabel = when {
                    mode == Settings.MODE_DEEPSEEK && useVision -> "纯 DeepSeek·看图"
                    mode == Settings.MODE_DEEPSEEK -> "纯 DeepSeek·纯文字"
                    useVision -> "三档·截图视觉"
                    else -> "二档·纯文字"
                }
                val answerFromBank = (merged.firstOrNull()?.score ?: 0.0) >= high
                RecognizeOutcome(
                    tier = tier,
                    ocrText = fullText,
                    matchedQuery = matched,
                    top = merged,
                    cloudQuestion = data.question,
                    cloudAnswer = data.answer,
                    cloudExplanation = data.explanation,
                    usage = data.usage,
                    elapsedMs = System.currentTimeMillis() - started,
                    ambiguous = ambiguous,
                    message = buildString {
                        if (answerFromBank) {
                            append("$tierLabel 调用成功，答案已与题库对齐（接近率 ${merged.first().percent}%）")
                        } else {
                            append("$tierLabel 调用成功，答案来自模型")
                        }

                        if (!maySendImage && (forceVision || top.isEmpty() || best < low)) {
                            append("（接近率偏低，但「发送内容」是只发文字，本次没有截图）")
                        }
                    }
                )
            }

            is DsResult.Err -> RecognizeOutcome(
                tier = RecognizeOutcome.Tier.FAILED,
                ok = false,
                ocrText = fullText,
                matchedQuery = matched,
                top = top,
                elapsedMs = System.currentTimeMillis() - started,
                ambiguous = ambiguous,
                message = friendlyError(result)
            )
        }
    }

    private fun friendlyError(err: DsResult.Err): String = when (err.kind) {
        DsResult.ErrKind.NO_KEY -> "未配置 API Key"
        DsResult.ErrKind.AUTH -> "API Key 无效（401），请到启动页重新填写"
        DsResult.ErrKind.NO_BALANCE -> "账户余额不足（402），已停止重试"
        DsResult.ErrKind.RATE_LIMIT -> "请求过于频繁（429），请稍后再试"
        DsResult.ErrKind.SERVER -> "服务端异常（${err.httpCode}），可重试"
        DsResult.ErrKind.NETWORK -> "网络不可用，已降级为本地结果"
        DsResult.ErrKind.BAD_FORMAT -> "返回内容格式异常：${err.message.take(80)}"
        DsResult.ErrKind.UNKNOWN -> err.message
    }

    private companion object {
        const val TAG = "Match"

        const val VISION_MAX_SIDE = 1280

        const val AMBIGUOUS_RATIO = 0.7
    }
}
