package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.domain.patient.PatientCardStyle

enum class PdfOrientation(val arabicLabel: String) {
    PORTRAIT("عمودي"),
    LANDSCAPE("أفقي");

    companion object {
        fun fromSetting(value: String?): PdfOrientation =
            entries.firstOrNull { it.name == value } ?: PORTRAIT
    }
}

enum class PdfPaperSize(
    val arabicLabel: String,
    val widthPoints: Int,
    val heightPoints: Int
) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    A5("A5", 420, 595),
    A3("A3", 842, 1191);

    companion object {
        fun fromSetting(value: String?): PdfPaperSize =
            entries.firstOrNull { it.name == value } ?: A4
    }
}

enum class PdfColorPreset(val arabicLabel: String) {
    TEAL("فيروزي"),
    BLUE("أزرق"),
    GREEN("أخضر"),
    PURPLE("بنفسجي"),
    PASTEL_RAINBOW("باستيل");

    companion object {
        fun fromSetting(value: String?): PdfColorPreset =
            entries.firstOrNull { it.name == value } ?: TEAL
    }
}

data class PdfExportOptions(
    val orientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val paperSize: PdfPaperSize = PdfPaperSize.A4,
    val colorPreset: PdfColorPreset = PdfColorPreset.TEAL,
    val darkMode: Boolean = false,
    val patientCardStyle: PatientCardStyle = PatientCardStyle.BADGE_HEADER
)
