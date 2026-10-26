package com.hos.rushdpatients.network.telegram

import com.hos.rushdpatients.config.AppConstants

object TelegramConfig {
    const val BASE_URL = "https://api.telegram.org"
    const val FILE_BASE_URL = "https://api.telegram.org/file"

    const val DEFAULT_TIMEOUT_SECONDS = 30L
    const val UPLOAD_TIMEOUT_SECONDS = 120L
    const val DOWNLOAD_TIMEOUT_SECONDS = 120L

    const val MAX_RETRIES = 3
    const val MIN_CALL_INTERVAL_MS = AppConstants.TELEGRAM_MIN_INTERVAL_MS
}
