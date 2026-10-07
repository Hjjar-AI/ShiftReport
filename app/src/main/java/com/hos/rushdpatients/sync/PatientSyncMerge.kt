package com.hos.rushdpatients.sync

import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.data.model.Patient

internal data class PatientMergeResult(
    val patients: List<Patient>,
    val conflicts: List<PatientFieldConflict>
)

internal fun samePatientContent(left: Patient?, right: Patient?): Boolean {
    if (left == null || right == null) return left == right
    return left.copy(updatedAt = java.time.Instant.EPOCH, revision = 0, sortOrder = 0) ==
        right.copy(updatedAt = java.time.Instant.EPOCH, revision = 0, sortOrder = 0)
}

internal fun mergePatients(
    basePatients: List<Patient>,
    localPatients: List<Patient>,
    remotePatients: List<Patient>,
    resolutions: Map<String, ConflictChoice> = emptyMap()
): PatientMergeResult {
    val base = basePatients.associateBy(Patient::id)
    val local = localPatients.associateBy(Patient::id)
    val remote = remotePatients.associateBy(Patient::id)
    val merged = mutableListOf<Patient>()
    val conflicts = mutableListOf<PatientFieldConflict>()

    (base.keys + local.keys + remote.keys).forEach { id ->
        val before = base[id]
        val ours = local[id]
        val theirs = remote[id]

        if (before == null || ours == null || theirs == null) {
            val localChanged = !samePatientContent(ours, before)
            val remoteChanged = !samePatientContent(theirs, before)
            val key = "$id:record"
            val selected = when {
                localChanged && remoteChanged && samePatientContent(ours, theirs) -> ours
                localChanged && remoteChanged -> when (resolutions[key]) {
                    ConflictChoice.LOCAL -> ours
                    ConflictChoice.REMOTE -> theirs
                    null -> {
                        conflicts += PatientFieldConflict(
                            id,
                            ours?.name ?: theirs?.name ?: before?.name ?: id,
                            "record",
                            "السجل الكامل / الحذف",
                            ours?.name ?: "محذوف محلياً",
                            theirs?.name ?: "محذوف عن بعد"
                        )
                        null
                    }
                }
                localChanged -> ours
                remoteChanged -> theirs
                else -> ours ?: theirs
            }
            selected?.let(merged::add)
            return@forEach
        }
        val localRecordChanged = !samePatientContent(ours, before)

        fun <T> field(
            name: String,
            label: String,
            baseValue: T,
            localValue: T,
            remoteValue: T,
            display: (T) -> String = { it.toString() }
        ): T {
            val localChanged = localValue != baseValue
            val remoteChanged = remoteValue != baseValue
            if (!localChanged) return remoteValue
            if (!remoteChanged || localValue == remoteValue) return localValue
            val key = "$id:$name"
            return when (resolutions[key]) {
                ConflictChoice.LOCAL -> localValue
                ConflictChoice.REMOTE -> remoteValue
                null -> {
                    conflicts += PatientFieldConflict(
                        patientId = id,
                        patientName = ours.name.ifBlank { theirs.name },
                        field = name,
                        fieldLabel = label,
                        localValue = display(localValue),
                        remoteValue = display(remoteValue)
                    )
                    localValue
                }
            }
        }

        merged += before.copy(
            admittanceNumber = field("admittanceNumber", "رقم القبول الحالي", before.admittanceNumber, ours.admittanceNumber, theirs.admittanceNumber),
            admittanceDate = field("admittanceDate", "تاريخ الدخول", before.admittanceDate, ours.admittanceDate, theirs.admittanceDate),
            gender = field("gender", "الجنس", before.gender, ours.gender, theirs.gender) {
                if (it == com.hos.rushdpatients.data.model.Gender.MALE) "ذكر" else "أنثى"
            },
            name = field("name", "الاسم", before.name, ours.name, theirs.name),
            birthDate = field("birthDate", "تاريخ الميلاد", before.birthDate, ours.birthDate, theirs.birthDate),
            hasCompanion = field("hasCompanion", "المرافق", before.hasCompanion, ours.hasCompanion, theirs.hasCompanion),
            diagnosisType = field("diagnosisType", "نوع التشخيص", before.diagnosisType, ours.diagnosisType, theirs.diagnosisType) { it.arabicLabel },
            initialDiagnosis = field("initialDiagnosis", "التشخيص الأولي", before.initialDiagnosis, ours.initialDiagnosis, theirs.initialDiagnosis),
            treatmentPlan = field("treatmentPlan", "الخطة العلاجية", before.treatmentPlan, ours.treatmentPlan, theirs.treatmentPlan),
            followUp = field("followUp", "المتابعة", before.followUp, ours.followUp, theirs.followUp),
            tasks = field("tasks", "المهام", before.tasks, ours.tasks, theirs.tasks) {
                PatientTasks.summary(it)
            },
            labs = field("labs", "التحاليل", before.labs, ours.labs, theirs.labs),
            responsibleResidentId = field("resident", "المقيم", before.responsibleResidentId, ours.responsibleResidentId, theirs.responsibleResidentId),
            responsibleSpecialistId = field("specialist", "الاختصاصي", before.responsibleSpecialistId, ours.responsibleSpecialistId, theirs.responsibleSpecialistId),
            badges = field("badges", "الشارات", before.badges, ours.badges, theirs.badges) { badges ->
                badges.joinToString("، ") { badge ->
                    badge.priority?.let { "${badge.text} (${it.arabicLabel})" } ?: badge.text
                }
            },
            isPriority = field("priority", "الأولوية", before.isPriority, ours.isPriority, theirs.isPriority),
            lastEditedByDoctorId = if (localRecordChanged) ours.lastEditedByDoctorId else theirs.lastEditedByDoctorId,
            lastEditedByName = if (localRecordChanged) ours.lastEditedByName else theirs.lastEditedByName,
            revision = maxOf(ours.revision, theirs.revision) + 1,
            updatedAt = java.time.Instant.now(),
            sortOrder = ours.sortOrder
        )
    }
    return PatientMergeResult(
        patients = merged.sortedWith(compareBy<Patient> { it.sortOrder }.thenBy { it.name }),
        conflicts = conflicts
    )
}

