package com.banana.search

import com.banana.search.core.RegionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RegionSpecTest {

    private val screens = listOf(
        1080 to 2400,
        1080 to 2340,
        720 to 1600,
        1440 to 3120,
        1000 to 1000
    )

    @Test
    fun `编码后再解码能还原到 1 像素以内`() {
        for ((w, h) in screens) {
            val rects = listOf(
                intArrayOf(0, 0, w, h),
                intArrayOf(0, 100, w, h / 2),

                intArrayOf(37, 211, w - 37, h - 211),
                intArrayOf(w / 2, h / 2, w - 1, h - 1)
            )
            for (r in rects) {
                val spec = RegionSpec.encode(r[0], r[1], r[2], r[3], w, h)
                assertTrue("编码不应为空: ${r.toList()} @ ${w}x$h", spec.isNotEmpty())
                val back = RegionSpec.decode(spec, w, h)
                requireNotNull(back) { "解码失败: $spec" }
                for (i in 0..3) {
                    assertTrue(
                        "第 $i 个坐标偏差过大: ${r[i]} vs ${back[i]}（$spec @ ${w}x$h）",
                        abs(r[i] - back[i]) <= 1
                    )
                }
            }
        }
    }

    @Test
    fun `未设置时解码返回 null`() {
        assertNull(RegionSpec.decode("", 1080, 2400))
        assertNull(RegionSpec.decode("   ", 1080, 2400))
    }

    @Test
    fun `格式不对时解码返回 null`() {
        assertNull(RegionSpec.decode("0.1,0.2,0.3", 1080, 2400))
        assertNull(RegionSpec.decode("a,b,c,d", 1080, 2400))
        assertNull(RegionSpec.decode("0.1,0.2,0.3,0.4,0.5", 1080, 2400))

        assertNull(RegionSpec.decode("0.1,0.2,1.3,0.9", 1080, 2400))

        assertNull(RegionSpec.decode("0.5,0.5,0.2,0.2", 1080, 2400))
    }

    @Test
    fun `退化的矩形编码成空串`() {
        assertEquals("", RegionSpec.encode(100, 100, 100, 300, 1080, 2400))
        assertEquals("", RegionSpec.encode(100, 100, 300, 100, 1080, 2400))
        assertEquals("", RegionSpec.encode(300, 100, 100, 300, 1080, 2400))

        assertEquals("", RegionSpec.encode(0, 0, 100, 100, 0, 2400))
    }

    @Test
    fun `比例数组可以直接给遮罩画出已有选区`() {
        val spec = RegionSpec.encode(100, 200, 900, 1000, 1000, 2000)
        val f = RegionSpec.fractions(spec, 1000, 2000)
        requireNotNull(f)
        assertEquals(0.1f, f[0], 0.001f)
        assertEquals(0.1f, f[1], 0.001f)
        assertEquals(0.9f, f[2], 0.001f)
        assertEquals(0.5f, f[3], 0.001f)
    }

    @Test
    fun `说明文字带上像素尺寸`() {
        val spec = RegionSpec.encode(10, 20, 410, 220, 1000, 2000)
        val text = RegionSpec.describe(spec, 1000, 2000)
        assertTrue(text, text.contains("400×200"))
        assertEquals("", RegionSpec.describe("", 1000, 2000))
    }

    @Test
    fun `比例串跨屏幕尺寸保持相对位置`() {
        val spec = RegionSpec.encode(108, 240, 972, 1200, 1080, 2400)

        val r = RegionSpec.decode(spec, 720, 1600)
        requireNotNull(r)
        assertTrue(abs(r[0] - 72) <= 1)
        assertTrue(abs(r[1] - 160) <= 1)
        assertTrue(abs(r[2] - 648) <= 1)
        assertTrue(abs(r[3] - 800) <= 1)
    }
}
