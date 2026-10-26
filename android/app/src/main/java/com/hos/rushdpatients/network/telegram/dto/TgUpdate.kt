package com.hos.rushdpatients.network.telegram.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TgUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TgMessage? = null,
    @SerialName("edited_message") val editedMessage: TgMessage? = null,
    @SerialName("channel_post") val channelPost: TgMessage? = null,
    @SerialName("callback_query") val callbackQuery: TgCallbackQuery? = null
)

@Serializable
data class TgCallbackQuery(
    val id: String,
    val from: TgUser,
    @SerialName("chat_instance") val chatInstance: String? = null,
    val data: String? = null
)