package com.hos.rushdpatients.sync

import kotlinx.serialization.Serializable

@Serializable
data class PublicationJournalEntry(
    val snapshotId: String,
    val parentSnapshotId: String? = null,
    val fileId: String,
    val messageId: Long,
    val at: Long,
    val deviceId: String,
    val forced: Boolean = false
)

/**
 * Machine-readable state, serialized as JSON and pinned in General.
 * CSV ordering uses Telegram message IDs; timestamps are display/sync metadata only.
 */
@Serializable
data class SyncState(
    val kind: String = KIND,
    val v: Int = CURRENT_VERSION,

    // --- CSV ---
    val csvFileId: String? = null,
    val csvMessageId: Long? = null,
    val csvDate: String? = null,
    val csvUpdatedAt: Long = 0L,
    val publicationJournal: List<PublicationJournalEntry> = emptyList(),

    // One immutable recovery version. Uploads inside the 10-hour editing window update the
    // current pointer without rotating this version.
    val previousCsvFileId: String? = null,
    val previousCsvMessageId: Long? = null,
    val previousCsvDate: String? = null,
    val previousCsvUpdatedAt: Long = 0L,

    // --- Doctors registry (file IDs; doctorsData supports legacy state) ---
    val doctorsData: String? = null,
    val doctorsFileId: String? = null,
    val doctorsMessageId: Long? = null,
    val doctorsUpdatedAt: Long = 0L,

    // --- Announcement ---
    val announcementMessageId: Long? = null,
    val announcementUpdatedAt: Long = 0L
) {
    val isRecognized: Boolean get() = kind == KIND && v in 1..CURRENT_VERSION

    companion object {
        const val KIND = "shift-report-sync-state"
        const val CURRENT_VERSION = 4
    }
}
