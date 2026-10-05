package com.hos.rushdpatients.ui.announcement

data class AnnouncementUiState(
    val loading: Boolean = true,
    val template: String = "",
    val sending: Boolean = false,
    val lastMessageId: Long? = null,
    val snackbar: String? = null
)
