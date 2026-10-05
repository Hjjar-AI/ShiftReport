package com.hos.rushdpatients.domain.sort

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SortSpecCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(spec: SortSpec): String = json.encodeToString(spec)

    fun decodeOrNull(value: String?): SortSpec? = value
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString<SortSpec>(it) }.getOrNull() }

    fun decode(value: String?): SortSpec = decodeOrNull(value) ?: SortSpec.DEFAULT
}
