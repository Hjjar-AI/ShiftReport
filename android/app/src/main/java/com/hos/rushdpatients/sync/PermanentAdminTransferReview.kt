package com.hos.rushdpatients.sync

/** In-memory only. No saved-state persistence or connection details in toString(). */
class PermanentAdminTransferReview internal constructor(
    val outgoingName: String,
    val recipientName: String,
    val recipientTelegramId: Long,
    internal val actorId: String,
    internal val recipientId: String,
    internal val source: SyncState,
    internal val before: String,
    internal val after: String,
    internal val connectionUnchanged: () -> Boolean
)
