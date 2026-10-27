package com.banana.search.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BankBatch(
    val id: String,
    val name: String,
    val source: String,
    val importedAt: Long,
    val enabled: Boolean,
    val count: Int
)

data class StoredQuestion(
    val question: String,
    val options: List<String>,
    val answers: List<String>,
    val type: String,
    val analysis: String,
    val blankCount: Int
)

object BankStore {

    private const val DIR = "banks"
    private const val INDEX = "index.json"
    private const val VERSION = 1

    @Synchronized
    fun batches(context: Context): List<BankBatch> {
        val index = indexFile(context)
        if (!index.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(index.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("batches") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                BankBatch(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    source = o.optString("source"),
                    importedAt = o.optLong("importedAt"),
                    enabled = o.optBoolean("enabled", true),
                    count = o.optInt("count", 0)
                )
            }.filter { it.id.isNotEmpty() }.sortedByDescending { it.importedAt }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun create(context: Context, name: String, source: String, questions: List<ParsedQuestion>): BankBatch {
        val dir = dir(context)
        dir.mkdirs()

        val used = batches(context).map { it.name }.toHashSet()
        var finalName = name
        var dup = 2
        while (finalName in used) {
            finalName = "$name ($dup)"
            dup++
        }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        var id = stamp
        var n = 1
        while (File(dir, "$id.json").exists()) {
            id = "$stamp-${n++}"
        }
        val items = JSONArray()
        for (q in questions) {
            items.put(
                JSONObject().apply {
                    put("question", q.question)
                    put("options", JSONArray(q.options))
                    put("answers", JSONArray(q.answers))
                    put("type", q.typeKey)
                    put("analysis", q.analysis)
                    put("blankCount", q.blankCount)
                }
            )
        }
        val file = JSONObject().apply {
            put("version", VERSION)
            put("id", id)
            put("name", finalName)
            put("source", source)
            put("importedAt", System.currentTimeMillis())
            put("items", items)
        }
        File(dir, "$id.json").writeText(file.toString(), Charsets.UTF_8)

        val batch = BankBatch(
            id = id,
            name = finalName,
            source = source,
            importedAt = System.currentTimeMillis(),
            enabled = true,
            count = questions.size
        )
        writeIndex(context, batches(context) + batch)
        return batch
    }

    fun questions(context: Context, id: String): List<StoredQuestion> {
        val f = File(dir(context), "$id.json")
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(f.readText(Charsets.UTF_8)).optJSONArray("items") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val q = o.optString("question").trim()
                if (q.isEmpty()) return@mapNotNull null
                StoredQuestion(
                    question = q,
                    options = o.optJSONArray("options").toStringList(),
                    answers = o.optJSONArray("answers").toStringList(),
                    type = o.optString("type", "qa"),
                    analysis = o.optString("analysis", ""),
                    blankCount = o.optInt("blankCount", QuestionNormalizer.countBlanks(q))
                )
            }
        }.getOrDefault(emptyList())
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

    @Synchronized
    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val list = batches(context).map { if (it.id == id) it.copy(enabled = enabled) else it }
        writeIndex(context, list)
    }

    @Synchronized
    fun delete(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        val dir = dir(context)
        set.forEach { id -> runCatching { File(dir, "$id.json").delete() } }
        writeIndex(context, batches(context).filter { it.id !in set })
    }

    @Synchronized
    fun deleteQuestion(context: Context, id: String, questionIndex: Int): Int {
        val dir = dir(context)
        val f = File(dir, "$id.json")
        if (!f.exists()) return 0
        val root = runCatching { JSONObject(f.readText(Charsets.UTF_8)) }.getOrNull() ?: return 0
        val arr = root.optJSONArray("items") ?: return 0
        if (questionIndex !in 0 until arr.length()) return arr.length()
        val remaining = JSONArray()
        for (i in 0 until arr.length()) {
            if (i == questionIndex) continue
            arr.optJSONObject(i)?.let { remaining.put(it) }
        }

        if (remaining.length() == 0) {
            delete(context, listOf(id))
            return 0
        }
        root.put("items", remaining)
        f.writeText(root.toString(), Charsets.UTF_8)
        writeIndex(context, batches(context).map { if (it.id == id) it.copy(count = remaining.length()) else it })
        return remaining.length()
    }

    private fun dir(context: Context) = File(context.filesDir, DIR)

    private fun indexFile(context: Context) = File(dir(context), INDEX)

    private fun writeIndex(context: Context, list: List<BankBatch>) {
        val dir = dir(context)
        dir.mkdirs()
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                    put("source", b.source)
                    put("importedAt", b.importedAt)
                    put("enabled", b.enabled)
                    put("count", b.count)
                }
            )
        }
        val root = JSONObject().apply {
            put("version", VERSION)
            put("batches", arr)
        }
        indexFile(context).writeText(root.toString(), Charsets.UTF_8)
    }
}
