package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportSummary
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compatibility entry point for the former card PDF setting. Output is now an
 * elegant, print-friendly row report using the shared non-splitting renderer.
 */
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
