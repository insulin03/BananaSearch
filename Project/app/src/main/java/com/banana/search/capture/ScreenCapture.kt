package com.banana.search.capture

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import com.banana.search.core.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer

class ScreenCapture(
    private val context: Context,
    private val resultCode: Int,
    private val data: Intent
) {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    var width: Int = 0
        private set
    var height: Int = 0
        private set
    private var densityDpi: Int = 320

    @Volatile
    private var mode: Int = MODE_NONE

    @Volatile
    private var fullSequence: Long = 0L

    @Volatile
    private var full: Bitmap? = null

    private var scratchBitmap: Bitmap? = null

    @Volatile
    private var running = false

    var onStopped: (() -> Unit)? = null

    val isRunning: Boolean get() = running

    @SuppressLint("WrongConstant")
    fun start(): Boolean {
        if (running) return true
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
            densityDpi = context.resources.displayMetrics.densityDpi
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            width = dm.widthPixels
            height = dm.heightPixels
            densityDpi = dm.densityDpi
        }
        if (width <= 0 || height <= 0) {
            AppLog.e(TAG, "屏幕尺寸异常 ${width}x$height")
            return false
        }

        return try {
            val ht = HandlerThread("qsearch-capture").also { it.start() }
            thread = ht
            val h = Handler(ht.looper)
            handler = h

            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mpm.getMediaProjection(resultCode, data) ?: run {
                AppLog.e(TAG, "getMediaProjection 返回 null（投屏授权已失效）")
                return false
            }

            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    AppLog.w(TAG, "投屏会话被系统终止")
                    release()
                    onStopped?.invoke()
                }
            }, h)

            val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            r.setOnImageAvailableListener({ rd -> onFrame(rd) }, h)
            reader = r

            virtualDisplay = p.createVirtualDisplay(
                "qsearch-vd",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface,
                null,
                h
            )
            projection = p
            running = true
            AppLog.i(TAG, "抓屏已启动 ${width}x$height dpi=$densityDpi")
            true
        } catch (t: Throwable) {
            AppLog.e(TAG, "启动抓屏失败", t)
            release()
            false
        }
    }

    suspend fun captureFull(prepare: () -> Unit, restore: () -> Unit, timeoutMs: Long = 2500L): Bitmap? {
        val before = fullSequence
        mode = MODE_FULL
        prepare()

        delay(40)
        val got = withTimeoutOrNull(timeoutMs) {
            while (fullSequence <= before) delay(16)
            true
        } ?: false
        restore()
        if (!got) {
            AppLog.w(TAG, "等待全分辨率帧超时（${timeoutMs}ms）")
            return null
        }
        return full
    }

    private fun onFrame(rd: ImageReader) {

        val image: Image = try {
            rd.acquireLatestImage()
        } catch (t: Throwable) {
            null
        } ?: return
        try {
            if (mode != MODE_FULL) return
            val bmp = imageToBitmap(image) ?: return
            full = bmp
            fullSequence++
        } catch (t: Throwable) {
            AppLog.w(TAG, "帧转换失败: ${t.message}")
        } finally {
            runCatching { image.close() }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        buffer.rewind()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        if (rowPadding <= 0) {
            val direct = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            direct.copyPixelsFromBuffer(buffer)
            return direct
        }

        val paddedWidth = width + rowPadding / pixelStride
        val scratch = scratchBitmap?.takeIf {
            !it.isRecycled && it.width == paddedWidth && it.height == height
        } ?: Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888).also { fresh ->
            scratchBitmap?.recycle()
            scratchBitmap = fresh
        }
        scratch.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(scratch, 0, 0, width, height)
    }

    fun release() {
        running = false
        mode = MODE_NONE
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { reader?.close() }
        reader = null
        runCatching { projection?.stop() }
        projection = null
        runCatching { thread?.quitSafely() }
        thread = null
        handler = null
        full = null
        scratchBitmap?.recycle()
        scratchBitmap = null
    }

    companion object {
        const val MODE_NONE = 0
        const val MODE_FULL = 2

        private const val TAG = "Capture"
    }
}
