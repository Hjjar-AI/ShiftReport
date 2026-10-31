package com.hos.rushdpatients.data.model

import java.util.Base64

data class PatientBadge(
    val text: String,
    val priority: PatientBadgePriority? = null
)

/** Versioned compact representation stored in the existing warning-detail columns. */
object PatientBadgeCodec {
    private const val PREFIX = "badges:"
    private const val NONE = "none"

    fun encode(badges: List<PatientBadge>): String {
        val normalized = badges.mapNotNull { badge ->
            badge.text.trim().takeIf(String::isNotBlank)?.let { badge.copy(text = it) }
        }
        if (normalized.isEmpty()) return ""
        return PREFIX + normalized.joinToString(";") { badge ->
            val level = badge.priority?.code ?: NONE
            val text = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(badge.text.toByteArray(Charsets.UTF_8))
            "$level:$text"
        }
    }

    fun decode(encoded: String): List<PatientBadge> {
        if (encoded.isBlank()) return emptyList()
        require(encoded.startsWith(PREFIX)) { "تمثيل الشارات غير مدعوم" }
        return encoded.removePrefix(PREFIX).split(';').map { value ->
            requireNotNull(decodeBadge(value)) { "شارة غير صالحة" }
        }
    }

    private fun decodeBadge(value: String): PatientBadge? {
        val separator = value.indexOf(':')
        if (separator <= 0) return null
        val level = value.substring(0, separator)
        val text = decodeText(value.substring(separator + 1))?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val priority = if (level == NONE) null else PatientBadgePriority.fromCode(level) ?: return null
        return PatientBadge(text = text, priority = priority)
    }

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()

}
