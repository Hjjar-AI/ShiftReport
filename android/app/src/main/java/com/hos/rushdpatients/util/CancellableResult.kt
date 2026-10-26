package com.hos.rushdpatients.util

import kotlinx.coroutines.CancellationException

/** Result wrapper for suspend workflows: cancellation is control flow, never a failed result. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}
