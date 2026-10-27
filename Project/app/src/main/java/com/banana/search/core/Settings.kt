package com.banana.search.core

import android.content.Context
import android.content.SharedPreferences

class Settings(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("qsearch_settings", Context.MODE_PRIVATE)

    init {

        if (sp.getInt(K_REGION_EPOCH, 1) < REGION_EPOCH) {
            val hadRegion = !sp.getString(K_REGION, "").isNullOrEmpty()
            sp.edit()
                .putString(K_REGION, "")
                .putInt(K_REGION_EPOCH, REGION_EPOCH)
                .apply()
            if (hadRegion) {
                AppLog.i("Settings", "检测到旧版本选区（坐标系已修正），已自动清除，请重新框选一次")
            }
        }
    }

    var deepseekEnabled: Boolean

        get() = sp.getBoolean(K_DEEPSEEK, false)
        set(v) = sp.edit().putBoolean(K_DEEPSEEK, v).apply()

    var searchMode: Int

        get() = sp.getInt(
            K_SEARCH_MODE,
            if (sp.getBoolean(K_DEEPSEEK, false)) MODE_HYBRID else MODE_LOCAL
        )
        set(v) {
            sp.edit().putInt(K_SEARCH_MODE, v.coerceIn(MODE_LOCAL, MODE_DEEPSEEK)).apply()

            sp.edit().putBoolean(K_DEEPSEEK, v != MODE_LOCAL).apply()
        }

    var sendImageToModel: Boolean
        get() = sp.getBoolean(K_SEND_IMAGE, true)
        set(v) = sp.edit().putBoolean(K_SEND_IMAGE, v).apply()

    var priceInputPerM: Double
        get() = sp.getFloat(K_PRICE_IN, 2f).toDouble()
        set(v) = sp.edit().putFloat(K_PRICE_IN, v.toFloat()).apply()

    var priceOutputPerM: Double
        get() = sp.getFloat(K_PRICE_OUT, 8f).toDouble()
        set(v) = sp.edit().putFloat(K_PRICE_OUT, v.toFloat()).apply()

    var blockCapture: Boolean
        get() = sp.getBoolean(K_BLOCK_CAPTURE, true)
        set(v) = sp.edit().putBoolean(K_BLOCK_CAPTURE, v).apply()

    var highThreshold: Float
        get() = sp.getFloat(K_HIGH, 0.85f)
        set(v) = sp.edit().putFloat(K_HIGH, v.coerceIn(0.5f, 1f)).apply()

    var lowThreshold: Float
        get() = sp.getFloat(K_LOW, 0.60f)
        set(v) = sp.edit().putFloat(K_LOW, v.coerceIn(0.1f, 0.95f)).apply()

    var topK: Int
        get() = sp.getInt(K_TOPK, 3)
        set(v) = sp.edit().putInt(K_TOPK, v.coerceIn(1, 5)).apply()

    var regionSpec: String
        get() = sp.getString(K_REGION, "") ?: ""
        set(v) = sp.edit().putString(K_REGION, v).apply()

    var ocrEnabled: Boolean
        get() = sp.getBoolean(K_OCR, true)
        set(v) = sp.edit().putBoolean(K_OCR, v).apply()

    var screenshotMode: Boolean
        get() = sp.getBoolean(K_SHOT_MODE, false)
        set(v) = sp.edit().putBoolean(K_SHOT_MODE, v).apply()

    var cropTop: Float
        get() = sp.getFloat(K_CROP_TOP, 0.05f)
        set(v) = sp.edit().putFloat(K_CROP_TOP, v.coerceIn(0f, 0.9f)).apply()

    var cropBottom: Float
        get() = sp.getFloat(K_CROP_BOTTOM, 0.72f)
        set(v) = sp.edit().putFloat(K_CROP_BOTTOM, v.coerceIn(0.1f, 1f)).apply()

    var panelWidthDp: Int
        get() = sp.getInt(K_PANEL_W, 280).coerceIn(220, 380)
        set(v) = sp.edit().putInt(K_PANEL_W, v.coerceIn(220, 380)).apply()

    var panelHeightDp: Int
        get() = sp.getInt(K_PANEL_H, 360).coerceIn(120, 720)
        set(v) = sp.edit().putInt(K_PANEL_H, v.coerceIn(120, 720)).apply()

    var barAlpha: Float
        get() = sp.getFloat(K_BAR_ALPHA, 0.94f)
        set(v) = sp.edit().putFloat(K_BAR_ALPHA, v.coerceIn(0.3f, 1f)).apply()

    var soundEnabled: Boolean
        get() = sp.getBoolean(K_SOUND, true)
        set(v) = sp.edit().putBoolean(K_SOUND, v).apply()

    var darkTheme: Boolean
        get() = sp.getBoolean(K_DARK, false)
        set(v) = sp.edit().putBoolean(K_DARK, v).apply()

    var lastTab: Int
        get() = sp.getInt(K_TAB, 0).coerceIn(0, 2)
        set(v) = sp.edit().putInt(K_TAB, v.coerceIn(0, 2)).apply()

    var lastSummary: String
        get() = sp.getString(K_LAST, "") ?: ""
        set(v) = sp.edit().putString(K_LAST, v).apply()

    var disclaimerVersion: String
        get() = sp.getString(K_DISCLAIMER_VERSION, "") ?: ""
        set(v) = sp.edit().putString(K_DISCLAIMER_VERSION, v).apply()

        companion object {

        const val K_PRICE_IN = "price_input_per_m"
        const val K_PRICE_OUT = "price_output_per_m"

        const val MODE_LOCAL = 0
        const val MODE_HYBRID = 1
        const val MODE_DEEPSEEK = 2

        const val K_SEARCH_MODE = "search_mode"
        const val K_SEND_IMAGE = "send_image_to_model"

        const val REGION_EPOCH = 2
        const val K_REGION_EPOCH = "region_epoch"
        const val K_DEEPSEEK = "deepseek_enabled"

        const val K_DISCLAIMER_VERSION = "disclaimer_accepted_version"

        const val K_SOUND = "click_sound_enabled"

        const val K_BLOCK_CAPTURE = "block_capture"
        const val K_HIGH = "th_high"
        const val K_LOW = "th_low"
        const val K_TOPK = "top_k"
        const val K_REGION = "region_spec"
        const val K_OCR = "ocr_enabled"
        const val K_SHOT_MODE = "screenshot_mode"
        const val K_CROP_TOP = "crop_top"
        const val K_CROP_BOTTOM = "crop_bottom"
        const val K_BAR_ALPHA = "bar_alpha"
        const val K_PANEL_W = "panel_width_dp"
        const val K_PANEL_H = "panel_height_dp"
        const val K_DARK = "dark_theme"
        const val K_TAB = "last_tab"
        const val K_LAST = "last_summary"
    }
}
