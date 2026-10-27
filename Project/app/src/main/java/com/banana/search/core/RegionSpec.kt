package com.banana.search.core

import java.util.Locale

object RegionSpec {

    private const val DECIMALS = 4
    private val FORMAT = "%." + DECIMALS + "f"

    fun encode(left: Int, top: Int, right: Int, bottom: Int, screenW: Int, screenH: Int): String {
        if (screenW <= 0 || screenH <= 0) return ""
        if (right - left <= 0 || bottom - top <= 0) return ""
        val l = (left.toFloat() / screenW).coerceIn(0f, 1f)
        val t = (top.toFloat() / screenH).coerceIn(0f, 1f)
        val r = (right.toFloat() / screenW).coerceIn(0f, 1f)
        val b = (bottom.toFloat() / screenH).coerceIn(0f, 1f)
        if (r <= l || b <= t) return ""
        return listOf(l, t, r, b).joinToString(",") { String.format(Locale.US, FORMAT, it) }
    }

    fun decode(spec: String, screenW: Int, screenH: Int): IntArray? {
        if (spec.isBlank() || screenW <= 0 || screenH <= 0) return null
        val f = spec.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (f.size != 4) return null
        if (f.any { it.isNaN() || it < 0f || it > 1f }) return null

        if (f[2] <= f[0] || f[3] <= f[1]) return null
        val left = (f[0] * screenW).toInt().coerceIn(0, screenW - 1)
        val top = (f[1] * screenH).toInt().coerceIn(0, screenH - 1)
        val right = (f[2] * screenW).toInt().coerceIn(left + 1, screenW)
        val bottom = (f[3] * screenH).toInt().coerceIn(top + 1, screenH)
        if (right <= left || bottom <= top) return null
        return intArrayOf(left, top, right, bottom)
    }

    fun fractions(spec: String, screenW: Int, screenH: Int): FloatArray? {
        if (spec.isBlank() || screenW <= 0 || screenH <= 0) return null
        val f = spec.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (f.size != 4 || f.any { it.isNaN() || it < 0f || it > 1f }) return null
        if (f[2] <= f[0] || f[3] <= f[1]) return null
        return floatArrayOf(f[0], f[1], f[2], f[3])
    }

    fun describe(spec: String, screenW: Int, screenH: Int): String {
        val r = decode(spec, screenW, screenH) ?: return ""
        val w = r[2] - r[0]
        val h = r[3] - r[1]
        return "${r[0]},${r[1]} → ${r[2]},${r[3]}（${w}×${h}）"
    }
}
