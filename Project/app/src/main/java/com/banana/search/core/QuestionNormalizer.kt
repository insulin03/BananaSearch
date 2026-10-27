package com.banana.search.core

import java.text.Normalizer

object QuestionNormalizer {

    private val BLANK = Regex("[（(]\\s*[)）]|_{2,}")

    private val QUESTION_TYPE =
        Regex("[\\[（(【]\\s*(?:多选|单选|判断|填空|简答|论述|不定项|选择)\\s*题?\\s*[）)】\\]]")

    private val LEADING_INDEX = Regex("^\\s*(?:\\d{1,2}|[一二三四五六七八九十]{1,3})\\s*[.、．)）:：](?!\\d)\\s*")

    private val EXTRA_PUNCT: Set<Char> = setOf(
        '\u3002',
        '\u3001',
        '\uFF01',
        '\uFF1F',
        '\uFF1B',
        '\uFF1A',
        '\u201C', '\u201D',
        '\u2018', '\u2019',
        '\u300A', '\u300B',
        '\u3010', '\u3011',
        '\u3008', '\u3009',
        '\u300C', '\u300D',
        '\u300E', '\u300F',
        '\u2026',
        '\u2014',
        '\uFF5E',
        '\u00B7',
        '\u00D7',
        '\u00F7',
        '\u00B1',
        '\uFF05',
        '\uFF03',
        '\uFF20',
        '\uFF06',
        '\uFF0A',
        '\uFF0B',
        '\uFF0D',
        '\uFF1D',
        '\uFF0F',
        '\uFF3C',
        '\uFF5C',
        '\uFF08', '\uFF09',
        '\uFF0C',
        '\uFF0E',
        '\u3000'
    )

    private val PUNCT_TYPES: Set<Int> = setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt()
    )

    fun normalize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var s = BLANK.replace(raw, "")
        s = QUESTION_TYPE.replace(s, "")
        s = Normalizer.normalize(s, Normalizer.Form.NFKC)
        s = LEADING_INDEX.replace(s, "")
        val sb = StringBuilder(s.length)
        for (ch in s) {
            if (ch.isWhitespace()) continue
            if (ch in EXTRA_PUNCT) continue
            if (Character.getType(ch) in PUNCT_TYPES) continue
            sb.append(ch.lowercaseChar())
        }
        return sb.toString()
    }

    fun countBlanks(raw: String?): Int {
        if (raw.isNullOrEmpty()) return 0
        return BLANK.findAll(raw).count()
    }
}
