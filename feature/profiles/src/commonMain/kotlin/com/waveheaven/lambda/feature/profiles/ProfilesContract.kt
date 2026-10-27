package com.waveheaven.lambda.feature.profiles

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation destination for the listening profiles screen. */
@Serializable
data object ProfilesKey : NavKey

/** Everything the profiles screen renders. Immutable; produced only by [ProfilesViewModel]. */
data class ProfilesUiState(
    val profiles: List<ProfileItemUi> = emptyList(),
    val activeId: String? = null,
    /** The create/edit dialog, when open. */
    val editor: ProfileEditorUi? = null,
)

data class ProfileItemUi(
    val id: String,
    /** The user's name for their own profiles; built-in ones are named by the screen in the user's language. */
    val name: String,
    val isBuiltIn: Boolean,
    val targetSnrDb: Int,
    val minPercent: Int,
    val maxPercent: Int,
)

/** The profile being created ([id] `null`) or edited. Volumes are percentages of the range, in 5 % steps. */
data class ProfileEditorUi(
    val id: String?,
    val name: String,
    val targetSnrDb: Float,
    val minPercent: Float,
    val maxPercent: Float,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && maxPercent > minPercent
}

/** User intents the profiles screen understands. */
sealed interface ProfilesAction {
    data class Select(
        val id: String,
    ) : ProfilesAction

    data object CreateClicked : ProfilesAction

    data class EditClicked(
        val id: String,
    ) : ProfilesAction

    data class DeleteClicked(
        val id: String,
    ) : ProfilesAction

    data class NameChanged(
        val name: String,
    ) : ProfilesAction

    data class MarginChanged(
        val targetSnrDb: Float,
    ) : ProfilesAction

    data class VolumeRangeChanged(
        val percent: ClosedFloatingPointRange<Float>,
    ) : ProfilesAction

    data object SaveClicked : ProfilesAction

    data object EditorDismissed : ProfilesAction
}
