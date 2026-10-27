package com.banana.search

import com.banana.search.core.TierPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TierPolicyTest {

    private val high = 0.85

    @Test
    fun 高于绝对阈值直接命中() {
        assertTrue(TierPolicy.isConfidentLocal(best = 0.92, second = 0.91, highThreshold = high))
    }

    @Test
    fun 正好等于绝对阈值也算命中() {

        assertTrue(TierPolicy.isConfidentLocal(best = high, second = high, highThreshold = high))
    }

    @Test
    fun 赢家明确时低于绝对阈值也命中() {

        assertTrue(TierPolicy.isConfidentLocal(best = 0.79, second = 0.17, highThreshold = high))
    }

    @Test
    fun 第二名超过一半就不猜() {
        assertFalse(TierPolicy.isConfidentLocal(best = 0.75, second = 0.72, highThreshold = high))
    }

    @Test
    fun 第二名正好等于一半算赢家明确() {

        assertTrue(TierPolicy.isConfidentLocal(best = 0.80, second = 0.40, highThreshold = high))
    }

    @Test
    fun 低于决断下限一律交给模型() {
        assertFalse(TierPolicy.isConfidentLocal(best = 0.69, second = 0.01, highThreshold = high))
    }

    @Test
    fun 零分不会命中() {
        assertFalse(TierPolicy.isConfidentLocal(best = 0.0, second = 0.0, highThreshold = high))
    }

    @Test
    fun 说明文案区分三种情形() {
        assertEquals(
            "命中题库（92%），本次未消耗 Token",
            TierPolicy.describe(best = 0.92, second = 0.10, highThreshold = high)
        )
        assertEquals(
            "命中题库（79%，第二名仅 17%，赢家明确），本次未消耗 Token",
            TierPolicy.describe(best = 0.79, second = 0.17, highThreshold = high)
        )
        assertEquals(
            "本地最高仅 75%（第二名 72%），需要模型辅助",
            TierPolicy.describe(best = 0.75, second = 0.72, highThreshold = high)
        )
    }
}
