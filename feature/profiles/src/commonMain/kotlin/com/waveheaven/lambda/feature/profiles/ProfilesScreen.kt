package com.waveheaven.lambda.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waveheaven.lambda.core.designsystem.icon.WhIcons
import com.waveheaven.lambda.feature.profiles.resources.Res
import com.waveheaven.lambda.feature.profiles.resources.navigate_back
import com.waveheaven.lambda.feature.profiles.resources.profile_commute
import com.waveheaven.lambda.feature.profiles.resources.profile_gym
import com.waveheaven.lambda.feature.profiles.resources.profile_home
import com.waveheaven.lambda.feature.profiles.resources.profile_office
import com.waveheaven.lambda.feature.profiles.resources.profiles_add
import com.waveheaven.lambda.feature.profiles.resources.profiles_cancel
import com.waveheaven.lambda.feature.profiles.resources.profiles_delete
import com.waveheaven.lambda.feature.profiles.resources.profiles_edit
import com.waveheaven.lambda.feature.profiles.resources.profiles_editor_edit
import com.waveheaven.lambda.feature.profiles.resources.profiles_editor_margin
import com.waveheaven.lambda.feature.profiles.resources.profiles_editor_name
import com.waveheaven.lambda.feature.profiles.resources.profiles_editor_new
import com.waveheaven.lambda.feature.profiles.resources.profiles_editor_range
import com.waveheaven.lambda.feature.profiles.resources.profiles_intro
import com.waveheaven.lambda.feature.profiles.resources.profiles_save
import com.waveheaven.lambda.feature.profiles.resources.profiles_summary
import com.waveheaven.lambda.feature.profiles.resources.profiles_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/** Stateful entry point: connects [ProfilesViewModel] to the stateless [ProfilesScreen]. */
@Composable
fun ProfilesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfilesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfilesScreen(state = state, onAction = viewModel::onAction, onBack = onBack, modifier = modifier)
}

/** Lists the listening profiles as a single-choice group, with create, edit and delete for the user's own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    state: ProfilesUiState,
    onAction: (ProfilesAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(WhIcons.ArrowBack, contentDescription = stringResource(Res.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.profiles_intro), style = MaterialTheme.typography.bodyMedium)
            Column(modifier = Modifier.selectableGroup()) {
                state.profiles.forEach { profile ->
                    ProfileRow(profile = profile, isActive = profile.id == state.activeId, onAction = onAction)
                }
            }
            OutlinedButton(onClick = { onAction(ProfilesAction.CreateClicked) }) {
                Icon(WhIcons.Add, contentDescription = null)
                Text(stringResource(Res.string.profiles_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    state.editor?.let { ProfileEditorDialog(editor = it, onAction = onAction) }
}

@Composable
private fun ProfileRow(
    profile: ProfileItemUi,
    isActive: Boolean,
    onAction: (ProfilesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = profileName(profile)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = isActive, role = Role.RadioButton, onClick = { onAction(ProfilesAction.Select(profile.id)) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isActive, onClick = null)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.profiles_summary, profile.targetSnrDb, profile.minPercent, profile.maxPercent),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!profile.isBuiltIn) {
            IconButton(onClick = { onAction(ProfilesAction.EditClicked(profile.id)) }) {
                Icon(WhIcons.Edit, contentDescription = stringResource(Res.string.profiles_edit, name))
            }
            IconButton(onClick = { onAction(ProfilesAction.DeleteClicked(profile.id)) }) {
                Icon(WhIcons.Delete, contentDescription = stringResource(Res.string.profiles_delete, name))
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    editor: ProfileEditorUi,
    onAction: (ProfilesAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(ProfilesAction.EditorDismissed) },
        title = { Text(stringResource(if (editor.id == null) Res.string.profiles_editor_new else Res.string.profiles_editor_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onAction(ProfilesAction.NameChanged(it)) },
                    label = { Text(stringResource(Res.string.profiles_editor_name)) },
                    singleLine = true,
                )
                val marginLabel = stringResource(Res.string.profiles_editor_margin, editor.targetSnrDb.roundToInt())
                Text(marginLabel)
                Slider(
                    value = editor.targetSnrDb,
                    onValueChange = { onAction(ProfilesAction.MarginChanged(it)) },
                    valueRange = 0f..MAX_MARGIN_DB,
                    steps = MAX_MARGIN_DB.toInt() - 1,
                    modifier = Modifier.semantics { contentDescription = marginLabel },
                )
                val rangeLabel =
                    stringResource(Res.string.profiles_editor_range, editor.minPercent.roundToInt(), editor.maxPercent.roundToInt())
                Text(rangeLabel)
                RangeSlider(
                    value = editor.minPercent..editor.maxPercent,
                    onValueChange = { onAction(ProfilesAction.VolumeRangeChanged(it)) },
                    valueRange = 0f..MAX_PERCENT,
                    steps = VOLUME_STEPS,
                    modifier = Modifier.semantics { contentDescription = rangeLabel },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(ProfilesAction.SaveClicked) }, enabled = editor.canSave) {
                Text(stringResource(Res.string.profiles_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ProfilesAction.EditorDismissed) }) {
                Text(stringResource(Res.string.profiles_cancel))
            }
        },
    )
}

/** Built-in profiles are shown in the user's language; the user's own keep the name they typed. */
@Composable
private fun profileName(profile: ProfileItemUi): String =
    when (profile.id.takeIf { profile.isBuiltIn }) {
        "commute" -> stringResource(Res.string.profile_commute)
        "gym" -> stringResource(Res.string.profile_gym)
        "office" -> stringResource(Res.string.profile_office)
        "home" -> stringResource(Res.string.profile_home)
        else -> profile.name
    }

private const val MAX_MARGIN_DB = 20f
private const val MAX_PERCENT = 100f

/** 5 % steps between 0 and 100 %. */
private const val VOLUME_STEPS = 19
