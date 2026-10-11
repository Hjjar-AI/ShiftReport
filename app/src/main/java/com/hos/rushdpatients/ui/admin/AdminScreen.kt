package com.hos.rushdpatients.ui.admin

import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.AppSection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    onOpenDoctors: () -> Unit,
    onOpenAnnouncement: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAssignAdmin by remember { mutableStateOf(false) }
    var showRemoveAdmin by remember { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("لوحة المدير") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع",
                            modifier = Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 180f else 0f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(UiSpacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
        ) {
            state.currentActor?.let { actor ->
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(UiPadding.content)) {
                        Text("المدير الحالي: ${actor.fullName}")
                        Text("الرتبة: ${actor.rank}")
                        Text(
                            "تؤثر الإجراءات في هذه الشاشة على جميع مستخدمي المشروع.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            AppSection(title = "ملخص الصلاحيات") {
                Text("المديرون: ${state.admins.size} · الأطباء الآخرون: ${state.nonAdmins.size}")
            }

            AdminActionCard(
                title = "الأطباء والحسابات",
                description = "إضافة الأطباء وتعديل بياناتهم واستيراد أو تصدير سجل CSV.",
                actionLabel = "فتح سجل الأطباء",
                onClick = onOpenDoctors,
                primary = true
            )

            AdminActionCard(
                title = "إعلان المجموعة",
                description = "تعديل الرسالة المثبتة التي يراها أعضاء الفريق.",
                actionLabel = "إدارة الإعلان",
                onClick = onOpenAnnouncement
            )

            AppSection(title = "صلاحيات المديرين") {
                Text(
                    "الترقية تمنح أدوات الإدارة. أزل الصلاحية قبل حذف حساب مدير.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppOutlinedButton(
                    onClick = { showAssignAdmin = true },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ترقية طبيب إلى مدير") }
                AppOutlinedButton(
                    onClick = { showRemoveAdmin = true },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إزالة صلاحية مدير") }
            }
        }
    }

    if (showAssignAdmin) {
        AssignAdminDialog(
            candidates = state.nonAdmins,
            saving = state.saving,
            onConfirm = { doctor ->
                viewModel.assignAdmin(doctor, null) { msg ->
                    showAssignAdmin = false
                    notify(msg)
                }
            },
            onDismiss = { showAssignAdmin = false }
        )
    }

    if (showRemoveAdmin) {
        RemoveAdminDialog(
            admins = state.admins.filter { it.id != state.currentActor?.id },
            saving = state.saving,
            onConfirm = { doctor ->
                viewModel.removeAdmin(doctor) { msg ->
                    showRemoveAdmin = false
                    notify(msg)
                }
            },
            onDismiss = { showRemoveAdmin = false }
        )
    }
}

@Composable
private fun AdminActionCard(
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    AppSection(title = title) {
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (primary) {
            AppButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
        } else {
            AppOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
        }
    }
}
