package com.banana.search.core

object TierPolicy {

    const val MARGIN_FLOOR = 0.70

    const val DECISIVE_RATIO = 0.5

    fun isConfidentLocal(
        best: Double,
        second: Double,
        highThreshold: Double,
        marginFloor: Double = MARGIN_FLOOR
    ): Boolean {
        if (best >= highThreshold) return true
        if (best < marginFloor) return false
        return second <= best * DECISIVE_RATIO
    }

    fun describe(best: Double, second: Double, highThreshold: Double): String {
        val percent = (best * 100).toInt()
        val secondPercent = (second * 100).toInt()
        return when {
            best >= highThreshold -> "命中题库（$percent%），本次未消耗 Token"
            isConfidentLocal(best, second, highThreshold) ->
                "命中题库（$percent%，第二名仅 $secondPercent%，赢家明确），本次未消耗 Token"
            else -> "本地最高仅 $percent%（第二名 $secondPercent%），需要模型辅助"
        }
    }
}
