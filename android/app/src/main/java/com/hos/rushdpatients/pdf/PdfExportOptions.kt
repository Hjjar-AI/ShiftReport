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

enum class PdfRuleWeight(val arabicLabel: String, val points: Float) {
    THIN("رفيعة", .75f), MEDIUM("متوسطة", 1.5f), THICK("سميكة", 2.25f);
    companion object {
        fun fromSetting(value: String?): PdfRuleWeight = entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

enum class PdfRuleContrast(val arabicLabel: String, val light: Int, val dark: Int) {
    LIGHT("خفيف", 0xFFCBD5E1.toInt(), 0xFF475569.toInt()),
    MEDIUM("متوسط", 0xFF94A3B8.toInt(), 0xFF64748B.toInt()),
    STRONG("واضح", 0xFF64748B.toInt(), 0xFF94A3B8.toInt());
    companion object {
        fun fromSetting(value: String?): PdfRuleContrast = entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

enum class PdfRowShading(val arabicLabel: String) {
    AUTO("تلقائي حسب الفرق"), ZEBRA("صفوف متناوبة"), NONE("دون تظليل");
    companion object {
        fun fromSetting(value: String?): PdfRowShading = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

enum class PdfPresentationPreset(
    val arabicLabel: String, val weight: PdfRuleWeight, val contrast: PdfRuleContrast,
    val shading: PdfRowShading, val lightOnly: Boolean = false
) {
    BALANCED("متوازن", PdfRuleWeight.MEDIUM, PdfRuleContrast.MEDIUM, PdfRowShading.AUTO),
    CLEAR("فواصل واضحة", PdfRuleWeight.THICK, PdfRuleContrast.STRONG, PdfRowShading.ZEBRA),
    PRINT("للطباعة", PdfRuleWeight.MEDIUM, PdfRuleContrast.STRONG, PdfRowShading.NONE, true)
}

data class PdfExportOptions(
    val orientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val paperSize: PdfPaperSize = PdfPaperSize.A4,
    val colorPreset: PdfColorPreset = PdfColorPreset.TEAL,
    val darkMode: Boolean = false,
    val patientCardStyle: PatientCardStyle = PatientCardStyle.BADGE_HEADER,
    val hospitalName: String = "",
    val wardName: String = "",
    val generatedAtEpochMillis: Long = System.currentTimeMillis(),
    val ruleWeight: PdfRuleWeight = PdfRuleWeight.MEDIUM,
    val ruleContrast: PdfRuleContrast = PdfRuleContrast.MEDIUM,
    val rowShading: PdfRowShading = PdfRowShading.AUTO
)
