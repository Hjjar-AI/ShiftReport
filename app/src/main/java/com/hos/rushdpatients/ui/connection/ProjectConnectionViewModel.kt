package com.hos.rushdpatients.ui.connection

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.ConnectionChange
import com.hos.rushdpatients.config.ProjectConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectConnectionUiState(
    val busy: Boolean = false,
    val reviewProject: String? = null,
    val message: String? = null,
    val applied: Boolean = false
)

@HiltViewModel
class ProjectConnectionViewModel @Inject constructor(
    private val connections: ProjectConnectionManager
) : ViewModel() {
    private val _state = MutableStateFlow(ProjectConnectionUiState())
    val state = _state.asStateFlow()
    // In memory only; neither tokens nor passphrases enter SavedStateHandle.
    private var pending: ConnectionChange? = null

    fun importFile(uri: Uri, password: String) = prepare {
        connections.prepareFile(uri, password.toCharArray())
    }

    fun replaceToken(token: String) = prepare { connections.prepareToken(token) }

    private fun prepare(action: suspend () -> ConnectionChange) {
        if (_state.value.busy) return
        pending = null
        _state.value = ProjectConnectionUiState(busy = true)
        viewModelScope.launch {
            try {
                val change = action()
                pending = change
                _state.update { it.copy(reviewProject = change.expected.hospitalName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = safeMessage(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun confirm() {
        if (_state.value.busy) return
        val change = pending ?: return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                connections.apply(change)
                pending = null
                _state.update {
                    it.copy(reviewProject = null, applied = true,
                        message = "تم تحديث الاتصال مع الاحتفاظ بالمرضى والمناوبات ومفتاح التشفير. على المدير تصدير ملف انضمام جديد لبقية الأجهزة.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pending = null
                _state.update { it.copy(reviewProject = null, message = safeMessage(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun reset() {
        if (_state.value.busy) return
        pending = null
        _state.value = ProjectConnectionUiState()
    }

    private fun safeMessage(error: Exception): String = when (error) {
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "تعذر تحديث الاتصال"
        else -> "تعذر التحقق من الاتصال. تأكد من الإنترنت وصلاحيات البوت والرمز الجديد، ثم أعد المحاولة."
    }
}
