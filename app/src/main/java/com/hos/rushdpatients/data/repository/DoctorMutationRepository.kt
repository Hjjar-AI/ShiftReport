package com.hos.rushdpatients.data.repository

import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.auth.PasswordHasher
import com.hos.rushdpatients.domain.doctor.DoctorImportPlan
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

    suspend fun prepareImport(imported: List<Doctor>): DoctorImportPlan = withContext(dispatchers.io) {
        database.withTransaction {
            val actor = authorizer.requireAdmin()
            val existing = doctors.getAllIncludingDeleted()
            val merged = mergeImport(imported, existing, actor)
            DoctorImportPlan(imported.toList(), existing.toList(), actor.id, merged)
        }
    }

    suspend fun importRegistry(plan: DoctorImportPlan): Int = withContext(dispatchers.io) {
        database.withTransaction {
            val actor = authorizer.requireAdmin()
            val existing = doctors.getAllIncludingDeleted()
            if (actor.id != plan.actorId || existing.associateBy { it.id } != plan.expectedRegistry.associateBy { it.id }) {
                throw StaleDoctorImportException()
            }
            val merged = mergeImport(plan.imported, existing, actor)
            if (merged != plan.reviewedChanges) throw StaleDoctorImportException()
            doctors.upsertAll(merged)
            audit.record(actor.id, actor.fullName, AppConstants.AUDIT_DOCTORS_IMPORTED, "import_csv:${merged.size}")
            merged.size
        }
    }

    private suspend fun mergeImport(imported: List<Doctor>, existing: List<Doctor>, actor: Doctor): List<Doctor> {
        require(imported.isNotEmpty() && imported.size <= AppConstants.MAX_DOCTORS) { "عدد سجلات الاستيراد غير صالح" }
        require(imported.map { it.id }.distinct().size == imported.size) { "معرّفات مستوردة مكررة" }
        imported.forEach {
            require(it.id.isNotBlank() && it.rank >= 0 && (!it.isPermanentAdmin || it.rank > 0)) { "هوية أو صلاحية مستوردة غير صالحة" }
            require(DoctorValidator.validate(it.firstName, it.lastName, it.customTitle) is DoctorValidationResult.Valid) { "اسم أو لقب مستورد غير صالح" }
            require(it.supervisorGroupChatId == null ||
                it.clinicalRole == ClinicalRole.SUPERVISOR && it.supervisorGroupChatId < 0) { "مجموعة مشرف مستوردة غير صالحة" }
        }
        val merged = imported.map { incoming ->
            val matches = existing.filter { candidate ->
                candidate.id == incoming.id ||
                    (incoming.telegramId != null && candidate.telegramId == incoming.telegramId) ||
                    candidate.fullName.equals(incoming.fullName, ignoreCase = true)
            }
            require(matches.map { it.id }.distinct().size <= 1) {
                "بيانات ${incoming.fullName} تطابق أكثر من طبيب محلي"
            }
            val local = matches.singleOrNull()
            val localSecrets = local?.extraOptions.orEmpty()
                .filter { it.startsWith("syncAlias:") ||
                    it.startsWith("pin:") && local?.telegramId == incoming.telegramId }
                .toSet()
            var result = incoming.copy(
                id = local?.id ?: incoming.id,
                extraOptions = incoming.extraOptions.filterNot {
                    it.startsWith("pin:") || it.startsWith("syncAlias:")
                }.toSet() + localSecrets,
                // A portable file may be old; importing it must not delete a
                // currently active local clinician implicitly.
                deletedAt = if (local != null && !local.isDeleted) null else incoming.deletedAt
            )
            if (local != null && (local.isPermanentAdmin || local.id == actor.id)) {
                result = result.copy(
                    rank = local.rank,
                    isPermanentAdmin = local.isPermanentAdmin,
                    deletedAt = null
                )
            }
            if (local != null && !local.isDeleted) {
                require(local.clinicalRole == result.clinicalRole ||
                    patients.countActiveReferencesToDoctor(local.id) == 0) {
                    "أعد إسناد المرضى قبل تغيير التصنيف السريري للطبيب ${local.fullName}"
                }
            }
            val permissionChanged = local?.rank != result.rank ||
                local?.isPermanentAdmin != result.isPermanentAdmin
            if (permissionChanged && !actor.isPermanentAdmin) {
                require(local?.isPermanentAdmin != true && (local?.rank ?: 0) < actor.rank &&
                    !result.isPermanentAdmin && result.rank < actor.rank) {
                    "لا يمكن للاستيراد تغيير صلاحيات مدير أعلى رتبة أو منح صلاحية أعلى من المنفذ"
                }
            }
            require(result.telegramId == null || result.telegramId > 0) { "معرّف تليجرام غير صالح" }
            result
        }
        require(merged.map { it.id }.distinct().size == merged.size) {
            "تطابق أكثر من صف مستورد مع الطبيب المحلي نفسه"
        }
        val finalById = existing.associateBy { it.id }.toMutableMap().apply {
            merged.forEach { put(it.id, it) }
        }
        require(finalById.values.count { !it.isDeleted } <= AppConstants.MAX_DOCTORS) {
            "سيؤدي الاستيراد إلى تجاوز الحد الأقصى لعدد الأطباء"
        }
        val finalActive = finalById.values.filterNot { it.isDeleted }
        require(finalActive.map { it.fullName.lowercase() }.distinct().size == finalActive.size) {
            "سيؤدي الاستيراد إلى تكرار اسم طبيب محلي"
        }
        val finalTelegramIds = finalActive.mapNotNull { it.telegramId }
        require(finalTelegramIds.distinct().size == finalTelegramIds.size) {
            "سيؤدي الاستيراد إلى تكرار معرّف تليجرام"
        }
        val finalAdminRanks = finalActive.filter { it.rank > 0 }.map { it.rank }
        require(finalAdminRanks.distinct().size == finalAdminRanks.size) {
            "سيؤدي الاستيراد إلى تكرار رتبة مدير"
        }
        return merged
    }

    suspend fun promote(expected: Doctor, customTitle: String?, expectedActorId: String?): Doctor = withContext(dispatchers.io) {
        database.withTransaction {
            val actor = authorizer.requireAdmin()
            require(actor.id == expectedActorId) { "هوية المنفذ غير متطابقة" }
            val current = requireCurrent(expected)
            if (current.isAdmin) return@withTransaction current
            val title = customTitle?.takeIf { it.isNotBlank() } ?: DoctorNaming.defaultAdminTitle(current)
            require(DoctorValidator.validate(current.firstName, current.lastName, title) is DoctorValidationResult.Valid) { "اللقب غير صالح" }
            val updated = doctors.promoteToAdmin(current.id, title) ?: error("فشل رفع الصلاحية")
            audit.record(actor.id, actor.fullName, AppConstants.AUDIT_ADMIN_PROMOTED,
                "target=${current.fullName} rank=${updated.rank}")
            updated
        }
    }

    suspend fun demote(expected: Doctor, expectedActorId: String?): Doctor = withContext(dispatchers.io) {
        database.withTransaction {
            val actor = authorizer.requireAdmin()
            require(actor.id == expectedActorId) { "هوية المنفذ غير متطابقة" }
            val current = requireCurrent(expected)
            require(!current.isPermanentAdmin) { "لا يمكن إزالة مدير دائم" }
            require(actor.isPermanentAdmin || actor.rank > current.rank) { "لا يمكنك إزالة مدير أعلى رتبة" }
            if (!current.isAdmin) return@withTransaction current
            val updated = doctors.demoteFromAdmin(current.id) ?: error("فشل إزالة الصلاحية")
            audit.record(actor.id, actor.fullName, AppConstants.AUDIT_ADMIN_DEMOTED, "target=${current.fullName}")
            updated
        }
    }

    suspend fun setSupervisorGroup(expected: Doctor, chatId: Long?) = withContext(dispatchers.io) {
        require(chatId == null || chatId < 0) { "معرف مجموعة تليجرام يجب أن يكون رقماً سالباً" }
        database.withTransaction {
            val actor = authorizer.requireAdmin()
            val current = requireCurrent(expected)
            require(current.clinicalRole == ClinicalRole.SUPERVISOR) { "يمكن ربط مجموعة بالمشرفين فقط" }
            doctors.upsert(current.copy(supervisorGroupChatId = chatId))
            audit.record(actor.id, actor.fullName, AppConstants.AUDIT_DOCTOR_EDITED,
                "supervisor_group:${current.id}", beforeValue = current.supervisorGroupChatId?.toString().orEmpty(),
                afterValue = chatId?.toString().orEmpty())
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

class StaleDoctorImportException : IllegalStateException(
    "تغيّر سجل الأطباء أو المستخدم منذ معاينة الاستيراد. لم يُحفظ الاستيراد؛ اختر الملف وراجع المعاينة من جديد."
)
