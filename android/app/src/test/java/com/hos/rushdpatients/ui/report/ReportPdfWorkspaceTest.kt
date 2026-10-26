package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.report.*
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReportPdfWorkspaceTest {
    private val directory = Files.createTempDirectory("reviewed_pdf_test_").toFile()
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "راجع الملف" }
    private val settings = mockk<SettingsRepository> {
        coEvery { this@mockk.get(any()) } returns null
        coEvery { getBoolean(any(), any()) } returns false
    }
    private val projects = mockk<ProjectConfigStore> { every { current() } returns ProjectConfig() }
    private val workspace = ReportPdfWorkspace(text, mockk(), settings, projects, mockk(), mockk(), mockk())
    private val shift = Fixtures.shift()
    private val actor = Fixtures.doctor()
    private val built = BuiltReport(shift, listOf(Fixtures.patient()), listOf(actor), mapOf(actor.id to actor.fullName),
        ReportSummary(1, 1, 0, shift.date, listOf(actor)), listOf("synthetic"))
    private suspend fun reviewed(): File {
        val file = File(directory, "reviewed.pdf").apply { writeText("synthetic reviewed artifact") }
        workspace.replaceReview("general", ReviewedPdf(file, workspace.pdfDigest(file), ReportReview.exportFingerprint(built),
            workspace.loadPdfOptions(), PdfStyle.CARDS, built))
        return file
    }
    @After fun cleanup() { directory.deleteRecursively() }
    @Test fun `unreviewed or invalidated PDF cannot be sent`() = runBlocking<Unit> {
        assertThrows(PdfReviewRequiredException::class.java) { runBlocking { workspace.checkedPdf(built) } }
        reviewed()
        workspace.clearReviews()
        assertThrows(PdfReviewRequiredException::class.java) { runBlocking { workspace.checkedPdf(built) } }
    }
    @Test fun `same reviewed artifact passes and modified bytes require another preview`() = runBlocking<Unit> {
        val file = reviewed()
        assertEquals(file, workspace.checkedPdf(built))
        file.appendText("changed bytes")
        assertThrows(PdfReviewRequiredException::class.java) { runBlocking { workspace.checkedPdf(built) } }
    }
    @Test fun `appearance or clinical changes invalidate the reviewed artifact`() = runBlocking<Unit> {
        reviewed()
        coEvery { settings.get(AppConstants.SETTING_PDF_PAPER_SIZE) } returns PdfPaperSize.A4.name
        assertThrows(PdfReviewRequiredException::class.java) { runBlocking { workspace.checkedPdf(built) } }
        coEvery { settings.get(AppConstants.SETTING_PDF_PAPER_SIZE) } returns null
        assertThrows(ReportChangedSinceReviewException::class.java) { runBlocking {
            workspace.checkedPdf(built.copy(patients = listOf(built.patients.single().copy(labs = "new result"))))
        } }
    }
    @Test fun `missing reviewed file cannot fall back to another artifact`() = runBlocking<Unit> {
        reviewed().delete()
        assertThrows(PdfReviewRequiredException::class.java) { runBlocking { workspace.checkedPdf(built) } }
    }
}
