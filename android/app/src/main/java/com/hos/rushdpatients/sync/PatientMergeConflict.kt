package com.hos.rushdpatients.sync

data class PatientFieldConflict(
    val patientId: String,
    val patientName: String,
    val field: String,
    val fieldLabel: String,
    val localValue: String,
    val remoteValue: String
) {
    val key: String get() = "${patientId}:${this.field}"
}

enum class ConflictChoice { LOCAL, REMOTE }
