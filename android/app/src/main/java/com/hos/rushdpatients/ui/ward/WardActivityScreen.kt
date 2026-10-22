package com.hos.rushdpatients.ui.ward

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.uiDateTime
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.PaddingValues
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import com.hos.rushdpatients.ui.components.AppDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.sync.PublicationJournalEntry
import java.time.Instant

internal enum class ActivityFilter(@StringRes private val arabicLabelRes: Int) {
    ALL(R.string.ui_all), PATIENTS(R.string.ui_patients), SYNC(R.string.ui_ward_activity_screen_015), ACCESS(R.string.ui_ward_activity_screen_016), PUBLICATIONS(R.string.ui_ward_activity_screen_017);

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
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
    modifier: Modifier = Modifier,
    bottomClearance: Dp = UiSpacing.screen
) {
    val uiText = rememberUiStrings()
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
            if (it.forced) uiText(R.string.ui_ward_activity_screen_001) else uiText(R.string.ui_ward_activity_screen_002), activityTime(it.at)) }
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomClearance),
        state = if (filter == ActivityFilter.PUBLICATIONS) publicationScroll else activityScroll,
        modifier = modifier.fillMaxSize().padding(horizontal = UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
    ) {
        item(key = "activity-heading") {
            Text(uiText(R.string.ui_ward_activity_screen_003), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = UiSpacing.medium).semantics { heading() })
            AppTextButton(onClick = onRefresh, enabled = !loading,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(if (loading) uiText(R.string.ui_ward_activity_screen_004) else uiText(R.string.ui_ward_activity_screen_005))
            }
            error?.let { AppNotice(it, kind = NoticeKind.ERROR, actionLabel = uiText(R.string.ui_retry),
                onAction = onRefresh, actionEnabled = !loading) }
        }
        item(key = "activity-search") {
            com.hos.rushdpatients.ui.components.AppListToolbar(query, onQueryChange,
                uiText(R.string.ui_ward_activity_screen_006)) {
                com.hos.rushdpatients.ui.components.AppChoiceChips(ActivityFilter.entries.toList(), filter,
                    onFilterChange, { it.arabicLabel(uiText) })
            }
        }
        if (filter == ActivityFilter.PUBLICATIONS) {
            if (filteredPublications.isEmpty()) item { com.hos.rushdpatients.ui.components.EmptyState(uiText(R.string.ui_ward_activity_screen_007),
                actionLabel = uiText(R.string.ui_ward_activity_screen_008), onAction = { onQueryChange(""); onFilterChange(ActivityFilter.ALL) }) }
            items(filteredPublications, key = { "publication-${it.messageId}" }) { publication ->
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                    Text(if (publication.forced) uiText(R.string.ui_ward_activity_screen_001) else uiText(R.string.ui_ward_activity_screen_002), style = MaterialTheme.typography.titleSmall)
                    Text(activityTime(publication.at), style = MaterialTheme.typography.bodySmall)
                    Text(uiText(R.string.ui_ward_activity_screen_009), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppDivider()
            }
        } else {
            if (filtered.isEmpty()) item { com.hos.rushdpatients.ui.components.EmptyState(uiText(R.string.ui_ward_activity_screen_010),
                actionLabel = if (query.isNotBlank() || filter != ActivityFilter.ALL) uiText(R.string.ui_ward_activity_screen_008) else null,
                onAction = { onQueryChange(""); onFilterChange(ActivityFilter.ALL) }) }
            items(filtered, key = { "audit-${it.id}" }) { entry ->
                ActivityEntry(entry, entry.patientId?.let(patientNames::get))
                AppDivider()
            }
        }
        item(key = "activity-footer") {
            Text(uiText(R.string.ui_ward_activity_screen_011),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = UiSpacing.small))
        }
    }
}

@Composable
internal fun ActivityEntry(entry: AuditEntryEntity, patientName: String? = null) {
    val uiText = rememberUiStrings()
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        patientName?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        Text(entry.actorName ?: uiText(R.string.ui_ward_activity_screen_012), style = MaterialTheme.typography.labelLarge)
        Text(entry.detail.ifBlank { entry.action })
        Text(activityTime(entry.atEpochMillis), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        entry.beforeValue?.takeIf { it.isNotBlank() }?.let { Text(uiText(R.string.ui_ward_activity_screen_013, it), style = MaterialTheme.typography.bodySmall) }
        entry.afterValue?.takeIf { it.isNotBlank() }?.let { Text(uiText(R.string.ui_ward_activity_screen_014, it), style = MaterialTheme.typography.bodySmall) }
    }
}

private fun activityTime(epochMillis: Long): String = uiDateTime(Instant.ofEpochMilli(epochMillis))
