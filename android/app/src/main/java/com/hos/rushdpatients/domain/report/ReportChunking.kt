package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.config.AppConstants

object ReportChunking {

    fun chunk(
        titleBlock: String,
        patientBlocks: List<String>,
        summaryBlock: String,
        maxChars: Int = AppConstants.TELEGRAM_SAFE_CHARS
    ): List<String> {
        require(titleBlock.isNotBlank()) { "Title block must not be blank" }
        require(maxChars > 0) { "Message limit must be positive" }

        // Most reports retain their existing whole-patient layout. Only oversized
        // blocks need continuation messages; each continuation is valid Markdown.
        if (listOf(titleBlock, summaryBlock).any { it.length > maxChars } ||
            patientBlocks.any { it.length > maxChars }) {
            return buildList {
                addAll(splitOversized(titleBlock, maxChars))
                patientBlocks.forEach { addAll(splitOversized(it, maxChars)) }
                addAll(splitOversized(summaryBlock, maxChars))
            }
        }

        // If everything fits in one message, do it.
        val totalLen = titleBlock.length +
                patientBlocks.sumOf { it.length + 1 } +
                summaryBlock.length + 2
        if (totalLen <= maxChars) {
            return listOf(buildSingle(titleBlock, patientBlocks, summaryBlock))
        }

        val chunks = mutableListOf<String>()
        val current = StringBuilder(titleBlock)
        var currentLen = titleBlock.length

        fun flush() {
            if (current.isNotEmpty()) {
                chunks += current.toString()
                current.clear()
                currentLen = 0
            }
        }

        // Reserve room for the summary in the last chunk.
        // softCap = how much room the current (non-final) chunk can use.
        val softCap = (maxChars - summaryBlock.length - 4).coerceAtLeast(titleBlock.length + 1)

        for (block in patientBlocks) {
            val extra = block.length + 1
            if (currentLen > 0 && currentLen + extra > softCap) {
                flush()
            }
            if (currentLen > 0) {
                current.append('\n')
                currentLen += 1
            }
            current.append(block)
            currentLen += block.length
        }

        // Attach summary to the last chunk if it fits.
        val summaryExtra = summaryBlock.length + 2
        if (currentLen + summaryExtra <= maxChars) {
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(summaryBlock)
            chunks += current.toString()
        } else {
            flush()
            chunks += summaryBlock
        }

        return chunks
    }

    private fun buildSingle(
        title: String,
        patientBlocks: List<String>,
        summary: String
    ): String = buildString {
        append(title)
        for (b in patientBlocks) {
            append('\n')
            append(b)
        }
        append("\n\n")
        append(summary)
    }

    /** Supports the escaped text, bold/underline and links emitted by TextReportBuilder. */
    /** Captions use a smaller budget; continuation chunks retain valid Markdown and every name. */
    fun captionParts(caption: String): List<String> = splitOversized(caption, 1024)

    private fun splitOversized(block: String, limit: Int): List<String> {
        if (block.isEmpty()) return emptyList()
        if (block.length <= limit) return listOf(block)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        val open = mutableListOf<String>()
        var index = 0
        while (index < block.length) {
            val start = index
            val marker = when {
                block[index] == '\\' -> { index = (index + 2).coerceAtMost(block.length); null }
                block[index] == '[' -> {
                    // Links are indivisible: a continuation must never cut a URL or its label.
                    index++
                    while (index < block.length) {
                        if (block[index] == '\\') index = (index + 2).coerceAtMost(block.length)
                        else if (block[index++] == ']') break
                    }
                    require(index < block.length && block[index++] == '(') { "Malformed report link" }
                    while (index < block.length && block[index] != ')') index++
                    require(index < block.length) { "Malformed report link" }
                    index++
                    null
                }
                block.startsWith("__", index) -> { index += 2; "__" }
                block[index] == '*' -> { index++; "*" }
                else -> {
                    index += Character.charCount(Character.codePointAt(block, index))
                    null
                }
            }
            val token = block.substring(start, index)
            val nextOpen = open.toMutableList().apply {
                if (marker != null) {
                    if (lastOrNull() == marker) removeAt(lastIndex) else add(marker)
                }
            }
            val closingSize = nextOpen.sumOf { it.length }
            if (current.length + token.length + closingSize > limit) {
                val closing = open.asReversed().joinToString("")
                require(current.length > open.sumOf { it.length }) {
                    "جزء من التقرير أطول من حد الرسالة؛ قصّر اسم الطبيب أو الرابط ثم راجع التقرير"
                }
                chunks += current.toString() + closing
                current.clear().append(open.joinToString(""))
            }
            require(current.length + token.length + closingSize <= limit) {
                "جزء من التقرير أطول من حد الرسالة؛ قصّر اسم الطبيب أو الرابط ثم راجع التقرير"
            }
            current.append(token)
            open.clear()
            open.addAll(nextOpen)
        }
        require(open.isEmpty()) { "Unclosed report formatting" }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }
}
