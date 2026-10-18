package com.hos.rushdpatients.network.telegram.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TgMessage(
    @SerialName("message_id") val messageId: Long,
    val date: Long = 0L,
    val chat: TgChat? = null,
    val from: TgUser? = null,
    val text: String? = null,
    val caption: String? = null,
    val document: TgDocument? = null,
    @SerialName("forward_from") val forwardFrom: TgUser? = null,
    @SerialName("forward_from_chat") val forwardFromChat: TgChat? = null,
    @SerialName("forward_from_message_id") val forwardFromMessageId: Long? = null,
    @SerialName("reply_to_message") val replyToMessage: TgMessage? = null
)