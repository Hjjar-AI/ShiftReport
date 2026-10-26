package com.hos.rushdpatients.pdf

import android.graphics.Color

object PdfPalette {
    const val HEADER_TEAL = 0xFF146474.toInt()       // RGB(20, 100, 116)
    const val SUMMARY_BACKGROUND = 0xFFF8FAFC.toInt()      // Neutral summary and footer band.
    const val ZEBRA = 0xFFF8FAFC.toInt()             // Subtle alternating rows.
    const val CREAM = 0xFFFFFFFF.toInt()             // White alternate group rows.

    val GROUP_PALETTE: IntArray = intArrayOf(
        0xFFCFE8F0.toInt(),  // RGB(207, 232, 240) pale teal-blue
        0xFFD8EEE2.toInt(),  // RGB(216, 238, 226) pale mint
        0xFFF0F0D2.toInt(),  // RGB(240, 240, 210) pale sage-yellow
        0xFFFAE3DC.toInt(),  // RGB(250, 227, 220) pale coral
        0xFFE0E2F4.toInt()   // RGB(224, 226, 244) pale periwinkle
    )

    data class Resolved(
        val titleHeader: Int,
        val tableHeader: Int,
        val summary: Int,
        val pageBackground: Int,
        val text: Int,
        val border: Int,
        val zebra: Int,
        val cream: Int,
        val groups: IntArray,
        val ruleColor: Int,
        val ruleWidth: Float,
        val rowShading: PdfRowShading
    ) {
        fun group(index: Int): Int =
            groups[((index % groups.size) + groups.size) % groups.size]

        val teamSubheader: Int get() = Color.rgb(
            (Color.red(titleHeader) * .12f + Color.red(pageBackground) * .88f).toInt(),
            (Color.green(titleHeader) * .12f + Color.green(pageBackground) * .88f).toInt(),
            (Color.blue(titleHeader) * .12f + Color.blue(pageBackground) * .88f).toInt()
        )
    }

    fun resolve(options: PdfExportOptions): Resolved {
        val headerColors = when (options.colorPreset) {
            PdfColorPreset.TEAL -> 0xFF0D5663.toInt() to 0xFF146474.toInt()
            PdfColorPreset.BLUE -> 0xFF123F70.toInt() to 0xFF174A7E.toInt()
            PdfColorPreset.GREEN -> 0xFF214D39.toInt() to 0xFF285943.toInt()
            PdfColorPreset.PURPLE -> 0xFF493269.toInt() to 0xFF563D7C.toInt()
            PdfColorPreset.PASTEL_RAINBOW -> 0xFF8F2450.toInt() to 0xFF4E568F.toInt()
        }
        val lightGroups = when (options.colorPreset) {
            PdfColorPreset.PASTEL_RAINBOW -> intArrayOf(
                0xFFFFADAD.toInt(), 0xFFFFD6A5.toInt(), 0xFFFDFFB6.toInt(),
                0xFFCAFFBF.toInt(), 0xFF9BF6FF.toInt(), 0xFFA0C4FF.toInt(),
                0xFFBDB2FF.toInt(), 0xFFFFC6FF.toInt(), 0xFFFFFFFC.toInt()
            )
            else -> GROUP_PALETTE.copyOf()
        }
        val lightSummary = SUMMARY_BACKGROUND
        return Resolved(
            titleHeader = headerColors.first,
            tableHeader = headerColors.second,
            summary = lightSummary,
            pageBackground = 0xFFFFFFFF.toInt(),
            text = 0xFF1E293B.toInt(),
            border = 0xFFE2E8F0.toInt(),
            ruleColor = PdfRuleContrast.MEDIUM.light,
            ruleWidth = PdfRuleWeight.MEDIUM.points,
            rowShading = options.rowShading,
            zebra = ZEBRA,
            cream = CREAM,
            groups = lightGroups
        )
    }
}
