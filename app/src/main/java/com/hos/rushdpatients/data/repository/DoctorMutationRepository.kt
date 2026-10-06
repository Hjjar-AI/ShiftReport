package com.hos.rushdpatients.data.repository

import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.auth.PasswordHasher
import com.hos.rushdpatients.domain.doctor.DoctorEditInput
import com.hos.rushdpatients.domain.doctor.DoctorNaming
import com.hos.rushdpatients.domain.doctor.DoctorValidationResult
import com.hos.rushdpatients.domain.doctor.DoctorValidator
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Administrative doctor edits commit with live authorization, stale-row checks, pending state and audit. */
@Singleton
class DoctorMutationRepository @Inject constructor(
    private val database: AppDatabase,
    private val doctors: DoctorRepository,
    private val patients: PatientRepository,
    private val authorizer: AdminAuthorizer,
    private val passwordHasher: PasswordHasher,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider
) {
    private fun validate(input: DoctorEditInput) {
        require(DoctorValidator.validate(input.firstName, input.lastName, input.customTitle) is DoctorValidationResult.Valid) {
            "بيانات الاسم أو اللقب غير صحيحة"
        }
        require(input.telegramId == null || input.telegramId > 0) { "معرّف تليجرام غير صالح" }
        require(input.pin == null || input.pin.all(Char::isDigit) &&
            input.pin.length in AppConstants.PIN_MIN_LENGTH..AppConstants.PIN_MAX_LENGTH) {
            "الرقم السري يجب أن يكون من 4 إلى 8 أرقام"
        }
    }

    private suspend fun requireUnique(doctor: Doctor) {
        val all = doctors.getAll()
        require(all.none { it.id != doctor.id && it.fullName.equals(doctor.fullName, ignoreCase = true) }) {
            "الاسم موجود مسبقاً"
        }
        require(doctor.telegramId == null || all.none { it.id != doctor.id && it.telegramId == doctor.telegramId }) {
            "حساب تليجرام مرتبط بمستخدم آخر"
        }
    }

    private suspend fun requireCurrent(expected: Doctor): Doctor {
        val current = doctors.getById(expected.id)
        if (current == null || current.isDeleted || current != expected) throw StaleDoctorEditException()
        return current
    }

    suspend fun add(input: DoctorEditInput) = withContext(dispatchers.io) {
        validate(input)
        val pin = requireNotNull(input.pin) { "الرقم السري مطلوب للطبيب الجديد" }
        val pinHash = passwordHasher.hash(pin)
        database.withTransaction {
            val actor = authorizer.requireAdmin()
            val name = DoctorNaming.formatName(input.firstName, input.lastName)
            val doctor = Doctor(
                id = DoctorNaming.stableId(name), fullName = name,
                firstName = input.firstName.trim(), lastName = input.lastName.trim(),
                gender = input.gender, clinicalRole = input.clinicalRole,
                telegramId = input.telegramId, customTitle = input.customTitle?.takeIf { it.isNotBlank() },
                extraOptions = setOf("pin:$pinHash")
            )
            require(doctors.getById(doctor.id) == null) {
                "يوجد سجل سابق لهذا الطبيب؛ لا يمكن استبداله بإضافة جديدة"
            }
            require(doctors.count() < AppConstants.MAX_DOCTORS) { "تم الوصول إلى الحد الأقصى لعدد الأطباء" }
            requireUnique(doctor)
            doctors.upsert(doctor)
            audit.record(actor.id, actor.fullName, AppConstants.AUDIT_DOCTOR_ADDED, doctor.fullName)
        }
    }

    suspend fun edit(expected: Doctor, input: DoctorEditInput) = withContext(dispatchers.io) {
        validate(input)
        val pinHash = input.pin?.let(passwordHasher::hash)
        database.withTransaction {
            val actor = authorizer.requireAdmin()
            val current = requireCurrent(expected)
            require(current.clinicalRole == input.clinicalRole || patients.countActiveReferencesToDoctor(current.id) == 0) {
                "أعد إسناد المرضى قبل تغيير التصنيف السريري للطبيب"
            }
            val updated = current.copy(
                fullName = DoctorNaming.formatName(input.firstName, input.lastName),
                firstName = input.firstName.trim(), lastName = input.lastName.trim(),
                gender = input.gender, clinicalRole = input.clinicalRole,
                supervisorGroupChatId = current.supervisorGroupChatId.takeIf { input.clinicalRole == ClinicalRole.SUPERVISOR },
                telegramId = input.telegramId,
                customTitle = input.customTitle?.takeIf { it.isNotBlank() },
                extraOptions = if (pinHash == null) current.extraOptions else
                    current.extraOptions.filterNot { it.startsWith("pin:") }.toSet() + "pin:$pinHash"
            )
            requireUnique(updated)
            doctors.upsert(updated)
            audit.record(actor.id, actor.fullName, AppConstants.AUDIT_DOCTOR_EDITED, updated.fullName)
        }
    }

    suspend fun delete(expected: Doctor) = withContext(dispatchers.io) {
        database.withTransaction {
            val actor = authorizer.requireAdmin()
            val current = requireCurrent(expected)
            require(!current.isAdmin) { "أزل صلاحية المدير قبل حذف الطبيب" }
            doctors.requireNoClinicalReferences(current.id)
            doctors.softDelete(current.id)
            audit.record(actor.id, actor.fullName, AppConstants.AUDIT_DOCTOR_DELETED, current.fullName)
        }
    }
}

class StaleDoctorEditException : IllegalStateException(
    "تغيّر سجل الطبيب منذ فتحه. المسودة لم تُحفظ؛ راجع أحدث نسخة قبل المحاولة مجدداً."
)
