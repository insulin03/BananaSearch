package com.banana.search.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.banana.search.core.QuestionNormalizer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banana.search.core.BankBatch

@Composable
internal fun PasteBankDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val pal = LocalPal.current
    var text by remember { mutableStateOf("") }
    InfoDialog("粘贴题库文本", onDismiss = onDismiss) {
        Text("按 txt 规范粘贴：每道题之间空一行，选项以 A. 开头，答案写「答案: B」。", fontSize = 12.sp, color = pal.muted)
        Spacer(Modifier.height(8.dp))
        PalTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "1. 题干…\nA. 选项一\nB. 选项二\n答案: B",
            singleLine = false,
            minHeight = 150.dp,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton("导入", enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            onImport(text)
            onDismiss()
        }
    }
}

@Composable
internal fun SpecDialog(onDismiss: () -> Unit) {
    val pal = LocalPal.current
    val body = """
        一、txt
        每道题之间空一行或用 --- 分隔。
        题干可直接写，也可写「题目:」开头；序号会被自动去掉。
        选项写成 A. / A、/ A．/ (A) 开头，A~H 最多 8 个。
        答案写「答案: B」「答案: ABC」（多选）「答案: 对」（判断）。
        「题型: 多选 / 判断 / 填空 / 简答」可以不写，程序会自动判断。
        「解析: …」可选。

        二、Excel / CSV
        第一行是表头，从第二行开始一行一题。
        必须有题干列（题目/题干/question）。
        选项列名写 A、B、C… 或 选项A…，没有选项的格子留空即可。
        必须有答案列（答案/answer）。可选题型列（单选/多选/判断/填空/简答）。

        三、Word
        按上面的 txt 规则一段一行书写；文档里的表格按 Excel 规则解析。

        四、JSON / JSONL
        {"question": "题干", "options": ["A","B"], "answer": "A", "type": "single"}
        也可以每行一个对象（jsonl）。

        五、答案显示规则
        选择题写字母，程序会替换成对应选项原文显示。
        多选答案用顿号连接。找不到对应选项时才显示字母本身。
    """.trimIndent()
    InfoDialog("题库书写规范", onDismiss = onDismiss) {
        Text(body, fontSize = 11.5.sp, color = pal.text, lineHeight = 18.sp)
    }
}

@Composable
internal fun PreviewDialog(host: AppHost, batch: BankBatch, onDismiss: () -> Unit) {
    val pal = LocalPal.current
    val allItems = rememberBankQuestions(host, batch.id)
    var search by remember { mutableStateOf("") }

    val items = remember(allItems, search) {
        if (search.isBlank()) allItems
        else {
            val key = com.banana.search.core.QuestionNormalizer.normalize(search)
            allItems.filter {
                com.banana.search.core.QuestionNormalizer.normalize(it.question).contains(key)
            }
        }
    }
    val pageSize = 10
    var page by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableIntStateOf(-1) }

    LaunchedEffect(search) { page = 0 }
    val pages = maxOf(1, (items.size + pageSize - 1) / pageSize)
    if (page > pages - 1) page = pages - 1
    val from = page * pageSize
    val slice = items.drop(from).take(pageSize)

    InfoDialog("预览 · ${batch.name}", onDismiss = onDismiss) {
        PalTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = "搜索这一批的题干",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (search.isBlank()) "共 ${items.size} 题" else "匹配 ${items.size} / ${allItems.size} 题",
                fontSize = 12.sp, color = pal.muted
            )
            Spacer(Modifier.weight(1f))
            Text("第 ${page + 1} / $pages 页", fontSize = 12.sp, color = pal.muted)
        }
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(pal.surfaceSoft)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("#", Modifier.width(26.dp), fontSize = 11.sp, color = pal.muted)
            Text("题型", Modifier.width(52.dp), fontSize = 11.sp, color = pal.muted)
            Text("题干", Modifier.weight(1.25f).padding(end = 8.dp), fontSize = 11.sp, color = pal.muted)
            Text("答案", Modifier.weight(1f), fontSize = 11.sp, color = pal.muted)
        }
        HorizontalDivider(color = pal.line, thickness = 1.dp)

        slice.forEachIndexed { i, q ->
            val index = from + i
            val type = com.banana.search.core.QuestionTypes.infer(q.question, q.type, q.answers)

            val answer = com.banana.search.core.QuestionTypes.answerText(type, q.answers)
                .removePrefix("参考答案：").removePrefix("参考答案:")
                .removePrefix("答案：").removePrefix("答案:").trim()
            val open = expanded == index
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .pressable { expanded = if (open) -1 else index }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 1}",
                        Modifier.width(26.dp),
                        fontSize = 11.5.sp,
                        color = pal.muted
                    )
                    Text(
                        type.label,
                        Modifier.width(52.dp),
                        fontSize = 11.5.sp,
                        color = if (type == com.banana.search.core.QuestionType.UNKNOWN) pal.muted else pal.orange
                    )
                    Text(
                        q.question,
                        Modifier.weight(1.25f).padding(end = 8.dp),
                        fontSize = 12.sp,
                        color = pal.title,
                        lineHeight = 17.sp,
                        maxLines = if (open) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        answer,
                        Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = pal.green,
                        lineHeight = 17.sp,
                        maxLines = if (open) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (open) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点这一行收起",
                        fontSize = 10.5.sp,
                        color = pal.muted
                    )
                }
            }
            HorizontalDivider(color = pal.line, thickness = 1.dp)
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("上一页", enabled = page > 0, modifier = Modifier.weight(1f)) {
                if (page > 0) {
                    page--
                    expanded = -1
                }
            }
            Spacer(Modifier.width(8.dp))
            GhostButton("下一页", enabled = page < pages - 1, modifier = Modifier.weight(1f)) {
                if (page < pages - 1) {
                    page++
                    expanded = -1
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Hint("题干和答案太长时只显示两行，点任意一行展开全文。每题 ${pageSize} 条一页。")
    }
}

@Composable
internal fun DedupeDialog(host: AppHost, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val pal = LocalPal.current
    var revision by remember { mutableIntStateOf(0) }

    val groups by produceState(emptyList<List<DupHit>>(), revision) {
        value = withContext(Dispatchers.IO) {
            val map = HashMap<String, MutableList<DupHit>>()
            for (b in host.bankBatches()) {
                host.bankQuestions(b.id).forEachIndexed { qi, q ->
                    val key = QuestionNormalizer.normalize(q.question)
                    map.getOrPut(key) { ArrayList() }
                        .add(DupHit(q.question, b.id, b.name, q.answers.joinToString("、"), b.importedAt, qi))
                }
            }
            map.filterValues { it.size > 1 }
                .values
                .map { hits -> hits.sortedWith(compareBy({ it.importedAt }, { it.index })) }
                .toList()
        }
    }

    fun keepOne(hits: List<DupHit>, keepFirst: Boolean) {
        val keep = if (keepFirst) hits.first() else hits.last()
        hits.filter { it !== keep }.forEach { host.deleteBankQuestionByText(it.batchId, it.question) }
        revision++
        onChanged()
    }

    InfoDialog("重复题目", onDismiss = onDismiss) {
        if (groups.isEmpty()) {
            Text("没有发现重复题目。", fontSize = 13.sp, color = pal.text)
        } else {
            Text("共 ${groups.size} 组重复。可以整组一键去重，也可以逐条点「删这条」：", fontSize = 12.sp, color = pal.muted)
            Spacer(Modifier.height(8.dp))
            groups.forEach { hits ->
                Text(hits.first().question, fontSize = 12.5.sp, color = pal.title, lineHeight = 19.sp)
                hits.forEach { hit ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${hit.batchName}：${hit.answerText}",
                            fontSize = 11.5.sp,
                            color = pal.muted,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "删这条",
                            fontSize = 12.sp,
                            color = pal.red,
                            modifier = Modifier.clickable {
                                host.deleteBankQuestionByText(hit.batchId, hit.question)
                                revision++
                                onChanged()
                            }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "保留最早，删其余 ${hits.size - 1} 条",
                        fontSize = 11.5.sp,
                        color = pal.accent,
                        modifier = Modifier.clickable { keepOne(hits, keepFirst = true) }
                    )
                    Text(
                        "保留最新，删其余 ${hits.size - 1} 条",
                        fontSize = 11.5.sp,
                        color = pal.accent,
                        modifier = Modifier.clickable { keepOne(hits, keepFirst = false) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = pal.line, thickness = 1.dp)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

internal fun fmtPrice(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
internal fun PriceDialog(host: AppHost, onDismiss: () -> Unit) {
    val pal = LocalPal.current
    var inputText by remember { mutableStateOf(fmtPrice(host.priceInputPerM())) }
    var outputText by remember { mutableStateOf(fmtPrice(host.priceOutputPerM())) }
    InfoDialog("用量计价", onDismiss = onDismiss) {
        Text(
            "单位：元 / 百万 token。默认值按 deepseek-chat 标准价（输入 2、输出 8）填的，" +
                "官方调价或换了模型就改这里。缓存命中的折扣没有单独算，面板上的金额是上界。",
            fontSize = 11.5.sp,
            color = pal.muted,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PalTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = "输入单价",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
            PalTextField(
                value = outputText,
                onValueChange = { outputText = it },
                label = "输出单价",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("保存", modifier = Modifier.fillMaxWidth()) {
            inputText.trim().toDoubleOrNull()?.let { host.setPriceInputPerM(it) }
            outputText.trim().toDoubleOrNull()?.let { host.setPriceOutputPerM(it) }
            onDismiss()
        }
    }
}

@Composable
internal fun HistoryDialog(host: AppHost, onDismiss: () -> Unit) {
    val pal = LocalPal.current
    var reload by remember { mutableIntStateOf(0) }
    val entries by produceState(emptyList<com.banana.search.core.RecognizeHistory.Entry>(), reload) {
        value = withContext(Dispatchers.IO) { host.history() }
    }
    InfoDialog(title = "识别历史", onDismiss = onDismiss) {
        if (entries.isEmpty()) {
            Text("还没有识别记录。", fontSize = 13.sp, color = pal.text)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${entries.size} 条（新的在最上面）", fontSize = 11.5.sp, color = pal.muted, modifier = Modifier.weight(1f))
                Text(
                    "清空",
                    fontSize = 11.5.sp,
                    color = pal.red,
                    modifier = Modifier.clickable {
                        host.clearHistory()
                        reload++
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            entries.forEach { e ->
                val time = remember(e.at) {
                    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(e.at))
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(
                        "$time · ${e.tier}" + if (e.tokens > 0) " · ${com.banana.search.core.UsageFormat.grouped(e.tokens)} token ≈ ${com.banana.search.core.UsageFormat.money(e.yuan)}" else "",
                        fontSize = 10.5.sp,
                        color = pal.muted
                    )
                    Text(e.question, fontSize = 12.sp, color = pal.title, lineHeight = 17.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (e.answer.isNotBlank()) {
                        Text(e.answer, fontSize = 12.sp, color = pal.green, lineHeight = 17.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = pal.line, thickness = 1.dp)
            }
        }
    }
}
