package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.data.db.dao.AuditDao
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditRepository @Inject constructor(
    private val dao: AuditDao,
    private val dispatchers: DispatcherProvider
) {

    // Compatibility API only. New audit records are disabled by owner choice.
    @Suppress("UNUSED_PARAMETER")
    suspend fun record(
        actorDoctorId: String?, actorName: String?, action: String, detail: String = "",
        patientId: String? = null, beforeValue: String? = null, afterValue: String? = null
    ) = Unit

    suspend fun getForPatient(patientId: String) = withContext(dispatchers.io) {
        dao.getForPatient(patientId)
    }

    suspend fun getRecent(limit: Int = 50) = withContext(dispatchers.io) {
        dao.getRecent(limit)
    }

}
