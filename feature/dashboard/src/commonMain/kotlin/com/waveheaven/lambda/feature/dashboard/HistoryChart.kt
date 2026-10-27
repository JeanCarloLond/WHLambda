package com.waveheaven.lambda.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.feature.dashboard.resources.Res
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_history_day
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_history_title
import com.waveheaven.lambda.feature.dashboard.resources.day_1
import com.waveheaven.lambda.feature.dashboard.resources.day_2
import com.waveheaven.lambda.feature.dashboard.resources.day_3
import com.waveheaven.lambda.feature.dashboard.resources.day_4
import com.waveheaven.lambda.feature.dashboard.resources.day_5
import com.waveheaven.lambda.feature.dashboard.resources.day_6
import com.waveheaven.lambda.feature.dashboard.resources.day_7
import com.waveheaven.lambda.feature.dashboard.resources.day_today
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Seven bars, one per local day, scaled so 100 % sits at two thirds of the height and marked with a line. Each bar
 * is labelled with its day and its percentage in text, and read out as "Mon: 40 %" by screen readers (WH-052).
 */
@Composable
internal fun HistoryChart(
    days: List<HistoryDayUi>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.dashboard_history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Box(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT.dp)) {
                // The 100 % line.
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = ((CHART_HEIGHT - LABEL_SPACE) * LIMIT_FRACTION).dp),
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    days.forEach { day -> Bar(day) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                days.forEach { day ->
                    Text(
                        dayLabel(day),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

@Composable
private fun Bar(
    day: HistoryDayUi,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.dashboard_history_day, dayLabel(day), formatPercent(day.percent))
    val fraction = (day.percent / PERCENT * LIMIT_FRACTION).coerceIn(0f, 1f)
    Column(
        modifier = modifier.fillMaxHeight().clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(formatPercent(day.percent), style = MaterialTheme.typography.labelSmall)
        Box(
            modifier =
                Modifier
                    .width(BAR_WIDTH.dp)
                    .height(((CHART_HEIGHT - LABEL_SPACE) * fraction).dp)
                    .background(
                        color = if (day.percent >= LIMIT_PERCENT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                    ),
        )
    }
}

@Composable
private fun dayLabel(day: HistoryDayUi): String = stringResource(if (day.isToday) Res.string.day_today else dayName(day.isoDayOfWeek))

/** ISO day of the week (1 = Monday) to its short name. */
private fun dayName(isoDayOfWeek: Int): StringResource =
    listOf(
        Res.string.day_1,
        Res.string.day_2,
        Res.string.day_3,
        Res.string.day_4,
        Res.string.day_5,
        Res.string.day_6,
        Res.string.day_7,
    )[isoDayOfWeek - 1]

private const val CHART_HEIGHT = 140
private const val LABEL_SPACE = 20
private const val BAR_WIDTH = 24
private const val PERCENT = 100f
private const val LIMIT_PERCENT = 100

/** Where 100 % sits on the chart, so days above the limit still fit. */
private const val LIMIT_FRACTION = 2f / 3f
