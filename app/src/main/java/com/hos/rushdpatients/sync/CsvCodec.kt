package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.PatientTaskCodec
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadgeCodec
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import java.time.LocalDate
import java.time.Instant

data class ParsedShift(
    val shiftDate: LocalDate,
    val shiftId: String?,
    val doctorIds: List<String>,
    val sortSpecJson: String?,
    val shiftRevision: Long,
    val snapshotId: String?,
    val baseSnapshotId: String?,
    val deviceId: String?,
    val patients: List<Patient>
)

object CsvCodec {

    // ---------------- Encoding ----------------

    fun encode(shift: Shift, patients: List<Patient>): String {
        val sb = StringBuilder()
        sb.append(CsvSchema.META_PREFIX)
            .append(' ')
            .append(CsvSchema.META_SCHEMA_VERSION)
            .append('=')
            .append(CsvSchema.CURRENT_VERSION)
            .append(' ')
            .append(CsvSchema.META_SHIFT_DATE)
            .append('=')
            .append(shift.date)
            .append(' ')
            .append(CsvSchema.META_SHIFT_ID)
            .append('=')
            .append(shift.id)
            .append(' ')
            .append(CsvSchema.META_DOCTORS)
            .append('=')
            .append(shift.doctorIds.joinToString(","))
            .append(' ')
            .append(CsvSchema.META_SORT_SPEC)
            .append('=')
            .append(shift.sortSpecJson.orEmpty())
            .append(' ')
            .append(CsvSchema.META_SHIFT_REVISION).append('=').append(shift.revision)
            .append(' ')
            .append(CsvSchema.META_SNAPSHOT_ID).append('=').append(shift.snapshotId.orEmpty())
            .append(' ')
            .append(CsvSchema.META_BASE_SNAPSHOT_ID).append('=').append(shift.baseSnapshotId.orEmpty())
            .append(' ')
            .append(CsvSchema.META_DEVICE_ID).append('=').append(shift.publishedByDeviceId.orEmpty())
            .append('\n')

        sb.append(CsvSchema.COLUMNS.joinToString(",")).append('\n')

        for (p in patients) {
            if (p.isDeleted) continue
            sb.append(encodeRow(p)).append('\n')
        }

        return sb.toString()
    }

    private fun encodeRow(p: Patient): String = buildString {
        append(escape(p.id))
        append(',')
        append(escape(p.admittanceNumber))
        append(',')
        append(p.admittanceDate?.toString().orEmpty())
        append(',')
        append(p.gender.code)
        append(',')
        append(escape(p.name))
        append(',')
        append(p.birthDate?.year?.toString().orEmpty())
        append(',')
        append(if (p.hasCompanion) "Y" else "N")
        append(',')
        append(p.diagnosisType.code)
        append(',')
        append(escape(p.initialDiagnosis))
        append(',')
        append(escape(p.treatmentPlan))
        append(',')
        append(escape(p.followUp))
        append(',')
        append(escape(p.labs))
        append(',')
        append(escape(p.responsibleResidentId.orEmpty()))
        append(',')
        append(escape(p.responsibleSpecialistId.orEmpty()))
        append(',')
        append(escape(p.badges.mapNotNull { it.priority?.code }.joinToString("|")))
        append(',')
        append(escape(PatientBadgeCodec.encode(p.badges)))
        append(',')
        append(if (p.isPriority) "Y" else "N")
        append(',')
        append(escape(p.lastEditedByDoctorId.orEmpty()))
        append(',')
        append(escape(p.lastEditedByName.orEmpty()))
        append(',')
        append(p.updatedAt.toEpochMilli())
        append(',')
        append(p.revision)
        append(',')
        append(escape(PatientTaskCodec.encode(p.tasks)))
    }

    private fun escape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    // ---------------- Decoding ----------------

    fun decode(csv: String): ParsedShift {
        val cleaned = csv.removePrefix("\uFEFF")

        val rows = parseCsv(cleaned)
            .filterNot { it.isEmpty() || (it.size == 1 && it[0].isBlank()) }

        require(rows.size >= 2) {
            "CSV must contain a metadata line and a header line"
        }

        val metaLine = rows[0].firstOrNull()?.trim().orEmpty()
        require(metaLine.startsWith(CsvSchema.META_PREFIX)) {
            "First line must be the metadata line starting with '${CsvSchema.META_PREFIX}'"
        }

        val meta = parseMeta(metaLine)

        val version = meta[CsvSchema.META_SCHEMA_VERSION]?.toIntOrNull()
        require(version in CsvSchema.SUPPORTED_VERSIONS) {
            "إصدار ملف CSV غير مدعوم: ${version ?: "غير محدد"}"
        }

        val shiftDateText = meta[CsvSchema.META_SHIFT_DATE]
            ?: throw IllegalArgumentException("تاريخ الوردية مفقود من CSV")
        val shiftDate = runCatching { LocalDate.parse(shiftDateText) }
            .getOrElse { throw IllegalArgumentException("تاريخ الوردية غير صالح: $shiftDateText") }

        val shiftId = meta[CsvSchema.META_SHIFT_ID]?.takeIf { it.isNotBlank() }

        val doctorIds = meta[CsvSchema.META_DOCTORS]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val sortSpecJson = meta[CsvSchema.META_SORT_SPEC]?.takeIf { it.isNotBlank() }
        val shiftRevision = meta[CsvSchema.META_SHIFT_REVISION]?.toLongOrNull() ?: 0L
        val snapshotId = meta[CsvSchema.META_SNAPSHOT_ID]?.takeIf(String::isNotBlank)
        val baseSnapshotId = meta[CsvSchema.META_BASE_SNAPSHOT_ID]?.takeIf(String::isNotBlank)
        val deviceId = meta[CsvSchema.META_DEVICE_ID]?.takeIf(String::isNotBlank)
        if (sortSpecJson != null) {
            require(SortSpecCodec.decodeOrNull(sortSpecJson) != null) {
                "ترتيب المرضى في CSV غير صالح"
            }
        }

        val header = rows[1].map { it.trim() }
        val indexOf = header.withIndex().associate { (i, name) -> name to i }
        val requiredColumns = CsvSchema.COLUMNS.take(14) +
            if (version != null && version >= 7) listOf(CsvSchema.COL_TASKS) else emptyList()
        val missingColumns = requiredColumns.filterNot(indexOf::containsKey)
        require(missingColumns.isEmpty()) {
            "أعمدة CSV مفقودة: ${missingColumns.joinToString()}"
        }

        val errors = mutableListOf<String>()

        // NOTE: assign sortOrder = row index + 1 so the badge in the UI shows
        // the position the patient occupies in the file. Without this, every
        // imported patient lands at sortOrder = 0.
        val patients = rows.drop(2).mapIndexedNotNull { index, row ->
            runCatching { decodeRow(row, indexOf, index + 3) }
                .onFailure { errors += it.message ?: "خطأ في السطر ${index + 3}" }
                .getOrNull()
                ?.copy(sortOrder = index + 1)
        }

        val duplicateIds = patients.groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            errors += "معرّفات مرضى مكررة: ${duplicateIds.joinToString()}"
        }
        require(errors.isEmpty()) { errors.joinToString("\n") }

        return ParsedShift(
            shiftDate = shiftDate,
            shiftId = shiftId,
            doctorIds = doctorIds,
            sortSpecJson = sortSpecJson,
            shiftRevision = shiftRevision,
            snapshotId = snapshotId,
            baseSnapshotId = baseSnapshotId,
            deviceId = deviceId,
            patients = patients
        )
    }

    private fun decodeRow(
        row: List<String>,
        index: Map<String, Int>,
        rowNumber: Int
    ): Patient {
        fun field(name: String): String {
            val i = index[name] ?: return ""
            return row.getOrNull(i).orEmpty()
        }

        val name = field(CsvSchema.COL_NAME).trim()
        require(name.isNotEmpty()) { "السطر $rowNumber: اسم المريض مطلوب" }

        val admissionNumber = field(CsvSchema.COL_ADMIT_NUM).trim()
        require(admissionNumber.isNotEmpty()) { "السطر $rowNumber: رقم القبول الحالي مطلوب" }

        val genderText = field(CsvSchema.COL_GENDER).trim().uppercase()
        require(genderText in setOf("M", "MALE", "F", "FEMALE")) {
            "السطر $rowNumber: الجنس غير صالح"
        }
        val gender = Gender.fromCode(genderText)

        val companionText = field(CsvSchema.COL_COMPANION).trim().uppercase()
        require(companionText in setOf("Y", "N", "TRUE", "FALSE", "1", "0")) {
            "السطر $rowNumber: قيمة المرافق غير صالحة"
        }
        val hasCompanion = companionText in setOf("Y", "TRUE", "1")

        val admitDateText = field(CsvSchema.COL_ADMIT_DATE).trim()
        require(admitDateText.isNotEmpty()) { "السطر $rowNumber: تاريخ الدخول مطلوب" }
        val admitDate = runCatching { LocalDate.parse(admitDateText) }
            .getOrElse { throw IllegalArgumentException("السطر $rowNumber: تاريخ الدخول غير صالح") }

        val birthDateText = field(CsvSchema.COL_BIRTH_DATE).trim()
        require(birthDateText.isNotEmpty()) { "السطر $rowNumber: تاريخ الميلاد مطلوب" }
        val birthDate = parseBirthDate(birthDateText)
            ?: throw IllegalArgumentException("السطر $rowNumber: سنة الميلاد غير صالحة")

        val diagnosisText = field(CsvSchema.COL_DIAGNOSIS_TYPE).trim().uppercase()
        val diagnosisType = DiagnosisType.entries.firstOrNull { it.code == diagnosisText }
            ?: throw IllegalArgumentException("السطر $rowNumber: نوع التشخيص غير صالح")

        val id = field(CsvSchema.COL_EXTERNAL_ID).trim()
        require(id.isNotEmpty()) { "السطر $rowNumber: المعرّف الخارجي مطلوب" }
        val residentId = field(CsvSchema.COL_RESIDENT_ID).ifBlank { null }
        val specialistId = field(CsvSchema.COL_SUPERVISOR_ID).ifBlank { null }
        val badges = PatientBadgeCodec.decode(
            encoded = field(CsvSchema.COL_WARNING_DETAILS).trim(),
            legacyFlags = field(CsvSchema.COL_WARNING_FLAGS).trim()
        )
        val priority = field(CsvSchema.COL_IS_PRIORITY).trim().uppercase() in
            setOf("Y", "TRUE", "1")
        val updatedAt = field(CsvSchema.COL_UPDATED_AT).trim().toLongOrNull()
            ?.let(Instant::ofEpochMilli)
            ?: Instant.now()

        return Patient(
            id = id,
            admittanceNumber = admissionNumber,
            admittanceDate = admitDate,
            gender = gender,
            name = name,
            birthDate = birthDate,
            hasCompanion = hasCompanion,
            diagnosisType = diagnosisType,
            initialDiagnosis = field(CsvSchema.COL_INITIAL_DIAGNOSIS),
            treatmentPlan = field(CsvSchema.COL_TREATMENT_PLAN),
            followUp = field(CsvSchema.COL_FOLLOW_UP),
            labs = field(CsvSchema.COL_LABS),
            tasks = PatientTaskCodec.decode(field(CsvSchema.COL_TASKS)),
            responsibleResidentId = residentId,
            responsibleSpecialistId = specialistId,
            badges = badges,
            isPriority = priority,
            lastEditedByDoctorId = field(CsvSchema.COL_LAST_EDITED_BY_DOCTOR_ID)
                .ifBlank { null },
            lastEditedByName = field(CsvSchema.COL_LAST_EDITED_BY).ifBlank { null },
            revision = field(CsvSchema.COL_REVISION).toLongOrNull() ?: 0L,
            updatedAt = updatedAt
        )
    }

    private fun parseBirthDate(text: String): LocalDate? {
        runCatching { LocalDate.parse(text) }
            .getOrNull()
            ?.let { return LocalDate.of(it.year, 1, 1) }

        text.toIntOrNull()
            ?.takeIf { it in 1900..2100 }
            ?.let { return LocalDate.of(it, 1, 1) }

        return null
    }

    private fun parseMeta(line: String): Map<String, String> {
        val body = line.removePrefix(CsvSchema.META_PREFIX).trim()
        if (body.isEmpty()) return emptyMap()
        return body.split(" ")
            .mapNotNull { token ->
                val idx = token.indexOf('=')
                if (idx <= 0) null
                else token.substring(0, idx) to token.substring(idx + 1)
            }
            .toMap()
    }

    fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        while (i < csv.length) {
            val c = csv[i]
            when {
                inQuotes && c == '"' && i + 1 < csv.length && csv[i + 1] == '"' -> {
                    currentField.append('"')
                    i += 2
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }
                c == ',' && !inQuotes -> {
                    currentRow += currentField.toString()
                    currentField.clear()
                    i++
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++
                    currentRow += currentField.toString()
                    currentField.clear()
                    rows += currentRow.toList()
                    currentRow.clear()
                    i++
                }
                else -> {
                    currentField.append(c)
                    i++
                }
            }
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow += currentField.toString()
            rows += currentRow.toList()
        }

        return rows
    }
}
