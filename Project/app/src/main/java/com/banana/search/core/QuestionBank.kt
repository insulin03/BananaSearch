package com.banana.search.core

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ln

data class BankItem(
    val id: Int,
    val question: String,
    val normalized: String,
    val answers: List<String>,
    val type: String,
    val blankCount: Int,
    val batchId: String = ""
) {

    val shingles: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Similarity.shingles(normalized)
    }
}

object QuestionBank {

    private val loaded = AtomicBoolean(false)

    @Volatile
    private var items: List<BankItem> = emptyList()

    @Volatile
    private var idfMap: Map<String, Double> = emptyMap()

    val all: List<BankItem> get() = items
    val size: Int get() = items.size
    val isLoaded: Boolean get() = loaded.get()

    @Synchronized
    fun load(context: Context): Int {
        if (loaded.get()) return items.size
        return reload(context)
    }

    @Synchronized
    fun reload(context: Context): Int {
        val list = ArrayList<BankItem>()
        var id = 1
        for (batch in BankStore.batches(context)) {
            if (!batch.enabled) continue
            for (q in BankStore.questions(context, batch.id)) {
                if (q.question.isBlank() || q.answers.isEmpty()) continue
                list.add(
                    BankItem(
                        id = id++,
                        question = q.question,
                        normalized = QuestionNormalizer.normalize(q.question),
                        answers = q.answers,
                        type = q.type,
                        blankCount = q.blankCount,
                        batchId = batch.id
                    )
                )
            }
        }
        items = list
        idfMap = buildIdf(list)
        cachedSelfCheck = null
        loaded.set(true)
        return list.size
    }

    private fun buildIdf(bank: List<BankItem>): Map<String, Double> {
        val df = HashMap<String, Int>(4096)
        for (item in bank) {
            for (s in item.shingles) df[s] = (df[s] ?: 0) + 1
        }
        val n = bank.size.coerceAtLeast(1).toDouble()
        val out = HashMap<String, Double>(df.size)
        for ((k, v) in df) out[k] = ln(1.0 + n / (1.0 + v))
        return out
    }

    fun rankByNormalized(normQuery: String, topK: Int = 2): List<ScoredItem> =
        Similarity.rank(normQuery, items, idfMap, topK)

    fun rank(rawQuery: String, topK: Int = 2): List<ScoredItem> =
        rankByNormalized(QuestionNormalizer.normalize(rawQuery), topK)

    fun rankLines(lines: List<String>, topK: Int = 2): Similarity.LineMatch =
        Similarity.rankLines(lines, items, idfMap, topK)

    @Volatile
    private var cachedSelfCheck: SelfCheckResult? = null

    fun selfCheck(): SelfCheckResult {
        cachedSelfCheck?.let { return it }

        if (items.isEmpty()) return SelfCheckResult(0, 0, 0.0, 0.0)
        synchronized(this) {
            cachedSelfCheck?.let { return it }
            val result = computeSelfCheck()
            cachedSelfCheck = result
            return result
        }
    }

    private fun computeSelfCheck(): SelfCheckResult {
        if (items.isEmpty()) return SelfCheckResult(0, 0, 0.0, 0.0)
        var worst = Double.MAX_VALUE
        var worstId = items.first().id
        var sum = 0.0
        for (item in items) {
            val top = rankByNormalized(item.normalized, 1).firstOrNull() ?: continue
            val s = top.score
            sum += s
            if (s < worst) {
                worst = s
                worstId = item.id
            }
        }
        val worstFinal = if (worst == Double.MAX_VALUE) 0.0 else worst
        return SelfCheckResult(
            total = items.size,
            worstId = worstId,
            worstScore = worstFinal,
            avgScore = sum / items.size
        )
    }

    data class SelfCheckResult(
        val total: Int,
        val worstId: Int,
        val worstScore: Double,
        val avgScore: Double
    )

}
