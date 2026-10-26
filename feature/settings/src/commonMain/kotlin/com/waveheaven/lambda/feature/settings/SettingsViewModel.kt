package com.waveheaven.lambda.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.consent.status
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.ExposureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Settings: the weekly standard (WH-075), consent and its withdrawal (WH-073), and erasing every stored aggregate
 * (WH-043).
 */
class SettingsViewModel(
    private val exposureLog: ExposureLog,
    private val preferences: UserPreferences,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {
    /** What only this screen knows: open dialogs, progress, notices. */
    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> =
        combine(local, preferences.weeklyStandard, preferences.consent) { screen, standard, consent ->
            screen.copy(
                weeklyStandard = standard,
                consentStatus = consent.status(),
                consentDate =
                    consent
                        ?.acceptedAt
                        ?.toLocalDateTime(timeZone())
                        ?.date
                        ?.toString(),
                consentVersion = consent?.version,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.StandardSelected -> selectStandard(action.standard)
            SettingsAction.WithdrawConsentClicked -> local.update { it.copy(isConfirmingWithdrawal = true, deleteDataOnWithdrawal = false) }
            is SettingsAction.DeleteDataOnWithdrawalChanged -> local.update { it.copy(deleteDataOnWithdrawal = action.delete) }
            SettingsAction.WithdrawConsentConfirmed -> withdrawConsent()
            SettingsAction.WithdrawConsentDismissed -> local.update { it.copy(isConfirmingWithdrawal = false) }
            SettingsAction.DeleteDataClicked -> local.update { it.copy(isConfirmingDeletion = true) }
            SettingsAction.DeleteDataDismissed -> local.update { it.copy(isConfirmingDeletion = false) }
            SettingsAction.DeleteDataConfirmed -> deleteData()
            SettingsAction.NoticeShown -> local.update { it.copy(notice = null) }
        }
    }

    private fun selectStandard(standard: DoseStandard) {
        viewModelScope.launch { preferences.setWeeklyStandard(standard) }
    }

    private fun withdrawConsent() {
        val deleteData = local.value.deleteDataOnWithdrawal
        local.update { it.copy(isConfirmingWithdrawal = false, isDeleting = deleteData) }
        viewModelScope.launch {
            preferences.withdrawConsent()
            if (deleteData) exposureLog.deleteAll()
            val notice = if (deleteData) SettingsNotice.CONSENT_WITHDRAWN_AND_DATA_DELETED else SettingsNotice.CONSENT_WITHDRAWN
            local.update { it.copy(isDeleting = false, notice = notice) }
        }
    }

    private fun deleteData() {
        local.update { it.copy(isConfirmingDeletion = false, isDeleting = true) }
        viewModelScope.launch {
            exposureLog.deleteAll()
            local.update { it.copy(isDeleting = false, notice = SettingsNotice.DATA_DELETED) }
        }
    }
}
