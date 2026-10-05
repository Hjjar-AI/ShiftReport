package com.hos.rushdpatients.ui.doctors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorsScreen(
    onBack: () -> Unit,
    viewModel: DoctorsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Doctor?>(null) }
    var deleteTarget by remember { mutableStateOf<Doctor?>(null) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل الأطباء") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "مزامنة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("إضافة طبيب") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                state.doctors.isEmpty() -> EmptyState(
                    title = "لا يوجد أطباء",
                    subtitle = "أضف طبيباً جديداً للبدء"
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.doctors, key = { it.id }) { doctor ->
                        DoctorCard(
                            doctor = doctor,
                            onEdit = { editTarget = doctor },
                            onDelete = { deleteTarget = doctor }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddEditDoctorDialog(
            existing = null,
            saving = state.saving,
            onConfirm = { fn, ln, g, role, pin, ct, tid ->
                viewModel.addDoctor(fn, ln, g, role, pin, ct, tid) { showAdd = false }
            },
            onDismiss = { showAdd = false }
        )
    }

    editTarget?.let { d ->
        AddEditDoctorDialog(
            existing = d,
            saving = state.saving,
            onConfirm = { fn, ln, g, role, pin, ct, tid ->
                viewModel.editDoctor(d.id, fn, ln, g, role, pin, ct, tid) { editTarget = null }
            },
            onDismiss = { editTarget = null }
        )
    }

    deleteTarget?.let { d ->
        ConfirmDialog(
            title = "حذف طبيب",
            message = "هل أنت متأكد من حذف ${d.fullName}؟",
            onConfirm = {
                viewModel.deleteDoctor(d.id) { deleteTarget = null }
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun DoctorCard(
    doctor: Doctor,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(doctor.fullName, style = MaterialTheme.typography.bodyLarge)
                val roleText = when {
                    doctor.isPermanentAdmin -> "مدير دائم (رتبة ${doctor.rank})"
                    doctor.isAdmin -> "مدير (رتبة ${doctor.rank})"
                    else -> "طبيب"
                }
                Text(roleText, style = MaterialTheme.typography.bodySmall)
                Text("التصنيف: ${doctor.clinicalRole.arabicLabel}", style = MaterialTheme.typography.bodySmall)
                doctor.telegramId?.let {
                    Text("تليجرام: $it", style = MaterialTheme.typography.bodySmall)
                }
                doctor.customTitle?.let {
                    Text("اللقب: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "تعديل")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف")
            }
        }
    }
}
