package com.banana.search.core

enum class QuestionType(val label: String) {
    SINGLE("单选题"),
    MULTI("多选题"),
    JUDGE("判断题"),
    FILL("填空题"),
    QA("简答题"),
    UNKNOWN("未知题型")
}

object QuestionTypes {

    private val EXPLICIT = Regex("[（(]\\s*(单选|多选|不定项|判断|填空|简答|论述|选择)\\s*题?\\s*[)）]")

    private val OPTION_HEAD = Regex("(?m)^\\s*[A-H][．.、:：]")

    private val JUDGE_PAIR = Regex("(正确|对|是|√|T)\\s*[／/、,，;；|]\\s*(错误|错|否|×|F)")

    private val JUDGE_OPTION = Regex("[AB][．.、:：]\\s*(对|错|正确|错误|√|×|是|否)")

    private fun hasJudgeOptions(question: String): Boolean = JUDGE_OPTION.findAll(question).count() >= 2

    fun infer(question: String, bankType: String? = null, answers: List<String> = emptyList()): QuestionType {
        EXPLICIT.find(question)?.let { m ->
            return when (m.groupValues[1]) {
                "单选", "选择" -> QuestionType.SINGLE
                "多选", "不定项" -> QuestionType.MULTI
                "判断" -> QuestionType.JUDGE
                "填空" -> QuestionType.FILL
                else -> QuestionType.QA
            }
        }
        when (bankType?.lowercase()) {
            "choice", "single" -> return if (JUDGE_PAIR.containsMatchIn(question) || hasJudgeOptions(question)) {
                QuestionType.JUDGE
            } else {
                QuestionType.SINGLE
            }
            "multi", "multiple" -> return QuestionType.MULTI
            "judge", "boolean" -> return QuestionType.JUDGE
            "fill", "blank" -> return QuestionType.FILL
            "qa", "essay", "short" -> return QuestionType.QA
        }
        if (JUDGE_PAIR.containsMatchIn(question)) return QuestionType.JUDGE
        val options = OPTION_HEAD.findAll(question).count()
        if (options >= 2) return QuestionType.SINGLE

        if (question.contains("____") || question.contains("（ ）") || question.contains("( )")) {
            return QuestionType.FILL
        }
        return if (answers.isNotEmpty()) QuestionType.QA else QuestionType.UNKNOWN
    }

    fun answerPrefix(type: QuestionType): String = when (type) {
        QuestionType.QA -> "参考答案："
        QuestionType.UNKNOWN -> "答案："
        else -> "答案："
    }

    fun joinAnswers(type: QuestionType, answers: List<String>): String = when {
        answers.isEmpty() -> ""
        type == QuestionType.QA -> answers.joinToString("\n")
        type == QuestionType.MULTI -> answers.joinToString("、")
        type == QuestionType.FILL -> answers.joinToString("；")
        else -> answers.joinToString(" / ")
    }

    fun answerText(type: QuestionType, answers: List<String>): String {
        if (answers.isEmpty()) return ""
        return answerPrefix(type) + joinAnswers(type, answers)
    }
}
