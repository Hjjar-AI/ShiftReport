package com.hos.rushdpatients.di

import android.content.Context
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.db.dao.AuditDao
import com.hos.rushdpatients.data.db.dao.DoctorDao
import com.hos.rushdpatients.data.db.dao.PatientDao
import com.hos.rushdpatients.data.db.dao.SettingDao
import com.hos.rushdpatients.data.db.dao.ShiftDao
import com.hos.rushdpatients.data.db.dao.SyncStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = AppDatabase.getInstance(context)

    @Provides
    fun providePatientDao(db: AppDatabase): PatientDao = db.patientDao()

    @Provides
    fun provideDoctorDao(db: AppDatabase): DoctorDao = db.doctorDao()

    @Provides
    fun provideShiftDao(db: AppDatabase): ShiftDao = db.shiftDao()

    @Provides
    fun provideSettingDao(db: AppDatabase): SettingDao = db.settingDao()

    @Provides
    fun provideSyncStateDao(db: AppDatabase): SyncStateDao = db.syncStateDao()

    @Provides
    fun provideAuditDao(db: AppDatabase): AuditDao = db.auditDao()
}