package com.hos.rushdpatients.data.mapper

import com.hos.rushdpatients.data.db.entity.ShiftEntity
import com.hos.rushdpatients.data.model.Shift
import java.time.Instant
import java.time.LocalDate

object ShiftMapper {

    fun toEntity(shift: Shift): ShiftEntity = ShiftEntity(
        id = shift.id,
        dateEpochDay = shift.date.toEpochDay(),
        doctorIdsCsv = shift.doctorIds.joinToString(","),
        multiDoctorMode = shift.multiDoctorMode,
        createdAtEpochMillis = shift.createdAt.toEpochMilli(),
        updatedAtEpochMillis = shift.updatedAt.toEpochMilli(),
        sentAtEpochMillis = shift.sentAt?.toEpochMilli(),
        reportMessageId = shift.reportMessageId,
        pdfMessageId = shift.pdfMessageId,
        csvMessageId = shift.csvMessageId,
        sortSpecJson = shift.sortSpecJson,
        revision = shift.revision,
        snapshotId = shift.snapshotId,
        baseSnapshotId = shift.baseSnapshotId,
        publishedByDeviceId = shift.publishedByDeviceId
    )

    fun fromEntity(entity: ShiftEntity): Shift = Shift(
        id = entity.id,
        date = LocalDate.ofEpochDay(entity.dateEpochDay),
        doctorIds = if (entity.doctorIdsCsv.isBlank()) emptyList()
        else entity.doctorIdsCsv.split(","),
        multiDoctorMode = entity.multiDoctorMode,
        createdAt = Instant.ofEpochMilli(entity.createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis),
        sentAt = entity.sentAtEpochMillis?.let(Instant::ofEpochMilli),
        reportMessageId = entity.reportMessageId,
        pdfMessageId = entity.pdfMessageId,
        csvMessageId = entity.csvMessageId,
        sortSpecJson = entity.sortSpecJson,
        revision = entity.revision,
        snapshotId = entity.snapshotId,
        baseSnapshotId = entity.baseSnapshotId,
        publishedByDeviceId = entity.publishedByDeviceId
    )
}
