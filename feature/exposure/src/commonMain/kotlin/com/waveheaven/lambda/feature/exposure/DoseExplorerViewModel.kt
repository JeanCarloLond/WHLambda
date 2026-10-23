package com.waveheaven.lambda.feature.exposure

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reference implementation of the screen pattern (see `docs/architecture.md`): the ViewModel owns a
 * single [StateFlow] of immutable UI state and changes it only in response to [DoseExplorerAction]s.
 */
class DoseExplorerViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(DoseExplorerUiState.at(DoseExplorerUiState.DEFAULT_LEVEL_DBA))

    val state: StateFlow<DoseExplorerUiState> = mutableState.asStateFlow()

    fun onAction(action: DoseExplorerAction) {
        when (action) {
            is DoseExplorerAction.LevelChanged -> mutableState.value = DoseExplorerUiState.at(action.sliderValue)
        }
    }
}
