package com.banana.search.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.banana.search.MainActivity
import com.banana.search.R
import com.banana.search.capture.ImagePreprocess
import com.banana.search.capture.ScreenCapture
import com.banana.search.core.ApiKeyStore
import com.banana.search.core.AppLog
import android.graphics.Bitmap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.banana.search.core.DeepSeekClient
import com.banana.search.core.MatchEngine
import com.banana.search.core.QuestionBank
import com.banana.search.core.RecognizeOutcome
import com.banana.search.core.RegionSpec
import com.banana.search.core.Settings
import com.banana.search.reader.ScreenTextReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

    internal fun OverlayService.addButtonBar() {
        val outer = this
        if (buttonBar != null) return

        val darkTheme = settings.darkTheme
        val pal = OverlayUi.palette(darkTheme)

        val bar = android.widget.LinearLayout(this).apply {
            setBackgroundColor(0)
            alpha = settings.barAlpha
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val select = OverlayUi.createActionItem(this, darkTheme, "选区", OverlayUi.Glyph.SELECT, pal.text)

        val full = OverlayUi.createActionItem(this, darkTheme, "全屏", OverlayUi.Glyph.FULL, pal.text)
        val recognize = OverlayUi.createActionItem(this, darkTheme, "识别", OverlayUi.Glyph.RECOGNIZE, pal.text)
        val show = OverlayUi.createActionItem(this, darkTheme, "显示", OverlayUi.Glyph.SHOW, pal.text)
        val manual = OverlayUi.createActionItem(this, darkTheme, "手动", OverlayUi.Glyph.MANUAL, pal.text)

        val stop = OverlayUi.createActionItem(this, darkTheme, "停止", OverlayUi.Glyph.STOP, pal.text)
        recognizeButton = recognize
        showButton = show

        val buttons = listOf(select, full, recognize, show, manual, stop)
        buttons.forEachIndexed { index, view ->

            bar.addView(
                view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        select.setOnClickListener(
            android.view.View.OnClickListener {
                if (SystemClock.uptimeMillis() >= barTouchableAt) startRegionSelect()
            }
        )
        select.setOnLongClickListener {
            clearRegion()
            toast("已清除选区，恢复整屏识别")
            true
        }
        full.setOnClickListener {
            if (currentRegion() == null) {
                toast("当前就是整屏识别，未设置选区")
            } else {
                clearRegion()
                toast("已恢复整屏识别")
            }
        }
        fun guard(action: () -> Unit): android.view.View.OnClickListener =
            android.view.View.OnClickListener {
                if (SystemClock.uptimeMillis() >= barTouchableAt) action()
            }
        recognize.setOnClickListener(guard { triggerRecognize() })
        show.setOnClickListener(guard { togglePanel() })
        manual.setOnClickListener(guard { showManualInput() })
        stop.setOnClickListener(guard { showStopConfirm() })

        val screen = currentScreen()
        val barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                secureFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START

            y = (screen.y * 0.87f).toInt()
            x = OverlayUi.dp(outer, 8f)
            alpha = 1f
        }

        buttons.forEach { btn ->
            attachGroupDrag(btn, bar, barParams) {
                val c = circleButton
                val cp = circleParams
                (if (c != null && cp != null) listOf(c to cp) else emptyList()) + panelLinks()
            }
        }

        buttonBar = bar
        this.barParams = barParams

        val circleSize = OverlayUi.dp(this, 44f)
        val circle = OverlayUi.createCircleButton(this, darkTheme)
        val circleLp = WindowManager.LayoutParams(
            circleSize,
            circleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                secureFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screen.x - circleSize - OverlayUi.dp(outer, 8f)
            y = (screen.y * 0.87f).toInt()
            alpha = settings.barAlpha
        }

        attachGroupDrag(
            circle, circle, circleLp,
            linkedWith = { listOf(bar to barParams) + panelLinks() },
            afterMove = {

                if (barVisible) {
                    val overlap = circleLp.x < barParams.x + bar.width &&
                        circleLp.x + circle.width > barParams.x
                    if (overlap) {
                        val screen = currentScreen()
                        circleLp.x = (barParams.x + bar.width + OverlayUi.dp(this, 6f))
                            .coerceAtMost((screen.x - circle.width).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(circle, circleLp) }
                    }
                }
            }
        )
        circle.setOnClickListener { toggleBar() }
        runCatching { wm.addView(circle, circleLp) }
            .onFailure { AppLog.e(OverlayService.TAG, "添加圆形按钮失败", it) }
        circleButton = circle
        circleParams = circleLp
        AppLog.i(OverlayService.TAG, "悬浮控件已就绪（方块按钮默认收起）")
    }

    internal fun OverlayService.centerBar(bar: View, params: WindowManager.LayoutParams) {
        val outer = this
        val w = bar.width
        val h = bar.height
        if (!locateBar(params, w, h)) return
        if (w > 0) barWidth = w
        if (h > 0) barHeight = h
        runCatching { wm.updateViewLayout(bar, params) }
    }

    internal fun OverlayService.locateBar(params: WindowManager.LayoutParams, w: Int, h: Int): Boolean {
        if (w <= 0) return false
        val screen = currentScreen()
        val circle = circleButton
        val cp = circleParams
        if (circle != null && cp != null && circle.width > 0) {

            val gap = OverlayUi.dp(this, 6f)
            params.x = (cp.x - w - gap).coerceAtLeast(OverlayUi.dp(this, 4f))
            params.y = cp.y + (circle.height - h) / 2
            if (h > 0 && params.y + h > screen.y) params.y = (screen.y - h).coerceAtLeast(0)
            return true
        }
        params.x = ((screen.x - w) / 2).coerceAtLeast(OverlayUi.dp(this, 4f))

        if (h > 0 && params.y + h > screen.y) params.y = (screen.y - h).coerceAtLeast(0)
        return true
    }

    internal fun OverlayService.rebuildOverlay() {
        val outer = this
        if (buttonBar == null && circleButton == null) return
        val wantBar = barVisible
        val keep = panel?.let { lastOutcome }

        val keepBarX = barParams?.x
        val keepBarY = barParams?.y
        val keepCircleX = circleParams?.x
        val keepCircleY = circleParams?.y
        barVisible = false
        runCatching { buttonBar?.let { wm.removeView(it) } }
        runCatching { circleButton?.let { wm.removeView(it) } }
        if (panel != null) hidePanel()
        buttonBar = null
        barParams = null
        circleButton = null
        circleParams = null
        showButton = null
        recognizeButton = null

        addButtonBar()

        if (keepBarX != null && keepBarY != null) {
            barParams?.let { it.x = keepBarX; it.y = keepBarY }
        }
        if (keepCircleX != null && keepCircleY != null) {
            val c = circleButton
            val cp = circleParams
            if (c != null && cp != null) {
                cp.x = keepCircleX
                cp.y = keepCircleY
                runCatching { wm.updateViewLayout(c, cp) }
            }
        }

        if (wantBar) {
            val b = buttonBar
            val bp = barParams
            if (b != null && bp != null) {
                runCatching { wm.addView(b, bp) }.onSuccess { barVisible = true }
                b.post { centerBar(b, bp) }
            }
        }
        if (panelWanted && keep != null) renderOutcome(keep) else updateShowButton()
        AppLog.i(OverlayService.TAG, "悬浮层已按主题重建")
    }

    internal fun OverlayService.positionPanelAboveButtons(p: View, onPositioned: (() -> Unit)? = null) {
        val params = panelParams ?: return
        val screen = currentScreen()
        val bar = buttonBar
        val barP = barParams
        val anchorY = barP?.y ?: circleParams?.y ?: (screen.y * 0.87f).toInt()
        val anchorX = if (barVisible) (barP?.x ?: circleParams?.x ?: 0) else (circleParams?.x ?: 0)
        val anchorW = if (barVisible) (bar?.width ?: 0) else (circleButton?.width ?: 0)

        p.post {
            val w = p.width
            val h = p.height
            if (w <= 0 || h <= 0) {
                onPositioned?.invoke()
                return@post
            }
            val gap = OverlayUi.dp(this, 8f)

            params.x = if (anchorW > 0) {
                (anchorX + (anchorW - w) / 2).coerceIn(0, (screen.x - w).coerceAtLeast(0))
            } else {
                anchorX.coerceIn(0, (screen.x - w).coerceAtLeast(0))
            }

            val above = anchorY - h - gap
            params.y = if (above >= 0) {
                above
            } else {
                (anchorY + (if (barVisible) (bar?.height ?: 0) else (circleButton?.height ?: 0)) + gap)
                    .coerceIn(0, (screen.y - h).coerceAtLeast(0))
            }
            runCatching { wm.updateViewLayout(p, params) }
            onPositioned?.invoke()
        }
    }

    internal fun OverlayService.attachPanelDrag(p: View) {
        val outer = this
        val handle = p.findViewWithTag<View>(OverlayUi.TAG_DRAG_HANDLE) ?: return
        val params = panelParams ?: return
        attachGroupDrag(handle, p, params) {
            val b = buttonBar
            val bp = barParams
            val c = circleButton
            val cp = circleParams
            (if (b != null && bp != null) listOf(b to bp) else emptyList()) +
                (if (c != null && cp != null) listOf(c to cp) else emptyList())
        }
    }

internal fun fadeOutThenRemove(
    view: View,
    duration: Long = 80,
    translateY: Float? = null,
    remove: () -> Unit
) {
    view.animate().cancel()
    val anim = view.animate().alpha(0f)
    if (translateY != null) anim.translationY(translateY)
    anim.setDuration(duration).withEndAction { view.post(remove) }.start()
}
