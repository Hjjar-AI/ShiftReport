package com.hos.rushdpatients.testutil

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import java.time.Instant
import java.time.LocalDate

object Fixtures {

    fun doctor(
        id: String = "doc-1",
        fullName: String = "د. أحمد علي",
        firstName: String = "أحمد",
        lastName: String = "علي",
        gender: Gender = Gender.MALE,
        telegramId: Long? = null,
        rank: Int = 0,
        isPermanentAdmin: Boolean = false,
        customTitle: String? = null
    ) = Doctor(
        id = id,
        fullName = fullName,
        firstName = firstName,
        lastName = lastName,
        gender = gender,
        telegramId = telegramId,
        telegramUsername = null,
        customTitle = customTitle,
        rank = rank,
        isPermanentAdmin = isPermanentAdmin,
        extraOptions = emptySet(),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        deletedAt = null
    )

    fun patient(
        id: String = "p-1",
        name: String = "مريض",
        age: Int = 30,
        gender: Gender = Gender.MALE,
        diagnosis: String = "",
        condition: String = "",
        hasEscort: Boolean = false,
        admittanceNumber: String = "",
        admittanceDate: LocalDate? = null,
        admittanceDays: Int? = null,
        treatment: String = "",
        labs: String = "",
        notes: String = "",
        residentId: String? = null,
        supervisorId: String? = null,
        sortOrder: Int = 1
    ) = Patient(
        id = id,
        name = name,
        age = age,
        gender = gender,
        diagnosis = diagnosis,
        condition = condition,
        hasEscort = hasEscort,
        admittanceNumber = admittanceNumber,
        admittanceDate = admittanceDate,
        admittanceDays = admittanceDays,
        treatment = treatment,
        labs = labs,
        notes = notes,
        residentId = residentId,
        supervisorId = supervisorId,
        sortOrder = sortOrder,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        deletedAt = null
    )

    fun shift(
        id: String = "shift-1",
        date: LocalDate = LocalDate.of(2026, 9, 26),
        doctorIds: List<String> = listOf("doc-1"),
        multiDoctorMode: Boolean = false
    ) = Shift(
        id = id,
        date = date,
        doctorIds = doctorIds,
        multiDoctorMode = multiDoctorMode,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        sentAt = null,
        reportMessageId = null,
        pdfMessageId = null,
        csvMessageId = null,
        sortSpecJson = null
    )
}