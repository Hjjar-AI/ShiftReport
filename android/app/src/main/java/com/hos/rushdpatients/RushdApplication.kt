package com.hos.rushdpatients

import android.app.Application
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RushdApplication : Application() {
    internal lateinit var pdfCacheCleanup: kotlinx.coroutines.Job
        private set

    override fun onCreate() {
        super.onCreate()
        val processName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        // Startup providers initialize WorkManager in the main process only.
        // Secondary components must not repeat application-wide cleanup or scheduling.
        if (processName != null && processName != packageName) return
        pdfCacheCleanup = com.hos.rushdpatients.pdf.ClinicalPdfCache.clear(this)
        // Stop any previously scheduled job; synchronization is now manual only.
        WorkManager.getInstance(this).cancelUniqueWork("patient_csv_auto_sync")
        com.hos.rushdpatients.data.maintenance.RecycleBinCleanupWorker.schedule(this)
    }
}
