package com.hos.rushdpatients.network.telegram.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TgEnvelope<T>(
    val ok: Boolean,
    val result: T? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    val description: String? = null,
    val parameters: TgErrorParameters? = null
)

@Serializable
data class TgErrorParameters(
    @SerialName("retry_after") val retryAfter: Int? = null,
    @SerialName("migrate_to_chat_id") val migrateToChatId: Long? = null
)