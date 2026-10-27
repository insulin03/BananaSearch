package com.banana.search.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.banana.search.core.AppLog
import kotlin.math.abs

class RegionSelectOverlay(
    private val context: Context,
    private val initialFraction: FloatArray?,
    private val onDone: (Rect?) -> Unit
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var mask: MaskView? = null

    fun show() {
        if (mask != null) return
        val screen = currentScreen()
        val view = MaskView(context, initialFraction, screen)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or

                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or

                (if (com.banana.search.core.Settings(context).blockCapture)
                    WindowManager.LayoutParams.FLAG_SECURE else 0),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { wm.addView(view, lp) }
            .onFailure { AppLog.e(TAG, "添加选区遮罩失败", it) }
        mask = view
    }

    fun dismiss() {
        val v = mask ?: return
        runCatching { wm.removeView(v) }
        mask = null
    }

    @Suppress("DEPRECATION")
    private fun currentScreen(): Rect {
        val r = Rect()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            r.set(0, 0, b.width(), b.height())
        } else {
            val p = Point()
            wm.defaultDisplay.getRealSize(p)
            r.set(0, 0, p.x, p.y)
        }
        return r
    }

    private inner class MaskView(
        ctx: Context,
        private val initialFraction: FloatArray?,
        private val screen: Rect
    ) : View(ctx) {

        private val dim = Paint().apply { color = 0x8C000000.toInt() }
        private val border = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.5f)
            color = 0xFF1F6FEB.toInt()
        }
        private val cornerPaint = Paint().apply { color = 0xFF1F6FEB.toInt() }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(15f)
        }
        private val hintBg = Paint().apply { color = 0xCC000000.toInt() }
        private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
        private val cancelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2B2415.toInt()
            textSize = dp(15f)
            isFakeBoldText = true
        }

        private val cancelRect = RectF()

        private val selection = RectF()
        private var startX = 0f
        private var startY = 0f
        private var dragging = false
        private var anchorX = -1f
        private var anchorY = -1f
        private val touchSlop = dp(16f)
        private val minWidth = dp(50f)
        private val minHeight = dp(36f)

        init {

            initialFraction?.let { f ->
                selection.set(
                    f[0] * screen.width(), f[1] * screen.height(),
                    f[2] * screen.width(), f[3] * screen.height()
                )
            }
        }

        private fun originOnScreen(): IntArray {
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            return loc
        }

        override fun onDraw(canvas: Canvas) {
            val loc = originOnScreen()
            val ox = loc[0].toFloat()
            val oy = loc[1].toFloat()
            val w = width.toFloat()
            val h = height.toFloat()

            val l = selection.left - ox
            val t = selection.top - oy
            val r = selection.right - ox
            val b = selection.bottom - oy

            if (selection.isEmpty || selection.width() < 1f) {
                canvas.drawRect(0f, 0f, w, h, dim)
            } else {
                canvas.drawRect(0f, 0f, w, t, dim)
                canvas.drawRect(0f, b, w, h, dim)
                canvas.drawRect(0f, t, l, b, dim)
                canvas.drawRect(r, t, w, b, dim)
                canvas.drawRect(l, t, r, b, border)
                val s = dp(5f)
                canvas.drawRect(l - s, t - s, l + s, t + s, cornerPaint)
                canvas.drawRect(r - s, b - s, r + s, b + s, cornerPaint)
            }

            val tip = if (anchorX >= 0) "再点一下确定对角（点回原点取消）"
            else "拖动框选识别区域，或点两下选对角"
            drawHint(canvas, tip, h * 0.12f)

            val cw = dp(96f)
            val ch = dp(42f)
            val cx = w - cw - dp(16f)
            val cy = h - ch - dp(96f)
            cancelRect.set(cx, cy, cx + cw, cy + ch)
            canvas.drawRoundRect(cancelRect, ch / 2, ch / 2, cancelPaint)
            val ct = "取消"
            val ctw = cancelText.measureText(ct)
            canvas.drawText(ct, cx + (cw - ctw) / 2, cy + ch / 2 + dp(5f), cancelText)

            val tip2 = when {
                anchorX >= 0 -> "第一角 (${anchorX.toInt()}, ${anchorY.toInt()})"
                selection.isEmpty -> "当前未设置选区（识别整屏）"
                else -> "当前选区 ${selection.width().toInt()}×${selection.height().toInt()}"
            }
            drawHint(canvas, tip2, h * 0.12f + dp(32f))
        }

        private fun drawHint(canvas: Canvas, text: String, baseline: Float) {
            val w = width.toFloat()
            val tw = hintPaint.measureText(text)
            val pad = dp(12f)
            canvas.drawRoundRect(
                (w - tw) / 2 - pad, baseline - dp(24f),
                (w + tw) / 2 + pad, baseline + dp(10f),
                dp(8f), dp(8f), hintBg
            )
            canvas.drawText(text, (w - tw) / 2, baseline, hintPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {

            val px = event.rawX
            val py = event.rawY
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val loc = originOnScreen()
                    if (cancelRect.contains(px - loc[0], py - loc[1])) {
                        AppLog.i(TAG, "点了取消按钮，退出选区")
                        finish(null)
                        return true
                    }
                    startX = px
                    startY = py
                    dragging = false
                    if (anchorX < 0) selection.setEmpty()
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(px - startX)
                    val dy = abs(py - startY)
                    if (anchorX >= 0) {

                        dragging = true
                        selection.set(
                            minOf(anchorX, px), minOf(anchorY, py),
                            maxOf(anchorX, px), maxOf(anchorY, py)
                        )
                        invalidate()
                    } else if (dx > touchSlop || dy > touchSlop) {
                        dragging = true
                        selection.set(
                            minOf(startX, px), minOf(startY, py),
                            maxOf(startX, px), maxOf(startY, py)
                        )
                        invalidate()
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    AppLog.i(
                        TAG,
                        "UP/CANCEL action=${event.actionMasked} 屏幕=${px.toInt()},${py.toInt()} " +
                            "dragging=$dragging anchor=${anchorX.toInt()},${anchorY.toInt()} " +
                            "sel=${selection.width().toInt()}x${selection.height().toInt()}"
                    )
                    if (dragging && selection.width() > minWidth && selection.height() > minHeight) {
                        finish(
                            Rect(
                                selection.left.toInt(), selection.top.toInt(),
                                selection.right.toInt(), selection.bottom.toInt()
                            )
                        )
                        return true
                    }

                    performClick()
                    if (anchorX < 0) {
                        anchorX = px
                        anchorY = py
                        selection.set(anchorX, anchorY, anchorX, anchorY)
                        invalidate()
                        AppLog.i(TAG, "已放下第一个角 屏幕=${anchorX.toInt()},${anchorY.toInt()}")
                        return true
                    }
                    val rect = Rect(
                        minOf(anchorX, px).toInt(), minOf(anchorY, py).toInt(),
                        maxOf(anchorX, px).toInt(), maxOf(anchorY, py).toInt()
                    )
                    anchorX = -1f
                    anchorY = -1f
                    if (rect.width() > minWidth && rect.height() > minHeight) {
                        finish(rect)
                    } else {
                        AppLog.i(TAG, "两点太近，取消选区设置")
                        finish(null)
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun finish(rect: Rect?) {
            dismiss()
            if (rect != null) AppLog.i(TAG, "选区已设置: $rect") else AppLog.i(TAG, "选区未变更")
            onDone(rect)
        }

        private fun dp(v: Float): Float = v * resources.displayMetrics.density
    }

    private companion object {
        const val TAG = "RegionSelect"
    }
}
