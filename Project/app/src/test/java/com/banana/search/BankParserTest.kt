package com.banana.search

import com.banana.search.core.BankParser
import com.banana.search.core.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankParserTest {

    @Test
    fun `单选题把答案字母换成选项原文`() {
        val text = """
            题目：下列哪个是可执行文件的后缀？
            A. .txt
            B. .apk
            C. .jpg
            D. .mp3
            答案：B
        """.trimIndent()
        val r = BankParser.parseText(text)
        assertEquals(1, r.questions.size)
        val q = r.questions.first()
        assertEquals(QuestionType.SINGLE, q.type)
        assertEquals(4, q.options.size)

        assertEquals(listOf(".apk"), q.answers)
    }

    @Test
    fun `多选题按顿号保留每一条原文`() {
        val text = """
            题目：以下哪些属于操作系统？
            A. Windows
            B. Word
            C. Linux
            D. Excel
            答案：AC
        """.trimIndent()
        val q = BankParser.parseText(text).questions.first()
        assertEquals(QuestionType.MULTI, q.type)
        assertEquals(listOf("Windows", "Linux"), q.answers)
    }

    @Test
    fun `判断题把对错归一`() {
        val text = """
            题目：安卓是一个开放源代码的移动操作系统。
            答案：正确
        """.trimIndent()
        val q = BankParser.parseText(text).questions.first()
        assertEquals(QuestionType.JUDGE, q.type)
        assertEquals(listOf("对"), q.answers)
    }

    @Test
    fun `简答题整段保留`() {
        val text = """
            题目：简述什么是多态。
            答案：同一操作作用于不同对象，可以有不同的解释和执行结果。
        """.trimIndent()
        val q = BankParser.parseText(text).questions.first()
        assertEquals(QuestionType.QA, q.type)
        assertTrue(q.answers.first().contains("不同的解释"))
    }

    @Test
    fun `缺答案的题会被跳过并报错`() {
        val text = """
            题目：这道题没有答案
            A. 是
            B. 否

            题目：这道题是好的
            答案：是
        """.trimIndent()
        val r = BankParser.parseText(text)
        assertEquals(1, r.questions.size)
        assertEquals(1, r.errors.size)
        assertTrue(r.errors.first().reason.contains("缺少答案"))
    }

    @Test
    fun `空题干会被跳过`() {
        val text = """
            答案：孤立的一句答案
        """.trimIndent()
        val r = BankParser.parseText(text)
        assertEquals(0, r.questions.size)
        assertEquals(1, r.errors.size)
    }

    @Test
    fun `表格按列头识别题干与答案`() {
        val headers = listOf("题目", "A", "B", "C", "D", "答案")
        val rows = listOf(
            listOf("1 + 1 等于几？", "1", "2", "3", "4", "B"),
            listOf("太阳从哪边升起？", "东", "西", "南", "北", "A")
        )
        val r = BankParser.parseTable(headers, rows)
        assertEquals(2, r.questions.size)
        assertEquals(listOf("2"), r.questions[0].answers)
        assertEquals(listOf("东"), r.questions[1].answers)
        assertEquals(QuestionType.SINGLE, r.questions[0].type)
    }

    @Test
    fun `JSON 数据集支持别名与数组答案`() {
        val text = """
            [
              {"question": "以下哪些是浏览器？", "options": ["Chrome", "Linux", "Firefox"], "answer": ["A", "C"]},
              {"q": "判断题：地球是圆的。", "key": "对"}
            ]
        """.trimIndent()
        val r = BankParser.parseJson(text)
        assertEquals(2, r.questions.size)
        assertEquals(listOf("Chrome", "Firefox"), r.questions[0].answers)
        assertEquals(QuestionType.MULTI, r.questions[0].type)
        assertEquals(listOf("对"), r.questions[1].answers)
    }

    @Test
    fun `显式题型标注优先于自动判断`() {
        val text = """
            题目：以下说法正确的是
            题型：多选
            A. 甲
            B. 乙
            答案：AB
        """.trimIndent()
        val q = BankParser.parseText(text).questions.first()
        assertEquals(QuestionType.MULTI, q.type)
    }

    @Test
    fun `单选项列用竖线分隔时能拆成多个选项`() {
        val headers = listOf("题目", "选项", "答案")
        val rows = listOf(
            listOf("下列属于面向对象特性的有？", "封装 | 继承 | 多态 | 编译", "ABC"),
            listOf("下列哪个是值类型？", "int；string；bool", "A")
        )
        val r = BankParser.parseTable(headers, rows)
        assertEquals(2, r.questions.size)
        assertEquals(listOf("封装", "继承", "多态", "编译"), r.questions[0].options)
        assertEquals(listOf("封装", "继承", "多态"), r.questions[0].answers)
        assertEquals(QuestionType.MULTI, r.questions[0].type)
        assertEquals(listOf("int", "string", "bool"), r.questions[1].options)
        assertEquals(listOf("int"), r.questions[1].answers)
        assertEquals(QuestionType.SINGLE, r.questions[1].type)
    }
}
