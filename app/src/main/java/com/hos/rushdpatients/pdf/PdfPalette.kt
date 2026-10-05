package com.hos.rushdpatients.pdf

object PdfPalette {
    const val HEADER_TEAL = 0xFF146474.toInt()       // RGB(20, 100, 116)
    const val SUMMARY_BLUE = 0xFFE0F2FE.toInt()      // RGB(224, 242, 254)
    const val ZEBRA = 0xFFF2F2F2.toInt()             // RGB(242, 242, 242)
    const val CREAM = 0xFFFFFDF5.toInt()             // RGB(255, 253, 245)

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
        val groups: IntArray
    ) {
        fun group(index: Int): Int =
            groups[((index % groups.size) + groups.size) % groups.size]
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
        val lightSummary = when (options.colorPreset) {
            PdfColorPreset.PASTEL_RAINBOW -> 0xFFFFFDFC.toInt()
            else -> SUMMARY_BLUE
        }
        return if (options.darkMode) {
            Resolved(
                titleHeader = headerColors.first,
                tableHeader = headerColors.second,
                summary = 0xFF343B42.toInt(),
                pageBackground = 0xFF202428.toInt(),
                text = 0xFFF7F9FA.toInt(),
                border = 0xFFB8C0C8.toInt(),
                zebra = 0xFF2B3137.toInt(),
                cream = 0xFF272C31.toInt(),
                groups = intArrayOf(
                    0xFF304850.toInt(),
                    0xFF344A40.toInt(),
                    0xFF4A4632.toInt(),
                    0xFF4D3936.toInt(),
                    0xFF3D3E52.toInt()
                )
            )
        } else {
            Resolved(
                titleHeader = headerColors.first,
                tableHeader = headerColors.second,
                summary = lightSummary,
                pageBackground = 0xFFFFFFFF.toInt(),
                text = 0xFF000000.toInt(),
                border = 0xFF000000.toInt(),
                zebra = ZEBRA,
                cream = CREAM,
                groups = lightGroups
            )
        }
    }
}
