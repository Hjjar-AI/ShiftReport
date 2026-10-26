package com.hos.rushdpatients.sync

class CsvRollbackReview internal constructor(
    internal val snapshot: CsvSnapshot,
    internal val csv: String,
    internal val actorId: String,
    internal val telegramId: Long?,
    val text: String
)
