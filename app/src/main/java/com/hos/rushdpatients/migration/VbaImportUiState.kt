package com.hos.rushdpatients.migration

enum class ImportMode(val arabicLabel: String) {
    REPLACE("استبدال"),
    ADD("إضافة")
}

data class VbaImportUiState(
    val loading: Boolean = false,
    val preview: VbaImportPreview? = null,
    val imported: VbaCsvImporter.ImportResult? = null,
    val importMode: ImportMode = ImportMode.REPLACE,
    val error: String? = null
)
