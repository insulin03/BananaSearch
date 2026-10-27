package com.banana.search

import com.banana.search.core.BankItem
import com.banana.search.core.QuestionNormalizer
import com.banana.search.core.Similarity
import com.banana.search.core.TierPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.ln

class SimilarityTest {

    private fun loadBank(): List<BankItem> {
        val candidates = listOf(
            File("src/test/resources/questionbank.json"),
            File("app/src/test/resources/questionbank.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("找不到 questionbank.json（测试资源），尝试过：${candidates.map { it.absolutePath }}")
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val arr = root.getJSONArray("items")
        val out = ArrayList<BankItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val q = o.getString("question").trim()
            val answers = ArrayList<String>()
            val a = o.getJSONArray("answers")
            for (j in 0 until a.length()) answers.add(a.getString(j).trim())
            out.add(
                BankItem(
                    id = o.getInt("id"),
                    question = q,
                    normalized = QuestionNormalizer.normalize(q),
                    answers = answers,
                    type = o.optString("type", "qa"),
                    blankCount = QuestionNormalizer.countBlanks(q)
                )
            )
        }
        return out
    }

    private fun buildIdf(bank: List<BankItem>): Map<String, Double> {
        val df = HashMap<String, Int>()
        for (item in bank) for (s in item.shingles) df[s] = (df[s] ?: 0) + 1
        val n = bank.size.toDouble()
        return df.mapValues { ln(1.0 + n / (1.0 + it.value)) }
    }

    private fun pick(bank: List<BankItem>, keyword: String): BankItem =
        bank.firstOrNull { it.question.contains(keyword) }
            ?: error("题库里找不到包含「$keyword」的题目，题库可能已更新，请同步测试素材")

    private fun synthetic(id: Int, q: String, answers: List<String>, type: String = "choice") =
        BankItem(
            id = id,
            question = q,
            normalized = QuestionNormalizer.normalize(q),
            answers = answers,
            type = type,
            blankCount = QuestionNormalizer.countBlanks(q)
        )

    @Test
    fun bankLoadsWithExpectedSize() {
        val bank = loadBank()
        assertEquals("题库条数应与解析结果一致", EXPECTED_ITEMS, bank.size)
        assertTrue("每题都必须有答案", bank.all { it.answers.isNotEmpty() })
        assertTrue("每题归一化后都不能为空", bank.all { it.normalized.isNotEmpty() })
        println("[题库] 载入 ${bank.size} 题；类型分布=" + bank.groupingBy { it.type }.eachCount())
    }

    @Test
    fun normalizationIsStableAndClean() {
        assertEquals("学校一共有多少个教学机构？", "学校一共有多少个教学机构", QuestionNormalizer.normalize("学校一共有多少个教学机构？"))
        assertEquals("空括号标记必须被删除", "学生宿舍不得留宿他人特殊情况要经过同意", QuestionNormalizer.normalize("学生宿舍不得留宿他人，特殊情况要经过（ ）同意。"))
        assertEquals("全角括号也要能删", "考试方式包括", QuestionNormalizer.normalize("考试方式包括()。"))
        assertEquals("空白与标点都不该残留", "学校校训是什么", QuestionNormalizer.normalize(" 学校 校训 是 什么 ? "))
        val bank = loadBank()
        for (item in bank) {
            assertTrue(
                "id=${item.id} 归一化后仍有空白：${item.normalized}",
                item.normalized.none { it.isWhitespace() }
            )
        }
    }

    @Test
    fun blankCountDetection() {
        assertEquals(0, QuestionNormalizer.countBlanks("学校一共有多少个教学机构？"))
        assertEquals(1, QuestionNormalizer.countBlanks("学生宿舍不得留宿他人，特殊情况要经过（ ）同意。"))
        assertEquals(2, QuestionNormalizer.countBlanks("年龄要求在（）以上、（）以下。"))
    }

    @Test
    fun everyQuestionMatchesItself() {
        val bank = loadBank()
        val idf = buildIdf(bank)
        var worst = Double.MAX_VALUE
        var worstId = -1
        val duplicates = mutableListOf<String>()
        for (item in bank) {
            val all = Similarity.rank(item.normalized, bank, idf, bank.size)
            val selfScore = all.firstOrNull { it.item.id == item.id }?.score ?: 0.0
            assertTrue("id=${item.id} 未能以 ≥0.95 的接近率命中自己，实际 $selfScore", selfScore >= 0.95)

            val top = all.first()
            if (top.item.id != item.id) {
                assertEquals(
                    "id=${item.id} 的 Top1 是 id=${top.item.id}，但两者题干归一化结果必须相同",
                    item.normalized, top.item.normalized
                )
                duplicates.add("id=${item.id} 与 id=${top.item.id} 题干相同（${top.percent}%）")
            }
            if (selfScore < worst) {
                worst = selfScore
                worstId = item.id
            }
        }
        println("[自匹配] ${bank.size} 题全部自命中，最低接近率 ${(worst * 100).toInt()}%（id=$worstId）")
        println("[自匹配] 题库内题干完全重复的条目：${if (duplicates.isEmpty()) "无" else duplicates.joinToString("；")}")
        assertTrue("自匹配最低分应 ≥ 0.95，实际 $worst", worst >= 0.95)
    }

    @Test
    fun toleratesOcrNoise() {
        val bank = loadBank()
        val idf = buildIdf(bank)

        val qDegree = pick(bank, "授予学位")
        val qCredit = pick(bank, "总学分")
        val qShort = pick(bank, "培养目标")

        data class Case(val item: BankItem, val query: String, val minPercent: Int, val note: String)

        val cases = listOf(
            Case(qDegree, qDegree.question, 100, "原样"),
            Case(qCredit, qCredit.question, 100, "原样"),
            Case(qDegree, qDegree.question.replace("授予", "授子"), 80, "OCR 错字"),
            Case(qCredit, qCredit.question.replace("2026版", "2026 版"), 100, "OCR 多了空格"),
            Case(qShort, qShort.question.removeSuffix("。"), 100, "句末标点丢失")
        )

        println("[抗噪] id\t接近率\t判定\t说明")
        for (c in cases) {
            val top = Similarity.rank(QuestionNormalizer.normalize(c.query), bank, idf, 2)
            val hit = top.any { it.item.id == c.item.id }
            val percent = top.firstOrNull { it.item.id == c.item.id }?.percent ?: 0
            println("[抗噪] ${c.item.id}\t$percent%\t${if (hit) "PASS" else "FAIL"}\t${c.note}（Top1=id${top.first().item.id} ${top.first().percent}%）")
            assertTrue("「${c.note}」的正确题目未进 Top2（Top1 是 id=${top.first().item.id}）", hit)
            assertTrue("「${c.note}」接近率 $percent% 低于预期下限 ${c.minPercent}%", percent >= c.minPercent)
        }
    }

    @Test
    fun threshold85Coverage() {
        val bank = loadBank()
        val idf = buildIdf(bank)
        var pass = 0
        for (item in bank) {
            val top = Similarity.rank(item.normalized, bank, idf, 1).first()
            if (top.score >= 0.85) pass++
        }
        val ratio = pass.toDouble() / bank.size
        println("[一档覆盖率] 接近率≥85% 的题目：$pass/${bank.size} = ${(ratio * 100).toInt()}%")
        assertTrue("一档覆盖率应 ≥ 95%，实际 ${(ratio * 100).toInt()}%", ratio >= 0.95)
    }

    @Test
    fun unrelatedQuestionsScoreLow() {
        val bank = loadBank()
        val idf = buildIdf(bank)
        val a = pick(bank, "授予学位")
        val b = pick(bank, "就业方向")
        val score = Similarity.rank(a.normalized, listOf(b), idf, 1).first().score
        println("[无关题] id${a.id}「${a.question.take(16)}」对 id${b.id}「${b.question.take(16)}」的接近率 = ${(score * 100).toInt()}%")
        assertTrue("两道完全不同的题不应有高相似度，实际 $score", score < 0.45)

        val alien = Similarity.rank(QuestionNormalizer.normalize("学校食堂几点开门？"), bank, idf, 1).first()
        println("[无关题] 「学校食堂几点开门？」Top1=id${alien.item.id} ${alien.percent}%")
        assertTrue("无关文本不该被判为一档", alien.score < 0.85)
    }

    @Test
    fun shortNoiseLinesAreFilteredOut() {
        val candidates = Similarity.candidateQueries(listOf("13:30", "5G", "启动", "复制", "寝室成员有人偷盗"))
        assertTrue("纯数字/信号行不该成为候选：$candidates", candidates.none { it == "13:30" || it == "5G" })
        assertTrue("含汉字的题干行应保留", candidates.any { it.contains("寝室成员有人偷盗") })
    }

    @Test
    fun noisyScreenStillFindsQuestion() {
        val bank = loadBank()
        val idf = buildIdf(bank)
        val target = pick(bank, "专业课之外")
        val lines = listOf(
            "13:30", "5G", "39",
            "模拟刷题页", "答题", "背题", "语音",
            "广告　找不到好玩的游戏？来 TapTap，超多高分游戏等你来玩",
            target.question,
            "A．正确", "B．错误",
            "本人作答：4次　正确率：25.0%", "全站作答：13454次　正确率：72.24%"
        )
        val whole = Similarity.rank(QuestionNormalizer.normalize(lines.joinToString("\n")), bank, idf, 1).first()
        val lineMatch = Similarity.rankLines(lines, bank, idf, 2)
        val lineTop = lineMatch.top.first()
        println("[整屏噪声] 整段匹配 Top1=id${whole.item.id} ${whole.percent}%")
        println("[整屏噪声] 行级匹配 Top1=id${lineTop.item.id} ${lineTop.percent}%  胜出候选=\"${lineMatch.query}\"")
        assertEquals("行级匹配必须命中 id=${target.id}", target.id, lineTop.item.id)
        assertTrue("行级匹配接近率应 ≥ 95%，实际 ${lineTop.percent}%", lineTop.percent >= 95)
        assertTrue(
            "行级匹配（${lineTop.percent}%）应优于整段匹配（${whole.percent}%）",
            lineTop.score > whole.score
        )
    }

    @Test
    fun wrappedQuestionLinesAreJoined() {
        val bank = loadBank()
        val idf = buildIdf(bank)
        val target = pick(bank, "学位必须达到的条件")
        val lines = listOf(
            "13:30", "模拟刷题页",
            "请简述获得模拟学位",
            "必须达到的条件。",
            "启动"
        )
        val match = Similarity.rankLines(lines, bank, idf, 2)
        val top = match.top.first()
        println("[换行题干] Top1=id${top.item.id} ${top.percent}%  胜出候选=\"${match.query}\"")
        assertEquals("应命中 id=${target.id}", target.id, top.item.id)
        assertTrue("拼接换行后应达到 99% 以上，实际 ${top.percent}%", top.percent >= 99)
    }

    @Test
    fun accessibilityTextMatchesNearPerfectly() {
        val bank = loadBank()
        val idf = buildIdf(bank)
        val target = pick(bank, "培养目标")
        val lines = listOf(
            "模拟练习页（测试数据）",
            "1. 请简述本课程的培养目标。（简答题）",
            "2. 完成本课程学习后可授予学位是（ ）。"
        )
        val match = Similarity.rankLines(lines, bank, idf, 2)
        val top = match.top.first()
        val second = match.top.getOrNull(1)?.score ?: 0.0
        println("[无障碍文字] Top1=id${top.item.id} ${top.percent}%  第二名=${(second * 100).toInt()}%  候选=\"${match.query}\"")
        assertEquals("应命中 id=${target.id}", target.id, top.item.id)
        assertTrue("去掉题号与题型标注后应达到 95% 以上，实际 ${top.percent}%", top.percent >= 95)
        assertTrue("且必须判定为一档", TierPolicy.isConfidentLocal(top.score, second, 0.85))
    }

    @Test
    fun leadingIndexStrippingIsSafe() {
        assertEquals("学生需要按照分配的宿舍及床位居住不得", QuestionNormalizer.normalize("1. 学生需要按照分配的宿舍及床位居住，不得（ ）。"))
        assertEquals("学生需要按照分配的宿舍及床位居住不得", QuestionNormalizer.normalize("12、学生需要按照分配的宿舍及床位居住，不得（ ）。"))
        assertEquals("学生需要按照分配的宿舍及床位居住不得", QuestionNormalizer.normalize("一、学生需要按照分配的宿舍及床位居住，不得（ ）。"))
        assertTrue(
            "小数开头的数字不应被当成题号删除",
            QuestionNormalizer.normalize("3.14是圆周率").startsWith("314")
        )
        assertEquals(
            "题型标注属于噪声，应被去掉",
            "考试方式包括",
            QuestionNormalizer.normalize("考试方式包括（ ）。（多选题）")
        )
    }

    @Test
    fun mergedOptionLineStillQualifiesAsConfidentLocal() {
        val bank = loadBank()
        val idf = buildIdf(bank)
        val target = pick(bank, "学位必须达到的条件")
        val ocrLine = "请简述获得模拟学位A. 结业证书必须达到的条件。"
        val lines = listOf("恐技因", "启动", "识别链路自检…", ocrLine, "关于")

        val match = Similarity.rankLines(lines, bank, idf, 2)
        val top = match.top.first()
        val second = match.top.getOrNull(1)?.score ?: 0.0
        println("[拼行+错字] Top1=id${top.item.id} ${top.percent}%  第二名=${(second * 100).toInt()}%  候选=\"${match.query}\"")

        assertEquals("应命中 id=${target.id}", target.id, top.item.id)

        assertTrue("拼进选项行后应达到 70% 以上，实际 ${top.percent}%", top.score >= 0.70)
        assertTrue(
            "赢家明确，应判定为一档：${TierPolicy.describe(top.score, second, 0.85)}",
            TierPolicy.isConfidentLocal(top.score, second, 0.85)
        )
    }

    @Test
    fun closeRaceIsNotTreatedAsConfident() {
        assertFalse("两个高分咬紧时不该判定为一档", TierPolicy.isConfidentLocal(0.75, 0.72, 0.85))
        assertFalse("全线低分时不该判定为一档", TierPolicy.isConfidentLocal(0.55, 0.10, 0.85))
        assertTrue("绝对阈值命中", TierPolicy.isConfidentLocal(0.90, 0.88, 0.85))
        assertTrue("决断式命中", TierPolicy.isConfidentLocal(0.79, 0.17, 0.85))
    }

    @Test
    fun nearDuplicateFamilyIsRankedCorrectly() {
        val family = (1..5).map { n ->
            synthetic(
                id = 900 + n,
                q = "计算机科学与技术专业第${n}学期开设的专业必修课总学分为( )。",
                answers = listOf("${n * 4} 学分")
            )
        }
        val filler = listOf(
            synthetic(1000, "学校食堂几点开门？", listOf("6:30")),
            synthetic(1001, "图书馆开放到几点？", listOf("22:00")),
            synthetic(1002, "宿舍熄灯时间是( )。", listOf("23:00")),
            synthetic(1003, "校医院的急诊电话是( )。", listOf("027-81652110"))
        )
        val bank = family + filler
        val idf = buildIdf(bank)

        var wrongAnswer = 0
        for (item in family) {
            val top = Similarity.rank(item.normalized, bank, idf, 1).first()
            val sameAnswer = top.item.answers == item.answers
            if (!sameAnswer) {
                wrongAnswer++
                println("[近同文] id=${item.id}「${item.question.take(24)}」命中 id=${top.item.id} 答案=${top.item.answers}（本题答案=${item.answers}）")
            }
        }
        println("[近同文] 该族 ${family.size} 题中，自匹配后被答错的：$wrongAnswer 条")

        assertTrue("近同文族混淆过多：$wrongAnswer/${family.size}", wrongAnswer <= family.size / 3)
    }

    private companion object {

        const val EXPECTED_ITEMS = 9
    }
}
