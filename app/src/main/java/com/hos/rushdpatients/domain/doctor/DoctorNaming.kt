package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import java.util.Locale
import java.util.UUID

/**
 * Replaces VBA `getUserInfo` "title" behaviour and `feminize`/`checkFemale`.
 */
object DoctorNaming {

    const val SHIFT_DOCTOR_TITLE = "طبيب المناوبة"
    const val ADMIN_TITLE = "المدير"
    const val FEMININE_SUFFIX = "ة"
    const val DR_PREFIX = "د."

    fun feminize(doctor: Doctor): String =
        if (doctor.gender == Gender.FEMALE) FEMININE_SUFFIX else ""

    fun defaultShiftTitle(doctor: Doctor): String =
        SHIFT_DOCTOR_TITLE + feminize(doctor)

    fun defaultAdminTitle(doctor: Doctor): String =
        ADMIN_TITLE + feminize(doctor)

    /**
     * Custom title if set, otherwise the default shift-doctor title.
     */
    fun displayTitle(doctor: Doctor): String {
        val custom = doctor.customTitle
        return if (custom.isNullOrBlank()) defaultShiftTitle(doctor) else custom
    }

    /**
     * Build "د. First Last" from first and last name parts.
     */
    fun formatName(firstName: String, lastName: String): String =
        "$DR_PREFIX ${firstName.trim()} ${lastName.trim()}".trim()

    fun stableId(fullName: String): String {
        val normalized = fullName.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        return UUID.nameUUIDFromBytes("shift-report-doctor|$normalized".toByteArray()).toString()
    }

    /**
     * Extract first name from a stored full name. Assumes VBA format
     * "د. First Last" or "د. First".
     */
    fun extractFirstName(fullName: String): String {
        val cleaned = fullName.removePrefix(DR_PREFIX).trim()
        return cleaned.substringBefore(' ').trim()
    }

    fun extractLastName(fullName: String): String {
        val cleaned = fullName.removePrefix(DR_PREFIX).trim()
        val idx = cleaned.indexOf(' ')
        return if (idx < 0) "" else cleaned.substring(idx + 1).trim()
    }
}
