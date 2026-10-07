package com.hos.rushdpatients.util

import android.util.Log
import com.hos.rushdpatients.BuildConfig

object Logging {

    private const val TAG = "Rushd"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, t: Throwable? = null) {
        Log.w(TAG, message, t)
    }

    fun e(message: String, t: Throwable? = null) {
        Log.e(TAG, message, t)
    }

    /**
     * Redact patient-identifying fields before logging.
     * Never log names, ages, diagnoses, notes, or telegram IDs at INFO or above.
     */
    fun redact(value: String?, visibleChars: Int = 2): String {
        if (value.isNullOrEmpty()) return ""
        if (value.length <= visibleChars) return "*".repeat(value.length)
        return value.take(visibleChars) + "*".repeat(value.length - visibleChars)
    }
}