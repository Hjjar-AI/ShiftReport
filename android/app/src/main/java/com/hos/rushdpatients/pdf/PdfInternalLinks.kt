package com.hos.rushdpatients.pdf

import android.graphics.RectF
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/**
 * Adds standard internal Link annotations to a freshly generated Android PdfDocument.
 * Canvas has no annotation API. An incremental revision retains its native text/fonts
 * and streams; only page dictionaries and the new annotations are written.
 * This deliberately accepts native classic-xref output, not imported/arbitrary PDFs.
 */
internal object PdfInternalLinks {
    data class Link(val page: Int, val rect: RectF, val destinationPage: Int, val destinationTop: Float)
    private data class Ref(val number: Int, val generation: Int)
    private data class Entry(val offset: Long, val generation: Int)
    private val reference = Regex("(\\d+)\\s+(\\d+)\\s+R")

    fun append(file: File, links: List<Link>, pageHeight: Float) {
        if (links.isEmpty()) return
        RandomAccessFile(file, "rw").use { pdf ->
            val tailSize = minOf(pdf.length(), 4096L).toInt()
            pdf.seek(pdf.length() - tailSize)
            val tail = ByteArray(tailSize).also(pdf::readFully).toString(Charsets.ISO_8859_1)
            val previous = Regex("startxref\\s+(\\d+)\\s+%%EOF").findAll(tail).lastOrNull()
                ?.groupValues?.get(1)?.toLong() ?: error("تعذر قراءة دليل PDF")
            pdf.seek(previous)
            check(pdf.readLine()?.trim() == "xref") { "تنسيق دليل PDF غير مدعوم للروابط" }
            val entries = mutableMapOf<Int, Entry>()
            while (true) {
                val line = checkNotNull(pdf.readLine()).trim()
                if (line == "trailer") break
                if (line.isEmpty()) continue
                val range = line.split(Regex("\\s+"))
                check(range.size == 2) { "دليل PDF غير صالح" }
                val first = range[0].toInt()
                repeat(range[1].toInt()) { index ->
                    val parts = checkNotNull(pdf.readLine()).trim().split(Regex("\\s+"))
                    check(parts.size == 3) { "مدخل PDF غير صالح" }
                    if (parts[2] == "n") entries[first + index] = Entry(parts[0].toLong(), parts[1].toInt())
                }
            }
            val trailer = dictionary(pdf)
            fun ref(key: String, dictionary: String): Ref {
                val match = Regex("/$key\\s+(\\d+)\\s+(\\d+)\\s+R").find(dictionary)
                    ?: error("مرجع PDF مفقود")
                return Ref(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            }
            fun objectDictionary(ref: Ref): String {
                val entry = checkNotNull(entries[ref.number])
                check(entry.generation == ref.generation) { "جيل مرجع PDF غير صالح" }
                pdf.seek(entry.offset)
                check(pdf.readLine()?.trim() == "${ref.number} ${ref.generation} obj") { "كائن PDF غير صالح" }
                return dictionary(pdf)
            }
            val root = ref("Root", trailer)
            val pages = mutableListOf<Pair<Ref, String>>()
            val visited = mutableSetOf<Int>()
            fun visit(node: Ref) {
                check(visited.add(node.number)) { "شجرة صفحات PDF غير صالحة" }
                val dict = objectDictionary(node)
                if (Regex("/Type\\s*/Page(?!s)\\b").containsMatchIn(dict)) {
                    pages += node to dict
                } else {
                    check(Regex("/Type\\s*/Pages\\b").containsMatchIn(dict)) { "صفحة PDF غير صالحة" }
                    val kids = Regex("/Kids\\s*\\[([^]]*)]", RegexOption.DOT_MATCHES_ALL).find(dict)
                        ?.groupValues?.get(1) ?: error("صفحات PDF مفقودة")
                    reference.findAll(kids).forEach { match ->
                        visit(Ref(match.groupValues[1].toInt(), match.groupValues[2].toInt()))
                    }
                }
            }
            visit(ref("Pages", objectDictionary(root)))
            val size = Regex("/Size\\s+(\\d+)").find(trailer)?.groupValues?.get(1)?.toInt()
                ?: error("حجم دليل PDF مفقود")
            check(size > (entries.keys.maxOrNull() ?: 0)) { "حجم دليل PDF غير صالح" }
            check(!Regex("/Prev\\b").containsMatchIn(trailer)) { "PDF ليس تصديراً جديداً" }
            links.forEach { link ->
                check(link.page in pages.indices && link.destinationPage in pages.indices)
                check(link.rect.left < link.rect.right && link.rect.top < link.rect.bottom)
                check(link.rect.top >= 0f && link.rect.bottom <= pageHeight)
                check(link.destinationTop in 0f..pageHeight)
            }
            // Validate before appending: native Canvas output has no existing annotations.
            links.map { it.page }.distinct().forEach { index ->
                check(!Regex("/Annots\\b").containsMatchIn(pages[index].second)) { "روابط PDF موجودة مسبقاً" }
            }
            var next = size
            val offsets = sortedMapOf<Int, Entry>()
            fun write(text: String) = pdf.write(text.toByteArray(Charsets.ISO_8859_1))
            fun writeObject(ref: Ref, value: String) {
                offsets[ref.number] = Entry(pdf.filePointer, ref.generation)
                write("${ref.number} ${ref.generation} obj\n$value\nendobj\n")
            }
            fun number(value: Float): String = String.format(Locale.US, "%.3f", value)
            pdf.seek(pdf.length())
            write("\n")
            links.groupBy { it.page }.forEach { (index, pageLinks) ->
                val annotationRefs = pageLinks.map { link ->
                    val annotation = Ref(next++, 0)
                    val destination = pages[link.destinationPage].first
                    val rect = link.rect
                    val box = "${number(rect.left)} ${number(pageHeight - rect.bottom)} ${number(rect.right)} ${number(pageHeight - rect.top)}"
                    writeObject(annotation, "<< /Type /Annot /Subtype /Link /Rect [$box] /Border [0 0 0] " +
                        "/Dest [${destination.number} ${destination.generation} R /XYZ null ${number(pageHeight - link.destinationTop)} null] >>")
                    "${annotation.number} 0 R"
                }
                val (pageRef, dict) = pages[index]
                writeObject(pageRef, dict.dropLast(2) + " /Annots [${annotationRefs.joinToString(" ")}] >>")
            }
            val xref = pdf.filePointer
            write("xref\n")
            // Single-entry subsections also support non-consecutive updated page numbers.
            offsets.forEach { (id, entry) ->
                write("$id 1\n" + String.format(Locale.US, "%010d %05d n \n", entry.offset, entry.generation))
            }
            val updatedTrailer = trailer.replace(Regex("/Size\\s+\\d+"), "/Size $next")
            write("trailer\n${updatedTrailer.dropLast(2)} /Prev $previous >>\nstartxref\n$xref\n%%EOF\n")
        }
    }

    /** Read a balanced PDF dictionary, ignoring delimiters inside strings and comments. */
    private fun dictionary(pdf: RandomAccessFile): String {
        val result = StringBuilder()
        var depth = 0
        var literalDepth = 0
        var escaped = false
        var comment = false
        var hex = false
        while (result.length < 1_048_576) {
            val byte = pdf.read()
            check(byte >= 0) { "قاموس PDF غير مكتمل" }
            val c = byte.toChar()
            if (result.isEmpty() && c.isWhitespace()) continue
            result.append(c)
            if (comment) {
                if (c == '\n' || c == '\r') comment = false
                continue
            }
            if (literalDepth > 0) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '(' -> literalDepth++
                    c == ')' -> literalDepth--
                }
                continue
            }
            if (hex) {
                if (c == '>') hex = false
                continue
            }
            when (c) {
                '%' -> comment = true
                '(' -> literalDepth = 1
                '<' -> {
                    val next = pdf.read()
                    check(next >= 0)
                    result.append(next.toChar())
                    if (next.toChar() == '<') depth++ else hex = next.toChar() != '>'
                }
                '>' -> {
                    check(pdf.read() == '>'.code) { "قاموس PDF غير صالح" }
                    result.append('>')
                    depth--
                    if (depth == 0) return result.toString()
                }
            }
            check(depth > 0) { "قاموس PDF مفقود" }
        }
        error("قاموس PDF أكبر من الحد المسموح")
    }
}
