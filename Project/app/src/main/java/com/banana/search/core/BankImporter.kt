package com.banana.search.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BankImporter {

    sealed interface Outcome

    data class Imported(val batch: BankBatch, val errors: List<BankParseError>) : Outcome

    data class ImportedBundle(val batches: List<BankBatch>) : Outcome

    data class Failed(val message: String) : Outcome

    data class Summary(val imported: List<Imported>, val failures: List<Pair<String, String>>)

    fun importUris(context: Context, uris: List<Uri>): Summary {
        val imported = ArrayList<Imported>()
        val failures = ArrayList<Pair<String, String>>()
        for (uri in uris) {
            val name = displayName(context, uri).ifEmpty { "未命名文件" }
            when (val r = importUri(context, uri, name)) {
                is Imported -> imported.add(r)
                is ImportedBundle -> r.batches.forEach { imported.add(Imported(it, emptyList())) }
                is Failed -> failures.add(name to r.message)
            }
        }
        return Summary(imported, failures)
    }

    fun importUri(context: Context, uri: Uri, name: String): Outcome {
        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: return Failed("打不开这个文件（没有读取权限）")
        return try {
            stream.use { doImport(context, name, it) }
        } catch (e: Exception) {
            Failed("解析失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun importText(context: Context, text: String, name: String): Outcome {
        val clean = text.trim()
        if (clean.isEmpty()) return Failed("内容是空的")
        return try {
            val batchName = name.ifBlank {
                "粘贴导入 " + SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date())
            }
            finish(context, batchName, "粘贴文本", sniffAndParse(clean, ""))
        } catch (e: Exception) {
            Failed("解析失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun doImport(context: Context, name: String, input: InputStream): Outcome {
        val ext = name.substringAfterLast('.', "").lowercase()
        val baseName = name.substringBeforeLast('.').ifEmpty { name }
        return when (ext) {
            "docx" -> {
                val doc = OfficeReader.readDocx(input)
                val fromText = if (doc.paragraphs.any { it.isNotBlank() }) {
                    BankParser.parseText(doc.paragraphs.joinToString("\n"))
                } else {
                    BankParseResult(emptyList(), emptyList())
                }

                val result = if (fromText.questions.isNotEmpty()) fromText else mergeTables(doc.tables)
                finish(context, baseName, name, result)
            }
            "xlsx" -> {
                val sheet = OfficeReader.readXlsx(input)
                finish(context, baseName, name, BankParser.parseTable(sheet.headers, sheet.rows))
            }
            "csv", "tsv" -> {
                val text = OfficeReader.decodeText(input.readCapped())
                val rows = OfficeReader.parseCsv(text)
                if (rows.isEmpty()) Failed("表格是空的")
                else finish(context, baseName, name, BankParser.parseTable(rows.first(), rows.drop(1)))
            }
            "json", "jsonl" -> {
                val text = OfficeReader.decodeText(input.readCapped())
                restoreIfBundle(context, text) ?: finish(context, baseName, name, BankParser.parseJson(text))
            }
            "txt", "md", "text" -> {
                val text = OfficeReader.decodeText(input.readCapped())
                finish(context, baseName, name, sniffAndParse(text, ext))
            }
            else -> {
                val bytes = input.readCapped()
                if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {

                    val hint = zipHint(bytes)
                    when (hint) {
                        "docx" -> {
                            val doc = OfficeReader.readDocx(bytes.inputStream())
                            finish(context, baseName, name, BankParser.parseText(doc.paragraphs.joinToString("\n")))
                        }
                        "xlsx" -> {
                            val sheet = OfficeReader.readXlsx(bytes.inputStream())
                            finish(context, baseName, name, BankParser.parseTable(sheet.headers, sheet.rows))
                        }
                        else -> Failed("认不出这个文件格式，请改成 .txt / .docx / .xlsx / .csv / .json")
                    }
                } else {
                    finish(context, baseName, name, sniffAndParse(OfficeReader.decodeText(bytes), ""))
                }
            }
        }
    }

    private fun sniffAndParse(text: String, ext: String): BankParseResult {
        val t = text.trimStart().removePrefix("\uFEFF")

        markdownTableRows(t)?.let { rows ->
            if (rows.size >= 2) return BankParser.parseTable(rows.first(), rows.drop(1))
        }
        if (ext == "json" || ext == "jsonl" || t.startsWith("{") || t.startsWith("[")) {
            return BankParser.parseJson(text)
        }
        val firstLine = t.lineSequence().firstOrNull().orEmpty()
        if (firstLine.contains('\t')) {
            val rows = OfficeReader.parseCsv(text)
            if (rows.size >= 2) return BankParser.parseTable(rows.first(), rows.drop(1))
        }
        return BankParser.parseText(text)
    }

    private fun mergeTables(tables: List<List<List<String>>>): BankParseResult {
        if (tables.isEmpty()) return BankParseResult(emptyList(), emptyList())
        val questions = ArrayList<ParsedQuestion>()
        val errors = ArrayList<BankParseError>()
        for (t in tables) {
            if (t.size < 2) continue
            val r = BankParser.parseTable(t.first(), t.drop(1))
            questions.addAll(r.questions)
            errors.addAll(r.errors)
        }
        return BankParseResult(questions, errors)
    }

    private fun restoreIfBundle(context: Context, text: String): Outcome? {
        val bundles = BankBackup.parseBundle(text) ?: return null
        val created = BankBackup.restore(context, bundles)
        if (created.isEmpty()) return Failed("备份包里没有可用的题目")
        return ImportedBundle(created)
    }

    private fun finish(context: Context, name: String, source: String, result: BankParseResult): Outcome {
        if (result.questions.isEmpty()) {
            val why = result.errors.firstOrNull()?.let { "${it.where}：${it.reason}" } ?: "没有解析到任何题目"
            return Failed(why)
        }
        val batch = BankStore.create(context, name, source, result.questions)
        QuestionBank.reload(context)
        return Imported(batch, result.errors)
    }

    private fun markdownTableRows(text: String): List<List<String>>? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("|") }
            .toList()
        if (lines.size < 2) return null
        val rows = lines.map { line ->
            line.trim('|').split('|').map { cell -> cell.trim() }
        }.filterNot { row ->
            row.all { cell -> cell.isEmpty() || cell.matches(Regex(":?-{2,}:?")) }
        }
        return if (rows.size >= 2 && rows.first().any { it.isNotEmpty() }) rows else null
    }

    private fun zipHint(bytes: ByteArray): String = runCatching {
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                when {
                    e.name.startsWith("word/") -> return@use "docx"
                    e.name.startsWith("xl/") -> return@use "xlsx"
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
            ""
        }
    }.getOrDefault("")

    private fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx).orEmpty()
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }
}
