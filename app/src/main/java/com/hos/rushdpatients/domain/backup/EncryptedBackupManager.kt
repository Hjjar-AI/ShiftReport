package com.hos.rushdpatients.domain.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.domain.patient.PatientValidationResult
import com.hos.rushdpatients.domain.patient.PatientValidator
import com.hos.rushdpatients.sync.CsvCodec
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider
) {
    suspend fun exportCurrent(uri: Uri, password: CharArray): Int = withContext(dispatchers.io) {
        require(password.size >= 8) { "كلمة مرور النسخة الاحتياطية يجب ألا تقل عن 8 محارف" }
        val shift = shiftRepository.getByDate(ShiftDate.current())
            ?: error("لا توجد وردية لإنشاء نسخة احتياطية")
        val patients = patientRepository.getForShift(shift.id)
        val plain = CsvCodec.encode(shift, patients).toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(plain, password)
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(encrypted) }
            ?: error("تعذر فتح ملف النسخة الاحتياطية")
        patients.size
    }

    suspend fun restore(uri: Uri, password: CharArray): Int = withContext(dispatchers.io) {
        require(password.size >= 8) { "كلمة مرور النسخة الاحتياطية يجب ألا تقل عن 8 محارف" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("تعذر قراءة ملف النسخة الاحتياطية")
        val parsed = runCatching {
            CsvCodec.decode(decrypt(bytes, password).toString(Charsets.UTF_8))
        }.getOrElse { throw IllegalArgumentException("كلمة المرور خاطئة أو الملف تالف") }

        require(parsed.patients.all { PatientValidator.validate(it) is PatientValidationResult.Valid }) {
            "تحتوي النسخة الاحتياطية على بيانات مرضى غير صالحة"
        }
        val doctors = doctorRepository.getAll().associateBy { it.id }
        require(parsed.doctorIds.all(doctors::containsKey)) {
            "تحتاج النسخة إلى أطباء غير موجودين على هذا الجهاز"
        }
        require(parsed.patients.all { patient ->
            patient.responsibleResidentId == null || doctors.containsKey(patient.responsibleResidentId)
        }) { "تحتوي النسخة على مقيم غير موجود" }
        require(parsed.patients.all { patient ->
            patient.responsibleSpecialistId == null || doctors.containsKey(patient.responsibleSpecialistId)
        }) { "تحتوي النسخة على اختصاصي غير موجود" }

        require(parsed.patients.all { patient ->
            patient.tasks.all { it.done || it.ownerDoctorId == null || doctors.containsKey(it.ownerDoctorId) }
        }) { "تحتوي النسخة على مسؤول مهمة غير موجود" }

        if (parsed.shiftDate == ShiftDate.current()) {
            // Publish the intent before mutating patient data so background fetch cannot observe
            // changed rows while the pending marker is still false.
            settingsRepository.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true)
        }
        database.withTransaction {
            val shift = shiftRepository.getOrCreateForDate(parsed.shiftDate, parsed.shiftId)
            val conflicting = parsed.patients.firstOrNull { patient ->
                patientRepository.getShiftId(patient.id)?.let { it != shift.id } == true
            }
            require(conflicting == null) {
                "معرّف المريض ${conflicting?.id} مستخدم في وردية أخرى"
            }
            patientRepository.softDeleteMissingFromShift(shift.id, parsed.patients.map { it.id })
            patientRepository.upsertAll(parsed.patients, shift.id)
            shiftRepository.upsert(
                shift.copy(
                    doctorIds = parsed.doctorIds.distinct(),
                    sortSpecJson = parsed.sortSpecJson
                )
            )
        }
        parsed.patients.size
    }

    private fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    private fun decrypt(payload: ByteArray, password: CharArray): ByteArray {
        require(payload.size > MAGIC.size + SALT_BYTES + IV_BYTES &&
            payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "صيغة النسخة الاحتياطية غير مدعومة"
        }
        val saltStart = MAGIC.size
        val ivStart = saltStart + SALT_BYTES
        val dataStart = ivStart + IV_BYTES
        val salt = payload.copyOfRange(saltStart, ivStart)
        val iv = payload.copyOfRange(ivStart, dataStart)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload.copyOfRange(dataStart, payload.size))
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }

    private companion object {
        val MAGIC = "RUSHDBK1".toByteArray(Charsets.US_ASCII)
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
    }
}
