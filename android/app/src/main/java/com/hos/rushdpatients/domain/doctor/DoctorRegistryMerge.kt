package com.hos.rushdpatients.domain.doctor

/** Only portable registry fields participate; PINs, aliases and timestamps stay device-local. */
enum class DoctorMergeChoice { LOCAL, REMOTE }

data class DoctorRegistryConflict(
    val key: String,
    val doctorName: String,
    val fieldLabel: String,
    val localValue: String,
    val remoteValue: String
)
