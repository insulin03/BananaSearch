package com.banana.search

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import android.view.WindowManager
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.banana.search.core.ApiKeyStore
import com.banana.search.core.BankBatch
import com.banana.search.core.BankImporter
import com.banana.search.core.BankStore
import com.banana.search.core.StoredQuestion
import com.banana.search.core.AppLog
import com.banana.search.core.DeepSeekClient
import com.banana.search.core.QuestionBank
import com.banana.search.core.QuestionTypes
import com.banana.search.core.RegionSpec
import com.banana.search.core.SoundFx
import com.banana.search.core.Settings
import com.banana.search.overlay.OverlayService
import com.banana.search.overlay.RegionSelectOverlay
import com.banana.search.reader.ScreenTextReader
import com.banana.search.ui.AppHost
import com.banana.search.ui.DISCLAIMER_REVISION
import com.banana.search.ui.AppUi
import com.banana.search.ui.RegionPreview

class MainActivity : ComponentActivity(), AppHost {

    private lateinit var keyStore: ApiKeyStore
    private lateinit var settings: Settings

    private val resumeTick = mutableIntStateOf(0)

    private var framePreview by mutableStateOf(false)

    private var a11yManager: android.view.accessibility.AccessibilityManager? = null
    private var a11yStateListener:
        android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener? = null
    private var a11yTouchListener:
        android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener? = null

    private val regionRevisionState = mutableIntStateOf(0)

    private val regionPreviewState = mutableStateOf<RegionPreview?>(null)

    private var regionOverlay: RegionSelectOverlay? = null

    private var lastSelfTestQuestion: String = ""

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            AppLog.i("Main", "已获得投屏授权，启动前台服务（截屏 OCR 模式）")
            OverlayService.startScreenMode(this, result.resultCode, data)

            moveTaskToBack(true)
        } else {
            toast("已取消屏幕投送授权，无法启动抓屏")
        }
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        toast(if (granted) "通知权限已授予" else "未授予通知权限，前台服务通知可能不显示")
    }

    private val bankMessage = mutableStateOf<String?>(null)

    private val bankPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        bankMessage.value = "正在解析 ${uris.size} 个文件…"
        Thread {
            val summary = runCatching { BankImporter.importUris(this, uris) }.getOrNull()
            val text = if (summary == null) "导入失败：解析时出错" else describeImport(summary)
            runOnUiThread {
                bankMessage.value = text
                resumeTick.intValue++
                QuestionBank.reload(this)
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keyStore = ApiKeyStore(this)
        settings = Settings(this)
        SoundFx.install(this)
        SoundFx.setEnabled(settings.soundEnabled)

        OverlayService.onRunningChanged = { resumeTick.intValue++ }

        runCatching {
            val am = getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            if (am != null) {
                val state = android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener {
                    resumeTick.intValue++
                }
                val touch = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener {
                    resumeTick.intValue++
                }
                am.addAccessibilityStateChangeListener(state)
                am.addTouchExplorationStateChangeListener(touch)
                a11yManager = am
                a11yStateListener = state
                a11yTouchListener = touch
            }
        }

        runCatching {

            val dm = getSystemService(android.hardware.display.DisplayManager::class.java)
            val d = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val best = d?.supportedModes?.maxByOrNull { it.refreshRate }
            if (best != null && best.refreshRate > 60f) {
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = best.refreshRate
                }
            }
        }

        setContent {

        var showFirstRun by rememberSaveable {
            mutableStateOf(settings.disclaimerVersion != DISCLAIMER_REVISION)
        }
            AppUi(
                host = this@MainActivity,
                resumeTick = resumeTick.intValue,
                showFirstRun = showFirstRun,
                onAcceptFirstRun = {
                    settings.disclaimerVersion = DISCLAIMER_REVISION
                    showFirstRun = false
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++

        regionRevisionState.intValue++
    }

    override fun onDestroy() {

        runCatching {
            a11yManager?.let { am ->
                a11yStateListener?.let { am.removeAccessibilityStateChangeListener(it) }
                a11yTouchListener?.let { am.removeTouchExplorationStateChangeListener(it) }
            }
        }
        a11yManager = null
        a11yStateListener = null
        a11yTouchListener = null

        regionOverlay?.dismiss()
        regionOverlay = null
        regionPreviewState.value?.let { it.crop.recycle() }
        regionPreviewState.value = null
        super.onDestroy()
    }

    override fun maskedKey(): String = keyStore.masked()

    override fun revealKey(): String = keyStore.load() ?: ""

    override fun saveKey(key: String) {
        keyStore.save(key)
        toast(if (key.isBlank()) "已清除 API Key" else "API Key 已加密保存到本机")
    }

    override fun clearKey() {
        keyStore.clear()
        toast("已清除 API Key")
    }

    override suspend fun testKey(): Pair<Boolean, String> {
        val probe = DeepSeekClient { keyStore.load() }
        return probe.testConnection()
    }

    override fun searchMode(): Int = settings.searchMode

    override fun setSearchMode(mode: Int) {
        settings.searchMode = mode
        notifySettingsChanged()
    }

    override fun sendImageToModel(): Boolean = settings.sendImageToModel

    override fun setSendImageToModel(v: Boolean) {
        settings.sendImageToModel = v
        notifySettingsChanged()
    }

    override fun pickBankFiles() {
        runCatching {
            bankPicker.launch(
                arrayOf(
                    "text/plain",
                    "text/csv",
                    "text/comma-separated-values",
                    "application/csv",
                    "application/json",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/octet-stream"
                )
            )
        }.onFailure {
            com.banana.search.ui.AppToast.show(this, "这台设备没有可用的文件选择器")
        }
    }

    override fun importBankText(text: String) {
        bankMessage.value = "正在解析粘贴内容…"
        Thread {
            val outcome = runCatching { BankImporter.importText(this, text, "") }.getOrNull()
            val msg = when (outcome) {
                is BankImporter.Imported -> buildString {
                    append("导入成功：${outcome.batch.count} 题")
                    if (outcome.errors.isNotEmpty()) {
                        val first = outcome.errors.first()
                        append("，跳过 ${outcome.errors.size} 条（${first.where}：${first.reason}）")
                    }
                }
                is BankImporter.Failed -> "导入失败：${outcome.message}"
                else -> "导入失败：解析时出错"
            }
            runOnUiThread {
                bankMessage.value = msg
                resumeTick.intValue++
                QuestionBank.reload(this)
            }
        }.start()
    }

    override fun bankBatches(): List<BankBatch> = BankStore.batches(this)

    override fun bankQuestions(batchId: String): List<StoredQuestion> =
        BankStore.questions(this, batchId)

    override fun setBankBatchEnabled(batchId: String, enabled: Boolean) {
        BankStore.setEnabled(this, batchId, enabled)
        QuestionBank.reload(this)
    }

    override fun deleteBankBatches(ids: List<String>) {
        BankStore.delete(this, ids)
        QuestionBank.reload(this)
    }

    override fun deleteBankQuestionByText(batchId: String, question: String) {
        val index = BankStore.questions(this, batchId).indexOfFirst { it.question == question }
        if (index < 0) return
        BankStore.deleteQuestion(this, batchId, index)
        QuestionBank.reload(this)
    }

    override fun takeBankMessage(): String? {
        val m = bankMessage.value
        bankMessage.value = null
        return m
    }

    override fun saveBankTemplates() {
        Thread {
            val msg = runCatching { writeTemplates() }.getOrElse { "保存失败：${it.message}" }
            runOnUiThread { com.banana.search.ui.AppToast.show(this, msg) }
        }.start()
    }

    override fun history(): List<com.banana.search.core.RecognizeHistory.Entry> =
        com.banana.search.core.RecognizeHistory.list(this)

    override fun clearHistory() {
        com.banana.search.core.RecognizeHistory.clear(this)
    }

    override fun priceInputPerM(): Double = settings.priceInputPerM

    override fun setPriceInputPerM(v: Double) {
        settings.priceInputPerM = v.coerceIn(0.0, 1000.0)
    }

    override fun priceOutputPerM(): Double = settings.priceOutputPerM

    override fun setPriceOutputPerM(v: Double) {
        settings.priceOutputPerM = v.coerceIn(0.0, 1000.0)
    }

    override fun exportBank() {
        Thread {
            val msg = runCatching { writeBankExport() }.getOrElse { "导出失败：${it.message}" }
            runOnUiThread { com.banana.search.ui.AppToast.show(this, msg) }
        }.start()
    }

    private fun writeBankExport(): String {
        val (json, total) = com.banana.search.core.BankBackup.exportJson(this)
        if (total == 0) return "题库是空的，没有可导出的内容"
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
        val name = "题库备份-$stamp.json"
        val bytes = json.toByteArray(Charsets.UTF_8)
        val folder = "Download/香蕉做题题库"
        val where = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "写入下载目录失败"
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return "写入下载目录失败"
            "$folder/$name"
        } else {
            val dir = java.io.File(getExternalFilesDir(null), "香蕉做题题库").apply { mkdirs() }
            val f = java.io.File(dir, name)
            f.writeBytes(bytes)
            f.absolutePath
        }
        return "已导出 $total 题到 $where"
    }

    private fun writeTemplates(): String {
        val names = runCatching { assets.list("templates")?.toList().orEmpty() }
            .getOrDefault(emptyList())
        if (names.isEmpty()) return "包里没有模板文件"
        var saved = 0
        val folder = "Download/香蕉做题模板"
        val legacyDir = getExternalFilesDir("templates")?.apply { mkdirs() }
        for (name in names) {
            runCatching {
                val bytes = assets.open("templates/$name").use { it.readBytes() }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
                        put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                } else {
                    legacyDir?.let { File(it, name).writeBytes(bytes) }
                }
                saved++
            }
        }
        return if (saved == 0) "保存失败" else "已保存 $saved 个模板到 $folder"
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "")) {
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json", "jsonl" -> "application/json"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else -> "application/octet-stream"
    }

    private fun describeImport(summary: BankImporter.Summary): String {
        val sb = StringBuilder()
        val total = summary.imported.sumOf { it.batch.count }
        sb.append("导入完成：${summary.imported.size} 批，共 $total 题")
        if (summary.failures.isNotEmpty()) sb.append("；${summary.failures.size} 个文件失败")
        for (r in summary.imported) {
            sb.append("\n· ").append(r.batch.name).append("：").append(r.batch.count).append(" 题")
            if (r.errors.isNotEmpty()) {
                val first = r.errors.first()
                sb.append("，跳过 ").append(r.errors.size).append(" 条（")
                    .append(first.where).append("：").append(first.reason).append("）")
            }
        }
        for ((name, why) in summary.failures) {
            sb.append("\n· ").append(name).append("：").append(why)
        }
        return sb.toString()
    }

    override fun blockCapture(): Boolean = settings.blockCapture
    override fun setBlockCapture(v: Boolean) {
        settings.blockCapture = v

        if (OverlayService.isRunning) notifySettingsChanged()
    }

    override fun soundEnabled(): Boolean = settings.soundEnabled
    override fun setSoundEnabled(v: Boolean) {
        settings.soundEnabled = v
        SoundFx.setEnabled(v)
        if (v) SoundFx.play()
    }

    override fun darkTheme(): Boolean = settings.darkTheme
    override fun setDarkTheme(v: Boolean) {
        settings.darkTheme = v

        if (OverlayService.isRunning) {
            runCatching { OverlayService.refresh(this, rebuildTheme = true) }
        }
    }

    override fun lastTab(): Int = settings.lastTab
    override fun setLastTab(v: Int) { settings.lastTab = v }

    override fun overlayGranted(): Boolean = AndroidSettings.canDrawOverlays(this)

    override fun notificationGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    override fun batteryExempt(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    override fun requestOverlay() {
        runCatching {
            startActivity(
                Intent(
                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { toast("无法打开悬浮窗设置：${it.message}") }
    }

    override fun requestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            toast("当前系统版本无需单独申请通知权限")
        }
    }

    @Suppress("BatteryLife")
    override fun requestBattery() {
        runCatching {
            startActivity(
                Intent(
                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            runCatching { startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure { toast("请手动在「设置→电池」中允许本应用后台运行") }
        }
    }

    override fun accessibilityEnabled(): Boolean = ScreenTextReader.isEnabled(this)
    override fun requestAccessibility() {
        runCatching { startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure { toast("无法打开无障碍设置：${it.message}") }
    }

    override fun startSession() {
        if (framePreview) {
            toast("先关闭框体预览，再启动服务")
            return
        }
        if (!overlayGranted()) {
            toast("请先授予「显示在其他应用上层」权限")
            return
        }
        if (settings.deepseekEnabled && keyStore.load().isNullOrBlank()) {
            toast("已开启 DeepSeek 辅助，请先填写 API Key 并测试连通（或把开关关掉，纯本地运行）")
            return
        }
        if (settings.screenshotMode) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            runCatching {

                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                } else {
                    mpm.createScreenCaptureIntent()
                }
                projectionLauncher.launch(intent)
            }.onFailure { toast("无法发起投屏授权：${it.message}") }
        } else {
            if (!ScreenTextReader.isEnabled(this)) {
                toast("请先开启无障碍服务，它只在点「识别」时读一次屏幕文字")
                return
            }
            AppLog.i("Main", "启动前台服务（无障碍读文字模式，不建立投屏会话）")
            OverlayService.startTextMode(this)
            moveTaskToBack(true)
        }
    }

    override fun previewOpen(): Boolean = framePreview

    override fun setPreviewOpen(v: Boolean) {
        if (v && OverlayService.isRunning) {
            toast("服务正在运行，先停止服务再预览框体")
            return
        }
        framePreview = v
    }

    override fun stopSession() {
        OverlayService.stop(this)
        toast("已停止识别服务")

        resumeTick.intValue++
    }

    override fun serviceRunning(): Boolean = OverlayService.isRunning

    override fun ocrEnabled() = settings.ocrEnabled
    override fun setOcrEnabled(v: Boolean) { settings.ocrEnabled = v }

    override fun highThreshold() = settings.highThreshold
    override fun setHighThreshold(v: Float) { settings.highThreshold = v }

    override fun lowThreshold() = settings.lowThreshold
    override fun setLowThreshold(v: Float) { settings.lowThreshold = v }

    override fun topK() = settings.topK
    override fun setTopK(v: Int) { settings.topK = v }

    override fun barAlpha() = settings.barAlpha
    override fun setBarAlpha(v: Float) { settings.barAlpha = v }
    override fun panelWidthDp() = settings.panelWidthDp
    override fun panelHeightDp() = settings.panelHeightDp
    override fun setPanelWidth(v: Int) { settings.panelWidthDp = v; notifySettingsChanged() }
    override fun setPanelHeight(v: Int) { settings.panelHeightDp = v; notifySettingsChanged() }

    override fun cropTop() = settings.cropTop
    override fun setCropTop(v: Float) { settings.cropTop = v }

    override fun cropBottom() = settings.cropBottom
    override fun setCropBottom(v: Float) { settings.cropBottom = v }

    override fun screenshotMode() = settings.screenshotMode
    override fun setScreenshotMode(v: Boolean) { settings.screenshotMode = v }

    override fun regionSpec() = settings.regionSpec
    override fun setRegionSpec(v: String) { settings.regionSpec = v }

    override fun regionRevision(): Int = regionRevisionState.intValue

    override fun selectRegion() {
        if (regionOverlay != null) {
            toast("选区遮罩已经打开了")
            return
        }
        if (!AndroidSettings.canDrawOverlays(this)) {
            toast("请先授予「显示在其他应用上层」权限")
            return
        }
        val size = screenSize()
        val existing = RegionSpec.fractions(settings.regionSpec, size[0], size[1])

        val overlay = RegionSelectOverlay(applicationContext, existing) { rect ->
            regionOverlay = null
            if (rect != null) {
                settings.regionSpec = RegionSpec.encode(
                    rect.left, rect.top, rect.right, rect.bottom, size[0], size[1]
                )
                AppLog.i("Main", "设置页框选：$rect → ${settings.regionSpec}")
                toast("选区已设置（${rect.width()}×${rect.height()}）")
            } else {
                AppLog.i("Main", "设置页框选已取消，选区保持不变")
            }
            regionRevisionState.intValue++
            notifySettingsChanged()
        }
        regionOverlay = overlay
        overlay.show()
        AppLog.i("Main", "设置页打开选区遮罩")
    }

    override fun requestRegionPreview() {
        val size = screenSize()
        val rect = RegionSpec.decode(settings.regionSpec, size[0], size[1])
        if (rect == null) {
            toast("还没设置选区，先点「框选区域」")
            return
        }
        if (!ScreenTextReader.isEnabled(this)) {
            toast("「查看选区状态」要用无障碍截图，请先开启无障碍服务")
            return
        }
        AppLog.i("Main", "请求选区截图，选区=${rect.toList()}")

        OverlayService.hideForCapture(this)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            ScreenTextReader.takeScreenshot { bitmap, error ->
                OverlayService.showAfterCapture(this@MainActivity)
                if (bitmap == null) {
                    toast(error ?: "截图失败")
                    return@takeScreenshot
                }
                val preview = cropToRegion(bitmap, rect, size[0], size[1])
                if (preview == null) {
                    toast("选区落在截图之外，请重新框选")
                    return@takeScreenshot
                }
                regionPreviewState.value?.let { it.crop.recycle() }
                regionPreviewState.value = preview
                AppLog.i(
                    "Main",
                    "选区截图就绪：整屏 ${preview.shotW}×${preview.shotH}，裁出 ${preview.cropW}×${preview.cropH}"
                )
            }
        }, 60)
    }

    override fun regionPreview(): RegionPreview? = regionPreviewState.value

    override fun clearRegionPreview() {
        val preview = regionPreviewState.value ?: return
        regionPreviewState.value = null

        preview.crop.recycle()
        AppLog.i("Main", "选区预览已关闭，截图已释放")
    }

    private fun cropToRegion(shot: Bitmap, rect: IntArray, screenW: Int, screenH: Int): RegionPreview? {
        val shotW = shot.width
        val shotH = shot.height
        if (shotW <= 0 || shotH <= 0) return null
        val sx = shotW.toFloat() / screenW
        val sy = shotH.toFloat() / screenH
        val left = (rect[0] * sx).toInt().coerceIn(0, shotW - 1)
        val top = (rect[1] * sy).toInt().coerceIn(0, shotH - 1)
        val right = (rect[2] * sx).toInt().coerceIn(left + 1, shotW)
        val bottom = (rect[3] * sy).toInt().coerceIn(top + 1, shotH)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) {
            shot.recycle()
            return null
        }
        val cropped = Bitmap.createBitmap(shot, left, top, w, h)

        val keep = if (cropped === shot) cropped.copy(Bitmap.Config.ARGB_8888, false) else cropped
        shot.recycle()
        return RegionPreview(
            crop = keep,
            region = Rect(rect[0], rect[1], rect[2], rect[3]),
            screenW = screenW,
            screenH = screenH,
            shotW = shotW,
            shotH = shotH,
        )
    }

    private fun screenSize(): IntArray {
        val wm = applicationContext.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            return intArrayOf(bounds.width(), bounds.height())
        }
        val point = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(point)
        return intArrayOf(point.x, point.y)
    }

    override fun bankSize(): Int = QuestionBank.size

    override fun selfCheckSummary(): String {
        if (!QuestionBank.isLoaded) return "题库尚未载入"
        val r = QuestionBank.selfCheck()
        return "共 ${r.total} 题 · 平均接近率 ${(r.avgScore * 100).toInt()}% · " +
            "最低 ${(r.worstScore * 100).toInt()}%（id=${r.worstId}）"
    }

    override fun selfTestSample(): String {
        if (!QuestionBank.isLoaded) return ""
        val pool = QuestionBank.all
        if (pool.isEmpty()) return ""

        val candidates = pool.filter { it.question != lastSelfTestQuestion }.ifEmpty { pool }
        val item = candidates.randomOrNull() ?: return ""
        lastSelfTestQuestion = item.question
        val type = QuestionTypes.infer(item.question, item.type, item.answers)
        return buildString {

            append('【').append(type.label).append('】').append(item.question)
            if (item.answers.isNotEmpty()) {
                append('\n')

                append(QuestionTypes.answerText(type, item.answers))
            }
        }
    }

    override fun lastSummary(): String = settings.lastSummary

    override fun logDump(): String = AppLog.dump()

    override fun clearLog() {
        AppLog.clear()
        toast("日志已清空")
    }

    override fun notifySettingsChanged() {
        if (OverlayService.isRunning) OverlayService.refresh(this)
    }

    override fun toast(msg: String) {
        com.banana.search.ui.AppToast.show(this, msg)
    }
}
