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
}