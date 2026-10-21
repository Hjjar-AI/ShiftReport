package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.AppDestinationRow
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

/** One manual-entry action, then clearly explained sources; mutations stay caller-owned. */
@Composable
internal fun EmptyWardPatients(isReadOnly: Boolean, busy: Boolean, canCopy: Boolean,
    bottomClearance: Dp, onAdd: () -> Unit, onFetch: () -> Unit, onCopyPrevious: () -> Unit,
    topContent: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = com.hos.rushdpatients.ui.theme.UiLayout.emptyWardMaxWidth)
            .fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = UiSpacing.section, vertical = UiSpacing.section)
            .padding(bottom = bottomClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
            topContent()
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                Icon(Icons.Filled.ViewAgenda, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(UiSpacing.section).size(UiSize.icon))
            }
            Text(if (isReadOnly) "لا يوجد مرضى في هذه المناوبة" else "ابدأ قائمة مرضى المناوبة",
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
            Text(if (isReadOnly) "هذه مناوبة محفوظة للعرض فقط."
                else "قائمة هذه المناوبة على جهازك فارغة. اختر كيف تريد البدء.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!isReadOnly) {
                AppButton(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(UiSize.iconMedium))
                    Text("إضافة أول مريض", Modifier.padding(start = UiSpacing.small))
                }
                Text("لإدخال مريض جديد يدوياً", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("هل لديك بيانات جاهزة؟", modifier = Modifier.fillMaxWidth()
                    .padding(top = UiSpacing.screen).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall)
                Surface(shape = MaterialTheme.shapes.medium, border = com.hos.rushdpatients.ui.theme.UiBorder.stroke()) {
                    AppDestinationRow(title = "جلب بيانات الفريق", icon = Icons.Filled.CloudDownload,
                        summary = "استرجع أحدث نسخة منشورة على تليجرام بعد التأكيد.",
                        enabled = !busy, onClick = onFetch)
                }
                Surface(shape = MaterialTheme.shapes.medium, border = com.hos.rushdpatients.ui.theme.UiBorder.stroke()) {
                    AppDestinationRow(title = "نسخ المناوبة السابقة", icon = Icons.Filled.History,
                        summary = "ابدأ اليوم بنسخة من المرضى والمهام غير المكتملة؛ تراجعها قبل الحفظ.",
                        enabled = !busy && canCopy, onClick = onCopyPrevious)
                }
            }
        }
    }
}
