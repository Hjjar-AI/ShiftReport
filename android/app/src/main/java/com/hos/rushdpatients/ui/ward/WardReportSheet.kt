package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.window.layout.FoldingFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WardReportSheet(
    patientCount: Int,
    freshnessLabel: String,
    issueCount: Int,
    foldingFeature: FoldingFeature?,
    onReviewReport: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
            listFraction = .42f, onListFractionChange = {},
            modifier = Modifier.fillMaxWidth().appDialogBody(UiDialog.sheetMaxHeight), primary = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiSpacing.screen, vertical = UiSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                Text("إرسال تقرير المناوبة", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${uiNumber(patientCount)} مريض · $freshnessLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                com.hos.rushdpatients.ui.components.AppNotice(
                    message = if (issueCount == 0) "التقرير جاهز للمراجعة والإرسال"
                        else "${com.hos.rushdpatients.ui.components.uiNumber(issueCount)} ملاحظات تحتاج المراجعة قبل الإرسال",
                    kind = if (issueCount > 0) com.hos.rushdpatients.ui.components.NoticeKind.WARNING
                        else com.hos.rushdpatients.ui.components.NoticeKind.SUCCESS)
                AppButton(
                    onClick = onReviewReport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(UiAction.REVIEW.icon, contentDescription = null)
                    Text(
                        UiAction.REVIEW.label + " التقرير",
                        modifier = Modifier.padding(start = UiSpacing.small)
                    )
                }
                AppTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إغلاق") }
            }
        })
    }
}
