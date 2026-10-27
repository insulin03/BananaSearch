package com.banana.search.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.banana.search.BuildConfig

import android.net.Uri

import android.content.Intent

import androidx.compose.runtime.mutableFloatStateOf
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banana.search.core.BankBatch
import com.banana.search.core.Settings
import kotlinx.coroutines.launch

@Composable
internal fun LaunchTab(host: AppHost, tick: Int) {
    val pal = LocalPal.current
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var probing by remember { mutableStateOf(false) }

    var masked by remember { mutableStateOf(host.maskedKey()) }
    var shotMode by remember { mutableStateOf(host.screenshotMode()) }
    var mode by remember { mutableIntStateOf(host.searchMode()) }
    var sendImage by remember { mutableStateOf(host.sendImageToModel()) }
    var priceOpen by remember { mutableStateOf(false) }

    var localTick by remember { mutableIntStateOf(0) }
    val running = remember(tick, localTick) { host.serviceRunning() }
    val overlay = remember(tick) { host.overlayGranted() }
    val notify = remember(tick) { host.notificationGranted() }
    val battery = remember(tick) { host.batteryExempt() }
    val a11y = remember(tick) { host.accessibilityEnabled() }

    LaunchedEffect(tick) {
        masked = host.maskedKey()
        mode = host.searchMode()
        sendImage = host.sendImageToModel()
    }

    ScrollPane {

        SectionCard(
            0,
            "① 取词方式",
            locked = running,
            help = "无障碍读文字（推荐）：点「识别」时读取当前界面的文字节点。不需要截屏，因此状态栏没有投屏指示、" +
                "启动时也不弹投屏授权框，而且文字是精确的，题库命中率基本恒为满分。\n\n" +
                "截屏 OCR：截屏后再做离线 OCR。只有题干是图片 / Canvas 渲染时才有必要，代价是建立投屏会话" +
                "（状态栏红标常驻）且 OCR 可能有个别错字。\n\n" +
                "服务运行中不能切换，要先停止服务。"
        ) {
            SegRow(
                options = listOf("无障碍读文字", "截屏 OCR"),
                selected = if (shotMode) 1 else 0,
                enabled = !running
            ) { index ->
                shotMode = index == 1
                host.setScreenshotMode(shotMode)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (shotMode) "支持图片题，会出现投屏指示" else "文字精确、无投屏指示",
                fontSize = 11.5.sp,
                color = if (shotMode) pal.orange else pal.green,
                fontWeight = FontWeight.Medium
            )
        }

        SectionCard(
            1,
            "② 授权",
            help = "悬浮窗权限：显示圆形按钮与方块按钮，缺了就什么都看不到。\n\n" +
                "通知权限：常驻通知是前台服务保活的一部分。\n\n" +
                "无障碍服务：只在点「识别」时读一次屏幕文字，不做连续监控。\n\n" +
                "投屏授权：仅截屏 OCR 模式需要，点「启动」时由系统弹出。\n\n" +
                "忽略电池优化：降低被系统省电策略回收的概率，可选。\n\n" +
                "vivo / OriginOS 还需在「设置 → 电池 → 后台高耗电」与「自启动」里放行本应用，" +
                "否则任何应用都挡不住厂商省电策略。"
        ) {
            PermRow(
                "悬浮窗权限", "显示悬浮控件的必需品", overlay, required = true,
                actionLabel = "去授权", onAction = { host.requestOverlay() }
            )
            PermRow(
                "通知权限", "前台服务保活的一部分", notify, required = true,
                actionLabel = "去授权", onAction = { host.requestNotification() }
            )
            if (shotMode) {
                PermRow(
                    "投屏授权", "截屏模式需要，点「启动」时弹出", false, required = false,
                    actionLabel = "启动时弹", onAction = {
                        host.toast("点下面的「启动」按钮时系统会弹出投屏授权框")
                    }
                )
            } else {
                PermRow(
                    "无障碍服务", "点「识别」时读一次屏幕文字", a11y, required = true,
                    actionLabel = "去开启", onAction = { host.requestAccessibility() }
                )
            }
            PermRow(
                "忽略电池优化", "可选，降低被回收概率", battery, required = false,
                actionLabel = "去设置", onAction = { host.requestBattery() }
            )
        }

        SectionCard(
            2,
            "③ 搜索模式",
            locked = running,
            help = "纯本地题库：只在导入的题库里找答案，不联网、不消耗任何 Token，也不用填 API Key。" +
                "题库里没有的题只会给出近似候选。\n\n" +
                "混合模式：先查题库，有把握直接给答案（零 Token）；没把握才把题干/截图交给模型。" +
                "这是最省 Token 的联网方式。\n\n" +
                "纯 DeepSeek：题库完全不参与，每道题都问模型。适合题库里根本没有的科目，" +
                "但每次识别都会产生 Token。"
        ) {
            SegRow(
                options = listOf("纯本地", "混合模式", "纯 DeepSeek"),
                selected = mode,
                enabled = !running
            ) { index ->
                mode = index
                host.setSearchMode(index)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (mode) {
                    Settings.MODE_LOCAL -> "纯本地题库：完全离线，只查导入的题库"
                    Settings.MODE_HYBRID -> "混合模式：题库优先，没把握才问模型"
                    else -> "纯 DeepSeek：每题都问模型，token 消耗最高"
                },
                fontSize = 11.5.sp,
                color = if (mode == Settings.MODE_LOCAL) pal.green else pal.orange,
                fontWeight = FontWeight.Medium
            )
        }

        AnimatedVisibility(
            visible = mode != Settings.MODE_LOCAL,

            enter = expandVertically(tween(260, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
            exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
        ) {
        SectionCard(
            3,
            "④ DeepSeek API 填写",
            locked = running,
            help = "关闭：只用内置题库，不联网、不消耗任何 Token，也不需要填 API Key。" +
                "题库里能命中的题照样能答。\n\n" +
                "开启：本地题库没把握的题会交给 deepseek-flash——先把本地最接近的几道题连同答案一起" +
                "给它判断归属，仍不确定才让它独立作答；能命中题库时不会调用接口。\n\n" +
                "API Key 用 Android Keystore 加密后只存在本机，不会上传到任何第三方服务器；" +
                "「测试连通」调用官方余额接口，不消耗生成 Token。\n\n" +
                "服务运行中不能切换开关，要先停止服务。"
        ) {
            Text("发送内容", fontSize = 12.5.sp, color = pal.muted, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            SegRow(
                options = listOf("只发文字", "文字 + 截图"),
                selected = if (sendImage) 1 else 0,
                enabled = !running
            ) { index ->
                sendImage = index == 1
                host.setSendImageToModel(sendImage)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (sendImage) {
                    "文字 + 截图：题面混进学号、页码等无关文字时，模型能自己看图找题干，更准；" +
                        "每次多一张图的 Token。截屏走无障碍 takeScreenshot，不弹投屏授权、状态栏无红标。"
                } else {
                    "只发文字：单次请求最便宜。识别到的文字如果很乱（混进学号、通知栏），模型可能被带偏。"
                },
                fontSize = 11.5.sp,
                color = if (sendImage) pal.orange else pal.green,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "计价：输入 ¥${fmtPrice(host.priceInputPerM())} / 输出 ¥${fmtPrice(host.priceOutputPerM())} 每百万 token",
                    fontSize = 11.5.sp,
                    color = pal.muted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "修改",
                    fontSize = 11.5.sp,
                    color = pal.accent,
                    modifier = Modifier.clickable { priceOpen = true }
                )
            }
            if (priceOpen) {
                PriceDialog(host, onDismiss = { priceOpen = false })
            }

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(200)) + expandVertically(tween(240), expandFrom = Alignment.Top),
                exit = fadeOut(tween(130)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top)
            ) {
                Column {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = pal.line)
                Spacer(Modifier.height(12.dp))
                val saved = when {
                    masked.isEmpty() -> ""
                    showKey -> host.revealKey().ifEmpty { masked }
                    else -> masked
                }
                Text(
                    if (masked.isEmpty()) "尚未保存 API Key" else "已保存：$saved",
                    fontSize = 12.sp,
                    color = if (masked.isEmpty()) pal.red else pal.green
                )
                Spacer(Modifier.height(8.dp))
                PalTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = "粘贴 sk- 开头的 API Key",
                    masked = !showKey,
                    enabled = !running
                )
                Spacer(Modifier.height(10.dp))

                val hasKey = masked.isNotEmpty() || keyInput.isNotBlank()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KeyButton(
                        "保存",
                        enabled = !running,
                        fill = pal.accent,
                        ink = pal.accentInk,
                        modifier = Modifier.weight(1f)
                    ) {
                        host.saveKey(keyInput)
                        keyInput = ""
                        testResult = null
                        masked = host.maskedKey()
                    }
                    KeyButton(
                        if (testing) "测试中" else "测试连通",
                        enabled = !running && !testing && hasKey,
                        fill = pal.accentSoft,
                        ink = pal.title,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (keyInput.isNotBlank()) {
                            host.saveKey(keyInput)
                            keyInput = ""
                            masked = host.maskedKey()
                        }
                        testing = true
                        scope.launch {
                            testResult = host.testKey()
                            testing = false
                        }
                    }
                    KeyButton(
                        "清除",
                        enabled = !running && hasKey,
                        ink = pal.red,
                        modifier = Modifier.weight(1f)
                    ) {
                        keyInput = ""
                        host.clearKey()
                        testResult = null
                        masked = host.maskedKey()
                    }
                    KeyButton(
                        if (showKey) "隐藏" else "显示",
                        enabled = !running && hasKey,
                        ink = pal.muted,
                        modifier = Modifier.weight(1f)
                    ) {
                        val next = !showKey
                        showKey = next

                        keyInput = if (next) host.revealKey() else ""
                    }
                }
                testResult?.let { (ok, msg) ->
                    Text(
                        msg,
                        fontSize = 12.sp,
                        color = if (ok) pal.green else pal.red,
                        lineHeight = 18.sp
                    )
                }
                            }
            }
        }
        }

        SectionCard(
            4,
            "启动",
            help = if (shotMode)
                "点击后：系统弹出投屏授权框 → 选择「共享整个屏幕」→ 应用自动退回手机主屏幕，" +
                    "屏幕上出现圆形按钮。截屏模式下状态栏会有投屏指示，这是系统行为。\n\n" +
                    "投屏会话只在你点「识别」那一瞬间抓一帧，平时不采集画面。"
            else
                "点击后：直接退回手机主屏幕并出现圆形按钮——不会弹投屏授权框，状态栏也不会有投屏指示。\n\n" +
                    "无障碍模式下只在点「识别」那一瞬间读一次文字节点，平时不做任何监控。\n\n" +
                    "服务会以常驻通知的形式保活，用手机主屏返回键退出应用也不会停止；" +
                    "要停止就回到本页点「停止服务」。"
        ) {

            AnimatedVisibility(
                visible = running,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(80)) + shrinkVertically(tween(120, delayMillis = 80))
            ) {
                Column {
                    Text("服务正在运行", color = pal.green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))

                    PrimaryButton(
                        "停止服务",
                        modifier = Modifier.fillMaxWidth()
                    ) { host.stopSession(); localTick++ }
                }
            }
            AnimatedVisibility(
                visible = !running,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(80)) + shrinkVertically(tween(120, delayMillis = 80))
            ) {
            Column {
                val ready = overlay && (shotMode || a11y) &&
                (mode == Settings.MODE_LOCAL || masked.isNotEmpty())

                PrimaryButton(
                    if (probing) "检查 Key…" else "启动",
                    enabled = ready && !probing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (mode == Settings.MODE_LOCAL) {
                        host.startSession(); localTick++
                    } else {
                        probing = true
                        scope.launch {
                            val (ok, msg) = host.testKey()
                            probing = false
                            testResult = ok to msg
                            if (ok) { host.startSession(); localTick++ }
                        }
                    }
                }
                testResult?.let { (ok, msg) ->
                    if (!ok && mode != Settings.MODE_LOCAL) Hint("Key 不可用：$msg", pal.red)
                }
                if (!overlay) Hint("需要先授予悬浮窗权限", pal.red)
                if (!shotMode && !a11y) Hint("需要先开启无障碍服务", pal.red)
                if (mode != Settings.MODE_LOCAL && masked.isEmpty()) {
                    Hint("已开启 DeepSeek 辅助，需要先保存 API Key（关掉开关即可不需要）", pal.red)
                }
                Spacer(Modifier.height(4.dp))
                Hint("点完自动退回主屏，屏幕右侧出现圆形按钮。")
            }
            }
        }

        SectionCard(5, "悬浮控件怎么用") {
            UsageItem("圆形按钮", pal.orange,
                "平时只有一个浅色描边的小圆点。按一下展开方块按钮，再按一下收起；圆点、方块按钮、答案小窗三者联动移动。")
            UsageItem("选区", pal.orange,
                "拖出一块区域，识别只在这个范围内进行，广告和统计信息会被挡在外面。也可以点两下选对角；长按清除。")
            UsageItem("全屏", pal.orange,
                "一键把选区恢复成整屏。框错了又懒得重框时用它。")
            UsageItem("识别", pal.orange,
                "在选区内读一次文字。只在按下这一瞬间工作，平时不监控；结果先存起来，不自动弹窗。")
            UsageItem("显示 / 隐藏", pal.orange,
                "点「显示」展开答案小窗，文字变「隐藏」；再点收起。小窗可拖标题栏移动。收起方块按钮时小窗跟着消失，但状态会被记住，再展开会恢复。")
            UsageItem("手动", pal.orange,
                "弹出输入框自己输入或粘贴题干，点「搜索」后输入框收起、小窗直接显示这道题的结果。")
        }
    }
}

@Composable
internal fun UsageItem(title: String, color: Color, body: String) {
    val pal = LocalPal.current
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    Text(
        body,
        fontSize = 12.sp,
        color = pal.muted,
        lineHeight = 18.sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
    )
}

@Composable
internal fun ParamsTab(host: AppHost) {
    val pal = LocalPal.current
    var ocr by remember { mutableStateOf(host.ocrEnabled()) }
    var high by remember { mutableFloatStateOf(host.highThreshold()) }
    var low by remember { mutableFloatStateOf(host.lowThreshold()) }
    var topK by remember { mutableIntStateOf(host.topK()) }
    var alpha by remember { mutableFloatStateOf(host.barAlpha()) }
    var panelW by remember { mutableIntStateOf(host.panelWidthDp()) }
    var panelH by remember { mutableIntStateOf(host.panelHeightDp()) }

    val previewing = host.previewOpen()
    var cropTop by remember { mutableFloatStateOf(host.cropTop()) }
    var cropBottom by remember { mutableFloatStateOf(host.cropBottom()) }
    var regionTick by remember { mutableIntStateOf(0) }

    val running = host.serviceRunning()

    ScrollPane {
        if (running) LockBanner()
        SectionCard(
            0,
            "三档分流的两个阈值",
            locked = running,
            help = "一档：接近率 ≥ 阈值就直接用本地题库答案，0 Token。\n" +
                "二档：接近率 ≥ 下阈值时，只把文字发给模型做对齐（不发截图）。\n" +
                "低于二档：才发一张截图（detail=low，单图 ≤1024 token）。\n\n" +
                "除绝对阈值外还有一条「决断式命中」：接近率 ≥70% 且第二名不到它的一半时，" +
                "也直接采信本地答案。实测中 OCR 把选项行拼进题干、分数只有 79%、但第二名仅 17% 的情况，" +
                "就靠这条规则省下了 Token；而两道题咬得很紧时不会乱猜，会交给模型。\n\n" +
                "DeepSeek 辅助关闭时本页参数不生效，识别只走一档。"
        ) {

            when {
                host.searchMode() == com.banana.search.core.Settings.MODE_DEEPSEEK -> {
                    Text(
                        "当前是「纯 DeepSeek」模式：每题都问模型，本页的三档分流不生效。",
                        fontSize = 11.5.sp, color = pal.orange
                    )
                    Spacer(Modifier.height(6.dp))
                }
                host.searchMode() == com.banana.search.core.Settings.MODE_LOCAL -> {
                    Text("纯本地题库模式：只走一档，本页参数不生效", fontSize = 11.5.sp, color = pal.green)
                    Spacer(Modifier.height(6.dp))
                }
                !host.sendImageToModel() -> {
                    Text(
                        "「发送内容」是只发文字：低于二档也不会发截图，仍然只发文字。",
                        fontSize = 11.5.sp, color = pal.orange
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            ParamSlider(
                label = "一档 ≥ ${(high * 100).toInt()}%：直接用本地答案",
                value = high, range = 0.5f..1.0f
            ) { high = it; host.setHighThreshold(it) }
            ParamSlider(
                label = "二档 ≥ ${(low * 100).toInt()}%：只发文字给模型",
                value = low, range = 0.1f..0.9f
            ) { low = it; host.setLowThreshold(it) }
            Text(
                if (host.sendImageToModel()) "低于二档：才发一张截图（≤1024 token）"
                else "低于二档：本应发截图，但当前只发文字",
                fontSize = 12.5.sp, color = pal.text
            )
            Spacer(Modifier.height(10.dp))

            Text(
                when (host.searchMode()) {
                    com.banana.search.core.Settings.MODE_LOCAL -> buildString {
                        append("按当前设置实际走法（纯本地题库）：\n")
                        append("· 接近率 ≥ ${(high * 100).toInt()}% → 直接用题库答案，0 Token\n")
                        append("· 低于 ${(high * 100).toInt()}% → 只列出最接近的候选，不联网")
                    }
                    com.banana.search.core.Settings.MODE_DEEPSEEK -> buildString {
                        append("按当前设置实际走法（纯 DeepSeek）：\n")
                        append("· 题库不参与，每次都调用模型\n")
                        append("· 发送内容：")
                        if (host.sendImageToModel()) append("题干文字 + 一张截图")
                        else append("只有题干文字（不截图）")
                    }
                    else -> buildString {
                        append("按当前设置实际走法（混合模式）：\n")
                        append("· 接近率 ≥ ${(high * 100).toInt()}% → 直接用题库答案，0 Token\n")
                        append("· ${(low * 100).toInt()}% ~ ${(high * 100).toInt()}% → 把题干文字发给模型对齐\n")
                        append("· 低于 ${(low * 100).toInt()}% → ")
                        if (host.sendImageToModel()) append("发一张截图给模型看图（单图 ≤1024 token）")
                        else append("仍只发文字；不会截图")
                    }
                },
                fontSize = 11.5.sp, color = pal.muted, lineHeight = 18.sp
            )
        }

        SectionCard(
            1,
            "本地 OCR（仅截屏模式生效）",
            locked = running,
            help = "关掉后本地不再做 OCR：截屏模式下只能把截图交给模型看图，会更费 token；" +
                "无障碍模式下本项无意义（那本来就不用截屏）。"
        ) {
            Column {
                Text("启用离线 OCR（ML Kit 中文）", fontSize = 13.5.sp, color = pal.text)
                Text("仅截屏 OCR 模式生效", fontSize = 11.sp, color = pal.muted)
                Spacer(Modifier.height(10.dp))

                SegRow(
                    options = listOf("关闭", "开启"),
                    selected = if (ocr) 1 else 0,
                    enabled = !running
                ) { index -> ocr = index == 1; host.setOcrEnabled(ocr) }
            }
        }

        SectionCard(
            2,
            "识别选区",
            locked = running,
            help = "选区有两个入口，指的是同一块区域：\n\n" +
                "· 本页的「框选区域」——在应用里直接框，框完可以马上用下面的「查看选区状态」核对。\n" +
                "· 悬浮层的「选区」按钮——识别时随手改，同样写回这份设置。\n\n" +
                "把广告、统计信息、选项列表排除在外，能明显提高命中率——" +
                "整屏文字一起参与匹配时，无关内容会把接近率稀释掉。\n\n" +
                "「查看选区状态」会截一帧屏幕、只裁出选区那一块给你看，用来确认框的位置对不对。" +
                "截图只存在内存里，关掉弹窗立刻释放，不写任何文件。"
        ) {
            val revision = host.regionRevision()
            val spec = remember(revision, regionTick) { host.regionSpec() }
            Text(
                if (spec.isBlank()) "未设置——当前识别整屏" else "已设置（屏幕比例 $spec）",
                fontSize = 12.5.sp,
                color = if (spec.isBlank()) pal.muted else pal.green
            )
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryButton(
                    "框选区域",
                    enabled = !running,
                    modifier = Modifier.weight(1f)
                ) { host.selectRegion() }
                PrimaryButton(
                    "查看选区状态",
                    enabled = !running,
                    modifier = Modifier.weight(1f),

                    fill = pal.accentSoft
                ) {
                    host.requestRegionPreview()
                }
            }
            if (spec.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                GhostButton("清除选区", enabled = !running, modifier = Modifier.fillMaxWidth()) {
                    host.setRegionSpec("")
                    regionTick++
                    host.notifySettingsChanged()
                }
            }
        }

        SectionCard(
            3,
            "结果面板",
            locked = running,
            help = "展示候选数：结果小窗里列出的本地题库候选题条数，默认 3 条。\n\n" +
                "按钮透明度：圆形按钮与方块按钮的不透明度，调低可以减少对屏幕内容的遮挡。\n\n" +
                "框体宽度：答案小窗的宽度，默认 280dp。觉得框体太大就调小，阅读长题干时调大。\n\n" +
                "框体高度：答案小窗最多能有多高，默认 360dp（120–720）。内容矮时按内容显示，" +
                "超过这个高度才在框内滚动；所以调小只会裁剪超长内容，不会把短内容拉出空白。"
        ) {
            ParamSlider(
                label = "展示候选数：$topK 条",
                value = topK.toFloat(), range = 1f..5f, steps = 3,
                enabled = !running
            ) { topK = it.toInt(); host.setTopK(it.toInt()) }
            ParamSlider(
                label = "按钮透明度：${(alpha * 100).toInt()}%",
                value = alpha, range = 0.3f..1f,
                enabled = !running
            ) { alpha = it; host.setBarAlpha(it) }
            ParamSlider(
                label = "框体宽度：${panelW}dp",
                value = panelW.toFloat(), range = 220f..380f, steps = 7,
                enabled = !running
            ) { panelW = it.toInt(); host.setPanelWidth(it.toInt()) }
            ParamSlider(
                label = "框体高度：${panelH}dp",
                value = panelH.toFloat(), range = 120f..720f, steps = 24,
                enabled = !running
            ) { panelH = it.toInt(); host.setPanelHeight(it.toInt()) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryButton(
                    text = if (previewing) "关闭框体预览" else "框体预览",
                    enabled = !running,
                    fill = LocalPal.current.accentSoft,
                    ink = LocalPal.current.title,
                    modifier = Modifier.weight(1f)
                ) { host.setPreviewOpen(!previewing) }
                GhostButton(text = "看一次尺寸", enabled = !running, modifier = Modifier.weight(1f)) {
                    host.toast("当前框体：${panelW}dp × ${panelH}dp（内容超出高度时框内滚动）")
                }
            }
            Text(
                text = if (previewing) {
                    "预览中：屏幕上只显示一个空框，按真实宽高。先关掉预览才能启动服务。"
                } else {
                    "框体预览：按上面的宽高在屏幕上放一个空框，先量尺寸再启动。"
                },
                fontSize = 11.sp,
                color = if (previewing) LocalPal.current.orange else LocalPal.current.muted
            )
        }

        SectionCard(
            4,
            "题干裁剪区域（屏幕高度百分比）",
            help = "这是最容易踩的坑：长题干被下边界切掉后，接近率会明显下降，" +
                "本该零 Token 的一档会掉进二档甚至三档。识别不准时先把下边界调大。\n\n" +
                "悬浮控件默认停在 87% 处，位于裁剪区之外，不会被截进去。"
        ) {
            ParamSlider(
                label = "上边界：${(cropTop * 100).toInt()}%",
                value = cropTop, range = 0f..0.4f
            ) { cropTop = it; host.setCropTop(it) }
            ParamSlider(
                label = "下边界：${(cropBottom * 100).toInt()}%",
                value = cropBottom, range = 0.3f..1f
            ) { cropBottom = it; host.setCropBottom(it) }
        }
    }

    val preview = host.regionPreview()
    if (preview != null) {
        InfoDialog(title = "选区状态（截图校验）", onDismiss = { host.clearRegionPreview() }) {
            Image(
                bitmap = preview.crop.asImageBitmap(),
                contentDescription = "选区截图",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(pal.surfaceSoft)
                    .border(1.dp, pal.line, RoundedCornerShape(7.dp))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "这就是识别时会取的那一块：${preview.cropW}×${preview.cropH} 像素",
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = pal.title
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "选区：${preview.region.left},${preview.region.top} → " +
                    "${preview.region.right},${preview.region.bottom}" +
                    "（屏幕 ${preview.screenW}×${preview.screenH}）",
                fontSize = 12.sp, color = pal.muted, lineHeight = 18.sp
            )
            Text(
                "整屏截图：${preview.shotW}×${preview.shotH}",
                fontSize = 12.sp, color = pal.muted, lineHeight = 18.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "核对方式：这张图里应当只有题干和选项，没有状态栏、没有吸附工具栏、没有半行被切。" +
                    "如果和框的那块对不上，重新框一次即可。",
                fontSize = 12.sp, color = pal.text, lineHeight = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "截图只在内存里，点「知道了」立即释放，不写任何文件。",
                fontSize = 12.sp, color = pal.green, lineHeight = 18.sp
            )
        }
    }
}

@Composable
internal fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
    onChange: (Float) -> Unit
) {
    val pal = LocalPal.current
    val active = if (pal.isDark) pal.accent else Color(0xFFFFD96A)
    Text(label, fontSize = 12.5.sp, color = pal.text)
    Slider(
        value = value,
        onValueChange = { if (enabled) onChange(it) },
        enabled = enabled,
        valueRange = range,
        steps = steps,

        colors = SliderDefaults.colors(
            thumbColor = pal.accent,
            activeTrackColor = active,
            inactiveTrackColor = pal.track,
            disabledThumbColor = pal.accent,
            disabledActiveTrackColor = active,
            disabledInactiveTrackColor = pal.track
        )
    )
}

@Composable
internal fun AboutTab(host: AppHost, tick: Int) {
    val pal = LocalPal.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf("") }
    var last by remember { mutableStateOf(host.lastSummary()) }
    var openLog by remember { mutableStateOf(false) }
    var openGithubConfirm by remember { mutableStateOf(false) }
    var openState by remember { mutableStateOf(false) }
    var openHistory by remember { mutableStateOf(false) }

    var privacyTick by remember { mutableIntStateOf(0) }
    val running = host.serviceRunning()

    LaunchedEffect(tick) {
        log = host.logDump()
        last = host.lastSummary()
        if (sample.isBlank()) sample = host.selfTestSample()
    }

    ScrollPane {
        SectionCard(
            0,
            "识别链路自检",
            help = "把下面这道题留在屏幕前台，点「识别」再点「显示」：结果面板给出接近率接近 100% 的同一道题时，" +
                "说明「取词 → 归一化 → 题库检索」这条本地链路是通的——无障碍模式读到的是精确文字，" +
                "接近率通常就是满分。\n\n" +
                "这一档不联网、不消耗任何 Token；把 DeepSeek 关掉也照样能自检。"
        ) {
            val empty = remember(tick) { host.bankSize() == 0 }
            if (empty) {
                Text(
                    "题库是空的，先去「题库」页导入一份，再回来做自检。",
                    fontSize = 12.5.sp,
                    color = pal.orange,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(8.dp))
                Hint("导入后这里会自动出现一道样例题，用来验证「取词 → 归一化 → 检索」这条本地链路。")
                Spacer(Modifier.height(2.dp))
            } else {
                Text(
                    "把下面这道题留在前台，点「识别」再点「显示」，接近率应接近 100%。",
                    fontSize = 12.sp,
                    color = pal.muted
                )
                Spacer(Modifier.height(10.dp))
            }

            if (!empty) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.5.dp))
                        .background(pal.surfaceSoft)
                        .border(1.dp, pal.line, RoundedCornerShape(7.5.dp))
                        .padding(12.dp)
                ) {
                    val lines = sample.ifBlank { "题库还没载入完成，稍后再试" }
                        .split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    lines.forEach { line ->
                        Text(line, fontSize = 16.sp, color = pal.title, lineHeight = 24.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            PrimaryButton("换一道题", enabled = !empty, modifier = Modifier.fillMaxWidth()) {
                val t0 = android.os.SystemClock.uptimeMillis()
                sample = host.selfTestSample()
                com.banana.search.core.AppLog.i(
                    "SelfTest",
                    "换一道题耗时 ${android.os.SystemClock.uptimeMillis() - t0}ms"
                )
            }
        }

        SectionCard(
            1,
            "运行日志与状态",
            help = "运行日志：应用最近的事件记录，排查问题时可复制出来。\n\n" +
                "识别状态：内置题库题量、自检结果、最近一次识别的结论。"
        ) {
            Column(Modifier.fillMaxWidth()) {
                AboutRow("运行日志", "最近的应用事件，可复制", showDivider = true) {
                    log = host.logDump(); last = host.lastSummary(); openLog = true
                }
                AboutRow("识别状态", "题库题量、自检与最近识别", showDivider = true) {
                    last = host.lastSummary(); openState = true
                }
                AboutRow("识别历史", "最近 50 次识别记录，可清空", showDivider = false) {
                    openHistory = true
                }
            }
        }

        SectionCard(
            2,
            "隐私",
            locked = running,
            help = "开启后，悬浮工具条、圆形按钮、答案小窗、手动输入框、选区遮罩和所有弹窗都会加 FLAG_SECURE：\n" +
                "系统截图、录屏、投屏里这些窗口会被系统直接涂黑，别人看不到你在搜什么。\n\n" +
                "代价是自己用手机截图也拍不到这几块窗口；但自己用「截屏 OCR」时，程序会在抓帧前先把悬浮层藏起来，\n" +
                "所以识别不受影响。\n\n" +
                "默认开启。关掉之后这些窗口就能被正常截图和录制。"
        ) {
            val blocked = remember(privacyTick, tick) { host.blockCapture() }
            SegRow(
                options = listOf("关闭", "开启"),
                selected = if (blocked) 1 else 0,

                enabled = !running
            ) { idx ->
                host.setBlockCapture(idx == 1)
                privacyTick++
            }
            Spacer(Modifier.height(6.dp))
            if (blocked) {
                Hint("已开启：录屏、截图里悬浮层会被系统遮挡", pal.green)
            } else {
                Hint("已关闭：悬浮层可以被截图和录制", pal.red)
            }
        }

        SectionCard(3, "关于这个程序") {
            AboutInfoRow("作者", "insulin03 - 【胰岛素】")
            Spacer(Modifier.height(10.dp))
            AboutInfoRow(
                label = "GitHub 页",
                value = "https://github.com/insulin03",
                link = true
            ) { openGithubConfirm = true }
            Spacer(Modifier.height(10.dp))
            AboutInfoRow(
                label = "使用边界",
                value = "本项目仅支持个人学习使用。严禁用于考试作弊、代考、商业售卖、" +
                    "二次打包分发，或以任何其他不正当手段使用本程序。" +
                    "因使用本程序产生的一切后果，由使用者自行承担。"
            )
            Spacer(Modifier.height(10.dp))
            AboutInfoRow(
                label = "开源许可",
                value = "Apache-2.0。题库内容由使用者自行提供，与项目作者无关。"
            )
            Spacer(Modifier.height(10.dp))
            AboutInfoRow("版本号", "v" + BuildConfig.VERSION_NAME)
        }
    }

    if (openGithubConfirm) {

        InfoDialog(title = "打开 GitHub 主页？", onDismiss = { openGithubConfirm = false }, showConfirm = false) {
            Text(
                "即将离开本应用，用浏览器打开\nhttps://github.com/insulin03",
                fontSize = 13.sp,
                color = pal.text,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("取消") { openGithubConfirm = false }
                PrimaryButton("前往") {
                    openGithubConfirm = false
                    val ctx = host as? Activity ?: return@PrimaryButton
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/insulin03"))
                        )
                    }
                }
            }
        }
    }

    if (openLog) {
        InfoDialog(title = "运行日志", onDismiss = { openLog = false }) {
            Row {
                GhostButton("刷新") { log = host.logDump() }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { host.clearLog(); log = host.logDump() }) {
                    Text("清空", fontSize = 12.sp, color = pal.red)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                log.ifBlank { "暂无日志" },
                fontSize = 10.sp,
                color = pal.muted,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.5.dp))
                    .background(pal.surfaceSoft)
                    .padding(8.dp)
            )
        }
    }

    if (openHistory) {
        HistoryDialog(host, onDismiss = { openHistory = false })
    }

    if (openState) {
        InfoDialog(title = "识别状态", onDismiss = { openState = false }) {
            Text("内置题库：${host.bankSize()} 题", fontSize = 14.sp, color = pal.title)
            Text(host.selfCheckSummary(), fontSize = 12.sp, color = pal.muted)
            Spacer(Modifier.height(10.dp))
            Text("最近一次识别", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = pal.title)
            Text(last.ifBlank { "还没有识别记录" }, fontSize = 12.sp, color = pal.muted)
        }
    }

}

internal data class DupHit(
    val question: String,
    val batchId: String,
    val batchName: String,
    val answerText: String,

    val importedAt: Long = 0L,
    val index: Int = 0
)

internal fun fmtTime(ts: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(ts))

@Composable
internal fun BankTab(host: AppHost, tick: Int) {
    val pal = LocalPal.current
    var revision by remember { mutableIntStateOf(0) }
    val batches = rememberBankBatches(host, tick, revision)
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pasteOpen by remember { mutableStateOf(false) }
    var specOpen by remember { mutableStateOf(false) }
    var dedupeOpen by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BankBatch?>(null) }
    var confirm by remember { mutableStateOf<BankBatch?>(null) }
    var bulkConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var bankQuery by remember { mutableStateOf("") }
    var searchPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(bankQuery) { searchPage = 0 }

    LaunchedEffect(tick, revision) {
        host.takeBankMessage()?.let { message = it }
        val alive = batches.map { it.id }.toSet()
        val kept = selected.filter { it in alive }.toSet()
        if (kept.size != selected.size) selected = kept
    }

    ScrollPane {
        SectionCard(
            0,
            "导入题库",
            help = "题库文件支持 .txt / .docx / .xlsx / .csv / .json，可以一次选多个文件，每个文件单独成为一批。\n\n" +
                "导入只读取文件内容，不会修改原文件；解析失败的题目会被跳过，并在结果里告诉你是第几段、什么原因。"
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("选择文件", modifier = Modifier.weight(1f)) { host.pickBankFiles() }
                GhostButton("粘贴文本", modifier = Modifier.weight(1f)) { pasteOpen = true }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("书写规范", modifier = Modifier.weight(1f)) { specOpen = true }
                GhostButton("保存模板", modifier = Modifier.weight(1f)) { host.saveBankTemplates() }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("导出题库备份", modifier = Modifier.fillMaxWidth()) { host.exportBank() }
            Spacer(Modifier.height(8.dp))
            Hint("按批次管理：一次导入 = 一批，可单独启停、预览、删除。导出会生成一个 JSON 文件，换手机后直接当题库导入即可还原批次。")
        }

        SectionCard(
            2,
            "搜索题目",
            help = "在所有已导入的批次里按题干搜索，不区分大小写与全半角。\n\n" +
                "输入即筛；右侧批次名告诉你这道题是从哪一批来的，点「删除」只删这一条。\n\n" +
                "只搜某一批时，进那一批的「预览」，那里有自己的搜索框。"
        ) {
            PalTextField(
                value = bankQuery,
                onValueChange = { bankQuery = it },
                placeholder = "搜索题干关键词，空着不显示结果"
            )
            val keyword = bankQuery.trim()
            if (keyword.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Hint("输入关键词后，这里会列出所有批次里的命中题目。")
            } else {

                val hits = rememberBankSearch(host, batches, keyword)
                Spacer(Modifier.height(8.dp))
                Text("命中 ${hits.size} 题", fontSize = 12.sp, color = pal.muted)
                Spacer(Modifier.height(6.dp))
                val pageSize = 10
                val pages = maxOf(1, (hits.size + pageSize - 1) / pageSize)
                if (searchPage > pages - 1) searchPage = pages - 1
                val slice = hits.drop(searchPage * pageSize).take(pageSize)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(pal.surfaceSoft)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("批次", Modifier.width(78.dp), fontSize = 11.sp, color = pal.muted)
                    Text("题干", Modifier.weight(1.3f).padding(end = 8.dp), fontSize = 11.sp, color = pal.muted)
                    Text("答案", Modifier.weight(1f).padding(end = 6.dp), fontSize = 11.sp, color = pal.muted)
                    Text("", Modifier.width(40.dp))
                }
                HorizontalDivider(color = pal.line, thickness = 1.dp)
                slice.forEach { (b, q) ->
                    val type = com.banana.search.core.QuestionTypes.infer(q.question, q.type, q.answers)
                    val answer = com.banana.search.core.QuestionTypes.answerText(type, q.answers)
                        .removePrefix("参考答案：").removePrefix("参考答案:")
                        .removePrefix("答案：").removePrefix("答案:").trim()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            b.name,
                            Modifier.width(78.dp),
                            fontSize = 10.5.sp,
                            color = pal.muted,
                            maxLines = 1
                        )
                        Text(
                            q.question,
                            Modifier.weight(1.3f).padding(end = 8.dp),
                            fontSize = 12.sp,
                            color = pal.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            answer,
                            Modifier.weight(1f).padding(end = 6.dp),
                            fontSize = 12.sp,
                            color = pal.green,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "删除",
                            Modifier
                                .width(40.dp)
                                .pressable {
                                    host.deleteBankQuestionByText(b.id, q.question)
                                    revision++
                                },
                            fontSize = 11.sp,
                            color = pal.red,
                            textAlign = TextAlign.Center
                        )
                    }
                    HorizontalDivider(color = pal.line.copy(alpha = 0.5f), thickness = 1.dp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("上一页", enabled = searchPage > 0, modifier = Modifier.weight(1f)) {
                        if (searchPage > 0) searchPage--
                    }
                    GhostButton("下一页", enabled = searchPage < pages - 1, modifier = Modifier.weight(1f)) {
                        if (searchPage < pages - 1) searchPage++
                    }
                }
                Spacer(Modifier.height(6.dp))
                Hint("第 ${searchPage + 1} / $pages 页，每页 10 条。")
            }
        }

        SectionCard(
            3,
            "已导入题库",
            help = "每次导入生成一批。停用的批次不参与识别匹配，但数据保留。\n\n" +
                "删除按批次进行；批量删除可以一次清掉多批。删掉之后识别立刻只剩剩下的题。"
        ) {
            if (batches.isEmpty()) {
                Text("还没有导入任何题库。", fontSize = 13.sp, color = pal.title)
                Spacer(Modifier.height(6.dp))
                Hint("导入后，识别会在这批题目里找答案；题库为空时不消耗任何 Token。")
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val onBatches = batches.count { it.enabled }
                    val text = buildString {
                        append("${batches.size} 批 · ${batches.sumOf { it.count }} 题")

                        if (onBatches != batches.size) {
                            append("（启用 $onBatches 批 · ${batches.filter { it.enabled }.sumOf { it.count }} 题）")
                        }
                    }
                    Text(
                        text,
                        fontSize = 12.sp,
                        color = pal.muted,
                        modifier = Modifier.weight(1f)
                    )
                    GhostButton(if (selecting) "取消选择" else "批量删除") {
                        selecting = !selecting
                        if (!selecting) selected = emptySet()
                    }
                }
                Spacer(Modifier.height(8.dp))
                batches.forEach { b ->
                    BatchRow(
                        batch = b,
                        selectable = selecting,
                        checked = b.id in selected,
                        onToggleCheck = {
                            selected = if (b.id in selected) selected - b.id else selected + b.id
                        },
                        onToggleEnabled = {
                            host.setBankBatchEnabled(b.id, !b.enabled)
                            revision++
                        },
                        onPreview = { preview = b },
                        onDelete = { confirm = b }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (selecting) {
                    PrimaryButton("删除所选（${selected.size}）", enabled = selected.isNotEmpty()) {

                        bulkConfirm = true
                    }
                }
            }
        }

        SectionCard(
            2,
            "查重",
            help = "按题干归一化（去标点、去空格、统一全角）之后比对，完全相同才算重复。\n\n" +
                "重复项由你自己决定删哪一条——程序不替你选，避免误删掉写得更好的那一条。"
        ) {
            GhostButton("扫描重复题目", modifier = Modifier.fillMaxWidth()) { dedupeOpen = true }
            Spacer(Modifier.height(6.dp))
            Hint("多批题库拼在一起时最容易出现重复。")
        }
    }

    if (pasteOpen) {
        PasteBankDialog(
            onDismiss = { pasteOpen = false },
            onImport = { text ->
                host.importBankText(text)
                revision++
            }
        )
    }
    if (specOpen) SpecDialog { specOpen = false }
    if (dedupeOpen) {
        DedupeDialog(
            host = host,
            onChanged = { revision++ },
            onDismiss = { dedupeOpen = false }
        )
    }
    preview?.let { b -> PreviewDialog(host, b) { preview = null } }
    if (bulkConfirm) {
        val chosen = batches.filter { it.id in selected }
        InfoDialog("删除所选题库？", onDismiss = { bulkConfirm = false }) {
            Text(
                "将删除 ${chosen.size} 批、共 ${chosen.sumOf { it.count }} 题，删掉之后不能恢复。",
                fontSize = 13.sp, color = pal.text
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("确认删除", modifier = Modifier.fillMaxWidth()) {
                host.deleteBankBatches(selected.toList())
                selected = emptySet()
                selecting = false
                bulkConfirm = false
                revision++
            }
        }
    }
    confirm?.let { b ->
        InfoDialog("删除这批题库？", onDismiss = { confirm = null }) {
            Text("「${b.name}」共 ${b.count} 题，删掉之后不能恢复。", fontSize = 13.sp, color = pal.text)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("确认删除", modifier = Modifier.fillMaxWidth()) {
                host.deleteBankBatches(listOf(b.id))
                confirm = null
                revision++
            }
        }
    }
    message?.let { msg ->
        InfoDialog("导入结果", onDismiss = { message = null }) {
            Text(msg, fontSize = 12.5.sp, color = pal.text, lineHeight = 19.sp)
        }
    }
}

@Composable
internal fun BatchRow(
    batch: BankBatch,
    selectable: Boolean,
    checked: Boolean,
    onToggleCheck: () -> Unit,
    onToggleEnabled: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    val pal = LocalPal.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.5.dp))
            .background(pal.surfaceSoft)
            .border(1.dp, pal.line, RoundedCornerShape(7.5.dp))
            .padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (selectable) {
                Text(
                    if (checked) "●" else "○",
                    fontSize = 15.sp,
                    color = if (checked) pal.accent else pal.muted,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { onToggleCheck() }
                )
            }
            Column(Modifier.weight(1f)) {
                Text(batch.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.title)
                Text(
                    "${batch.count} 题 · ${fmtTime(batch.importedAt)}" +
                        if (batch.enabled) "" else " · 已停用",
                    fontSize = 11.sp,
                    color = if (batch.enabled) pal.muted else pal.orange
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostButton(if (batch.enabled) "停用" else "启用", modifier = Modifier.weight(1f)) { onToggleEnabled() }
            GhostButton("预览", modifier = Modifier.weight(1f)) { onPreview() }
            GhostButton("删除", color = pal.red, modifier = Modifier.weight(1f)) { onDelete() }
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    link: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val pal = LocalPal.current
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick) else Modifier)
    ) {
        Text(label, fontSize = 12.sp, color = pal.muted)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 13.sp,
            color = if (link) pal.accent else pal.title,
            lineHeight = 20.sp,
            fontWeight = if (link) FontWeight.Medium else FontWeight.Normal
        )
    }
}
