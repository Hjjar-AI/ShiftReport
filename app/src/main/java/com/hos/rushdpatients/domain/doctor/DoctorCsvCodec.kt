package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import java.time.Instant

/** UTF-8/RFC-4180 doctor-registry interchange format for spreadsheet editing. */
object DoctorCsvCodec {

    const val FILE_NAME = "shiftreport-doctors.csv"
    private const val VERSION = "1"

    private val columns = listOf(
        "schema_version",
        "id",
        "first_name",
        "last_name",
        "full_name",
        "gender",
        "clinical_role",
        "supervisor_group_chat_id",
        "telegram_id",
        "telegram_username",
        "custom_title",
        "rank",
        "is_permanent_admin",
        "extra_options",
        "updated_at",
        "deleted_at"
    )

    data class Decoded(
        val doctors: List<Doctor>,
        val activeCount: Int,
        val adminCount: Int,
        val deletedCount: Int
    )

    fun encode(doctors: List<Doctor>): String = buildString {
        append(columns.joinToString(","))
        append('\n')
        doctors.forEach { doctor ->
            val portableOptions = doctor.extraOptions.filterNot {
                it.startsWith("pin:") || it.startsWith("syncAlias:")
            }.sorted().joinToString(";")
            append(
                listOf(
                    VERSION,
                    doctor.id,
                    doctor.firstName,
                    doctor.lastName,
                    doctor.fullName,
                    doctor.gender.code,
                    doctor.clinicalRole.code,
                    doctor.supervisorGroupChatId?.toString().orEmpty(),
                    doctor.telegramId?.toString().orEmpty(),
                    doctor.telegramUsername.orEmpty(),
                    doctor.customTitle.orEmpty(),
                    doctor.rank.toString(),
                    doctor.isPermanentAdmin.toString(),
                    portableOptions,
                    doctor.updatedAt.toString(),
                    doctor.deletedAt?.toString().orEmpty()
                ).joinToString(",", transform = ::escape)
            )
            append('\n')
        }
    }

    fun decode(text: String): Decoded {
        val rows = parse(text.removePrefix("\uFEFF"))
            .filterNot { row -> row.all(String::isBlank) }
        require(rows.isNotEmpty()) { "ملف الأطباء فارغ" }

        val header = rows.first().map { it.trim().lowercase() }
        val indexes = columns.associateWith { header.indexOf(it) }
        val missing = indexes.filterValues { it < 0 }.keys
        require(missing.isEmpty()) {
            "ملف الأطباء يفتقد الأعمدة: ${missing.joinToString(", ")}"
        }

        fun List<String>.value(column: String): String =
            getOrNull(indexes.getValue(column)).orEmpty().trim()

        val doctors = rows.drop(1).mapIndexed { index, row ->
            val line = index + 2
            require(row.value("schema_version") == VERSION) {
                "إصدار غير مدعوم في السطر $line"
            }
            val id = row.value("id")
            val firstName = row.value("first_name")
            val lastName = row.value("last_name")
            val fullName = row.value("full_name")
                .ifBlank { DoctorNaming.formatName(firstName, lastName) }
            require(id.isNotBlank()) { "معرّف الطبيب مطلوب في السطر $line" }
            val validation = DoctorValidator.validate(firstName, lastName, row.value("custom_title"))
            require(validation is DoctorValidationResult.Valid) {
                "بيانات الاسم أو اللقب غير صالحة في السطر $line"
            }

            val gender = when (row.value("gender").uppercase()) {
                "M", "MALE" -> Gender.MALE
                "F", "FEMALE" -> Gender.FEMALE
                else -> error("الجنس غير صالح في السطر $line")
            }
            val clinicalRole = ClinicalRole.entries.firstOrNull {
                it.code.equals(row.value("clinical_role"), ignoreCase = true)
            } ?: error("التصنيف السريري غير صالح في السطر $line")
            val supervisorGroup = row.value("supervisor_group_chat_id")
                .takeIf(String::isNotBlank)?.toLongOrNull()
            val telegramId = row.value("telegram_id")
                .takeIf(String::isNotBlank)?.toLongOrNull()
            require(row.value("supervisor_group_chat_id").isBlank() || supervisorGroup != null) {
                "معرّف مجموعة المشرف غير رقمي في السطر $line"
            }
            require(row.value("telegram_id").isBlank() || telegramId != null) {
                "معرّف تليجرام غير رقمي في السطر $line"
            }
            require(supervisorGroup == null ||
                (clinicalRole == ClinicalRole.SUPERVISOR && supervisorGroup < 0L)) {
                "معرّف مجموعة المشرف أو التصنيف غير صالح في السطر $line"
            }
            val rank = row.value("rank").toIntOrNull()
                ?: error("الرتبة غير صالحة في السطر $line")
            require(rank >= 0) { "الرتبة لا يمكن أن تكون سالبة في السطر $line" }
            val permanent = row.value("is_permanent_admin").lowercase().let {
                when (it) {
                    "true", "1", "yes" -> true
                    "false", "0", "no", "" -> false
                    else -> error("قيمة المدير الدائم غير صالحة في السطر $line")
                }
            }
            require(!permanent || rank > 0) { "المدير الدائم يحتاج رتبة في السطر $line" }
            val updatedAt = row.value("updated_at").takeIf(String::isNotBlank)
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: Instant.now()
            val deletedAt = row.value("deleted_at").takeIf(String::isNotBlank)?.let {
                runCatching { Instant.parse(it) }.getOrElse {
                    error("تاريخ الحذف غير صالح في السطر $line")
                }
            }

            Doctor(
                id = id,
                fullName = fullName,
                firstName = firstName,
                lastName = lastName,
                gender = gender,
                clinicalRole = clinicalRole,
                supervisorGroupChatId = supervisorGroup,
                telegramId = telegramId,
                telegramUsername = row.value("telegram_username").ifBlank { null },
                customTitle = row.value("custom_title").ifBlank { null },
                rank = rank,
                isPermanentAdmin = permanent,
                extraOptions = row.value("extra_options").split(';')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .filterNot { it.startsWith("pin:") || it.startsWith("syncAlias:") }
                    .toSet(),
                updatedAt = updatedAt,
                deletedAt = deletedAt
            )
        }

        require(doctors.isNotEmpty()) { "ملف الأطباء لا يحتوي سجلات" }
        require(doctors.size <= AppConstants.MAX_DOCTORS) { "عدد الأطباء يتجاوز الحد المسموح" }
        require(doctors.map { it.id }.distinct().size == doctors.size) {
            "ملف الأطباء يحتوي معرّفات مكررة"
        }
        require(doctors.map { it.fullName.lowercase() }.distinct().size == doctors.size) {
            "ملف الأطباء يحتوي أسماء مكررة"
        }
        val telegramIds = doctors.mapNotNull { it.telegramId }
        require(telegramIds.distinct().size == telegramIds.size) {
            "ملف الأطباء يحتوي معرّفات تليجرام مكررة"
        }
        val activeRanks = doctors.filter { !it.isDeleted && it.rank > 0 }.map { it.rank }
        require(activeRanks.distinct().size == activeRanks.size) {
            "ملف الأطباء يحتوي رتب مديرين مكررة"
        }

        return Decoded(
            doctors = doctors,
            activeCount = doctors.count { !it.isDeleted },
            adminCount = doctors.count { !it.isDeleted && it.isAdmin },
            deletedCount = doctors.count { it.isDeleted }
        )
    }

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '\n' || it == '\r' || it == '"' }) {
            "\"$escaped\""
        } else escaped
    }

    private fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    row += field.toString()
                    field.clear()
                }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    row += field.toString()
                    field.clear()
                    rows += row
                    row = mutableListOf()
                }
                else -> field.append(char)
            }
            index++
        }
        require(!quoted) { "علامة اقتباس غير مغلقة في ملف الأطباء" }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }
}
