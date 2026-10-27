package com.banana.search.core

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class DeepSeekUsage(
    val promptTokens: Int,
    val cacheHitTokens: Int,
    val cacheMissTokens: Int,
    val completionTokens: Int
) {
    fun short(): String = "输入$promptTokens(命中$cacheHitTokens) 输出$completionTokens"
}

data class ParsedAnswer(
    val question: String,
    val answer: String,
    val explanation: String,
    val usage: DeepSeekUsage?
)

sealed class DsResult {
    data class Ok(val data: ParsedAnswer) : DsResult()

    data class Err(val kind: ErrKind, val httpCode: Int, val message: String) : DsResult()

    enum class ErrKind { NO_KEY, AUTH, NO_BALANCE, RATE_LIMIT, SERVER, NETWORK, BAD_FORMAT, UNKNOWN }
}

class DeepSeekClient(private val keyProvider: () -> String?) {

    private val systemPrompt: String = SYSTEM_PROMPT

    fun hasKey(): Boolean = !keyProvider().isNullOrBlank()

    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val key = keyProvider()?.trim().orEmpty()
        if (key.isEmpty()) return@withContext false to "尚未填写 API Key"
        try {
            val r = request("GET", "$BASE/user/balance", null, key, 20000)
            when (r.code) {
                200 -> {
                    val o = runCatching { JSONObject(r.body ?: "{}") }.getOrElse { JSONObject() }
                    val available = o.optBoolean("is_available", false)
                    var balanceText = "未知"
                    o.optJSONArray("balance_infos")?.optJSONObject(0)?.let { b ->
                        balanceText = b.optString("currency", "CNY") + " " + b.optString("total_balance", "?")
                    }
                    if (available) true to "连通正常，余额 $balanceText" else false to "Key 有效但余额不足（$balanceText）"
                }
                401 -> false to "Key 无效或已失效（401）"
                402 -> false to "余额不足，请充值（402）"
                429 -> false to "触发限流（429），稍后重试"
                else -> false to "HTTP ${r.code}：${r.body?.take(140).orEmpty()}"
            }
        } catch (t: Throwable) {
            false to "网络异常：${t.javaClass.simpleName} ${t.message.orEmpty()}"
        }
    }

    suspend fun ask(
        text: String?,
        imageJpeg: ByteArray?,
        candidates: List<Pair<String, String>> = emptyList(),
        timeoutMs: Int = 90000
    ): DsResult = withContext(Dispatchers.IO) {
        askBlocking(text, imageJpeg, candidates, timeoutMs)
    }

    private fun askBlocking(
        text: String?,
        imageJpeg: ByteArray?,
        candidates: List<Pair<String, String>>,
        timeoutMs: Int
    ): DsResult {
        val key = keyProvider()?.trim().orEmpty()
        if (key.isEmpty()) return DsResult.Err(DsResult.ErrKind.NO_KEY, 0, "尚未配置 API Key")

        val userParts = JSONArray()
        userParts.put(
            JSONObject().put("type", "text")
                .put("text", buildUserText(text, imageJpeg != null, candidates))
        )
        if (imageJpeg != null) {
            val b64 = Base64.encodeToString(imageJpeg, Base64.NO_WRAP)
            userParts.put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject()
                        .put("url", "data:image/jpeg;base64,$b64")
                        .put("detail", "low")
                )
            )
        }

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userParts))

        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("max_tokens", MAX_TOKENS)
            .put("temperature", 0.2)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("stream", false)

        var r = try {
            request("POST", "$BASE/chat/completions", body.toString(), key, timeoutMs)
        } catch (t: Throwable) {
            return DsResult.Err(DsResult.ErrKind.NETWORK, 0, "网络异常：${t.javaClass.simpleName} ${t.message.orEmpty()}")
        }

        if (r.code == 400 && r.body.orEmpty().contains("thinking", ignoreCase = true)) {
            body.remove("thinking")
            r = try {
                request("POST", "$BASE/chat/completions", body.toString(), key, timeoutMs)
            } catch (t: Throwable) {
                return DsResult.Err(DsResult.ErrKind.NETWORK, 0, "网络异常：${t.javaClass.simpleName} ${t.message.orEmpty()}")
            }
        }

        if (r.code != 200) {
            val kind = when (r.code) {
                401 -> DsResult.ErrKind.AUTH
                402 -> DsResult.ErrKind.NO_BALANCE
                429 -> DsResult.ErrKind.RATE_LIMIT
                in 500..599 -> DsResult.ErrKind.SERVER
                else -> DsResult.ErrKind.UNKNOWN
            }
            return DsResult.Err(kind, r.code, "HTTP ${r.code}：${r.body?.take(180).orEmpty()}")
        }

        return try {
            val root = JSONObject(r.body.orEmpty())
            val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            val content = message?.optString("content").orEmpty()
            val usage = root.optJSONObject("usage")?.let {
                DeepSeekUsage(
                    promptTokens = it.optInt("prompt_tokens"),
                    cacheHitTokens = it.optInt("prompt_cache_hit_tokens"),
                    cacheMissTokens = it.optInt("prompt_cache_miss_tokens"),
                    completionTokens = it.optInt("completion_tokens")
                )
            }
            val parsed = parseJsonAnswer(content)
            if (parsed == null) {
                DsResult.Err(DsResult.ErrKind.BAD_FORMAT, 200, "返回内容无法解析为 json：${content.take(140)}")
            } else {
                DsResult.Ok(parsed.copy(usage = usage))
            }
        } catch (t: Throwable) {
            DsResult.Err(DsResult.ErrKind.BAD_FORMAT, 200, "解析失败：${t.message.orEmpty()}")
        }
    }

    private fun buildUserText(
        text: String?,
        withImage: Boolean,
        candidates: List<Pair<String, String>>
    ): String {
        val t = text?.trim().orEmpty()
        val head = when {
            withImage && t.isEmpty() -> "以下是屏幕截图，请识别其中的题目并输出 json。"
            withImage -> "以下是屏幕截图。本地 OCR 得到的文字（可能有个别错字，仅供对齐参考）：\n$t\n\n请以截图为准，输出 json。"
            else -> "题目文字（来自屏幕识别，可能有个别错字）：\n$t\n\n请输出 json。"
        }
        if (candidates.isEmpty()) return head
        val sb = StringBuilder(head)
        sb.append("\n\n本地题库里与它最接近的题目（注意：这些题措辞相近但含义可能不同，")
        sb.append("只有确认真正对应时才采用其答案，否则按题目本身作答）：\n")
        candidates.forEachIndexed { index, (q, a) ->
            sb.append(index + 1).append(") 题干：").append(q).append('\n')
            sb.append("   答案：").append(a).append('\n')
        }
        return sb.toString()
    }

    private fun extractJson(raw: String): String? {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trim()
            val end = s.lastIndexOf("```")
            if (end >= 0) s = s.substring(0, end).trim()
        }
        val start = s.indexOf('{')
        val stop = s.lastIndexOf('}')
        if (start < 0 || stop <= start) return null
        return s.substring(start, stop + 1)
    }

    private fun parseJsonAnswer(raw: String): ParsedAnswer? {
        val json = extractJson(raw) ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val q = o.optString("question").trim()
        val a = o.optString("answer").trim()
        val e = o.optString("explanation").trim()
        if (q.isEmpty() && a.isEmpty()) return null
        return ParsedAnswer(q, a, e, null)
    }

    private class HttpResult(val code: Int, val body: String?)

    private fun request(method: String, url: String, body: String?, key: String, timeoutMs: Int): HttpResult {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = timeoutMs
            doInput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            }
            return HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val MODEL = "deepseek-flash"
        private const val BASE = "https://api.deepseek.com"
        private const val MAX_TOKENS = 1024

        private val SYSTEM_PROMPT = """
            你是题目解析助手。用户会给你一道题的文字或屏幕截图。你只需要输出一个 json 对象，不要输出任何解释性文字、不要使用代码块。
            json 结构固定为：
            {"question":"归一化后的完整题干","answer":"简洁答案","explanation":"不超过80字的解析"}
            规则：
            1. 只输出 json，字段顺序固定为 question、answer、explanation。
            2. question 必须完整保留题干本身，不要改写、不要省略、不要添加题号。
            3. answer 用最短形式给出；多个空用「；」分隔；选项类题目给出应当选择的选项文字。
            4. explanation 尽量短，不超过 80 字；不确定时留空字符串。
            5. 如果文字和图片里都没有可识别的题目，question 与 answer 都输出空字符串。
            6. 不要编造题目内容；截图模糊时，给出你能确定的部分。
            7. 如果用户消息里附带了「本地题库里与它最接近的题目」，那批题可能只是措辞相近、含义并不相同；只有确认某一条与题目真正对应时才采用它的答案，否则忽略它们、按题目本身作答。
        """.trimIndent()
    }
}
