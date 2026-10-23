package com.hos.rushdpatients.testutil

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.sync.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Before

abstract class SyncFixture : ClinicalRepositoryFixture() {
    protected lateinit var wire: SyntheticTelegram
    protected lateinit var remote: RemoteSyncStateStore
    protected lateinit var cursors: SyncStateRepository
    protected lateinit var undo: LocalDownloadUndo
    internal lateinit var retrieval: PatientRetrievalCoordinator
    protected lateinit var doctorSync: DoctorSyncCoordinator
    @Before fun openSync() = runBlocking<Unit> {
        wire = SyntheticTelegram(preferences, dispatchers)
        remote = RemoteSyncStateStore(wire.client, wire.topics, wire.json)
        cursors = SyncStateRepository(db.syncStateDao(), dispatchers)
        undo = LocalDownloadUndo(db, shifts, patients, settings, audit, sessions, dispatchers, wire.json)
        retrieval = PatientRetrievalCoordinator(Mutex(), preferences, wire.client, shifts, patients, doctors,
            cursors, settings, db, audit, dispatchers, remote, undo)
        doctorSync = DoctorSyncCoordinator(preferences, wire.client, wire.topics, doctors, cursors, settings,
            db, audit, authorizer, sessions, dispatchers, remote)
        settings.put(AppConstants.SETTING_DOCTORS_BASE_REGISTRY, DoctorsRegistryCodec.encode(doctors.getAll()))
        wire.state = SyncState(doctorsData = DoctorsRegistryCodec.encode(doctors.getAll()))
    }
}
