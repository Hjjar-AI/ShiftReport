package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.report.ReportSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Production assignments always require current admin identity before and after rendering. */
class PdfAssignmentExporter @Inject constructor(
    private val adminAuthorizer: AdminAuthorizer,
    private val renderer: PdfAssignmentRenderer
) {
    suspend fun export(
        patients: List<Patient>, doctorNames: Map<String, String>, registry: List<Doctor>, summary: ReportSummary,
        options: PdfExportOptions, outputFile: File, documentLabel: String? = null
    ): File = withContext(Dispatchers.IO) {
        val actorId = adminAuthorizer.requireAdmin().id
        try {
            renderer.export(patients, doctorNames, registry, summary, options, outputFile, documentLabel)
            require(adminAuthorizer.requireAdmin().id == actorId) { "تغيّر المستخدم؛ أعد تصدير توزيع المرضى" }
            outputFile
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        }
    }
}
