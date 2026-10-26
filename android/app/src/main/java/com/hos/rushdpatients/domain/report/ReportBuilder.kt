package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.util.ShiftDate
import com.hos.rushdpatients.domain.doctor.RegistryChangeSummary
import androidx.room.withTransaction
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.withContext
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import javax.inject.Inject
import javax.inject.Singleton

data class BuiltReport(
    val shift: Shift,
    val patients: List<Patient>,
    val doctors: List<Doctor>,
    val doctorNames: Map<String, String>,
    val summary: ReportSummary,
    val textChunks: List<String>,
    val registryDoctors: List<Doctor> = doctors,
    val registryChangeSummary: RegistryChangeSummary? = null
)

@Singleton
class ReportBuilder @Inject constructor(
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val shiftRepository: ShiftRepository,
    private val textReportBuilder: TextReportBuilder,
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider
) {

    /**
     * Assemble all pieces required to render or send the report.
     */
    suspend fun build(shiftId: String, reportDate: java.time.LocalDate = ShiftDate.current()): BuiltReport =
        buildSnapshot(shiftId, requireRoster = true, reportDate = reportDate)

    /** Names-only local export does not require the clinical report's shift roster. */
    suspend fun buildAssignments(shiftId: String, reportDate: java.time.LocalDate = ShiftDate.current()): BuiltReport =
        buildSnapshot(shiftId, requireRoster = false, reportDate = reportDate)

    private suspend fun buildSnapshot(shiftId: String, requireRoster: Boolean,
        reportDate: java.time.LocalDate = ShiftDate.current()): BuiltReport = withContext(dispatchers.io) {
        database.withTransaction {
            val shift = shiftRepository.getById(shiftId)
                ?: error("Shift not found: $shiftId")

            val allDoctors = doctorRepository.getAllIncludingDeleted()
            val doctorsById = allDoctors.associateBy { it.id }
            val shiftDoctors = shift.doctorIds.mapNotNull { doctorsById[it] }
            if (requireRoster) require(shiftDoctors.isNotEmpty()) { "حدد أطباء المناوبة قبل إنشاء التقرير" }

            val residentNames = allDoctors.associate { it.id to it.fullName }
            val supervisorNames = residentNames
            val sortSpec = SortSpecCodec.decode(shift.sortSpecJson)
            val unsortedPatients = patientRepository.getForShift(shiftId)
                .filterNot { it.isDeleted }
            val patients = PatientComparators.ordered(unsortedPatients, sortSpec, residentNames)

            val summary = ReportSummary(
                patientCount = patients.size,
                psychoCount = patients.count { isPsycho(it) },
                escortCount = patients.count { it.hasCompanion },
                shiftDate = reportDate,
                doctors = shiftDoctors
            )

            val chunks = textReportBuilder.build(
                shift = shift.copy(date = reportDate),
                patients = patients,
                doctors = shiftDoctors,
                summary = summary,
                residentNames = residentNames,
                supervisorNames = supervisorNames
            )

            BuiltReport(
                shift = shift,
                patients = patients,
                doctors = shiftDoctors,
                doctorNames = residentNames,
                summary = summary,
                textChunks = chunks,
                registryDoctors = allDoctors,
                registryChangeSummary = doctorRepository.pendingSharedChangesSummary()
            )
        }
    }

    private fun isPsycho(patient: Patient): Boolean =
        patient.diagnosisType == DiagnosisType.PSYCHIATRIC ||
                patient.diagnosisType == DiagnosisType.DUAL
}
