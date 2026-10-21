package com.hos.rushdpatients.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.data.db.DatabaseKeyProvider
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.ui.components.*
import dagger.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface StorageAccessEntryPoint {
    fun projectConfigStore(): ProjectConfigStore
    fun sessionManager(): SessionManager
}

/** Resolve encrypted storage before any repository-backed ViewModel is constructed. */
@Composable
internal fun StorageAccessGate(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    var attempt by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        failed = false
        try {
            withContext(Dispatchers.IO) { checkStorageAccess(context) }
            ready = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Never reset preferences, recreate an existing database key, or expose exception details.
            failed = true
        }
    }
    when {
        ready -> content()
        failed -> AppCenteredContent {
            AppNotice(
                "تعذر فتح التخزين المشفر على هذا الجهاز. لم تُحذف البيانات ولم يُستبدل مفتاح قاعدة موجودة.",
                kind = NoticeKind.ERROR
            )
            Text("افتح قفل الجهاز ثم أعد المحاولة أو أعد تشغيله. إذا استمرت المشكلة، احتفظ ببيانات التطبيق وراجع مسؤول المشروع لاستعادة الوصول؛ لا تمسح التخزين، فقد توجد تعديلات لم تُنشر بعد.")
            AppButton(onClick = { attempt++ }) { Text("إعادة المحاولة") }
        }
        else -> AppCenteredContent { AppLoadingState("جار فتح التخزين المشفر…") }
    }
}

private suspend fun checkStorageAccess(context: Context) {
    (context as com.hos.rushdpatients.RushdApplication).pdfCacheCleanup.join()
    DatabaseKeyProvider(context).getOrCreatePassphrase().fill(0)
    val entryPoint = EntryPointAccessors.fromApplication(context, StorageAccessEntryPoint::class.java)
    val config = entryPoint.projectConfigStore().current()
    if (!config.demoMode && context.getDatabasePath(com.hos.rushdpatients.config.AppConstants.DATABASE_NAME).exists()) {
        // A stored but unusable key/database must reach the same preservation screen.
        com.hos.rushdpatients.data.db.AppDatabase.getInstance(context).openHelper.writableDatabase
    }
    entryPoint.sessionManager().current()
}
