package com.waveheaven.lambda.shared.exposure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.dBA
import com.waveheaven.lambda.shared.resources.Res
import com.waveheaven.lambda.shared.resources.app_name
import com.waveheaven.lambda.shared.resources.dose_explorer_level
import com.waveheaven.lambda.shared.resources.dose_explorer_niosh
import com.waveheaven.lambda.shared.resources.dose_explorer_subtitle
import com.waveheaven.lambda.shared.resources.dose_explorer_who
import com.waveheaven.lambda.shared.resources.medical_disclaimer
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val MIN_LEVEL_DBA = 60f
private const val MAX_LEVEL_DBA = 110f
private const val DEFAULT_LEVEL_DBA = 85f

/**
 * Walking-skeleton screen: shows how long a steady listening level is safe according to
 * NIOSH (daily) and WHO (weekly). It exercises the shared domain model end to end.
 */
@Composable
fun DoseExplorerScreen(modifier: Modifier = Modifier) {
    var levelDba by rememberSaveable { mutableFloatStateOf(DEFAULT_LEVEL_DBA) }
    val level = levelDba.roundToInt().dBA
    val levelLabel = stringResource(Res.string.dose_explorer_level, level.decibels.roundToInt())

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(Res.string.dose_explorer_subtitle), style = MaterialTheme.typography.bodyLarge)

        Text(levelLabel, style = MaterialTheme.typography.titleLarge)
        Slider(
            value = levelDba,
            onValueChange = { levelDba = it },
            valueRange = MIN_LEVEL_DBA..MAX_LEVEL_DBA,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = levelLabel },
        )

        SafeTimeCard(
            label = stringResource(Res.string.dose_explorer_niosh),
            value = formatDuration(DoseStandard.NIOSH.allowedDuration(level)),
        )
        SafeTimeCard(
            label = stringResource(Res.string.dose_explorer_who),
            value = formatDuration(DoseStandard.WHO_ADULT.allowedDuration(level)),
        )

        Text(stringResource(Res.string.medical_disclaimer), style = MaterialTheme.typography.bodySmall)
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
