package com.hos.rushdpatients.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.network.telegram.TelegramException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

class AutoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val service = EntryPointAccessors.fromApplication(
            applicationContext,
            AutoSyncEntryPoint::class.java
        ).syncService()
        val doctorsResult = service.synchronizeDoctors()
        val csvResult = if (doctorsResult.isSuccess) service.synchronizeCurrentPatients() else null
        val error = doctorsResult.exceptionOrNull() ?: csvResult?.exceptionOrNull()
        return when {
            doctorsResult.isSuccess && csvResult?.isSuccess == true -> Result.success()
            error is TelegramException -> Result.retry()
            else -> Result.success()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoSyncEntryPoint {
    fun syncService(): SyncService
}

object AutoSyncScheduler {
    private const val UNIQUE_WORK = "patient_csv_auto_sync"

    fun configure(context: Context, enabled: Boolean, wifiOnly: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()
        val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(
            AppConstants.SYNC_CSV_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        manager.enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
