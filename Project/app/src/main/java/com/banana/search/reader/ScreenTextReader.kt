package com.banana.search.reader

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import com.banana.search.core.AppLog
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

class ScreenTextReader : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        AppLog.i(TAG, "无障碍服务已连接，可按需读取屏幕文字")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != packageName) currentPackage = pkg
        }
    }

    override fun onInterrupt() {
        AppLog.w(TAG, "无障碍服务被中断")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = WeakReference(null)
        AppLog.w(TAG, "无障碍服务已断开")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "Reader"
        private const val MAX_DEPTH = 40
        private const val MAX_LINES = 200
        private const val MAX_CHARS = 20000

        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.gms"
        )

        @Volatile
        private var instance: WeakReference<ScreenTextReader> = WeakReference(null)

        @Volatile
        var currentPackage: String? = null
            private set

        data class ReadResult(val lines: List<String>, val sourcePackage: String?) {
            val fullText: String get() = lines.joinToString("\n")
            val isEmpty: Boolean get() = lines.isEmpty()

            companion object {
                fun empty() = ReadResult(emptyList(), null)
            }
        }

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ScreenTextReader::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                val name = splitter.next()
                if (name.equals(expected.flattenToString(), ignoreCase = true) ||
                    name.equals(expected.flattenToShortString(), ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }

        fun takeScreenshot(onResult: (Bitmap?, String?) -> Unit) {
            val service = instance.get() ?: run {
                AppLog.w(TAG, "无障碍服务未连接，无法截图")
                onResult(null, "无障碍服务未连接，无法截图")
                return
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                onResult(null, "系统版本低于 Android 11，无障碍截图不可用")
                return
            }
            takeScreenshotR(service, onResult)
        }

        @RequiresApi(Build.VERSION_CODES.R)
        private fun takeScreenshotR(
            service: AccessibilityService,
            onResult: (Bitmap?, String?) -> Unit
        ) {

            val handler = Handler(Looper.getMainLooper())
            val executor = Executor { command -> handler.post(command) }
            val attempt = runCatching {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val buffer = result.hardwareBuffer

                            val bitmap = runCatching {
                                Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                            }.getOrNull()
                            runCatching { buffer.close() }
                            if (bitmap != null) {
                                AppLog.i(TAG, "无障碍截图成功 ${bitmap.width}×${bitmap.height}")
                                onResult(bitmap, null)
                            } else {
                                onResult(null, "截图解码失败")
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            AppLog.w(TAG, "无障碍截图失败 code=$errorCode")
                            onResult(null, "无障碍截图失败（code=$errorCode），等一秒再试")
                        }
                    }
                )
            }
            if (attempt.isFailure) {
                AppLog.e(TAG, "发起无障碍截图失败", attempt.exceptionOrNull())
                onResult(null, "发起无障碍截图失败：${attempt.exceptionOrNull()?.message}")
            }
        }

        fun readScreenLines(
            region: Rect? = null,
            excludeRects: List<Rect> = emptyList()
        ): ReadResult {
            val service = instance.get() ?: run {
                AppLog.w(TAG, "无障碍服务未连接，无法读取屏幕文字")
                return ReadResult.empty()
            }

            val windowRoots = ArrayList<AccessibilityNodeInfo>(4)
            try {
                service.windows?.forEach { w ->
                    val pkg = w.root?.packageName?.toString() ?: "?"
                    val focused = if (Build.VERSION.SDK_INT >= 34) w.isFocused else false

                    AppLog.i(
                        TAG,
                        if (AppLog.debugEnabled) "窗口 pkg=$pkg type=${w.type} active=${w.isActive} focused=$focused"
                        else "窗口 type=${w.type} active=${w.isActive} focused=$focused"
                    )
                    w.root?.let { windowRoots.add(it) }
                }
            } catch (t: Throwable) {
                AppLog.w(TAG, "取窗口列表失败: ${t.message}")
            }

            val active = try {
                service.rootInActiveWindow
            } catch (t: Throwable) {
                AppLog.w(TAG, "取根节点失败: ${t.message}")
                null
            }

            val ordered = ArrayList<AccessibilityNodeInfo>(windowRoots.size + 1)
            active?.let { ordered.add(it) }
            ordered.addAll(windowRoots)
            val ranked = ordered
                .mapNotNull { root ->
                    val pkg = root.packageName?.toString() ?: return@mapNotNull null
                    if (pkg in IGNORED_PACKAGES) return@mapNotNull null
                    val window = try { root.window } catch (t: Throwable) { null }
                    val type = window?.type ?: AccessibilityWindowInfo.TYPE_APPLICATION
                    val isApp = type == AccessibilityWindowInfo.TYPE_APPLICATION
                    val isActive = window?.isActive == true
                    val isFocused = if (Build.VERSION.SDK_INT >= 34) window?.isFocused == true else false
                    val score = when {
                        isApp && isActive -> 4
                        isApp && isFocused -> 3
                        isApp -> 2
                        else -> 1
                    }
                    root to score
                }
                .sortedByDescending { it.second }
            val root = ranked.firstOrNull()?.first ?: active

            if (root == null) {
                AppLog.w(TAG, "当前窗口没有可读节点（可能是图片渲染或受限窗口）")
                return ReadResult.empty()
            }

            val lines = ArrayList<String>(64)
            val seen = HashSet<String>(128)
            val source = root.packageName?.toString()
            collect(root, region, excludeRects, lines, seen, 0)
            AppLog.i(
                TAG,
                "读到 ${lines.size} 行文字" +
                    (if (AppLog.debugEnabled) "，来源包名=$source" else "") +
                    (if (region != null) "，已按选区过滤 $region" else "") +
                    (if (excludeRects.isNotEmpty()) "，已排除 ${excludeRects.size} 个悬浮控件区域" else "")
            )
            return ReadResult(lines, source)
        }

        private fun collect(
            node: AccessibilityNodeInfo?,
            region: Rect?,
            excludeRects: List<Rect>,
            out: MutableList<String>,
            seen: MutableSet<String>,
            depth: Int
        ) {
            if (node == null || depth > MAX_DEPTH) return
            if (out.size >= MAX_LINES) return
            if (out.sumOf { it.length } > MAX_CHARS) return

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (region != null) {

                if (!Rect.intersects(bounds, region)) return
            }

            val excluded = bounds.width() > 0 && bounds.height() > 0 &&
                excludeRects.any { it.contains(bounds) }
            if (!excluded) {
                val text = node.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty() && seen.add(text)) out.add(text)

                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                if (desc.isNotEmpty() && seen.add(desc)) out.add(desc)
            }

            val count = node.childCount
            for (i in 0 until count) {
                collect(node.getChild(i), region, excludeRects, out, seen, depth + 1)
            }
        }
    }
}
