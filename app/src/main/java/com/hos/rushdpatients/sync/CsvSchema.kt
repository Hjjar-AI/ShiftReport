package com.hos.rushdpatients.sync

object CsvSchema {

    const val META_PREFIX = "#"
    const val CURRENT_VERSION = 7
    val SUPPORTED_VERSIONS = 3..CURRENT_VERSION

    // Column order — this is the on-wire format.
    val COLUMNS: List<String> = listOf(
        "externalId",
        "admitNum",
        "admitDate",
        "gender",
        "name",
        "birthDate",
        "hasCompanion",
        "diagnosisType",
        "initialDiagnosis",
        "treatmentPlan",
        "followUp",
        "labs",
        "responsibleResidentId",
        "responsibleSupervisorId",
        "warningFlags",
        "warningDetails",
        "isPriority",
        "lastEditedByDoctorId",
        "lastEditedBy",
        "updatedAt",
        "revision",
        "tasksJson"
    )

    const val COL_EXTERNAL_ID = "externalId"
    const val COL_ADMIT_NUM = "admitNum"
    const val COL_ADMIT_DATE = "admitDate"
    const val COL_GENDER = "gender"
    const val COL_NAME = "name"
    const val COL_BIRTH_DATE = "birthDate"
    const val COL_COMPANION = "hasCompanion"
    const val COL_DIAGNOSIS_TYPE = "diagnosisType"
    const val COL_INITIAL_DIAGNOSIS = "initialDiagnosis"
    const val COL_TREATMENT_PLAN = "treatmentPlan"
    const val COL_FOLLOW_UP = "followUp"
    const val COL_LABS = "labs"
    const val COL_RESIDENT_ID = "responsibleResidentId"
    const val COL_SUPERVISOR_ID = "responsibleSupervisorId"
    const val COL_WARNING_FLAGS = "warningFlags"
    const val COL_WARNING_DETAILS = "warningDetails"
    const val COL_IS_PRIORITY = "isPriority"
    const val COL_LAST_EDITED_BY_DOCTOR_ID = "lastEditedByDoctorId"
    const val COL_LAST_EDITED_BY = "lastEditedBy"
    const val COL_UPDATED_AT = "updatedAt"
    const val COL_REVISION = "revision"
    const val COL_TASKS = "tasksJson"

    const val META_SHIFT_DATE = "shiftDate"
    const val META_SHIFT_ID = "shiftId"
    const val META_DOCTORS = "doctors"
    const val META_SCHEMA_VERSION = "schemaVersion"
    const val META_SORT_SPEC = "sortSpec"
    const val META_SHIFT_REVISION = "shiftRevision"
    const val META_SNAPSHOT_ID = "snapshotId"
    const val META_BASE_SNAPSHOT_ID = "baseSnapshotId"
    const val META_DEVICE_ID = "deviceId"
}
