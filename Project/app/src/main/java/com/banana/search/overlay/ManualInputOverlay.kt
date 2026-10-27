package com.banana.search.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.banana.search.core.AppLog

class ManualInputOverlay(
    private val context: Context,

    private val anchor: android.graphics.Point? = null,
    private val onSubmit: (String) -> Unit,
    private val onClosed: () -> Unit = {}
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null

    fun rootView(): View? = root

    fun show() {
        if (root != null) return

        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val pal = OverlayUi.palette(com.banana.search.core.Settings(context).darkTheme)

        val edit = EditText(context).apply {
            hint = "输入或粘贴题干"
            setTextColor(pal.text)
            setHintTextColor(pal.sub)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = OverlayUi.roundedBox(
                fill = pal.soft,
                radiusPx = dp(4.5f).toFloat(),
                strokeColor = pal.stroke,
                strokePx = dp(1f)
            )
        }

        val search = TextView(context).apply {
            text = "搜索"
            setTextColor(pal.accentInk)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
            background = OverlayUi.roundedBox(pal.accent, dp(12f).toFloat())
        }

        val cancel = TextView(context).apply {
            text = "取消"
            setTextColor(pal.sub)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(cancel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(search, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        val title = TextView(context).apply {
            text = "手动搜索"
            setTextColor(pal.orange)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2f), 0, dp(2f))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = OverlayUi.roundedBox(
                fill = pal.glass,
                radiusPx = dp(10f).toFloat(),
                strokeColor = pal.stroke,
                strokePx = dp(1f)
            )
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            addView(title)
            addView(edit, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) })
            addView(buttonRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) })
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        val width = minOf(screenWidth - dp(32f), dp(236f))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or

            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or

            (if (com.banana.search.core.Settings(context).blockCapture)
                WindowManager.LayoutParams.FLAG_SECURE else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START

            val a = anchor
            if (a != null) {
                x = a.x.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
                y = (a.y - dp(72f)).coerceAtLeast(0)
            } else {
                x = (screenWidth - width) / 2
                y = screenHeight / 3
            }

            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE

            alpha = 0f
        }

        @SuppressLint("ClickableViewAccessibility")
        title.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0

            override fun onTouch(v: View, e: android.view.MotionEvent): Boolean {
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startX = params.x; startY = params.y
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.x = (startX + (e.rawX - downX)).toInt()
                            .coerceIn(0, (screenWidth - width).coerceAtLeast(0))
                        params.y = (startY + (e.rawY - downY)).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(container, params) }
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {

                        if (kotlin.math.abs(e.rawX - downX) < 8f && kotlin.math.abs(e.rawY - downY) < 8f) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        fun close() {
            val v = root ?: return
            root = null
            fadeOutThenRemove(v, 110) {
                runCatching { wm.removeView(v) }
                onClosed()
            }
        }

        search.setOnClickListener {
            val text = edit.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                edit.error = "请先输入题干"
                return@setOnClickListener
            }
            close()
            AppLog.i(TAG, "手动输入提交，长度=${text.length}")
            onSubmit(text)
        }
        cancel.setOnClickListener { close() }

        runCatching { wm.addView(container, params) }
            .onFailure {
                AppLog.e(TAG, "添加手动输入框失败", it)
                return
            }
        root = container

        container.post {
            if (root == null) return@post
            val a = anchor
            if (a != null) {
                val h = container.height
                if (h > 0) {
                    params.y = (a.y - h - (8f * context.resources.displayMetrics.density).toInt())
                        .coerceAtLeast(0)
                }
            }
            params.alpha = 1f
            runCatching { wm.updateViewLayout(container, params) }

            container.translationY = 8f * context.resources.displayMetrics.density
            container.alpha = 0f
            container.animate().alpha(1f).translationY(0f).setDuration(90).start()
        }

        edit.requestFocus()
        edit.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun dismiss() {
        val v = root ?: return
        root = null
        fadeOutThenRemove(v, 80) {
            runCatching { wm.removeView(v) }
            onClosed()
        }
    }

    val isShowing: Boolean get() = root != null

    private companion object {
        const val TAG = "ManualInput"
    }
}
