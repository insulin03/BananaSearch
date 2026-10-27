package com.banana.search.core

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

private const val MAX_BYTES = 20L * 1024 * 1024

internal fun InputStream.readCapped(): ByteArray {
    val buf = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(chunk)
        if (n < 0) break
        total += n
        require(total <= MAX_BYTES) { "文件超过 20MB，题库文件不该这么大" }
        buf.write(chunk, 0, n)
    }
    return buf.toByteArray()
}

object OfficeReader {

    data class DocxResult(val paragraphs: List<String>, val tables: List<List<List<String>>>)

    data class XlsxResult(val headers: List<String>, val rows: List<List<String>>)

    fun readDocx(input: InputStream): DocxResult {
        var xml: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xml = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val body = xml ?: error("word/document.xml 缺失，可能不是有效的 docx")

        val paragraphs = ArrayList<String>()
        val tables = ArrayList<List<List<String>>>()
        val parser = Xml.newPullParser()
        parser.setInput(body.reader())
        var event = parser.eventType
        var inText = false
        var sb = StringBuilder()
        var table: ArrayList<List<String>>? = null
        var row: ArrayList<String>? = null
        var cell: StringBuilder? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "tbl" -> table = ArrayList()
                    "tr" -> if (table != null) row = ArrayList()
                    "tc" -> if (row != null) cell = StringBuilder()
                    "t" -> inText = true
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "tc" -> {
                        val c = cell
                        val r = row
                        if (c != null && r != null) r.add(c.toString().trim())
                        cell = null
                    }
                    "tr" -> {
                        val r = row
                        val t = table
                        if (r != null && t != null) t.add(r)
                        row = null
                    }
                    "tbl" -> {
                        val t = table
                        if (t != null && t.isNotEmpty()) tables.add(t)
                        table = null
                    }
                    "p" -> {
                        if (table == null) {

                            paragraphs.add(sb.toString().trim())
                            sb = StringBuilder()
                        } else {

                            sb = StringBuilder()
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inText) sb.append(parser.text.orEmpty())
            }
            event = parser.next()
        }
        return DocxResult(paragraphs, tables)
    }

    fun readXlsx(input: InputStream): XlsxResult {
        var shared: ByteArray? = null
        val sheets = HashMap<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    shared = zip.readBytes()
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheets[name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val strings = shared?.let { parseSharedStrings(it) } ?: emptyList()
        val sheet = sheets.entries.sortedBy { it.key }.firstOrNull()?.value
            ?: error("找不到工作表，可能不是有效的 xlsx")
        val grid = parseSheet(sheet, strings)
        if (grid.isEmpty()) return XlsxResult(emptyList(), emptyList())
        val width = grid.maxOf { it.size }
        val headers = (0 until width).map { c -> grid.first().getOrNull(c).orEmpty() }
        val rows = grid.drop(1).map { r -> (0 until width).map { c -> r.getOrNull(c).orEmpty() } }
        return XlsxResult(headers, rows)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream().reader())
        var event = parser.eventType
        var inText = false
        var sb = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> sb = StringBuilder()
                    "t" -> inText = true
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> out.add(sb.toString())
                }
                XmlPullParser.TEXT -> if (inText) sb.append(parser.text.orEmpty())
            }
            event = parser.next()
        }
        return out
    }

    private fun parseSheet(bytes: ByteArray, strings: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream().reader())
        var event = parser.eventType
        var row = ArrayList<String>() ?: ArrayList()
        var nextCol = 0
        var cellCol = 0
        var cellType = ""
        var inValue = false
        var inInlineText = false
        var value = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        row = ArrayList()
                        nextCol = 0
                    }
                    "c" -> {
                        cellCol = nextCol
                        val ref = parser.getAttributeValue(null, "r")
                        if (ref != null) {
                            val letters = ref.takeWhile { it.isLetter() }
                            if (letters.isNotEmpty()) cellCol = columnIndex(letters)
                        }
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        value = StringBuilder()
                    }
                    "v" -> inValue = true
                    "is", "t" -> if (cellType == "inlineStr") inInlineText = true
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "is", "t" -> inInlineText = false
                    "c" -> {
                        val raw = value.toString().trim()
                        val text = when (cellType) {
                            "s" -> raw.toIntOrNull()?.let { strings.getOrNull(it) } ?: ""
                            "inlineStr" -> raw
                            else -> raw
                        }
                        while (row.size < cellCol) row.add("")
                        if (row.size == cellCol) row.add(text) else row[cellCol] = text
                        nextCol = cellCol + 1
                    }
                    "row" -> rows.add(row)
                }
                XmlPullParser.TEXT -> if (inValue || inInlineText) value.append(parser.text.orEmpty())
            }
            event = parser.next()
        }
        return rows
    }

    private fun columnIndex(letters: String): Int {
        var n = 0
        for (ch in letters.uppercase()) n = n * 26 + (ch - 'A' + 1)
        return (n - 1).coerceAtLeast(0)
    }

    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("\uFEFF")
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> {
                        cell.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> cell.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' || c == '\t' -> {
                    row.add(cell.toString()); cell.setLength(0)
                }
                c == '\n' -> {
                    row.add(cell.toString()); cell.setLength(0)
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = ArrayList()
                }
                c == '\r' -> Unit
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            if (row.any { it.isNotBlank() }) rows.add(row)
        }
        return rows
    }

    fun decodeText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) String(bytes, charset("GBK")) else utf8
    }
}
