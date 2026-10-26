package com.hos.rushdpatients.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing

/** The screen and search share one visibility predicate; search never indexes credentials/values. */
internal fun availableSettingsSections(canManageProject: Boolean, permanentAdmin: Boolean): List<SettingsSectionId> =
    SettingsSectionId.entries.filter {
        (canManageProject || !it.adminOnly) && (it != SettingsSectionId.RESET || permanentAdmin)
    }

/** Search opens existing workspaces. The caller keeps its forms mounted underneath. */
@Composable
internal fun SettingsSearchPanel(
    sections: List<SettingsSectionId>, onOpen: (SettingsWorkspace) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiText = rememberUiStrings()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val destinations = SettingsWorkspace.entries.filter { workspace ->
        val members = sections.filter { it.workspace == workspace }
        members.isNotEmpty() && ArabicSearchNormalizer.matches(query,
            (listOf(workspace.title(uiText), workspace.description(uiText)) + members.map { it.title(uiText) }).joinToString(" "))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(UiPadding.screen), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppSearchField(query, { query = it }, uiText(R.string.ui_settings_search),
                modifier = Modifier.focusRequester(focus))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                if (destinations.isEmpty()) EmptyState(uiText(R.string.ui_settings_search_empty), icon = AppIcons.Search)
                destinations.forEach { workspace ->
                    AppDestinationRow(title = workspace.title(uiText), summary = workspace.description(uiText),
                        icon = when (workspace) {
                            SettingsWorkspace.APPEARANCE -> AppIcons.Palette
                            SettingsWorkspace.WORKFLOW -> AppIcons.Route
                            SettingsWorkspace.REPORTS -> AppIcons.PictureAsPdf
                            SettingsWorkspace.SECURITY -> AppIcons.Lock
                            SettingsWorkspace.DATA -> AppIcons.Sync
                            SettingsWorkspace.FILES -> AppIcons.Description
                            SettingsWorkspace.BACKUP -> AppIcons.Backup
                            SettingsWorkspace.PROJECT -> AppIcons.Link
                            SettingsWorkspace.INVITATIONS -> AppIcons.PersonAdd
                            SettingsWorkspace.DESTINATIONS -> AppIcons.Groups
                            SettingsWorkspace.RESET -> AppIcons.RestartAlt
                            else -> AppIcons.Settings
                        }, onClick = { onOpen(workspace) })
                }
            }
        }
    }
}
