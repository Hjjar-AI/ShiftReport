package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.report.ExactReportCopyRequiredException
import com.hos.rushdpatients.domain.auth.LocalPinException
import com.hos.rushdpatients.domain.auth.LocalPinFailure
import com.hos.rushdpatients.network.telegram.TelegramException

/** Map transport/login conditions at the presentation boundary. */
fun failureText(uiText: UiStrings, error: Throwable): String? = when {
    error is ExactReportCopyRequiredException -> uiText(R.string.delivery_exact_copy_required)
    error is TelegramException && error.isRateLimited -> uiText(R.string.telegram_wait_retry,
        uiNumber(error.retryAfterSeconds?.coerceAtLeast(1) ?: 30))
    error is LocalPinException -> when (error.reason) {
        LocalPinFailure.BLOCKED -> uiText(R.string.pin_wait_minutes, uiNumber(error.waitMinutes))
        LocalPinFailure.INVALID_FORMAT -> uiText(R.string.pin_invalid_format)
        LocalPinFailure.UNAVAILABLE -> uiText(R.string.pin_doctor_unavailable)
        LocalPinFailure.NOT_ENROLLED -> uiText(R.string.pin_not_enrolled)
        LocalPinFailure.NOT_LINKED -> uiText(R.string.pin_not_linked)
        LocalPinFailure.STATE_UNREADABLE -> uiText(R.string.pin_state_unreadable)
        LocalPinFailure.INCORRECT -> uiText(R.string.pin_incorrect)
        LocalPinFailure.ACCOUNT_CHANGED -> uiText(R.string.pin_account_changed)
    }
    else -> error.message
}
