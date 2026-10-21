package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.sync.SyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Uses the owning ViewModel state/scope; repository guards remain unchanged. */
internal class WardActivityOperations(
    private val uiText: UiStrings,
    private val _state: MutableStateFlow<WardUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val syncService: SyncService,
    private val auditRepository: AuditRepository,
    private val adminAuthorizer: AdminAuthorizer
) {
    private var activityJob: Job? = null

    fun loadLocalActivity() {
        viewModelScope.launch {
            try {
                val activity = auditRepository.getRecent()
                _state.update { it.copy(recentActivity = activity) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(activityError = e.message ?: uiText(R.string.ui_ward_activity_operations_001)) }
            }
        }
    }

    fun loadRecentActivity() {
        if (activityJob?.isActive == true) return
        activityJob = viewModelScope.launch {
            try {
                adminAuthorizer.requireAdmin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(recentActivity = emptyList(), publications = emptyList(),
                    activityLoading = false, activityError = e.message) }
                return@launch
            }
            _state.update { it.copy(activityLoading = true, activityError = null) }
            try {
                val activity = auditRepository.getRecent()
                _state.update { it.copy(recentActivity = activity) }
                val publications = syncService.getPublicationJournal()
                _state.update { it.copy(publications = publications) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(activityError = e.message ?: uiText(R.string.ui_ward_activity_operations_002)) }
            } finally {
                _state.update { it.copy(activityLoading = false) }
            }
        }
    }
}
