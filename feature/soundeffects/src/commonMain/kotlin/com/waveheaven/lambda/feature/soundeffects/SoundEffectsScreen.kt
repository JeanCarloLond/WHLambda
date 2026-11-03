package com.waveheaven.lambda.feature.soundeffects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waveheaven.lambda.core.designsystem.icon.WhIcons
import com.waveheaven.lambda.feature.soundeffects.resources.Res
import com.waveheaven.lambda.feature.soundeffects.resources.effects_lost_control
import com.waveheaven.lambda.feature.soundeffects.resources.effects_lost_whole_output
import com.waveheaven.lambda.feature.soundeffects.resources.effects_switch
import com.waveheaven.lambda.feature.soundeffects.resources.effects_switch_description
import com.waveheaven.lambda.feature.soundeffects.resources.effects_title
import com.waveheaven.lambda.feature.soundeffects.resources.effects_warning
import com.waveheaven.lambda.feature.soundeffects.resources.navigate_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: connects [SoundEffectsViewModel] to the stateless [SoundEffectsScreen]. */
@Composable
fun SoundEffectsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoundEffectsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SoundEffectsScreen(state = state, onAction = viewModel::onAction, onBack = onBack, modifier = modifier)
}

/**
 * The equaliser and effects applied to other apps' music (#40): the on switch, the boost warning, the equaliser,
 * the effects, and where they apply. Every value is written out next to its control (WH-103).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundEffectsScreen(
    state: SoundEffectsUiState,
    onAction: (SoundEffectsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.effects_title)) },
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.isLoading) return@Column
            EnabledRow(enabled = state.enabled, onAction = onAction)
            Notices(state)
            EqualiserSection(state = state, onAction = onAction)
            EffectsSection(state = state, onAction = onAction)
            ReachSection(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun EnabledRow(
    enabled: Boolean,
    onAction: (SoundEffectsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = enabled, role = Role.Switch) { onAction(SoundEffectsAction.EnabledChanged(it)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(Res.string.effects_switch), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.effects_switch_description), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/** The boost warning (WH-094) and lost control (WH-095), as cards that cannot be missed. */
@Composable
private fun Notices(state: SoundEffectsUiState) {
    if (state.raisesLevel) Notice(stringResource(Res.string.effects_warning))
    if (state.lostControl.isNotEmpty()) Notice(stringResource(Res.string.effects_lost_control, state.lostControl.joinToString(", ")))
    if (state.lostWholeOutput) Notice(stringResource(Res.string.effects_lost_whole_output))
}

@Composable
private fun Notice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** A section title, announced as a heading. */
@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}
