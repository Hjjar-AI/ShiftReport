package com.hos.rushdpatients.config

import com.hos.rushdpatients.network.telegram.TelegramClient

/** Connection settings only: zero selects the existing project report destination. */
object ResidentsDestination {
    fun parse(group: String, topic: String): Pair<Long, Long> {
        val chatId = if (group.isBlank()) 0L else group.trim().toLongOrNull()?.takeIf { it < 0L }
            ?: error("معرّف مجموعة المقيمين يجب أن يكون عدداً سالباً")
        val topicId = if (topic.isBlank()) 0L else topic.trim().toLongOrNull()?.takeIf { it > 0L }
            ?: error("معرّف موضوع المقيمين يجب أن يكون عدداً موجباً")
        require(chatId != 0L || topicId == 0L) { "أدخل مجموعة المقيمين عند تحديد موضوع لها" }
        return chatId to topicId
    }

    suspend fun validate(telegram: TelegramClient, chatId: Long, topicId: Long, token: String? = null) {
        require(chatId <= 0L && topicId >= 0L && (chatId != 0L || topicId == 0L)) { "وجهة المقيمين غير صالحة" }
        if (chatId != 0L) telegram.validateReportDestination(chatId, topicId, token)
    }
}
