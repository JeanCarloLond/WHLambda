package com.waveheaven.lambda.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waveheaven.lambda.core.domain.exposure.ExposureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Settings: lets the user erase every stored listening aggregate (WH-043). */
class SettingsViewModel(
    private val exposureLog: ExposureLog,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.DeleteDataClicked -> mutableState.update { it.copy(isConfirmingDeletion = true) }
            SettingsAction.DeleteDataDismissed -> mutableState.update { it.copy(isConfirmingDeletion = false) }
            SettingsAction.DeleteDataConfirmed -> deleteData()
            SettingsAction.DeletionNoticeShown -> mutableState.update { it.copy(showDeletionNotice = false) }
        }
    }

    private fun deleteData() {
        mutableState.update { it.copy(isConfirmingDeletion = false, isDeleting = true) }
        viewModelScope.launch {
            exposureLog.deleteAll()
            mutableState.update { it.copy(isDeleting = false, showDeletionNotice = true) }
        }
    }
}
