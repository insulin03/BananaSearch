package com.banana.search

import com.banana.search.core.QuestionType
import com.banana.search.core.QuestionTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionTypeTest {

    @Test
    fun 题库标了choice就是单选或判断() {
        assertEquals(QuestionType.SINGLE, QuestionTypes.infer("我校计算机科学与技术专业授予学位是( )。", "choice"))
        assertEquals(QuestionType.JUDGE, QuestionTypes.infer("学好专业课就可以了。A．对  B．错", "choice"))
    }

    @Test
    fun 题库标了qa就是简答() {
        assertEquals(
            QuestionType.QA,
            QuestionTypes.infer("请简述计算机科学与技术专业的培养目标。", "qa", listOf("培养目标……"))
        )
    }

    @Test
    fun 题干自带标注优先于题库() {
        assertEquals(QuestionType.MULTI, QuestionTypes.infer("下列说法正确的有（多选题）", "choice"))
        assertEquals(QuestionType.QA, QuestionTypes.infer("简述题：培养目标（简答题）", "choice"))
    }

    @Test
    fun 没有题库类型时靠文本特征() {
        val withOptions = "下列说法正确的是\nA．甲\nB．乙\nC．丙\nD．丁"
        assertEquals(QuestionType.SINGLE, QuestionTypes.infer(withOptions, null))
        assertEquals(QuestionType.JUDGE, QuestionTypes.infer("这个说法是否正确？正确/错误", null))
        assertEquals(QuestionType.FILL, QuestionTypes.infer("计算机科学与技术专业隶属____学院", null))
        assertEquals(QuestionType.QA, QuestionTypes.infer("谈谈你对专业的理解", null, listOf("第一段", "第二段")))
        assertEquals(QuestionType.UNKNOWN, QuestionTypes.infer("谈谈你对专业的理解", null, emptyList()))
    }

    @Test
    fun 简答题答案按段落换行而不是排成选项() {
        val text = QuestionTypes.answerText(QuestionType.QA, listOf("要点一", "要点二"))
        assertEquals("参考答案：要点一\n要点二", text)
    }

    @Test
    fun 多选题答案用顿号连接() {
        assertEquals("答案：A、C", QuestionTypes.answerText(QuestionType.MULTI, listOf("A", "C")))
        assertEquals("答案：工学学士", QuestionTypes.answerText(QuestionType.SINGLE, listOf("工学学士")))
    }

    @Test
    fun 空答案不产生前缀() {
        assertEquals("", QuestionTypes.answerText(QuestionType.QA, emptyList()))
    }
}
