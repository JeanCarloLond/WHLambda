package com.waveheaven.lambda.feature.hearingcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.core.domain.hearing.HearingResult.Assessment
import com.waveheaven.lambda.feature.hearingcheck.resources.Res
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_asymmetric
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_delete
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_eq_text
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_even
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_frequency
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_not_heard
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_profile_saved
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_result_title
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_retake
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_save_profile
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_table_frequency
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_table_left
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_table_note
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_table_right
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_threshold
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_uneven
import org.jetbrains.compose.resources.stringResource

/** The thresholds as a table (readable row by row), what they mean, and what to do with them (WH-221, WH-222). */
@Composable
internal fun ResultSection(
    result: ResultUi,
    savedAsProfile: Boolean,
    onAction: (HearingCheckAction) -> Unit,
    showRetake: Boolean = true,
) {
    Text(
        stringResource(Res.string.hearing_result_title, result.date),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        stringResource(
            when (result.assessment) {
                Assessment.EVEN -> Res.string.hearing_even
                Assessment.ASYMMETRIC -> Res.string.hearing_asymmetric
                Assessment.UNEVEN -> Res.string.hearing_uneven
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (result.assessment == Assessment.EVEN) FontWeight.Normal else FontWeight.Bold,
    )
    ThresholdTable(result.rows)
    Text(stringResource(Res.string.hearing_table_note), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(Res.string.hearing_eq_text), style = MaterialTheme.typography.bodyMedium)
    if (savedAsProfile) {
        Text(stringResource(Res.string.hearing_profile_saved), style = MaterialTheme.typography.bodyMedium)
    } else {
        OutlinedButton(
            onClick = { onAction(HearingCheckAction.SaveAsProfile("")) },
        ) { Text(stringResource(Res.string.hearing_save_profile)) }
    }
    if (showRetake) {
        OutlinedButton(onClick = { onAction(HearingCheckAction.Begin) }) { Text(stringResource(Res.string.hearing_retake)) }
    }
    TextButton(
        onClick = { onAction(HearingCheckAction.DeleteResults) },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(stringResource(Res.string.hearing_delete))
    }
}

@Composable
private fun ThresholdTable(
    rows: List<ThresholdRow>,
    modifier: Modifier = Modifier,
) {
    val frequency = stringResource(Res.string.hearing_table_frequency)
    val right = stringResource(Res.string.hearing_table_right)
    val left = stringResource(Res.string.hearing_table_left)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.clearAndSetSemantics { }) {
                Cell(frequency, bold = true)
                Cell(right, bold = true)
                Cell(left, bold = true)
            }
            rows.forEach { row ->
                val hz = stringResource(Res.string.hearing_frequency, row.frequencyHz.toString())
                val r = threshold(row.right)
                val l = threshold(row.left)
                Row(modifier = Modifier.clearAndSetSemantics { contentDescription = "$hz: $right $r, $left $l" }) {
                    Cell(hz)
                    Cell(r)
                    Cell(l)
                }
            }
        }
    }
}

@Composable
private fun threshold(value: Int?): String =
    value?.let { stringResource(Res.string.hearing_threshold, it) } ?: stringResource(Res.string.hearing_not_heard)

@Composable
private fun RowScope.Cell(
    text: String,
    bold: Boolean = false,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.weight(1f),
    )
}
