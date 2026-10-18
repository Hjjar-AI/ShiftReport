package com.hos.rushdpatients.data.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Shift(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val doctorIds: List<String>,
    val multiDoctorMode: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val sentAt: Instant? = null,
    val reportMessageId: Long? = null,
    val pdfMessageId: Long? = null,
    val csvMessageId: Long? = null,
    val sortSpecJson: String? = null,
    val revision: Long = 0,
    val snapshotId: String? = null,
    val baseSnapshotId: String? = null,
    val publishedByDeviceId: String? = null
)
