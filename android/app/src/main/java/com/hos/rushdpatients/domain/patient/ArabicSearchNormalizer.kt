package com.hos.rushdpatients.domain.patient

import java.text.Normalizer

object ArabicSearchNormalizer {
    private val diacritics = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase()
        .replace(diacritics, "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ؤ', 'و')
        .replace('ئ', 'ي')
        .replace('ة', 'ه')
        .replace(Regex("\\s+"), " ")
        .trim()

    fun matches(query: String, vararg values: String?): Boolean {
        val needle = normalize(query)
        if (needle.isEmpty()) return true
        return values.any { normalize(it.orEmpty()).contains(needle) }
    }
}
