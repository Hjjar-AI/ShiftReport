package com.hos.rushdpatients.pdf

import android.graphics.Paint

/**
 * Wraps text into lines that fit within [maxWidth] using the given paint.
 * Splits on whitespace; force-breaks words that are wider than [maxWidth].
 */
object PdfTextWrapper {

    fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")

        val lines = mutableListOf<String>()
        val current = StringBuilder()

        // Preserve explicit newlines
        val paragraphs = text.split('\n')
        for ((pIdx, paragraph) in paragraphs.withIndex()) {
            if (pIdx > 0) {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current.clear()
                } else {
                    lines += ""
                }
            }
            val words = paragraph.split(' ').filter { it.isNotEmpty() }
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(word)
                } else {
                    if (current.isNotEmpty()) {
                        lines += current.toString()
                        current.clear()
                    }
                    // Force-break a single word that is too wide
                    var remaining = word
                    var guard = 0
                    while (paint.measureText(remaining) > maxWidth && remaining.isNotEmpty()) {
                        if (++guard > 1000) break   // hard stop, pathological input
                        val count = paint.breakText(remaining, true, maxWidth, null)
                        if (count <= 0) {
                            // breakText can return 0 on some pathological inputs
                            // (e.g. zero-width joining chars). Emit one char
                            // and continue so we never loop forever.
                            lines += remaining.substring(0, 1)
                            remaining = remaining.substring(1)
                            continue
                        }
                        val cut = count.coerceAtMost(remaining.length)
                        lines += remaining.substring(0, cut)
                        remaining = remaining.substring(cut)
                    }
                    if (remaining.isNotEmpty()) current.append(remaining)
                }
            }
        }

        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf("") }
    }

    fun lineHeight(paint: Paint, spacingMultiplier: Float = 1.15f): Float =
        (paint.fontMetrics.bottom - paint.fontMetrics.top) * spacingMultiplier
}