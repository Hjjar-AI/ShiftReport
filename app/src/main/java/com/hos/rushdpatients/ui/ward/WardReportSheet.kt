package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.hos.rushdpatients.ui.theme.LocalClinicalColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WardReportSheet(
    patientCount: Int,
    freshnessLabel: String,
    issueCount: Int,
    hasMergeConflicts: Boolean,
    foldingFeature: FoldingFeature?,
    onReviewReport: () -> Unit,
    onDismiss: () -> Unit
) {
    val clinicalColors = LocalClinicalColors.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
            listFraction = .42f, onListFractionChange = {},
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp), primary = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("إرسال تقرير المناوبة", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${patientCount} مريض · $freshnessLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (issueCount == 0) {
                        clinicalColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        if (issueCount == 0) {
                            "التقرير جاهز للمراجعة والإرسال"
                        } else {
                            "$issueCount ملاحظات تحتاج المراجعة قبل الإرسال"
                        },
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (hasMergeConflicts) {
                    Text(
                        "يتضمن العدد تعارضات مزامنة يجب حسمها داخل شاشة التقرير.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = onReviewReport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Text(
                        "مراجعة التقرير",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إغلاق") }
            }
        })
    }
}
