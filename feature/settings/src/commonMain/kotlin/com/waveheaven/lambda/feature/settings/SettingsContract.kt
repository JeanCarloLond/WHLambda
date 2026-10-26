package com.waveheaven.lambda.feature.settings

import androidx.navigation3.runtime.NavKey
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import com.waveheaven.lambda.core.domain.consent.ConsentStatus
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import kotlinx.serialization.Serializable

/** Navigation destination for the settings screen. */
@Serializable
data object SettingsKey : NavKey

/** Everything the settings screen renders. Immutable; produced only by [SettingsViewModel]. */
data class SettingsUiState(
    val weeklyStandard: DoseStandard = DoseStandard.WHO_ADULT,
    /** The user's correction to the microphone calibration, in whole dB (WH-004). */
    val microphoneAdjustmentDb: Int = 0,
    /** Desktop only: the app whose volume is adapted, and the apps playing now. `null` lists hide the section. */
    val mediaApp: String? = null,
    val runningApps: List<String>? = null,
    /** Dose alert thresholds the user wants (WH-083); none means muted. */
    val alerts: Set<DoseThreshold> = DoseThreshold.entries.toSet(),
    val consentStatus: ConsentStatus = ConsentStatus.MISSING,
    /** Local date consent was given, as `yyyy-mm-dd`, when it was. */
    val consentDate: String? = null,
    val consentVersion: Int? = null,
    /** The "withdraw consent" confirmation is open. */
    val isConfirmingWithdrawal: Boolean = false,
    /** In that confirmation, whether to delete the listening data too. */
    val deleteDataOnWithdrawal: Boolean = false,
    /** The "delete my listening data" confirmation is open. */
    val isConfirmingDeletion: Boolean = false,
    val isDeleting: Boolean = false,
    /** A notice the screen shows once, then reports with [SettingsAction.NoticeShown]. */
    val notice: SettingsNotice? = null,
)

enum class SettingsNotice {
    DATA_DELETED,
    CONSENT_WITHDRAWN,
    CONSENT_WITHDRAWN_AND_DATA_DELETED,
}

/** User intents the settings screen understands. */
sealed interface SettingsAction {
    data class StandardSelected(
        val standard: DoseStandard,
    ) : SettingsAction

    data class AlertToggled(
        val threshold: DoseThreshold,
        val enabled: Boolean,
    ) : SettingsAction

    data class AllAlertsMuted(
        val muted: Boolean,
    ) : SettingsAction

    data class MicrophoneAdjustmentChanged(
        val adjustmentDb: Float,
    ) : SettingsAction

    data object MicrophoneAdjustmentReset : SettingsAction

    data class MediaAppSelected(
        val app: String,
    ) : SettingsAction

    data object RefreshRunningApps : SettingsAction

    data object WithdrawConsentClicked : SettingsAction

    data class DeleteDataOnWithdrawalChanged(
        val delete: Boolean,
    ) : SettingsAction

    data object WithdrawConsentConfirmed : SettingsAction

    data object WithdrawConsentDismissed : SettingsAction

    data object DeleteDataClicked : SettingsAction

    data object DeleteDataConfirmed : SettingsAction

    data object DeleteDataDismissed : SettingsAction

    data object NoticeShown : SettingsAction
}
