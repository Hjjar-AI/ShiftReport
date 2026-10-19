package com.hos.rushdpatients

import android.app.Application
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RushdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Stop any previously scheduled job; synchronization is now manual only.
        WorkManager.getInstance(this).cancelUniqueWork("patient_csv_auto_sync")
    }
}
