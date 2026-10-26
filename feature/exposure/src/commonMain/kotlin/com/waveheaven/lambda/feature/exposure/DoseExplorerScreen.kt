package com.waveheaven.lambda.feature.exposure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waveheaven.lambda.core.designsystem.format.formatDuration
import com.waveheaven.lambda.core.designsystem.icon.WhIcons
import com.waveheaven.lambda.feature.exposure.resources.Res
import com.waveheaven.lambda.feature.exposure.resources.app_name
import com.waveheaven.lambda.feature.exposure.resources.dose_explorer_level
import com.waveheaven.lambda.feature.exposure.resources.dose_explorer_niosh
import com.waveheaven.lambda.feature.exposure.resources.dose_explorer_subtitle
import com.waveheaven.lambda.feature.exposure.resources.dose_explorer_who
import com.waveheaven.lambda.feature.exposure.resources.medical_disclaimer
import com.waveheaven.lambda.feature.exposure.resources.open_about
import com.waveheaven.lambda.feature.exposure.resources.open_settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/** Stateful entry point: connects [DoseExplorerViewModel] to the stateless [DoseExplorerScreen]. */
@Composable
fun DoseExplorerRoute(
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoseExplorerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DoseExplorerScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenAbout = onOpenAbout,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

/**
 * Shows how long a steady listening level is safe according to NIOSH (daily) and WHO (weekly).
 * Stateless: renders [state] and reports user intents through [onAction], so it can be previewed and tested alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoseExplorerScreen(
    state: DoseExplorerUiState,
    onAction: (DoseExplorerAction) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val levelLabel = stringResource(Res.string.dose_explorer_level, state.level.decibels.roundToInt())

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(WhIcons.Settings, contentDescription = stringResource(Res.string.open_settings))
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(WhIcons.Info, contentDescription = stringResource(Res.string.open_about))
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
            Text(stringResource(Res.string.dose_explorer_subtitle), style = MaterialTheme.typography.bodyLarge)

            Text(levelLabel, style = MaterialTheme.typography.titleLarge)
            Slider(
                value = state.sliderValue,
                onValueChange = { onAction(DoseExplorerAction.LevelChanged(it)) },
                valueRange = DoseExplorerUiState.LEVEL_RANGE,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = levelLabel },
            )

            SafeTimeCard(
                label = stringResource(Res.string.dose_explorer_niosh),
                value = formatDuration(state.nioshDailySafeTime),
            )
            SafeTimeCard(
                label = stringResource(Res.string.dose_explorer_who),
                value = formatDuration(state.whoWeeklySafeTime),
            )

            Text(stringResource(Res.string.medical_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SafeTimeCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
