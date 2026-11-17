package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.hos.rushdpatients.ui.theme.UiLayout

@Suppress("UNCHECKED_CAST")
internal class WardWorkspaceState {
    var listFraction by mutableStateOf(UiLayout.defaultListFraction)
    var showSearch by mutableStateOf(false)
    var requestSearchFocus by mutableStateOf(false)
    var collapsedGroups by mutableStateOf(emptyList<String>())
    var selectedViewOption by mutableStateOf(WardViewOption.SORT)
    var bulkExpansionOverride by mutableStateOf<Boolean?>(null)
    var expandedPatientIds by mutableStateOf(emptyList<String>())
    var collapsedPatientIds by mutableStateOf(emptyList<String>())
    var returnTarget by mutableStateOf<PatientReturnTarget?>(null)
    var recentlyEditedId by mutableStateOf<String?>(null)
    var animatedPatientId by mutableStateOf<String?>(null)

    fun setAllPatientsExpanded(expand: Boolean, persist: (Boolean) -> Unit) {
        animatedPatientId = null
        bulkExpansionOverride = expand
        expandedPatientIds = emptyList()
        collapsedPatientIds = emptyList()
        persist(expand)
    }

    fun toggleCard(id: String, allExpanded: Boolean) {
        animatedPatientId = id
        if (allExpanded) collapsedPatientIds = if (id in collapsedPatientIds) collapsedPatientIds - id else collapsedPatientIds + id
        else expandedPatientIds = if (id in expandedPatientIds) expandedPatientIds - id else expandedPatientIds + id
    }

    companion object {
        val Saver = listSaver<WardWorkspaceState, Any>(
            save = { listOf(it.listFraction, it.showSearch, it.collapsedGroups, it.selectedViewOption.name,
                it.bulkExpansionOverride?.toString().orEmpty(), it.expandedPatientIds, it.collapsedPatientIds) },
            restore = { saved -> WardWorkspaceState().apply {
                listFraction = saved[0] as Float
                showSearch = saved[1] as Boolean
                collapsedGroups = saved[2] as List<String>
                selectedViewOption = WardViewOption.valueOf(saved[3] as String)
                bulkExpansionOverride = (saved[4] as String).takeIf { it.isNotEmpty() }?.toBoolean()
                expandedPatientIds = saved[5] as List<String>
                collapsedPatientIds = saved[6] as List<String>
            } }
        )
    }
}

@Composable
internal fun rememberWardWorkspaceState(): WardWorkspaceState = rememberSaveable(saver = WardWorkspaceState.Saver) {
    WardWorkspaceState()
}

/** Common UI availability only; repository/security and action-specific checks remain in place. */
internal val WardUiState.canMutate: Boolean get() = !isReadOnly && !saving && !syncing
internal val WardUiState.canInteract: Boolean get() = !saving && !syncing
internal fun WardUiState.canStartEdit(editor: WardEditorState): Boolean = canMutate && editor.dialog == null
