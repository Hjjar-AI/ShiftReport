package com.hos.rushdpatients.sync

data class CsvFetchResult(val shiftId: String, val inserted: Int, val updated: Int)
data class CsvUploadResult(val messageId: Long, val patientCount: Int)
data class HandoverChangeSummary(
    val added: List<String>,
    val changed: List<String>,
    val removed: List<String>
) {
    val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
}

