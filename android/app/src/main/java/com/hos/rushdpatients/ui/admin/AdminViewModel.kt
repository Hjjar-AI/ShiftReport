package com.hos.rushdpatients.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.usecase.DemoteAdminUseCase
import com.hos.rushdpatients.domain.usecase.PromoteAdminUseCase
import com.hos.rushdpatients.domain.usecase.PromoteResult
import com.hos.rushdpatients.sync.SyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val sessionManager: SessionManager,
    private val promoteAdmin: PromoteAdminUseCase,
    private val demoteAdmin: DemoteAdminUseCase,
    private val syncService: SyncService
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init { observe() }

    private fun observe() {
        viewModelScope.launch {
            doctorRepository.observeAll().collect { list ->
                val actor = sessionManager.current()?.doctorId?.let { id ->
                    list.firstOrNull { it.id == id }
                }
                _state.update {
                    it.copy(
                        loading = false,
                        admins = list.filter { d -> d.isAdmin },
                        nonAdmins = list.filter { d -> !d.isAdmin },
                        currentActor = actor
                    )
                }
            }
        }
    }

    fun assignAdmin(expected: Doctor, customTitle: String?, onDone: (String) -> Unit) {
        if (_state.value.saving) return
        if (_state.value.currentActor?.isAdmin != true) {
            onDone("غير مصرح لك بتنفيذ هذا الإجراء")
            return
        }
        val actor = _state.value.currentActor ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                when (val result = promoteAdmin.execute(
                    expected = expected,
                    customTitle = customTitle,
                    actorDoctorId = actor.id
                )) {
                    is PromoteResult.Success -> {
                        onDone(uploadRegistry().fold(
                            onSuccess = { "تم ترقية ${result.doctor.fullName}" },
                            onFailure = { "تمت الترقية محلياً وتعذرت مزامنة تليجرام" }
                        ))
                    }
                    is PromoteResult.Failure -> onDone(result.reason)
                }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun removeAdmin(expected: Doctor, onDone: (String) -> Unit) {
        if (_state.value.saving) return
        if (_state.value.currentActor?.isAdmin != true) {
            onDone("غير مصرح لك بتنفيذ هذا الإجراء")
            return
        }
        val actor = _state.value.currentActor ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                when (val result = demoteAdmin.execute(
                    expected = expected,
                    actorDoctorId = actor.id
                )) {
                    is PromoteResult.Success -> {
                        onDone(uploadRegistry().fold(
                            onSuccess = { "تم إزالة ${result.doctor.fullName} من المديرين" },
                            onFailure = { "تمت الإزالة محلياً وتعذرت مزامنة تليجرام" }
                        ))
                    }
                    is PromoteResult.Failure -> onDone(result.reason)
                }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun uploadRegistry(): Result<Unit> {
        return syncService.uploadCurrentDoctors()
    }
}
