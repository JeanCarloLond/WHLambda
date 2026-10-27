package com.waveheaven.lambda.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/** Lists, selects, creates, edits and deletes listening profiles (WH-060 – WH-062). */
class ProfilesViewModel(
    private val store: ListeningProfileStore,
    private val newId: () -> String = { "custom-" + Random.nextLong().toULong().toString(ID_RADIX) },
) : ViewModel() {
    private val editor = MutableStateFlow<ProfileEditorUi?>(null)
    private var profiles: List<ListeningProfile> = emptyList()

    val state: StateFlow<ProfilesUiState> =
        combine(store.profiles(), store.activeProfile(), editor) { all, active, editing ->
            profiles = all
            ProfilesUiState(profiles = all.map { it.toItem() }, activeId = active.id, editor = editing)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProfilesUiState())

    fun onAction(action: ProfilesAction) {
        when (action) {
            is ProfilesAction.Select -> {
                viewModelScope.launch { store.setActive(action.id) }
            }

            ProfilesAction.CreateClicked -> {
                editor.value = NEW_PROFILE
            }

            is ProfilesAction.EditClicked -> {
                editor.value = profiles.firstOrNull { it.id == action.id && !it.isBuiltIn }?.toEditor()
            }

            is ProfilesAction.DeleteClicked -> {
                viewModelScope.launch { store.delete(action.id) }
            }

            is ProfilesAction.NameChanged -> {
                editor.update { it?.copy(name = action.name.take(MAX_NAME_LENGTH)) }
            }

            is ProfilesAction.MarginChanged -> {
                editor.update { it?.copy(targetSnrDb = action.targetSnrDb.roundToInt().toFloat()) }
            }

            is ProfilesAction.VolumeRangeChanged -> {
                editor.update {
                    it?.copy(minPercent = action.percent.start.snapToStep(), maxPercent = action.percent.endInclusive.snapToStep())
                }
            }

            ProfilesAction.SaveClicked -> {
                save()
            }

            ProfilesAction.EditorDismissed -> {
                editor.value = null
            }
        }
    }

    private fun save() {
        val editing = editor.value?.takeIf { it.canSave } ?: return
        val profile =
            ListeningProfile(
                id = editing.id ?: newId(),
                name = editing.name.trim(),
                isBuiltIn = false,
                targetSnrDb = editing.targetSnrDb.toDouble(),
                minVolume = editing.minPercent / PERCENT,
                maxVolume = editing.maxPercent / PERCENT,
                eq = profiles.firstOrNull { it.id == editing.id }?.eq,
            )
        editor.value = null
        viewModelScope.launch {
            store.save(profile)
            store.setActive(profile.id)
        }
    }

    private fun ListeningProfile.toItem() =
        ProfileItemUi(
            id = id,
            name = name,
            isBuiltIn = isBuiltIn,
            targetSnrDb = targetSnrDb.roundToInt(),
            minPercent = (minVolume * PERCENT).roundToInt(),
            maxPercent = (maxVolume * PERCENT).roundToInt(),
        )

    private fun ListeningProfile.toEditor() =
        ProfileEditorUi(id, name, targetSnrDb.toFloat(), (minVolume * PERCENT).toFloat(), (maxVolume * PERCENT).toFloat())

    private fun Float.snapToStep(): Float = (this / VOLUME_STEP_PERCENT).roundToInt() * VOLUME_STEP_PERCENT

    private companion object {
        const val PERCENT = 100.0
        const val VOLUME_STEP_PERCENT = 5f
        const val MAX_NAME_LENGTH = 30
        const val ID_RADIX = 36
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** A new profile starts from the commute settings. */
        val NEW_PROFILE =
            ProfileEditorUi(
                id = null,
                name = "",
                targetSnrDb = ListeningProfile.COMMUTE.targetSnrDb.toFloat(),
                minPercent = (ListeningProfile.COMMUTE.minVolume * PERCENT).toFloat(),
                maxPercent = (ListeningProfile.COMMUTE.maxVolume * PERCENT).toFloat(),
            )
    }
}
