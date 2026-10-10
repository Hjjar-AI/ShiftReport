package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.sync.PublicationJournalEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class ActivityFilter(val arabicLabel: String) {
    ALL("الكل"), PATIENTS("المرضى"), SYNC("المزامنة والتقارير"), ACCESS("الدخول"), PUBLICATIONS("المنشورات")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun WardActivityScreen(
    activity: List<AuditEntryEntity>,
    publications: List<PublicationJournalEntry>,
    patientNames: Map<String, String>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ActivityFilter,
    onFilterChange: (ActivityFilter) -> Unit,
    activityScroll: LazyListState,
    publicationScroll: LazyListState,
    onRefresh: () -> Unit,
    loading: Boolean = false,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    val filtered = remember(activity, patientNames, query, filter) {
        activity.filter { entry ->
            val matchesType = when (filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.PATIENTS -> entry.patientId != null || entry.action.startsWith("patient_")
                ActivityFilter.SYNC -> entry.action.contains("csv") || entry.action.contains("sync") || entry.action.contains("report")
                ActivityFilter.ACCESS -> entry.action == AppConstants.AUDIT_LOGIN || entry.action == AppConstants.AUDIT_LOGOUT
                ActivityFilter.PUBLICATIONS -> false
            }
            matchesType && ArabicSearchNormalizer.matches(query, entry.patientId?.let(patientNames::get).orEmpty(),
                entry.actorName.orEmpty(), entry.action, entry.detail, entry.beforeValue.orEmpty(), entry.afterValue.orEmpty())
        }
    }
    val filteredPublications = remember(publications, query) {
        publications.filter { query.isBlank() || ArabicSearchNormalizer.matches(query,
            if (it.forced) "نشر إجباري" else "نشر عادي", activityTime(it.at)) }
    }
    LazyColumn(
        state = if (filter == ActivityFilter.PUBLICATIONS) publicationScroll else activityScroll,
        modifier = modifier.fillMaxSize().padding(horizontal = UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
    ) {
        item(key = "activity-heading") {
            Text("مركز النشاط", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = UiSpacing.medium).semantics { heading() })
            AppTextButton(onClick = onRefresh, enabled = !loading,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(if (loading) "جار تحديث النشاط…" else "تحديث النشاط")
            }
            error?.let { AppNotice(it, kind = NoticeKind.ERROR, actionLabel = "إعادة المحاولة",
                onAction = onRefresh, actionEnabled = !loading) }
        }
        item(key = "activity-search") {
            AppTextField(value = query, onValueChange = onQueryChange,
                label = { Text("بحث بالمريض أو الطبيب أو العملية") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
        }
        item(key = "activity-filters") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                ActivityFilter.entries.forEach { option ->
                    FilterChip(selected = filter == option, onClick = { onFilterChange(option) },
                        label = { Text(option.arabicLabel) }, modifier = Modifier.heightIn(min = UiSpacing.touchTarget))
                }
            }
        }
        if (filter == ActivityFilter.PUBLICATIONS) {
            if (filteredPublications.isEmpty()) item { Text("لا توجد منشورات مطابقة") }
            items(filteredPublications, key = { "publication-${it.messageId}" }) { publication ->
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                    Text(if (publication.forced) "نشر إجباري" else "نشر عادي", style = MaterialTheme.typography.titleSmall)
                    Text(activityTime(publication.at), style = MaterialTheme.typography.bodySmall)
                    Text("محفوظ في تليجرام", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider()
            }
        } else {
            if (filtered.isEmpty()) item { Text("لا يوجد نشاط مطابق") }
            items(filtered, key = { "audit-${it.id}" }) { entry ->
                ActivityEntry(entry, entry.patientId?.let(patientNames::get))
                Divider()
            }
        }
        item(key = "activity-footer") {
            Text("يعرض النشاط المحلي الأخير وسجل المنشورات المتاح؛ لا يمثل إقراراً باستلام المناوبة.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 96.dp))
        }
    }
}

@Composable
internal fun ActivityEntry(entry: AuditEntryEntity, patientName: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        patientName?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        Text(entry.actorName ?: "النظام", style = MaterialTheme.typography.labelLarge)
        Text(entry.detail.ifBlank { entry.action })
        Text(activityTime(entry.atEpochMillis), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        entry.beforeValue?.takeIf { it.isNotBlank() }?.let { Text("قبل: $it", style = MaterialTheme.typography.bodySmall) }
        entry.afterValue?.takeIf { it.isNotBlank() }?.let { Text("بعد: $it", style = MaterialTheme.typography.bodySmall) }
    }
}

private fun activityTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
