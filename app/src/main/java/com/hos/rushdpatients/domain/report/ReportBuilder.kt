package com.hos.rushdpatients.domain.report

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
    val textChunks: List<String>
)

@Singleton
class ReportBuilder @Inject constructor(
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val shiftRepository: ShiftRepository,
    private val textReportBuilder: TextReportBuilder
) {

    /**
     * Assemble all pieces required to render or send the report.
     */
    suspend fun build(shiftId: String): BuiltReport {
        val shift = shiftRepository.getById(shiftId)
            ?: error("Shift not found: $shiftId")

        val allDoctors = doctorRepository.getAllIncludingDeleted()
        val doctorsById = allDoctors.associateBy { it.id }
        val shiftDoctors = shift.doctorIds.mapNotNull { doctorsById[it] }
        require(shiftDoctors.isNotEmpty()) { "حدد أطباء المناوبة قبل إنشاء التقرير" }

        val residentNames = allDoctors.associate { it.id to it.fullName }
        val supervisorNames = residentNames
        val sortSpec = SortSpecCodec.decode(shift.sortSpecJson)
        val unsortedPatients = patientRepository.getForShift(shiftId)
            .filterNot { it.isDeleted }
        val orderedPatients = if (sortSpec.levels.isEmpty()) {
            unsortedPatients.sortedBy { it.sortOrder }
        } else {
            unsortedPatients.sortedWith(PatientComparators.forSpec(sortSpec, residentNames))
        }
        val patients = orderedPatients.mapIndexed { index, patient ->
            patient.copy(sortOrder = index + 1)
        }

        val summary = ReportSummary(
            patientCount = patients.size,
            psychoCount = patients.count { isPsycho(it) },
            escortCount = patients.count { it.hasCompanion },
            shiftDate = shift.date,
            doctors = shiftDoctors
        )

        val chunks = textReportBuilder.build(
            shift = shift,
            patients = patients,
            doctors = shiftDoctors,
            summary = summary,
            residentNames = residentNames,
            supervisorNames = supervisorNames
        )

        return BuiltReport(
            shift = shift,
            patients = patients,
            doctors = shiftDoctors,
            doctorNames = residentNames,
            summary = summary,
            textChunks = chunks
        )
    }

    private fun isPsycho(patient: Patient): Boolean =
        patient.diagnosisType == DiagnosisType.PSYCHIATRIC ||
                patient.diagnosisType == DiagnosisType.DUAL
}
