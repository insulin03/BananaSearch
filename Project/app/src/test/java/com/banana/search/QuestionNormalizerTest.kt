package com.banana.search

import com.banana.search.core.QuestionNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionNormalizerTest {

    @Test
    fun 去掉序号与题型标注() {
        assertEquals(
            "我校计算机科学与技术专业授予学位是",
            QuestionNormalizer.normalize("3. 【单选题】我校计算机科学与技术专业授予学位是( )？")
        )
    }

    @Test
    fun 全角半角统一后仍能对齐() {
        val a = QuestionNormalizer.normalize("ＡＢＣ　１２３")
        val b = QuestionNormalizer.normalize("ABC 123")
        assertEquals(a, b)
    }

    @Test
    fun 空白与标点全部丢弃() {
        assertEquals("题干内容", QuestionNormalizer.normalize("题 干， 内 容。"))
    }

    @Test
    fun 大小写不敏感() {
        assertEquals(
            QuestionNormalizer.normalize("CPU"),
            QuestionNormalizer.normalize("cpu")
        )
    }

    @Test
    fun 空输入返回空串() {
        assertEquals("", QuestionNormalizer.normalize(null))
        assertEquals("", QuestionNormalizer.normalize(""))
    }

    @Test
    fun 统计空括号数量() {
        assertEquals(1, QuestionNormalizer.countBlanks("下列说法正确的是( )"))
        assertEquals(2, QuestionNormalizer.countBlanks("____ 和 （ ）"))
        assertEquals(0, QuestionNormalizer.countBlanks("没有空括号的题干"))
    }
}
