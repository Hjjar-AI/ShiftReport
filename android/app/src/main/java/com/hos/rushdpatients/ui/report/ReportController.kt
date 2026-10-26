package com.hos.rushdpatients.ui.report

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.domain.report.DeliveryResolution
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.pdf.PdfExportOptions

/** The shared screen depends on actions/state, never on a particular connection or store. */
internal interface ReportController {
    val state: StateFlow<ReportUiState>
    val simulation: Boolean get() = false
    fun load(reviewNotice: String? = null)
    fun dismissSnackbar()
    fun previewPdf()
    fun previewSupervisorPdf(supervisorId: String)
    fun onPdfPreviewOpened(uri: Uri)
    fun onPdfViewerUnavailable()
    fun consumePdfShare()
    fun setShiftDoctors(ids: List<String>, onSuccess: () -> Unit)
    fun setReportAsPdf(value: Boolean)
    fun setPdfAppearance(style: PdfStyle, requested: PdfExportOptions)
    fun retryLastAction(onReviewSend: () -> Unit)
    fun fetchDoctorsForReport()
    fun resolveDelivery(record: ReportDeliveryRecord, resolution: DeliveryResolution)
    fun previewExportPdf(route: String, draft: Boolean)
    fun saveExportPdfs(routes: Set<String>, draft: Boolean)
    fun shareExportPdf(route: String, draft: Boolean)
    fun exportLocally()
    fun sharePdf()
    fun exportSupervisorPdfs()
    fun exportAssignments()
    fun sendSelectedReports(includeGeneral: Boolean, supervisorIds: Set<String>, exportPdf: Boolean = false)
}

internal class LiveReportController(private val viewModel: ReportPreviewViewModel) : ReportController {
    override val state get() = viewModel.state
    override fun load(reviewNotice: String?) { viewModel.load(reviewNotice) }
    override fun dismissSnackbar() { viewModel.dismissSnackbar() }
    override fun previewPdf() { viewModel.previewPdf() }
    override fun previewSupervisorPdf(supervisorId: String) { viewModel.previewSupervisorPdf(supervisorId) }
    override fun onPdfPreviewOpened(uri: Uri) { viewModel.onPdfPreviewOpened(uri) }
    override fun onPdfViewerUnavailable() { viewModel.onPdfViewerUnavailable() }
    override fun consumePdfShare() { viewModel.consumePdfShare() }
    override fun setShiftDoctors(ids: List<String>, onSuccess: () -> Unit) { viewModel.setShiftDoctors(ids, onSuccess) }
    override fun setReportAsPdf(value: Boolean) { viewModel.setReportAsPdf(value) }
    override fun setPdfAppearance(style: PdfStyle, requested: PdfExportOptions) { viewModel.setPdfAppearance(style, requested) }
    override fun retryLastAction(onReviewSend: () -> Unit) { viewModel.retryLastAction(onReviewSend) }
    override fun fetchDoctorsForReport() { viewModel.fetchDoctorsForReport() }
    override fun resolveDelivery(record: ReportDeliveryRecord, resolution: DeliveryResolution) { viewModel.resolveDelivery(record, resolution) }
    override fun previewExportPdf(route: String, draft: Boolean) { viewModel.previewExportPdf(route, draft) }
    override fun saveExportPdfs(routes: Set<String>, draft: Boolean) { viewModel.saveExportPdfs(routes, draft) }
    override fun shareExportPdf(route: String, draft: Boolean) { viewModel.shareExportPdf(route, draft) }
    override fun exportLocally() { viewModel.exportLocally() }
    override fun sharePdf() { viewModel.sharePdf() }
    override fun exportSupervisorPdfs() { viewModel.exportSupervisorPdfs() }
    override fun exportAssignments() { viewModel.exportAssignments() }
    override fun sendSelectedReports(includeGeneral: Boolean, supervisorIds: Set<String>, exportPdf: Boolean) { viewModel.sendSelectedReports(includeGeneral, supervisorIds, exportPdf) }
}
