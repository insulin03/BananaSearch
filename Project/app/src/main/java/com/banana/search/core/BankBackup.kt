package com.banana.search.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BankBackup {

    const val FORMAT = "banana-bank-export"
    const val VERSION = 1

    data class BatchBundle(
        val name: String,
        val source: String,
        val importedAt: Long,
        val enabled: Boolean,
        val questions: List<StoredQuestion>
    )

    fun exportJson(context: Context): Pair<String, Int> {
        val batches = BankStore.batches(context)
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        var total = 0
        for (b in batches) {
            val questions = BankStore.questions(context, b.id)
            total += questions.size
            val bo = JSONObject()
            bo.put("name", b.name)
            bo.put("source", b.source)
            bo.put("importedAt", b.importedAt)
            bo.put("enabled", b.enabled)
            val qs = JSONArray()
            for (q in questions) {
                val qo = JSONObject()
                qo.put("question", q.question)
                qo.put("options", JSONArray(q.options))
                qo.put("answers", JSONArray(q.answers))
                qo.put("type", q.type)
                if (q.analysis.isNotEmpty()) qo.put("analysis", q.analysis)
                if (q.blankCount > 0) qo.put("blankCount", q.blankCount)
                qs.put(qo)
            }
            bo.put("questions", qs)
            arr.put(bo)
        }
        root.put("batches", arr)
        return root.toString(2) to total
    }

    fun isBundle(text: String): Boolean {
        val t = text.trimStart().removePrefix("\uFEFF")
        if (!t.startsWith("{")) return false
        return t.contains("\"$FORMAT\"") || t.contains(FORMAT)
    }

    fun parseBundle(text: String): List<BatchBundle>? {
        if (!isBundle(text)) return null
        return runCatching {
            val root = JSONObject(text.trim().removePrefix("\uFEFF"))
            val arr = root.optJSONArray("batches") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val bo = arr.optJSONObject(i) ?: return@mapNotNull null
                val qs = bo.optJSONArray("questions") ?: JSONArray()
                val questions = (0 until qs.length()).mapNotNull { j ->
                    val qo = qs.optJSONObject(j) ?: return@mapNotNull null
                    val q = qo.optString("question").trim()
                    val answers = qo.optJSONArray("answers").toStringList()
                    val options = qo.optJSONArray("options").toStringList()
                    if (q.isEmpty() || answers.isEmpty()) return@mapNotNull null
                    StoredQuestion(
                        question = q,
                        options = options,
                        answers = answers,
                        type = qo.optString("type", "qa"),
                        analysis = qo.optString("analysis", ""),
                        blankCount = qo.optInt("blankCount", 0)
                    )
                }
                if (questions.isEmpty()) return@mapNotNull null
                BatchBundle(
                    name = bo.optString("name", "备份批次"),
                    source = bo.optString("source", "备份导入"),
                    importedAt = bo.optLong("importedAt", System.currentTimeMillis()),
                    enabled = bo.optBoolean("enabled", true),
                    questions = questions
                )
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun restore(context: Context, bundles: List<BatchBundle>): List<BankBatch> {
        val created = ArrayList<BankBatch>()
        for (b in bundles) {

            val batch = BankStore.create(
                context,
                b.name,
                "备份：" + b.source,
                b.questions.map { q ->
                    ParsedQuestion(
                        question = q.question,
                        options = q.options,
                        answers = q.answers,
                        type = QuestionTypes.infer(q.question, q.type, q.answers),
                        analysis = q.analysis,
                        blankCount = q.blankCount
                    )
                }
            )
            if (!b.enabled) BankStore.setEnabled(context, batch.id, false)
            created.add(if (b.enabled) batch else batch.copy(enabled = false))
        }
        QuestionBank.reload(context)
        return created
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            val s = optString(i).trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}
