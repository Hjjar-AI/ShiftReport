package com.hos.rushdpatients

import android.app.Application
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.sync.AutoSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RushdApplication : Application() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var doctorRepository: DoctorRepository
    @Inject lateinit var projectConfigStore: ProjectConfigStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // On a fresh install the registry is empty until the user completes
            // Telegram bootstrap on the login screen. Only schedule background
            // sync once we actually have doctors locally.
            if (!projectConfigStore.current().initialized || doctorRepository.count() == 0) {
                return@launch
            }
            AutoSyncScheduler.configure(
                context = this@RushdApplication,
                enabled = settingsRepository.isAutoSyncEnabled(),
                wifiOnly = settingsRepository.isSyncWifiOnly()
            )
        }
    }
}
