package com.banana.search.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScoredItem(
    val item: BankItem,
    val score: Double
) {

    val percent: Int get() = (score * 100).roundToInt().coerceIn(0, 100)
}

object Similarity {

    const val N = 2
    const val W_NGRAM = 0.45
    const val W_EDIT = 0.25
    const val W_KEYWORD = 0.30
    private const val W_SUM = W_NGRAM + W_EDIT + W_KEYWORD

    fun shingles(s: String, n: Int = N): Set<String> {
        if (s.isEmpty()) return emptySet()
        if (s.length <= n) return hashSetOf(s)
        val out = HashSet<String>(s.length)
        for (i in 0..s.length - n) out.add(s.substring(i, i + n))
        return out
    }

    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val small: Set<String>
        val large: Set<String>
        if (a.size <= b.size) {
            small = a; large = b
        } else {
            small = b; large = a
        }
        var inter = 0
        for (x in small) if (large.contains(x)) inter++
        val union = a.size + b.size - inter
        return if (union == 0) 0.0 else inter.toDouble() / union
    }

    fun editRatio(a: String, b: String): Double {
        val m = a.length
        val n = b.length
        if (m == 0 || n == 0) return 0.0

        if (min(m, n).toDouble() / max(m, n) < 0.5) return 0.0
        var prev = IntArray(n + 1) { it }
        var cur = IntArray(n + 1)
        for (i in 1..m) {
            cur[0] = i
            val ca = a[i - 1]
            for (j in 1..n) {
                val cost = if (ca == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return 1.0 - prev[n].toDouble() / max(m, n)
    }

    fun keyword(query: Set<String>, target: Set<String>, idf: Map<String, Double>): Double {
        if (query.isEmpty()) return 0.0
        var hit = 0.0
        var total = 0.0
        for (x in query) {
            val w = idf[x] ?: 1.0
            total += w
            if (target.contains(x)) hit += w
        }
        return if (total == 0.0) 0.0 else hit / total
    }

    fun rank(
        normQuery: String,
        bank: List<BankItem>,
        idf: Map<String, Double>,
        topK: Int = 2
    ): List<ScoredItem> {
        if (normQuery.isEmpty() || bank.isEmpty()) return emptyList()
        val qa = shingles(normQuery)
        val out = ArrayList<ScoredItem>(bank.size)
        for (item in bank) {
            val target = item.shingles
            val ng = jaccard(qa, target)
            val ed = editRatio(normQuery, item.normalized)
            val kw = keyword(qa, target, idf)
            val total = (W_NGRAM * ng + W_EDIT * ed + W_KEYWORD * kw) / W_SUM
            out.add(ScoredItem(item, total))
        }
        out.sortByDescending { it.score }
        return if (out.size > topK) ArrayList(out.subList(0, topK)) else out
    }

    fun rankLines(
        lines: List<String>,
        bank: List<BankItem>,
        idf: Map<String, Double>,
        topK: Int = 2
    ): LineMatch {
        if (lines.isEmpty() || bank.isEmpty()) return LineMatch(emptyList(), "")
        var bestScore = -1.0
        var bestRaw = ""
        var bestNorm = ""
        for (candidate in candidateQueries(lines)) {
            val norm = QuestionNormalizer.normalize(candidate)
            if (norm.length < MIN_QUERY_LENGTH) continue
            val top = rank(norm, bank, idf, 1).firstOrNull() ?: continue
            if (top.score > bestScore) {
                bestScore = top.score
                bestRaw = candidate
                bestNorm = norm
            }
        }
        if (bestNorm.isEmpty()) return LineMatch(emptyList(), "")
        return LineMatch(rank(bestNorm, bank, idf, topK), bestRaw)
    }

    fun candidateQueries(lines: List<String>): List<String> {
        val cleaned = ArrayList<String>(lines.size)
        for (raw in lines) {
            val s = raw.trim()
            if (s.isEmpty()) continue
            if (s.none { it.code > CJK_BASE }) continue
            cleaned.add(s)
            if (cleaned.size >= MAX_LINES) break
        }
        val out = ArrayList<String>(cleaned.size * 3)
        for (i in cleaned.indices) {
            out.add(cleaned[i])
            if (i + 1 < cleaned.size) out.add(cleaned[i] + cleaned[i + 1])
            if (i + 2 < cleaned.size) out.add(cleaned[i] + cleaned[i + 1] + cleaned[i + 2])
            if (out.size >= MAX_CANDIDATES) break
        }
        return out
    }

    data class LineMatch(val top: List<ScoredItem>, val query: String)

    private const val MIN_QUERY_LENGTH = 4
    private const val MAX_LINES = 40
    private const val MAX_CANDIDATES = 120
    private const val CJK_BASE = 0x2E80
}
