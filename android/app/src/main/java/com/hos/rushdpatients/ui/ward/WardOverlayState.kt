package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

internal enum class WardOverlay { SUPPORT, FILTERS, TASKS, RECYCLE, FETCH_LATEST }

/** Secondary sheets/confirmations also share one exclusive state. */
internal class WardOverlayState {
    var current by mutableStateOf<WardOverlay?>(null)
    private fun set(value: Boolean, overlay: WardOverlay) {
        if (value) current = overlay else if (current == overlay) current = null
    }
    var showSupportingSheet: Boolean
        get() = current == WardOverlay.SUPPORT
        set(value) = set(value, WardOverlay.SUPPORT)
    var showFilters: Boolean
        get() = current == WardOverlay.FILTERS
        set(value) = set(value, WardOverlay.FILTERS)
    var showWardTasks: Boolean
        get() = current == WardOverlay.TASKS
        set(value) = set(value, WardOverlay.TASKS)
    var showRecycleBin: Boolean
        get() = current == WardOverlay.RECYCLE
        set(value) = set(value, WardOverlay.RECYCLE)
    var confirmLatest: Boolean
        get() = current == WardOverlay.FETCH_LATEST
        set(value) = set(value, WardOverlay.FETCH_LATEST)
    companion object {
        val Saver = Saver<WardOverlayState, String>(
            save = { it.current?.name.orEmpty() },
            restore = { saved -> WardOverlayState().apply { current = WardOverlay.entries.firstOrNull { it.name == saved } } }
        )
    }
}
