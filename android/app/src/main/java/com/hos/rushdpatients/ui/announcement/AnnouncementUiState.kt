package com.hos.rushdpatients.ui.announcement

import com.hos.rushdpatients.domain.report.ReportDeliveryRecord

data class AnnouncementUiState(
    val loading: Boolean = false,
    val template: String = "",
    val sending: Boolean = false,
    val resolving: Boolean = false,
    val reviewing: Boolean = false,
    val reviewText: String? = null,
    val reviewExisting: Boolean = false,
    val ledgerUnavailable: Boolean = false,
    val deliveryRecords: List<ReportDeliveryRecord> = emptyList(),
    val lastMessageId: Long? = null,
    val snackbar: String? = null
)
