package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.sync.KnownShiftSnapshot
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** One isolated session, including a pretend remote snapshot and device-local demo receipts. */
@ViewModelScoped
class InMemoryDemoWardRepository @Inject constructor() {
    internal val state = MutableStateFlow(DemoWardState())
    internal val receipts = MutableStateFlow<List<ReportDeliveryRecord>>(emptyList())
    internal var published = state.value
        private set
    internal var registry = state.value.doctors
        private set
    internal var publishedAt: Long? = null
        private set
    internal var downloadedAt: Long? = null
        private set
    internal var undo: DemoWardState? = null
        private set

    internal fun changes(current: DemoWardState = state.value) = KnownShiftSnapshot.changes(
        current.ordered, published.ordered, current.shiftDoctorIds != published.shiftDoctorIds || current.sort != published.sort)

    internal fun publish(snapshot: DemoWardState) {
        published = snapshot
        registry = snapshot.doctors
        publishedAt = System.currentTimeMillis()
        state.update { it.copy(revision = it.revision + 1) }
    }
    internal fun publishDoctors(doctors: List<com.hos.rushdpatients.data.model.Doctor>) {
        registry = doctors
        state.update { it.copy(revision = it.revision + 1) }
    }
    internal fun fetch() {
        val current = state.value
        undo = current
        downloadedAt = System.currentTimeMillis()
        state.value = current.copy(patients = published.patients, shiftDoctorIds = published.shiftDoctorIds,
            sort = published.sort, deleted = current.deleted.filterNot { row -> published.patients.any { it.id == row.id } },
            revision = current.revision + 1)
    }
    internal fun undoFetch() {
        val previous = undo ?: return
        undo = null
        state.value = state.value.copy(patients = previous.patients, shiftDoctorIds = previous.shiftDoctorIds,
            sort = previous.sort, deleted = previous.deleted, revision = state.value.revision + 1)
    }
    internal fun reset(message: String? = null) {
        val fresh = DemoWardState(message = message)
        published = fresh
        registry = fresh.doctors
        publishedAt = null
        downloadedAt = null
        undo = null
        receipts.value = emptyList()
        state.value = fresh
    }
}
