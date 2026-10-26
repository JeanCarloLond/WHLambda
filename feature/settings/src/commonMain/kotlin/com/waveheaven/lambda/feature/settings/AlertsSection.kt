package com.waveheaven.lambda.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import com.waveheaven.lambda.feature.settings.resources.Res
import com.waveheaven.lambda.feature.settings.resources.settings_alerts_description
import com.waveheaven.lambda.feature.settings.resources.settings_alerts_mute
import com.waveheaven.lambda.feature.settings.resources.settings_alerts_threshold
import com.waveheaven.lambda.feature.settings.resources.settings_alerts_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Dose alerts at 50, 80 and 100 % of the weekly budget, each switchable, or all muted (WH-083). */
@Composable
internal fun AlertsSection(
    enabled: Set<DoseThreshold>,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.settings_alerts_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(Res.string.settings_alerts_description), style = MaterialTheme.typography.bodyMedium)
        SwitchRow(
            label = stringResource(Res.string.settings_alerts_mute),
            checked = enabled.isEmpty(),
            onChange = { onAction(SettingsAction.AllAlertsMuted(it)) },
        )
        DoseThreshold.entries.forEach { threshold ->
            SwitchRow(
                label = stringResource(Res.string.settings_alerts_threshold, (threshold.fraction * PERCENT).roundToInt()),
                checked = threshold in enabled,
                onChange = { onAction(SettingsAction.AlertToggled(threshold, it)) },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private const val PERCENT = 100
