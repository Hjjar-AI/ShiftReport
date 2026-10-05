package com.hos.rushdpatients.network.telegram.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TgChatMember(
    val user: TgUser,
    val status: String,
    @SerialName("custom_title") val customTitle: String? = null,
    @SerialName("is_anonymous") val isAnonymous: Boolean? = null,
    @SerialName("can_manage_chat") val canManageChat: Boolean? = null,
    @SerialName("can_delete_messages") val canDeleteMessages: Boolean? = null,
    @SerialName("can_manage_video_chats") val canManageVideoChats: Boolean? = null,
    @SerialName("can_restrict_members") val canRestrictMembers: Boolean? = null,
    @SerialName("can_promote_members") val canPromoteMembers: Boolean? = null,
    @SerialName("can_change_info") val canChangeInfo: Boolean? = null,
    @SerialName("can_invite_users") val canInviteUsers: Boolean? = null,
    @SerialName("can_pin_messages") val canPinMessages: Boolean? = null
) {
    val isAdmin: Boolean get() = status == "administrator" || status == "creator"
    val isCreator: Boolean get() = status == "creator"
}