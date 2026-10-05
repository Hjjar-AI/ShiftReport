package com.hos.rushdpatients.util

object ArabicNumbers {

    private val ARABIC_TO_ASCII: Map<Char, Char> = mapOf(
        '\u0660' to '0', '\u0661' to '1', '\u0662' to '2', '\u0663' to '3', '\u0664' to '4',
        '\u0665' to '5', '\u0666' to '6', '\u0667' to '7', '\u0668' to '8', '\u0669' to '9',
        '\u06F0' to '0', '\u06F1' to '1', '\u06F2' to '2', '\u06F3' to '3', '\u06F4' to '4',
        '\u06F5' to '5', '\u06F6' to '6', '\u06F7' to '7', '\u06F8' to '8', '\u06F9' to '9'
    )

    private val ASCII_TO_ARABIC: Map<Char, Char> = mapOf(
        '0' to '\u0660', '1' to '\u0661', '2' to '\u0662', '3' to '\u0663', '4' to '\u0664',
        '5' to '\u0665', '6' to '\u0666', '7' to '\u0667', '8' to '\u0668', '9' to '\u0669'
    )

    fun toAscii(input: String): String = buildString(input.length) {
        for (c in input) append(ARABIC_TO_ASCII[c] ?: c)
    }

    fun toArabicDigits(input: String): String = buildString(input.length) {
        for (c in input) append(ASCII_TO_ARABIC[c] ?: c)
    }

    fun parseIntOrNull(input: String): Int? =
        toAscii(input).trim().toIntOrNull()

    fun parseLongOrNull(input: String): Long? =
        toAscii(input).trim().toLongOrNull()
}