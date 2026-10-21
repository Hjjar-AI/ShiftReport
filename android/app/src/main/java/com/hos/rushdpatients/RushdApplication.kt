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
        pdfCacheCleanup = com.hos.rushdpatients.pdf.ClinicalPdfCache.clear(this)
        // Stop any previously scheduled job; synchronization is now manual only.
        WorkManager.getInstance(this).cancelUniqueWork("patient_csv_auto_sync")
    }
}
