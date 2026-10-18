package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportSummary
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Exports colored portrait A5 patient cards with an opening, linked index. */
@Singleton
class PdfCardsExporter @Inject constructor(
    private val rowExporter: PdfReportExporter
) {
    suspend fun export(
        patients: List<Patient>,
        doctors: List<Doctor>,
        summary: ReportSummary,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>,
        options: PdfExportOptions = PdfExportOptions(),
        outputFile: File
    ): File = rowExporter.export(
        patients = patients,
        doctors = doctors,
        summary = summary,
        residentNames = residentNames,
        supervisorNames = supervisorNames,
        options = options,
        elegant = true,
        outputFile = outputFile
    )
}
