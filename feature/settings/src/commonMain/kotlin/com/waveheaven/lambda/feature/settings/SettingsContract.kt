package com.waveheaven.lambda.feature.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation destination for the settings screen. */
@Serializable
data object SettingsKey : NavKey

/** Everything the settings screen renders. Immutable; produced only by [SettingsViewModel]. */
data class SettingsUiState(
    /** The "delete my listening data" confirmation is open. */
    val isConfirmingDeletion: Boolean = false,
    val isDeleting: Boolean = false,
    /** Deletion finished; the screen shows a confirmation once, then reports [SettingsAction.DeletionNoticeShown]. */
    val showDeletionNotice: Boolean = false,
)

/** User intents the settings screen understands. */
sealed interface SettingsAction {
    data object DeleteDataClicked : SettingsAction

    data object DeleteDataConfirmed : SettingsAction

    data object DeleteDataDismissed : SettingsAction

    data object DeletionNoticeShown : SettingsAction
}
