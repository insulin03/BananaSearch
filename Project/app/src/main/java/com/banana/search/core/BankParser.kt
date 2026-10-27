package com.banana.search.core

import org.json.JSONArray
import org.json.JSONObject

data class ParsedQuestion(
    val question: String,
    val options: List<String>,
    val answers: List<String>,
    val type: QuestionType,
    val analysis: String = "",
    val blankCount: Int = 0
) {

    val typeKey: String
        get() = when (type) {
            QuestionType.SINGLE -> "single"
            QuestionType.MULTI -> "multi"
            QuestionType.JUDGE -> "judge"
            QuestionType.FILL -> "fill"
            QuestionType.QA -> "qa"
            QuestionType.UNKNOWN -> "qa"
        }
}

data class BankParseError(val where: String, val reason: String, val snippet: String)

data class BankParseResult(
    val questions: List<ParsedQuestion>,
    val errors: List<BankParseError>
) {
}

object BankParser {

    private val Q_LABEL = Regex("^(?:题目|题干|问题|question|stem|q)\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val A_LABEL = Regex("^(?:答案|正确答案|answer|key)\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val T_LABEL = Regex("^(?:题型|类型|type)\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val E_LABEL = Regex("^(?:解析|分析|说明|analysis|explain)\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)

    private val OPTION = Regex("^\\s*[（(]?([A-Ha-h])[）)]?\\s*[.、．:：)）]\\s*(.*)$")

    private val NUMBERING = Regex("^\\s*[（(]?\\d{1,3}[）)]?\\s*[.、．:：]\\s*")

    private val LETTERS = Regex("^[A-Ha-h](?:[\\s,，、;；/]*[A-Ha-h])*$")

    private val JUDGE_TRUE = setOf("对", "正确", "√", "T", "TRUE", "是", "YES", "Y")
    private val JUDGE_FALSE = setOf("错", "错误", "×", "X", "F", "FALSE", "否", "NO", "N")

    fun parseText(text: String): BankParseResult {
        val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = ArrayList<List<String>>()
        var current = ArrayList<String>()
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.isBlank() || line.trim() == "---") {
                if (current.isNotEmpty()) {
                    blocks.add(current)
                    current = ArrayList()
                }
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) blocks.add(current)

        val questions = ArrayList<ParsedQuestion>(blocks.size)
        val errors = ArrayList<BankParseError>()
        blocks.forEachIndexed { index, block ->
            val where = "第 ${index + 1} 段"
            when (val r = parseBlock(block)) {
                is BlockResult.Ok -> questions.add(r.question)
                is BlockResult.Fail -> errors.add(BankParseError(where, r.reason, r.snippet))
                is BlockResult.Skip -> Unit
            }
        }
        return BankParseResult(questions, errors)
    }

    private sealed interface BlockResult {
        data class Ok(val question: ParsedQuestion) : BlockResult
        data class Fail(val reason: String, val snippet: String) : BlockResult

        data object Skip : BlockResult
    }

    private fun parseBlock(block: List<String>): BlockResult {
        var question = ""
        var answerRaw = ""
        var typeLabel = ""
        var analysis = ""
        val options = ArrayList<String>()
        var field = 0

        for (raw in block) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val q = Q_LABEL.find(line)
            val a = A_LABEL.find(line)
            val t = T_LABEL.find(line)
            val e = E_LABEL.find(line)
            val opt = if (a == null && t == null && e == null) OPTION.find(line) else null
            when {
                q != null -> {
                    question = if (question.isEmpty()) q.groupValues[1].trim() else "$question\n${q.groupValues[1].trim()}"
                    field = 0
                }
                a != null -> {
                    answerRaw = a.groupValues[1].trim()
                    field = 2
                }
                t != null -> {
                    typeLabel = t.groupValues[1].trim()
                    field = 2
                }
                e != null -> {
                    analysis = e.groupValues[1].trim()
                    field = 3
                }
                opt != null -> {
                    options.add(opt.groupValues[2].trim())
                    field = 1
                }
                else -> when (field) {
                    1 -> if (options.isNotEmpty()) {
                        options[options.size - 1] = options.last() + "\n" + line
                    } else {
                        question = if (question.isEmpty()) line else "$question\n$line"
                    }
                    2 -> answerRaw = "$answerRaw\n$line"
                    3 -> analysis = "$analysis\n$line"
                    else -> question = if (question.isEmpty()) line else "$question\n$line"
                }
            }
        }

        question = stripNumbering(question.trim())
        if (question.isEmpty()) {
            return BlockResult.Fail("题干为空（只有选项没有题干）", block.firstOrNull().orEmpty().take(40))
        }
        if (answerRaw.isBlank()) {

            if (options.isEmpty()) return BlockResult.Skip
            return BlockResult.Fail("缺少答案", question.take(40))
        }
        val type = resolveType(typeLabel, question, options, answerRaw)
        val answers = resolveAnswers(answerRaw, options, type)
        if (answers.isEmpty()) {
            return BlockResult.Fail("答案为空或无法识别", question.take(40))
        }
        if (type == QuestionType.SINGLE || type == QuestionType.MULTI || type == QuestionType.JUDGE) {
            if (options.isEmpty() && type != QuestionType.JUDGE) {
                return BlockResult.Fail("标了选择题但没有选项", question.take(40))
            }
        }
        return BlockResult.Ok(
            ParsedQuestion(
                question = question,
                options = options,
                answers = answers,
                type = type,
                analysis = analysis.trim(),
                blankCount = QuestionNormalizer.countBlanks(question)
            )
        )
    }

    fun stripNumbering(text: String): String {
        val m = NUMBERING.find(text) ?: return text

        val rest = text.substring(m.range.last + 1).trim()
        return if (rest.isEmpty()) text else rest
    }

    fun parseTable(headers: List<String>, rows: List<List<String>>): BankParseResult {
        val index = HeaderIndex(headers)
        if (index.question < 0) {
            return BankParseResult(
                emptyList(),
                listOf(BankParseError("表头", "找不到题干列（题目/题干/question）", headers.joinToString(" | ").take(60)))
            )
        }
        val questions = ArrayList<ParsedQuestion>()
        val errors = ArrayList<BankParseError>()
        rows.forEachIndexed { i, row ->
            val where = "第 ${i + 2} 行"
            if (row.all { it.isBlank() }) return@forEachIndexed
            val question = stripNumbering(row.getOrNull(index.question).orEmpty().trim())
            if (question.isEmpty()) {
                errors.add(BankParseError(where, "题干为空", row.joinToString(" | ").take(40)))
                return@forEachIndexed
            }
            val answerRaw = row.getOrNull(index.answer).orEmpty().trim()
            if (answerRaw.isEmpty()) {
                errors.add(BankParseError(where, "缺少答案", question.take(40)))
                return@forEachIndexed
            }
            val options = index.options.mapNotNull { col ->
                row.getOrNull(col)?.trim()?.takeIf { it.isNotEmpty() }
            }.ifEmpty {

                row.getOrNull(index.packed).orEmpty()
                    .split('|', '｜', '；', ';')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
            val typeLabel = if (index.type >= 0) row.getOrNull(index.type).orEmpty().trim() else ""
            val analysis = if (index.analysis >= 0) row.getOrNull(index.analysis).orEmpty().trim() else ""
            val type = resolveType(typeLabel, question, options, answerRaw)
            val answers = resolveAnswers(answerRaw, options, type)
            if (answers.isEmpty()) {
                errors.add(BankParseError(where, "答案无法识别", question.take(40)))
                return@forEachIndexed
            }
            questions.add(
                ParsedQuestion(
                    question = question,
                    options = options,
                    answers = answers,
                    type = type,
                    analysis = analysis,
                    blankCount = QuestionNormalizer.countBlanks(question)
                )
            )
        }
        return BankParseResult(questions, errors)
    }

    private class HeaderIndex(headers: List<String>) {
        var question = -1
        var answer = -1
        var type = -1
        var analysis = -1

        var packed = -1
        val options = ArrayList<Int>()

        init {
            headers.forEachIndexed { i, raw ->
                val h = raw.trim().lowercase()
                when {
                    h in setOf("题目", "题干", "问题", "question", "stem", "q") && question < 0 -> question = i
                    h in setOf("答案", "正确答案", "answer", "key") && answer < 0 -> answer = i
                    h in setOf("题型", "类型", "type") && type < 0 -> type = i
                    h in setOf("解析", "分析", "analysis", "explain") && analysis < 0 -> analysis = i
                    h in setOf("选项", "options", "choices") && packed < 0 -> packed = i
                    h.length == 1 && h[0] in 'a'..'h' -> options.add(i)
                    h.startsWith("选项") && h.length == 3 && h[2] in 'a'..'h' -> options.add(i)
                    h.startsWith("option") && h.length == 7 && h[6] in 'a'..'h' -> options.add(i)
                }
            }
        }
    }

    fun parseJson(text: String): BankParseResult {
        val trimmed = text.trim().removePrefix("\uFEFF")
        val objects = ArrayList<JSONObject>()
        if (trimmed.startsWith("[")) {
            runCatching { JSONArray(trimmed) }.getOrNull()?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { objects.add(it) }
            }
        } else if (trimmed.startsWith("{")) {

            if (!trimmed.contains("\n")) {
                runCatching { JSONObject(trimmed) }.getOrNull()?.let { objects.add(it) }
            } else {
                trimmed.split('\n').forEach { line ->
                    val l = line.trim()
                    if (l.startsWith("{")) runCatching { JSONObject(l) }.getOrNull()?.let { objects.add(it) }
                }
            }
        }
        val questions = ArrayList<ParsedQuestion>()
        val errors = ArrayList<BankParseError>()
        objects.forEachIndexed { i, o ->
            val where = "第 ${i + 1} 条"
            val question = stripNumbering(firstString(o, "question", "stem", "q", "题目", "题干").trim())
            if (question.isEmpty()) {
                errors.add(BankParseError(where, "题干为空", o.toString().take(40)))
                return@forEachIndexed
            }
            val answerRaw = stringOrJoin(o, "answer", "answers", "key", "答案").trim()
            if (answerRaw.isEmpty()) {
                errors.add(BankParseError(where, "缺少答案", question.take(40)))
                return@forEachIndexed
            }
            val options = ArrayList<String>()
            val arr = o.optJSONArray("options") ?: o.optJSONArray("choices") ?: o.optJSONArray("opts")
            if (arr != null) {
                for (j in 0 until arr.length()) {
                    val s = arr.optString(j).trim()
                    if (s.isNotEmpty()) options.add(s)
                }
            } else {
                for (c in 'A'..'H') {
                    val s = o.optString(c.toString()).trim()
                    if (s.isNotEmpty()) options.add(s)
                }
            }
            val typeLabel = firstString(o, "type", "题型")
            val analysis = firstString(o, "analysis", "explain", "解析")
            val type = resolveType(typeLabel, question, options, answerRaw)
            val answers = resolveAnswers(answerRaw, options, type)
            if (answers.isEmpty()) {
                errors.add(BankParseError(where, "答案无法识别", question.take(40)))
                return@forEachIndexed
            }
            questions.add(
                ParsedQuestion(
                    question = question,
                    options = options,
                    answers = answers,
                    type = type,
                    analysis = analysis.trim(),
                    blankCount = QuestionNormalizer.countBlanks(question)
                )
            )
        }
        return BankParseResult(questions, errors)
    }

    private fun stringOrJoin(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            val arr = o.optJSONArray(k)
            if (arr != null) {
                val parts = ArrayList<String>()
                for (j in 0 until arr.length()) {
                    val v = arr.optString(j, "").trim()
                    if (v.isNotEmpty()) parts.add(v)
                }
                if (parts.isNotEmpty()) return parts.joinToString("|")
                continue
            }
            val v = o.optString(k, "").trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    private fun firstString(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (!o.has(k)) continue
            when (val value = o.opt(k)) {
                is String -> if (value.isBlank().not()) return value
                is JSONArray -> {
                    val sb = StringBuilder()
                    for (i in 0 until value.length()) {
                        val s = value.optString(i).trim()
                        if (s.isEmpty()) continue
                        if (sb.isNotEmpty()) sb.append('|')
                        sb.append(s)
                    }
                    if (sb.isNotEmpty()) return sb.toString()
                }
                is Number -> return value.toString()
                else -> Unit
            }
        }
        return ""
    }

    fun resolveType(label: String, question: String, options: List<String>, answerRaw: String): QuestionType {
        if (label.isNotBlank()) {
            when (label.lowercase().replace(" ", "")) {
                "单选", "单选题", "single", "singlechoice", "choice" -> return QuestionType.SINGLE
                "多选", "多选题", "multiple", "multi", "multiplechoice" -> return QuestionType.MULTI
                "判断", "判断题", "judge", "truefalse", "tf" -> return QuestionType.JUDGE
                "填空", "填空题", "fill", "blank" -> return QuestionType.FILL
                "简答", "简答题", "问答", "qa", "essay", "shortanswer" -> return QuestionType.QA
            }
        }
        if (options.size >= 2) {
            if (options.size == 2 && options.all { isJudgeWord(it) }) return QuestionType.JUDGE
            val letters = countLettersMulti(answerRaw)
            return if (letters > 1) QuestionType.MULTI else QuestionType.SINGLE
        }
        if (options.isEmpty()) {
            if (isJudgeWord(answerRaw)) return QuestionType.JUDGE
            if (QuestionNormalizer.countBlanks(question) > 0) return QuestionType.FILL
            return QuestionType.QA
        }
        return QuestionType.SINGLE
    }

    private fun isJudgeWord(s: String): Boolean {
        val t = s.trim().uppercase()
        return t in JUDGE_TRUE || t in JUDGE_FALSE || s.trim() in JUDGE_TRUE || s.trim() in JUDGE_FALSE
    }

    private fun countLettersMulti(raw: String): Int {
        val tokens = raw.split('|', '、', '；', ';', ',', '，')
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty() || !tokens.all { LETTERS.matches(it) }) return 0
        return tokens.sumOf { tk -> tk.count { it in 'A'..'H' || it in 'a'..'h' } }
    }

    fun resolveAnswers(raw: String, options: List<String>, type: QuestionType): List<String> {
        val text = raw.trim().removePrefix("答案").removePrefix(":").removePrefix("：").trim()
        if (text.isEmpty()) return emptyList()

        if (type == QuestionType.JUDGE || (options.isEmpty() && isJudgeWord(text))) {
            val up = text.uppercase()
            return when {
                up in JUDGE_TRUE || text in JUDGE_TRUE -> listOf("对")
                up in JUDGE_FALSE || text in JUDGE_FALSE -> listOf("错")
                else -> listOf(text)
            }
        }

        val tokens = text.split('|', '、', '；', ';', ',', '，')
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (options.isNotEmpty() && tokens.isNotEmpty() && tokens.all { LETTERS.matches(it) }) {
            val out = ArrayList<String>()
            for (tk in tokens) {
                for (ch in tk) {
                    val idx = when (ch) {
                        in 'A'..'H' -> ch - 'A'
                        in 'a'..'h' -> ch - 'a'
                        else -> -1
                    }
                    if (idx in options.indices) out.add(options[idx])
                }
            }
            if (out.isNotEmpty()) return out
        }

        if (options.isNotEmpty() && LETTERS.matches(text)) {
            val out = ArrayList<String>()
            for (ch in text) {
                val idx = when (ch) {
                    in 'A'..'H' -> ch - 'A'
                    in 'a'..'h' -> ch - 'a'
                    else -> -1
                }
                if (idx in options.indices) out.add(options[idx])
            }
            if (out.isNotEmpty()) return out
        }

        val prefixed = OPTION.find(text)
        if (prefixed != null && options.isNotEmpty()) {
            val idx = prefixed.groupValues[1].lowercase()[0] - 'a'
            if (idx in options.indices) return listOf(options[idx])
        }

        val parts = text.split('|', '、', '；', ';').map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size > 1) parts else listOf(text)
    }
}
