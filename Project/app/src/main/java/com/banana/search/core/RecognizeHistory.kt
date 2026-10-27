package com.banana.search.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object RecognizeHistory {

    private const val FILE = "history.json"
    private const val LIMIT = 50

    data class Entry(
        val at: Long,
        val tier: String,
        val question: String,
        val answer: String,
        val tokens: Int,
        val yuan: Double
    )

    @Synchronized
    fun list(context: Context): List<Entry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    at = o.optLong("at"),
                    tier = o.optString("tier"),
                    question = o.optString("question"),
                    answer = o.optString("answer"),
                    tokens = o.optInt("tokens"),
                    yuan = o.optDouble("yuan", 0.0)
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(context: Context, entry: Entry) {
        if (entry.question.isBlank() && entry.answer.isBlank()) return
        val old = list(context).take(LIMIT - 1)
        val arr = JSONArray()
        val head = JSONObject().apply {
            put("at", entry.at)
            put("tier", entry.tier)
            put("question", entry.question.take(500))
            put("answer", entry.answer.take(1000))
            put("tokens", entry.tokens)
            put("yuan", entry.yuan)
        }
        arr.put(head)
        old.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("at", e.at)
                    put("tier", e.tier)
                    put("question", e.question)
                    put("answer", e.answer)
                    put("tokens", e.tokens)
                    put("yuan", e.yuan)
                }
            )
        }
        runCatching { file(context).writeText(arr.toString(), Charsets.UTF_8) }
    }

    @Synchronized
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE)
}
