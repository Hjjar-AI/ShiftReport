package com.hos.rushdpatients.pdf

enum class PdfStyle(val arabicLabel: String) {
    CLASSIC("جدول كلاسيكي"),
    CARDS("صفوف أنيقة");

    companion object {
        fun fromSetting(value: String?): PdfStyle =
            entries.firstOrNull { it.name == value } ?: CLASSIC
    }
}
