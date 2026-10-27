package com.banana.search.ui

import com.banana.search.core.BankBatch
import com.banana.search.core.StoredQuestion

interface AppHost {

    fun maskedKey(): String

    fun revealKey(): String
    fun saveKey(key: String)
    fun clearKey()
    suspend fun testKey(): Pair<Boolean, String>

    fun searchMode(): Int
    fun setSearchMode(mode: Int)

    fun sendImageToModel(): Boolean
    fun setSendImageToModel(v: Boolean)

    fun priceInputPerM(): Double

    fun setPriceInputPerM(v: Double)

    fun priceOutputPerM(): Double

    fun setPriceOutputPerM(v: Double)

    fun blockCapture(): Boolean
    fun setBlockCapture(v: Boolean)

    fun soundEnabled(): Boolean
    fun setSoundEnabled(v: Boolean)

    fun overlayGranted(): Boolean
    fun notificationGranted(): Boolean
    fun batteryExempt(): Boolean
    fun accessibilityEnabled(): Boolean
    fun requestOverlay()
    fun requestNotification()
    fun requestBattery()
    fun requestAccessibility()

    fun startSession()
    fun previewOpen(): Boolean
    fun setPreviewOpen(v: Boolean)
    fun stopSession()
    fun serviceRunning(): Boolean

    fun ocrEnabled(): Boolean
    fun setOcrEnabled(v: Boolean)
    fun highThreshold(): Float
    fun setHighThreshold(v: Float)
    fun lowThreshold(): Float
    fun setLowThreshold(v: Float)
    fun topK(): Int
    fun setTopK(v: Int)
    fun panelWidthDp(): Int
    fun panelHeightDp(): Int
    fun setPanelWidth(v: Int)
    fun setPanelHeight(v: Int)
    fun barAlpha(): Float
    fun setBarAlpha(v: Float)
    fun cropTop(): Float
    fun setCropTop(v: Float)
    fun cropBottom(): Float
    fun setCropBottom(v: Float)
    fun screenshotMode(): Boolean
    fun setScreenshotMode(v: Boolean)
    fun regionSpec(): String
    fun setRegionSpec(v: String)

    fun selectRegion()

    fun regionRevision(): Int

    fun requestRegionPreview()
    fun regionPreview(): RegionPreview?

    fun clearRegionPreview()

    fun pickBankFiles()

    fun importBankText(text: String)

    fun bankBatches(): List<BankBatch>
    fun bankQuestions(batchId: String): List<StoredQuestion>
    fun setBankBatchEnabled(batchId: String, enabled: Boolean)
    fun deleteBankBatches(ids: List<String>)

    fun deleteBankQuestionByText(batchId: String, question: String)

    fun takeBankMessage(): String?

    fun history(): List<com.banana.search.core.RecognizeHistory.Entry>

    fun clearHistory()

    fun exportBank()

    fun saveBankTemplates()

    fun darkTheme(): Boolean
    fun setDarkTheme(v: Boolean)

    fun lastTab(): Int
    fun setLastTab(v: Int)

    fun bankSize(): Int
    fun selfCheckSummary(): String
    fun selfTestSample(): String
    fun lastSummary(): String
    fun logDump(): String
    fun clearLog()
    fun notifySettingsChanged()
    fun toast(msg: String)
}
