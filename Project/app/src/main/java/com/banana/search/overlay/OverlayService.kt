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

class OverlayService : Service() {

    internal lateinit var wm: WindowManager
    internal lateinit var settings: Settings
    internal lateinit var client: DeepSeekClient
    internal lateinit var engine: MatchEngine

    internal var capture: ScreenCapture? = null
    internal var textMode = true

    internal var buttonBar: LinearLayout? = null
    internal var barParams: WindowManager.LayoutParams? = null
    internal var recognizeButton: View? = null
    internal var showButton: View? = null

    internal var circleButton: View? = null
    internal var circleParams: WindowManager.LayoutParams? = null
    internal var barVisible = false

    internal var barWidth = 0
    internal var barHeight = 0

    internal var panel: View? = null

    internal var panelContent: LinearLayout? = null
    internal var panelParams: WindowManager.LayoutParams? = null

    internal var panelWanted = false

    internal var regionOverlay: RegionSelectOverlay? = null
    internal var manualOverlay: ManualInputOverlay? = null

    internal var wakeLock: PowerManager.WakeLock? = null

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val busy = AtomicBoolean(false)

    @Volatile
    internal var lastOutcome: RecognizeOutcome? = null

    private var running = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settings = Settings(this)
        val store = ApiKeyStore(this)
        client = DeepSeekClient { store.load() }
        engine = MatchEngine(this, settings, client)
        AppLog.i(TAG, "OverlayService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppLog.i(TAG, "收到停止指令")
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                applyBarAlpha()
                applySecureFlags()

                if (intent.getBooleanExtra(EXTRA_REBUILD, false)) rebuildOverlay()
                return START_STICKY
            }
            ACTION_CAPTURE_HIDE -> {
                hideOverlaysForCapture()
                return START_STICKY
            }
            ACTION_CAPTURE_SHOW -> {
                restoreOverlaysAfterCapture()
                return START_STICKY
            }
            ACTION_START -> {
                if (running) {
                    AppLog.i(TAG, "服务已在运行，忽略重复启动")
                    return START_STICKY
                }
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA) as? Intent
                }
                textMode = !(resultCode != Int.MIN_VALUE && data != null)
                if (textMode) startTextMode() else startScreenMode(resultCode, data!!)
                return START_STICKY
            }
            null -> {
                AppLog.w(TAG, "服务被系统重启，需要用户重新点击「启动」")
                goForeground(textModeType = true)
                notifyUser("服务已被系统重启，请回到应用重新点击「启动」")
                return START_STICKY
            }
        }
        return START_STICKY
    }

    internal fun goForeground(textModeType: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        val openApp = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_capture)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()

        val type = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 0
            textModeType -> if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        ServiceCompat.startForeground(this, NOTIFY_ID, notification, type)
        acquireWakeLock()
    }

    internal fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "qsearch:service").apply {
                setReferenceCounted(false)
            }
        }
        runCatching { wakeLock?.acquire(30 * 60 * 1000L) }
    }

    internal fun notifyUser(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_capture)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(NOTIFY_ID + 1, n) }
    }

    internal var toastView: View? = null

    internal fun toast(text: String) {
        val screen = currentScreen()
        toastView?.let { runCatching { wm.removeView(it) } }
        toastView = null
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(0xFFF5F1E4.toInt())
            textSize = 12.5f
            maxLines = 2
            gravity = Gravity.CENTER
            setPadding(OverlayUi.dp(this@OverlayService, 14f), OverlayUi.dp(this@OverlayService, 9f),
                OverlayUi.dp(this@OverlayService, 14f), OverlayUi.dp(this@OverlayService, 9f))
            background = GradientDrawable().apply {
                cornerRadius = OverlayUi.dp(this@OverlayService, 15f).toFloat()
                setColor(0xF2201B10.toInt())
                setStroke(OverlayUi.dp(this@OverlayService, 1f), 0x55FFD35C)
            }
            alpha = 0f
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                secureFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (screen.y * 0.74f).toInt()
        }
        if (runCatching { wm.addView(tv, lp) }.isFailure) {

            AppLog.w(TAG, "提示条挂载失败：$text")
            return
        }
        toastView = tv
        tv.post {
            tv.animate().alpha(1f).setDuration(110).withEndAction {
                tv.postDelayed({
                    tv.animate().alpha(0f).setDuration(160).withEndAction {
                        runCatching { wm.removeView(tv) }
                        if (toastView === tv) toastView = null
                    }.start()
                }, 1100)
            }.start()
        }
    }

    internal fun startTextMode() {
        if (!AndroidSettings.canDrawOverlays(this)) {
            AppLog.e(TAG, "缺少悬浮窗权限")
            notifyUser("缺少「显示在其他应用上层」权限，请回到应用授权后重试")
            return
        }
        goForeground(textModeType = true)
        running = true
        setRunning(true)
        addButtonBar()
        AppLog.i(TAG, "已进入无障碍读文字模式（无投屏会话）")
    }

    internal fun startScreenMode(resultCode: Int, data: Intent) {
        if (!AndroidSettings.canDrawOverlays(this)) {
            AppLog.e(TAG, "缺少悬浮窗权限")
            notifyUser("缺少「显示在其他应用上层」权限，请回到应用授权后重试")
            return
        }
        goForeground(textModeType = false)
        val cap = ScreenCapture(this, resultCode, data)
        cap.onStopped = {
            AppLog.w(TAG, "投屏会话结束")
            scope.launch { notifyUser("投屏会话已结束（可能被系统回收），请回到应用重新点击「启动」") }
        }
        if (!cap.start()) {
            notifyUser("无法建立投屏会话，请回到应用重新点击「启动」并允许屏幕投送")
            cap.release()
            return
        }
        capture = cap
        running = true
        setRunning(true)
        addButtonBar()
    }

    internal var barTouchableAt = 0L

    internal val captureHiddenAlpha = HashMap<View, Float>()

    internal fun toggleBar() {
        val bar = buttonBar ?: return
        val params = barParams ?: return
        if (barVisible) {
            barVisible = false
            val hadPanel = panel != null
            if (hadPanel) hidePanel()

            barTouchableAt = SystemClock.uptimeMillis() + 220
            bar.animate().cancel()
            bar.animate().alpha(0f).setDuration(80).withEndAction {

                if (!barVisible) {

                    bar.post {
                        if (!barVisible) runCatching { wm.removeView(bar) }
                    }

                }
            }.start()

            AppLog.i(
                TAG,
                "五个方块按钮已收起" + if (hadPanel) "，答案小窗一并隐藏" +
                    if (panelWanted) "（展开时恢复）" else "" else ""
            )
        } else {

            buttonBar?.let { _ ->
                showButton?.let { item ->
                    OverlayUi.setItemLabel(item, "显示")
                    OverlayUi.setItemActive(this, item, false, settings.darkTheme, animate = false)
                }
            }

            bar.animate().cancel()
            params.flags = params.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            bar.alpha = 0f

            if (barWidth > 0) locateBar(params, barWidth, barHeight)
            barVisible = true
            val attached = runCatching { wm.addView(bar, params) }.isSuccess
            if (!attached) {

                runCatching { wm.updateViewLayout(bar, params) }
                    .onFailure { AppLog.e(TAG, "展开方块按钮失败", it) }
            }
            bar.post {
                centerBar(bar, params)
                barTouchableAt = SystemClock.uptimeMillis() + 200
                bar.animate().alpha(settings.barAlpha).setDuration(90).start()
            }

            val restored = panelWanted && lastOutcome != null
            updateShowButton()
            if (restored) {
                bar.postDelayed({ if (barVisible) lastOutcome?.let { renderOutcome(it) } }, 170)
            }
            AppLog.i(TAG, "五个方块按钮已展开" + if (restored) "，答案小窗已恢复显示" else "")
        }
    }

    internal fun applyBarAlpha() {        val a = settings.barAlpha
        runCatching {
            buttonBar?.let {
                it.alpha = a
                if (barVisible) barParams?.let { p -> wm.updateViewLayout(it, p) }
            }
            circleButton?.let {
                it.alpha = a
                circleParams?.let { p -> wm.updateViewLayout(it, p) }
            }
        }
    }

    internal fun updateShowButton() {
        val item = showButton ?: return

        OverlayUi.setItemLabel(item, if (panel != null) "隐藏" else "显示")
        OverlayUi.setItemActive(this, item, panel != null, settings.darkTheme)
    }

    internal fun currentRegion(): Rect? {
        val s = currentScreen()
        val r = RegionSpec.decode(settings.regionSpec, s.x, s.y) ?: return null
        return Rect(r[0], r[1], r[2], r[3])
    }

    internal fun saveRegion(rect: Rect) {
        val s = currentScreen()

        settings.regionSpec = RegionSpec.encode(rect.left, rect.top, rect.right, rect.bottom, s.x, s.y)
    }

    internal fun clearRegion() {
        val had = settings.regionSpec.isNotBlank()
        settings.regionSpec = ""
        if (had) AppLog.i(TAG, "已清除选区，恢复整屏识别")
    }

    internal fun startRegionSelect() {
        if (regionOverlay != null) return
        val existing = currentRegion()?.let { r ->
            val s = currentScreen()
            floatArrayOf(
                r.left.toFloat() / s.x, r.top.toFloat() / s.y,
                r.right.toFloat() / s.x, r.bottom.toFloat() / s.y
            )
        }

        if (barVisible) {
            runCatching { wm.removeView(buttonBar) }
            barVisible = false
        }
        val overlay = RegionSelectOverlay(this, existing) { rect ->
            regionOverlay = null
            if (rect != null) {
                saveRegion(rect)
                toast("选区已设置（${rect.width()}×${rect.height()}），识别将只在这个范围内进行")
            } else {
                AppLog.i(TAG, "选区已取消，恢复工具栏")
            }

            if (!barVisible) toggleBar()
        }
        regionOverlay = overlay
        overlay.show()
    }

    internal fun showManualInput() {

        manualOverlay?.let { if (it.isShowing) { it.dismiss(); return } }
        val overlay = ManualInputOverlay(
            this,
            anchor = android.graphics.Point(
                barParams?.x ?: OverlayUi.dp(this, 8f),
                barParams?.y ?: (currentScreen().y * 0.87f).toInt()
            ),
            onSubmit = { text ->
                manualOverlay = null
                setManualActive(false)
                runManualSearch(text)
            },
            onClosed = {
                manualOverlay = null
                setManualActive(false)
            }
        )
        manualOverlay = overlay
        overlay.show()
        setManualActive(true)
    }

    internal fun setManualActive(active: Boolean) {
        val item = (buttonBar as? LinearLayout)?.let { bar ->
            (0 until bar.childCount).map { bar.getChildAt(it) }
                .filterIsInstance<LinearLayout>()
                .firstOrNull { ll -> (ll.getChildAt(1) as? TextView)?.text?.toString() == "手动" }
        } ?: return
        OverlayUi.setItemActive(this, item, active, settings.darkTheme)
    }

    internal fun runManualSearch(text: String) {
        if (!busy.compareAndSet(false, true)) {
            toast("上一次识别还没结束")
            return
        }
        scope.launch {
            try {
                if (!QuestionBank.isLoaded) {
                    withContext(Dispatchers.IO) { runCatching { QuestionBank.load(this@OverlayService) } }
                }
                acquireWakeLock()
                AppLog.i(TAG, "手动搜索，输入长度=${text.length}")
                val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                val outcome = engine.recognizeFromText(text, lines)
                lastOutcome = outcome
                settings.lastSummary = "手动搜索：" + outcome.message
                AppLog.i(TAG, "手动搜索结束：${outcome.tier.label} ${outcome.elapsedMs}ms ${outcome.message}")
                toast("搜索完成（${outcome.tier.label}）")

                renderOutcome(outcome)
            } catch (t: Throwable) {
                AppLog.e(TAG, "手动搜索异常", t)
                toast("搜索异常，详情见应用日志")
            } finally {
                busy.set(false)
            }
        }
    }

    internal suspend fun captureQuestionImage(region: Rect?): Bitmap? =
        suspendCancellableCoroutine { cont ->
            hideOverlaysForCapture()
            ScreenTextReader.takeScreenshot { shot, _ ->
                restoreOverlaysAfterCapture()
                val cropped = if (shot == null) null else runCatching {
                    ImagePreprocess.cropQuestion(
                        shot, settings.cropTop, settings.cropBottom, region
                    )
                }.getOrNull()
                if (shot != null && cropped !== shot) runCatching { shot.recycle() }
                if (cont.isActive) cont.resume(cropped)
            }
        }

    internal fun triggerRecognize() {
        if (!busy.compareAndSet(false, true)) {
            toast("上一次识别还没结束")
            return
        }
        scope.launch {
            val btn = recognizeButton
            try {
                if (!QuestionBank.isLoaded) {
                    withContext(Dispatchers.IO) { runCatching { QuestionBank.load(this@OverlayService) } }
                }
                acquireWakeLock()
                val region = currentRegion()

                val outcome = if (textMode) {
                    AppLog.i(TAG, "开始识别（无障碍读文字）" + if (region != null) "，选区=$region" else "")
                    val read = ScreenTextReader.readScreenLines(region, overlayRectsInScreen())

                    val shot = if (settings.searchMode == Settings.MODE_DEEPSEEK &&
                        settings.sendImageToModel
                    ) captureQuestionImage(region) else null
                    try {
                        engine.recognizeFromText(read.fullText, read.lines, shot)
                    } finally {
                        if (shot != null) runCatching { shot.recycle() }
                    }
                } else {
                    val cap = capture
                    if (cap == null || !cap.isRunning) {
                        notifyUser("投屏会话不可用，请回到应用重新点击「启动」")
                        return@launch
                    }
                    AppLog.i(TAG, "开始识别（截屏 OCR）")

                    val frame = cap.captureFull(
                        prepare = {
                            setPanelVisible(false)
                            hideOverlaysForCapture()
                        },
                        restore = {
                            restoreOverlaysAfterCapture()
                            setPanelVisible(true)
                        }
                    )
                    if (frame == null) {
                        RecognizeOutcome(
                            tier = RecognizeOutcome.Tier.FAILED,
                            ok = false,
                            message = "没能抓到画面：屏幕没有产生新的帧。请点亮屏幕后再点一次「识别」。"
                        )
                    } else {
                        engine.recognize(frame, region)
                    }
                }

                lastOutcome = outcome
                settings.lastSummary = outcome.message
                AppLog.i(TAG, "识别结束：${outcome.tier.label} ${outcome.elapsedMs}ms ${outcome.message}")
                if (panel != null) {

                    renderOutcome(outcome)
                    toast("识别完成（${outcome.tier.label}），小窗已刷新")
                } else {
                    toast("识别完成（${outcome.tier.label}）：展开按钮后点「显示」查看")
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "识别流程崩溃", t)
                val failed = RecognizeOutcome(
                    tier = RecognizeOutcome.Tier.FAILED,
                    ok = false,
                    message = "识别异常：${t.javaClass.simpleName} ${t.message.orEmpty()}"
                )
                lastOutcome = failed
                settings.lastSummary = failed.message
                if (panel != null) renderOutcome(failed)
                toast("识别异常，详情见应用日志")
            } finally {
                btn?.let { OverlayUi.setItemLabel(it, "识别") }
                busy.set(false)
            }
        }
    }

    internal fun togglePanel() {
        if (panel != null) {

            panelWanted = false
            hidePanel()
            return
        }
        val outcome = lastOutcome
        if (outcome == null) {
            toast("还没有识别结果，请先点「识别」")
            return
        }
        panelWanted = true
        renderOutcome(outcome)
    }

    internal fun panelLinks(): List<Pair<View, WindowManager.LayoutParams>> {
        val p = panel
        val pp = panelParams
        return if (p != null && pp != null) listOf(p to pp) else emptyList()
    }

    internal fun overlayRectsInScreen(): List<Rect> {
        val out = ArrayList<Rect>(3)
        val anchor = circleButton ?: buttonBar ?: return out
        val loc = IntArray(2)
        runCatching { anchor.getLocationOnScreen(loc) }
        val anchorParams = if (anchor === circleButton) circleParams else barParams
        val offX = if (anchorParams != null) loc[0] - anchorParams.x else 0
        val offY = if (anchorParams != null) loc[1] - anchorParams.y else 0

        fun add(v: View?, p: WindowManager.LayoutParams?) {
            if (v == null || p == null || v.parent == null) return
            if (v.width <= 0 || v.height <= 0) return
            out.add(
                Rect(
                    p.x + offX, p.y + offY,
                    p.x + offX + v.width, p.y + offY + v.height
                )
            )
        }
        add(buttonBar, barParams)
        add(panel, panelParams)
        add(circleButton, circleParams)
        return out
    }

    internal var panelFadePending = false

    internal fun renderOutcome(outcome: RecognizeOutcome) {

        panelWanted = true
        showPanel()
        val p = panel ?: return
        val c = panelContent ?: return
        OverlayUi.renderPanel(this, c, outcome, settings.darkTheme) { dismissPanel() }
        recordHistory(outcome)
        attachPanelDrag(p)
        positionPanelAboveButtons(p) {

            if (panelFadePending) {
                panelFadePending = false
                p.animate().alpha(1f).translationY(0f).setDuration(100).start()
            }
        }
        updateShowButton()
    }

    internal fun recordHistory(outcome: RecognizeOutcome) {
        val question = outcome.matchedQuery.ifBlank { outcome.ocrText }
        val answer = outcome.cloudAnswer.ifBlank {
            outcome.top.firstOrNull()?.item?.let { item ->
                com.banana.search.core.QuestionTypes.answerText(
                    com.banana.search.core.QuestionTypes.infer(item.question, item.type, item.answers),
                    item.answers
                )
            }.orEmpty()
        }
        val usage = outcome.usage
        val tokens = usage?.let { it.promptTokens + it.completionTokens } ?: 0
        val yuan = usage?.let {
            com.banana.search.core.UsageFormat.rmb(it, settings.priceInputPerM, settings.priceOutputPerM)
        } ?: 0.0
        val entry = com.banana.search.core.RecognizeHistory.Entry(
            at = System.currentTimeMillis(),
            tier = outcome.tier.label,
            question = question.take(500),
            answer = answer.take(1000),
            tokens = tokens,
            yuan = yuan
        )
        Thread {
            runCatching { com.banana.search.core.RecognizeHistory.add(this, entry) }
        }.start()
    }

    internal fun hidePanel() {
        val p = panel ?: return
        panel = null
        panelContent = null
        panelParams = null
        updateShowButton()

        fadeOutThenRemove(p, 80, OverlayUi.dp(this, 6f).toFloat()) {
            runCatching { wm.removeView(p) }
        }
        AppLog.i(TAG, "答案小窗已隐藏")
    }

    internal fun dismissPanel() {
        panelWanted = false
        hidePanel()
    }

    internal fun setPanelVisible(visible: Boolean) {
        val p = panel ?: return
        runCatching {
            p.alpha = if (visible) 1f else 0f
            panelParams?.let { wm.updateViewLayout(p, it) }
        }
    }

    internal fun hideOverlaysForCapture() {
        captureHiddenAlpha.clear()
        listOfNotNull(buttonBar, circleButton, panel, manualOverlay?.rootView(), toastView).forEach { v ->
            captureHiddenAlpha[v] = v.alpha
            v.alpha = 0f
        }
    }

    internal fun restoreOverlaysAfterCapture() {
        captureHiddenAlpha.forEach { (v, a) -> runCatching { v.alpha = a } }
        captureHiddenAlpha.clear()
    }

    private var stopConfirm: View? = null
    private var stopConfirmParams: WindowManager.LayoutParams? = null

    internal fun showStopConfirm() {
        if (stopConfirm != null) return
        val screen = currentScreen()
        val card = OverlayUi.createConfirmCard(
            this,
            dark = settings.darkTheme,
            title = "停止识别服务？",
            message = "停止后圆形按钮和工具栏都会消失，识别记录保留；之后可以在应用里重新启动。",
            confirmText = "停止服务",
            onCancel = { hideStopConfirm() },
            onConfirm = {
                hideStopConfirm()
                stopEverything()
                stopSelf()
            }
        )
        val params = WindowManager.LayoutParams(
            OverlayUi.dp(this, 280f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                secureFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            y = -(screen.y * 0.06f).toInt()
        }
        runCatching { wm.addView(card, params) }.onFailure {
            AppLog.e(TAG, "停止确认卡挂载失败", it)
            return
        }
        stopConfirm = card
        stopConfirmParams = params

        card.alpha = 0f
        card.translationY = OverlayUi.dp(this, 10f).toFloat()
        card.post {
            card.animate().alpha(1f).translationY(0f).setDuration(140).start()
        }
    }

    internal fun hideStopConfirm() {
        val v = stopConfirm ?: return
        stopConfirm = null
        stopConfirmParams = null
        fadeOutThenRemove(v, 100) { runCatching { wm.removeView(v) } }
    }

    internal fun showPanel() {
        if (panel != null) return
        val content = OverlayUi.createPanelRoot(this, settings.darkTheme)
        val screen = currentScreen()

        val width = minOf(
            screen.x - OverlayUi.dp(this, 24f),
            OverlayUi.dp(this, settings.panelWidthDp.toFloat())
        )
        val maxHeight = minOf(
            (screen.y * 0.8f).toInt(),
            OverlayUi.dp(this, settings.panelHeightDp.toFloat())
        )
        val root = OverlayUi.MaxHeightScrollView(this, maxHeight).apply {
            isFillViewport = false
            addView(
                content,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                secureFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screen.x - width) / 2

            val anchorY = barParams?.y ?: circleParams?.y ?: (screen.y * 0.87f).toInt()
            y = (anchorY - OverlayUi.dp(this@OverlayService, 260f)).coerceAtLeast(0)
        }
        OverlayUi.renderPanel(this, content, null, settings.darkTheme) { dismissPanel() }
        runCatching { wm.addView(root, params) }
            .onFailure { AppLog.e(TAG, "添加结果面板失败", it) }
        panel = root
        panelContent = content
        panelParams = params
        attachPanelDrag(root)

        root.alpha = 0f
        root.translationY = OverlayUi.dp(this, 6f).toFloat()
        panelFadePending = true
    }

    internal fun attachGroupDrag(
        view: View,
        container: View,
        params: WindowManager.LayoutParams,
        linkedWith: (() -> List<Pair<View, WindowManager.LayoutParams>>)? = null,
        afterMove: (() -> Unit)? = null
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var linked: List<Triple<View, WindowManager.LayoutParams, IntArray>> = emptyList()

        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    linked = linkedWith?.invoke().orEmpty().map { (lv, lp) ->
                        Triple(lv, lp, intArrayOf(lp.x, lp.y))
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 14 || abs(dy) > 14) moved = true
                    if (moved) {
                        val screen = currentScreen()
                        params.x = (startX + dx).toInt()
                            .coerceIn(0, (screen.x - container.width).coerceAtLeast(0))
                        params.y = (startY + dy).toInt()
                            .coerceIn(0, (screen.y - container.height).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(container, params) }

                        linked.forEach { (lv, lp, origin) ->
                            lp.x = (origin[0] + dx).toInt()
                                .coerceIn(0, (screen.x - lv.width).coerceAtLeast(0))
                            lp.y = (origin[1] + dy).toInt()
                                .coerceIn(0, (screen.y - lv.height).coerceAtLeast(0))

                            if (lv.parent != null) {
                                runCatching { wm.updateViewLayout(lv, lp) }
                            }
                        }
                        afterMove?.invoke()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    @Suppress("DEPRECATION")
    internal fun currentScreen(): Point {
        val p = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            p.set(b.width(), b.height())
        } else {
            wm.defaultDisplay.getRealSize(p)
        }
        return p
    }

    internal fun secureFlags(): Int =
        if (settings.blockCapture) WindowManager.LayoutParams.FLAG_SECURE else 0

    internal fun applySecureFlags() {
        val secure = secureFlags()
        fun apply(view: View?, params: WindowManager.LayoutParams?) {
            if (view == null || params == null) return
            val next = (params.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()) or secure
            if (next == params.flags) return
            params.flags = next
            runCatching { wm.updateViewLayout(view, params) }
        }
        apply(buttonBar, barParams)
        apply(circleButton, circleParams)
        apply(panel, panelParams)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLog.i(TAG, "任务被移除，服务保持运行")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        AppLog.i(TAG, "OverlayService onDestroy")
        stopEverything()
        super.onDestroy()
    }

    internal fun stopEverything() {
        running = false
        setRunning(false)
        panelWanted = false
        hidePanel()
        regionOverlay?.dismiss()
        regionOverlay = null
        manualOverlay?.dismiss()
        manualOverlay = null
        buttonBar?.let { runCatching { wm.removeView(it) } }
        buttonBar = null
        barParams = null
        circleButton?.let { runCatching { wm.removeView(it) } }
        circleButton = null
        circleParams = null
        barVisible = false
        recognizeButton = null
        showButton = null
        lastOutcome = null
        capture?.release()
        capture = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        internal const val TAG = "Overlay"
        internal const val CHANNEL_SERVICE = "qsearch_service"
        internal const val NOTIFY_ID = 8801

        const val ACTION_START = "com.banana.search.action.START"
        const val ACTION_STOP = "com.banana.search.action.STOP"
        const val ACTION_REFRESH = "com.banana.search.action.REFRESH"

        const val ACTION_CAPTURE_HIDE = "com.banana.search.action.CAPTURE_HIDE"

        const val ACTION_CAPTURE_SHOW = "com.banana.search.action.CAPTURE_SHOW"

        const val EXTRA_REBUILD = "rebuild_theme"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var onRunningChanged: ((Boolean) -> Unit)? = null

        internal fun setRunning(value: Boolean) {
            val changed = isRunning != value
            isRunning = value
            if (changed) onRunningChanged?.invoke(value)
        }

        fun hideForCapture(context: Context) {
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, OverlayService::class.java).setAction(ACTION_CAPTURE_HIDE)
                )
            }
        }

        fun showAfterCapture(context: Context) {
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, OverlayService::class.java).setAction(ACTION_CAPTURE_SHOW)
                )
            }
        }

        fun startTextMode(context: Context) {
            val i = Intent(context, OverlayService::class.java).setAction(ACTION_START)

            context.startForegroundService(i)
        }

        fun startScreenMode(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }

        fun refresh(context: Context, rebuildTheme: Boolean = false) {
            val i = Intent(context, OverlayService::class.java).setAction(ACTION_REFRESH)
            i.putExtra(EXTRA_REBUILD, rebuildTheme)
            context.startService(i)
        }
    }
}
