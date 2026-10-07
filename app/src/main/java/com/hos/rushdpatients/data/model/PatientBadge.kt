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

    fun decode(encoded: String, legacyFlags: String = ""): List<PatientBadge> {
        if (encoded.startsWith(PREFIX)) {
            return encoded.removePrefix(PREFIX).split(';').mapNotNull(::decodeBadge)
        }
        if (encoded.startsWith("badge:")) {
            val text = decodeText(encoded.removePrefix("badge:")) ?: return emptyList()
            return listOf(PatientBadge(text, legacyPriority(legacyFlags)))
        }

        val legacyItems = encoded.split(';', '|').mapNotNull { item ->
            val separator = item.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val text = decodeText(item.substring(separator + 1)) ?: return@mapNotNull null
            val legacyFlag = PatientWarningFlag.fromCode(item.substring(0, separator))
            PatientBadge(text, legacyFlag?.toBadgePriority())
        }
        if (legacyItems.isNotEmpty()) return legacyItems

        if (encoded.isNotBlank()) {
            return listOf(PatientBadge(encoded.trim(), legacyPriority(legacyFlags)))
        }

        return legacyFlags.split(',', '|').mapNotNull(PatientWarningFlag::fromCode)
            .map { PatientBadge(it.arabicLabel, it.toBadgePriority()) }
    }

    private fun decodeBadge(value: String): PatientBadge? {
        val separator = value.indexOf(':')
        if (separator <= 0) return null
        val level = value.substring(0, separator)
        val text = decodeText(value.substring(separator + 1))?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        return PatientBadge(
            text = text,
            priority = if (level == NONE) null else PatientBadgePriority.fromCode(level)
        )
    }

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()

    private fun legacyPriority(flags: String): PatientBadgePriority? {
        val legacy = flags.split(',', '|').mapNotNull(PatientWarningFlag::fromCode)
        return when {
            PatientWarningFlag.URGENT_REVIEW in legacy -> PatientBadgePriority.HIGH
            legacy.isNotEmpty() -> PatientBadgePriority.MEDIUM
            else -> PatientBadgePriority.fromCode(flags)
        }
    }

    private fun PatientWarningFlag.toBadgePriority(): PatientBadgePriority =
        if (this == PatientWarningFlag.URGENT_REVIEW) PatientBadgePriority.HIGH
        else PatientBadgePriority.MEDIUM
}
