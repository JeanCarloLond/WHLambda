package com.waveheaven.lambda.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waveheaven.lambda.core.domain.alert.DoseAlertSettings
import com.waveheaven.lambda.core.domain.alert.DoseAlertStore
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.consent.status
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.ExposureLog
import com.waveheaven.lambda.core.domain.volume.RunningAudioApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/**
 * Settings: the weekly standard (WH-075), dose alerts (WH-083), microphone calibration (WH-004), the music app on
 * desktop (#17), consent and its withdrawal (WH-073), and erasing every stored aggregate (WH-043).
 */
class SettingsViewModel(
    private val exposureLog: ExposureLog,
    private val preferences: UserPreferences,
    private val alerts: DoseAlertStore,
    private val runningApps: RunningAudioApps? = null,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {
    /** What only this screen knows: open dialogs, progress, notices. */
    private val local = MutableStateFlow(SettingsUiState(runningApps = runningApps?.listSafely()))

    val state: StateFlow<SettingsUiState> =
        combine(
            local,
            preferences.weeklyStandard,
            preferences.consent,
            alerts.settings,
            combine(preferences.microphoneAdjustmentDb, preferences.mediaApp, ::Pair),
        ) { screen, standard, consent, alertSettings, (adjustment, app) ->
            screen.copy(
                weeklyStandard = standard,
                alerts = alertSettings.enabled,
                microphoneAdjustmentDb = adjustment.roundToInt(),
                mediaApp = app,
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

            is SettingsAction.AlertToggled, is SettingsAction.AllAlertsMuted -> onAlertAction(action)

            is SettingsAction.MicrophoneAdjustmentChanged, SettingsAction.MicrophoneAdjustmentReset,
            is SettingsAction.MediaAppSelected, SettingsAction.RefreshRunningApps,
            -> onAudioAction(action)

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

    private fun onAlertAction(action: SettingsAction) {
        val current = state.value.alerts
        val enabled =
            when (action) {
                is SettingsAction.AlertToggled -> if (action.enabled) current + action.threshold else current - action.threshold
                is SettingsAction.AllAlertsMuted -> if (action.muted) emptySet() else DoseThreshold.entries.toSet()
                else -> return
            }
        setAlerts(enabled)
    }

    private fun onAudioAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.MicrophoneAdjustmentChanged -> setAdjustment(action.adjustmentDb.roundToInt().toDouble())
            SettingsAction.MicrophoneAdjustmentReset -> setAdjustment(0.0)
            is SettingsAction.MediaAppSelected -> viewModelScope.launch { preferences.setMediaApp(action.app) }
            SettingsAction.RefreshRunningApps -> local.update { it.copy(runningApps = runningApps?.listSafely()) }
            else -> return
        }
    }

    private fun selectStandard(standard: DoseStandard) {
        viewModelScope.launch { preferences.setWeeklyStandard(standard) }
    }

    private fun setAdjustment(adjustmentDb: Double) {
        val clamped = adjustmentDb.coerceIn(UserPreferences.MICROPHONE_ADJUSTMENT_RANGE_DB)
        viewModelScope.launch { preferences.setMicrophoneAdjustmentDb(clamped) }
    }

    /** Listing calls the system mixer; if it fails, the list is just empty. */
    private fun RunningAudioApps.listSafely(): List<String> = runCatching { list() }.getOrDefault(emptyList())

    private fun setAlerts(enabled: Set<DoseThreshold>) {
        viewModelScope.launch { alerts.setSettings(DoseAlertSettings(enabled)) }
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
