package com.hos.rushdpatients.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.currentActor?.let { actor ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("المدير الحالي: ${actor.fullName}")
                        Text("الرتبة: ${actor.rank}")
                    }
                }
            }

            Divider()

            Text("إدارة الأطباء", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = onOpenDoctors,
                modifier = Modifier.fillMaxWidth()
            ) { Text("سجل الأطباء") }

            Divider()

            Text("الإعلانات", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onOpenAnnouncement,
                modifier = Modifier.fillMaxWidth()
            ) { Text("إدارة إعلان المجموعة") }

            Divider()

            Text("إدارة صلاحيات المديرين", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { showAssignAdmin = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("ترقية طبيب إلى مدير") }
            OutlinedButton(
                onClick = { showRemoveAdmin = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("إزالة مدير") }

            Divider()

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("عدد المديرين: ${state.admins.size}")
                    Text("عدد الأطباء: ${state.nonAdmins.size}")
                }
            }
        }
    }

    if (showAssignAdmin) {
        AssignAdminDialog(
            candidates = state.nonAdmins,
            onConfirm = { id ->
                viewModel.assignAdmin(id, null) { msg ->
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
            onConfirm = { id ->
                viewModel.removeAdmin(id) { msg ->
                    showRemoveAdmin = false
                    notify(msg)
                }
            },
            onDismiss = { showRemoveAdmin = false }
        )
    }
}
