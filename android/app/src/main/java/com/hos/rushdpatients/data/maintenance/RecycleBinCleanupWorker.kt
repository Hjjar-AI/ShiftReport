package com.hos.rushdpatients.data.maintenance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Device-local retention only; restored and active patients never match the delete query. */
class RecycleBinCleanupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Do not initialize a production database for an unused/demo-only installation.
            if (!applicationContext.getDatabasePath(AppConstants.DATABASE_NAME).exists()) {
                return@withContext Result.success()
            }
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            AppDatabase.getInstance(applicationContext).patientDao().purgeSoftDeletedBefore(cutoff)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            // Android may defer this daily sweep; seven days is a minimum, not a deadline.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "patient_recycle_bin_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RecycleBinCleanupWorker>(1, TimeUnit.DAYS).build()
            )
        }
    }
}
