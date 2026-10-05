package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.data.db.dao.AuditDao
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditRepository @Inject constructor(
    private val dao: AuditDao,
    private val dispatchers: DispatcherProvider
) {

    suspend fun record(
        actorDoctorId: String?,
        actorName: String?,
        action: String,
        detail: String = "",
        patientId: String? = null,
        beforeValue: String? = null,
        afterValue: String? = null
    ) = withContext(dispatchers.io) {
        dao.insert(
            AuditEntryEntity(
                actorDoctorId = actorDoctorId,
                actorName = actorName,
                action = action,
                patientId = patientId,
                beforeValue = beforeValue,
                afterValue = afterValue,
                detail = detail,
                atEpochMillis = Instant.now().toEpochMilli()
            )
        )
    }

    suspend fun getForPatient(patientId: String) = withContext(dispatchers.io) {
        dao.getForPatient(patientId)
    }

    suspend fun getRecent(limit: Int = 50) = withContext(dispatchers.io) {
        dao.getRecent(limit)
    }

}
