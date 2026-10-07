package com.hos.rushdpatients.network.telegram

class TelegramException(
    val code: Int,
    override val message: String,
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause) {

    val isRateLimited: Boolean get() = code == 429
    val isUnauthorized: Boolean get() = code == 401
    val isBadRequest: Boolean get() = code == 400
    val isForbidden: Boolean get() = code == 403
    val isNotFound: Boolean get() = code == 404

    override fun toString(): String =
        "TelegramException(code=$code, retryAfter=$retryAfterSeconds, message=$message)"
}