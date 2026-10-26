package com.hos.rushdpatients.sync

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PatientWorkflowStatus(val pending: Boolean, val selected: ShiftChanges?)

/** Derived local publication state. Legacy stored pending flags are not authority. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PatientPublicationStatus @Inject constructor(
    private val shifts: ShiftRepository,
    private val patients: PatientRepository,
    private val doctors: DoctorRepository,
    private val settings: SettingsRepository,
    private val dispatchers: DispatcherProvider
) {
    suspend fun hasPending(): Boolean = withContext(dispatchers.io) {
        val shift = shifts.getLatest() ?: return@withContext false
        val names = doctors.getAllIncludingDeleted().associate { it.id to it.fullName }
        KnownShiftSnapshot.changes(shift, patients.getForShift(shift.id),
            settings.get(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + shift.id), names).pending
    }

    private fun observeShift(id: String, doctorNames: Map<String, String>): Flow<ShiftChanges> {
        // Decode only when the stored baseline changes, independent of patient emissions.
        val baseline = settings.observe(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + id)
            .distinctUntilChanged().map { csv -> csv?.let { runCatching { CsvCodec.decode(it) }.getOrNull() } }
        return combine(shifts.observeById(id), patients.observeForShift(id), baseline) { shift, rows, base ->
            KnownShiftSnapshot.againstBaseline(shift, rows, base, doctorNames)
        }.distinctUntilChanged().flowOn(dispatchers.io)
    }

    fun observePending(): Flow<Boolean> = observeWorkflow().map { it.pending }.distinctUntilChanged()

    fun observeWorkflow(selectedId: String? = null): Flow<PatientWorkflowStatus> {
        // Only the current ward can be published; historical rows must not gate its fetch.
        val ward = shifts.observeLatest().map { it?.id }.distinctUntilChanged()
        val names = doctors.observeAll().map {
            doctors.getAllIncludingDeleted().associate { it.id to it.fullName }
        }.distinctUntilChanged().flowOn(dispatchers.io)
        return combine(ward, names) { id, registry -> id to registry }
            .distinctUntilChanged().flatMapLatest { (currentId, registry) ->
                val ids = listOfNotNull(currentId, selectedId).distinct()
                if (ids.isEmpty()) flowOf(PatientWorkflowStatus(false, null))
                else combine(ids.map { observeShift(it, registry) }) { changes ->
                    PatientWorkflowStatus(currentId?.let { changes[ids.indexOf(it)].pending } ?: false,
                        (selectedId ?: currentId)?.let { changes[ids.indexOf(it)] })
                }
            }.distinctUntilChanged()
    }
}
