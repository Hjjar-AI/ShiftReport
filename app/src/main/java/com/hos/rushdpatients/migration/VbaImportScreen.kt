package com.hos.rushdpatients.migration

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.util.ShiftDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VbaImportScreen(
    onBack: () -> Unit,
    viewModel: VbaImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmApply by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::loadUri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("استيراد من الإكسل") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // NOTE: Do NOT use `return@Box` here. Early returns inside a
            // composable lambda corrupt Compose's group stack and crash on
            // recomposition with ArrayIndexOutOfBoundsException in Stack.pop.
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { picker.launch(arrayOf("text/*", "text/csv", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Text("  اختر ملف CSV")
                    }

                    state.error?.let { err ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = err,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    state.imported?.let { result ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("تم الاستيراد", style = MaterialTheme.typography.titleMedium)
                                Text("جديد: ${result.inserted}")
                                Text("محدث: ${result.updated}")
                                Text("بدون تغيير: ${result.skipped}")
                                if (result.removed > 0) {
                                    Text("نُقل إلى سلة المحذوفات: ${result.removed}")
                                }
                                Text("الوضع: ${result.mode.arabicLabel}")
                            }
                        }
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("إنهاء") }
                    }

                    state.preview?.let { preview ->
                        val archivedImport = preview.shiftDate != ShiftDate.current()
                        Divider()
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("الملف: ${preview.fileName}", style = MaterialTheme.typography.titleSmall)
                                Text("تاريخ الوردية: ${preview.shiftDate}")
                                if (preview.doctors.isNotEmpty()) {
                                    Text("أطباء: ${preview.doctors.joinToString()}")
                                }
                                Divider()
                                Text("جديد: ${preview.newPatients.size}")
                                Text("محدث: ${preview.updatedPatients.size}")
                                Text("بدون تغيير: ${preview.unchangedPatients.size}")
                                Text("الإجمالي: ${preview.totalPatients}")
                            }
                        }

                        if (archivedImport) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Text(
                                    text = "هذه مناوبة سابقة. ستُستعاد محلياً كنسخة محفوظة للعرض فقط ولن تُنشر كمناوبة حالية.",
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }

                        if (preview.warnings.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    preview.warnings.forEach { Text("• $it") }
                                }
                            }
                        }

                        Text("طريقة الاستيراد", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImportMode.entries.forEach { mode ->
                                if (state.importMode == mode) {
                                    Button(
                                        onClick = { viewModel.setImportMode(mode) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(mode.arabicLabel) }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.setImportMode(mode) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(mode.arabicLabel) }
                                }
                            }
                        }
                        Text(
                            text = if (state.importMode == ImportMode.REPLACE) {
                                "الافتراضي: يستبدل مرضى المناوبة، وينقل غير الموجودين في الملف إلى سلة المحذوفات."
                            } else {
                                "يبقي المرضى الحاليين ويضيف الملف إليهم. عند تكرار المعرّف، بيانات الملف هي الأحدث."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { confirmApply = true },
                                modifier = Modifier.weight(1f),
                                enabled = preview.totalPatients > 0
                            ) { Text(if (archivedImport) "استعادة النسخة" else "تطبيق") }
                            OutlinedButton(
                                onClick = viewModel::reset,
                                modifier = Modifier.weight(1f)
                            ) { Text("إلغاء") }
                        }
                    }
                }
            }
        }
    }

    if (confirmApply) {
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            title = { Text("تأكيد الاستيراد") },
            text = {
                Text(
                    if (state.importMode == ImportMode.REPLACE) {
                        "سيتم استبدال قائمة المناوبة ونقل المرضى غير الموجودين في الملف إلى سلة المحذوفات. هل تريد المتابعة؟"
                    } else {
                        "ستُضاف بيانات الملف إلى القائمة الحالية، وستفوز أحدث نسخة عند تكرار المريض. هل تريد المتابعة؟"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmApply = false
                    viewModel.apply()
                }) { Text("متابعة") }
            },
            dismissButton = {
                TextButton(onClick = { confirmApply = false }) { Text("إلغاء") }
            }
        )
    }
}
