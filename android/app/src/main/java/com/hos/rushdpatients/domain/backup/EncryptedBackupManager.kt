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
import com.hos.rushdpatients.domain.patient.PatientDoctorAssignments
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
    data class PreparedBackup(val bytes: ByteArray, val patientCount: Int)

    suspend fun prepareExport(password: CharArray): PreparedBackup = withContext(dispatchers.io) {
        try {
            require(password.size >= 8) { "كلمة مرور النسخة الاحتياطية يجب ألا تقل عن 8 محارف" }
            val shift = shiftRepository.getLatest()
                ?: error("لا توجد وردية لإنشاء نسخة احتياطية")
            val patients = patientRepository.getForShift(shift.id)
            val plain = CsvCodec.encode(shift, patients).toByteArray(Charsets.UTF_8)
            try {
                PreparedBackup(encrypt(plain, password), patients.size)
            } finally {
                plain.fill(0)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun restore(uri: Uri, password: CharArray): Int = withContext(dispatchers.io) {
        try {
            require(password.size >= 8) { "كلمة مرور النسخة الاحتياطية يجب ألا تقل عن 8 محارف" }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("تعذر قراءة ملف النسخة الاحتياطية")
            val parsed = runCatching {
                val plain = decrypt(bytes, password)
                try { CsvCodec.decode(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
            }.getOrElse { throw IllegalArgumentException("كلمة المرور خاطئة أو الملف تالف") }
            require(parsed.patients.all { PatientValidator.validate(it) is PatientValidationResult.Valid }) {
                "تحتوي النسخة الاحتياطية على بيانات مرضى غير صالحة"
            }
            database.withTransaction {
                // Validate the live registry in the same transaction as the replacement.
                val doctors = doctorRepository.getAll().associateBy { it.id }
                require(parsed.doctorIds.all(doctors::containsKey)) {
                    "تحتاج النسخة إلى أطباء غير موجودين على هذا الجهاز"
                }
                parsed.patients.forEach { PatientDoctorAssignments.requireValid(it, doctors) }
                val shift = shiftRepository.getOrCreateForDate(parsed.shiftDate, parsed.shiftId)
                require(parsed.patients.none { patient ->
                    patientRepository.getShiftId(patient.id)?.let { it != shift.id } == true
                }) { "معرّف مريض مستخدم في وردية أخرى" }
                val restored = parsed.patients.map { patient ->
                    patient.copy(revision = nextRevision(patient.revision, patientRepository.getById(patient.id)?.revision ?: 0L))
                }
                patientRepository.softDeleteMissingFromShift(shift.id, restored.map { it.id })
                patientRepository.upsertAll(restored, shift.id)
                shiftRepository.upsert(shift.copy(doctorIds = parsed.doctorIds.distinct(),
                    sortSpecJson = parsed.sortSpecJson, revision = nextRevision(shift.revision, parsed.shiftRevision)))

            }
            parsed.patients.size
        } finally {
            password.fill('\u0000')
        }
    }

    private fun nextRevision(saved: Long, current: Long): Long {
        val highest = maxOf(saved, current)
        require(highest < Long.MAX_VALUE) { "نسخة السجل خارج المجال المسموح" }
        return highest + 1
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
