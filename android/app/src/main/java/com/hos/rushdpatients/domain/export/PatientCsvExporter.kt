package com.hos.rushdpatients.domain.export

import android.content.Context
import android.net.Uri
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.pdf.MediaStoreSaver
import com.hos.rushdpatients.sync.CsvCodec
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for exporting the current shift's patients as a
 * CSV file. Used by both the Ward drawer and the Settings screen so the two
 * paths cannot drift.
 *
 * Output: Downloads/RushdPatients/Ward_Patients_<date>.csv on API 29+, or the
 * app's external documents directory on older devices. The file uses the same
 * on-wire format as Telegram sync, so it can be re-imported unchanged.
 */
@Singleton
class PatientCsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val mediaStoreSaver: MediaStoreSaver,
    private val dispatchers: DispatcherProvider
) {

    sealed interface Result {
        data class Success(
            val uri: Uri,
            val fileName: String,
            val patientCount: Int
        ) : Result

        data class Failure(val message: String) : Result
    }

    /** Exports the shift that matches the current shift-date boundary. */
    suspend fun exportCurrentShift(): Result = withContext(dispatchers.io) {
        val shift = shiftRepository.getByDate(ShiftDate.current())
            ?: return@withContext Result.Failure("لا توجد وردية للتصدير")
        exportShift(shift.id)
    }

    /** Exports a specific shift by id. */
    suspend fun exportShift(shiftId: String): Result = withContext(dispatchers.io) {
        val shift = shiftRepository.getById(shiftId)
            ?: return@withContext Result.Failure("الوردية غير موجودة")

        val patients = PatientComparators.ordered(
            patientRepository.getForShift(shift.id), SortSpecCodec.decode(shift.sortSpecJson),
            doctorRepository.getAllIncludingDeleted().associate { it.id to it.fullName }
        )
        if (patients.isEmpty()) {
            return@withContext Result.Failure("لا يوجد مرضى في هذه الوردية")
        }

        val csv = CsvCodec.encode(shift, patients)
        val fileName = defaultFileName(shift.date)
        val tmp = File.createTempFile("rushd_export_", ".csv", context.cacheDir)
        try {
            tmp.writeText(csv, Charsets.UTF_8)
            val uri = mediaStoreSaver.saveCsv(
                context = context,
                sourceFile = tmp,
                displayName = fileName
            )
            if (uri == null) {
                Result.Failure("تعذر حفظ ملف CSV")
            } else {
                Result.Success(uri, fileName, patients.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e.message ?: "فشل تصدير CSV")
        } finally {
            tmp.delete()
        }
    }

    private fun defaultFileName(date: LocalDate): String {
        val created = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        return "Rushd_Patients_${date}_$created.csv"
    }
}
