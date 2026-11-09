package com.waveheaven.lambda.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waveheaven.lambda.core.designsystem.theme.condensedFamily

/** Slider colours without a dot per step: the value is written next to every slider, so the dots only add noise. */
object WhSlider {
    @Composable
    fun colors(): SliderColors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
}

/** How a dose gauge is coloured. The value is always written too, so colour is never the only cue (WH-103). */
enum class GaugeTone { CALM, CAUTION, ALERT }

/**
 * A 270° ring with [label] (for example "42 %") in its centre, filled to [fraction] (0 to 1, capped at a full ring).
 */
@Composable
fun DoseGauge(
    fraction: Float,
    label: String,
    tone: GaugeTone,
    modifier: Modifier = Modifier,
    diameter: Dp = 112.dp,
) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)
    val color =
        when (tone) {
            GaugeTone.CALM -> MaterialTheme.colorScheme.primary
            GaugeTone.CAUTION -> MaterialTheme.colorScheme.tertiary
            GaugeTone.ALERT -> MaterialTheme.colorScheme.error
        }
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = size.minDimension * STROKE_SHARE
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, START_ANGLE, SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            val filled = SWEEP * fraction.coerceIn(0f, 1f)
            // A sliver at 0 % would read as a dot; start showing the fill from 1 %.
            if (filled > MIN_VISIBLE_SWEEP) {
                drawArc(color, START_ANGLE, filled, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            }
        }
        Text(
            label,
            fontFamily = condensedFamily(),
            fontWeight = FontWeight.Bold,
            fontSize = (diameter.value * LABEL_SHARE).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A large reading such as "72 dB(A)" under a small [label], for live values that should be read at a glance. */
@Composable
fun Readout(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontFamily = condensedFamily(), fontWeight = FontWeight.Bold, fontSize = READOUT_SIZE.sp, color = color)
            Text(unit, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val START_ANGLE = 135f
private const val SWEEP = 270f
private const val MIN_VISIBLE_SWEEP = 2.7f
private const val STROKE_SHARE = 0.1f
private const val LABEL_SHARE = 0.24f
private const val READOUT_SIZE = 44
private const val TRACK_ALPHA = 0.12f

/**
 * An equaliser's response drawn from its band gains: a smooth line over a 0 dB guide, filled towards it, with a dot per
 * band. [gainsDb] are evenly spaced bands (one per octave) within ±[rangeDb]. Purely visual; the values are written
 * next to each band's control.
 */
@Composable
fun EqCurve(
    gainsDb: List<Float>,
    rangeDb: Float,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = FILL_ALPHA)
    val guide = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)
    Canvas(modifier = modifier) {
        if (gainsDb.size < 2) return@Canvas
        val pad = size.height * CURVE_PAD
        val usable = size.height - 2 * pad
        val zero = pad + usable / 2

        fun x(i: Int) = size.width * i / (gainsDb.size - 1)

        fun y(g: Float) = zero - (g.coerceIn(-rangeDb, rangeDb) / rangeDb) * (usable / 2)
        drawLine(guide, Offset(0f, zero), Offset(size.width, zero), strokeWidth = GUIDE_WIDTH.dp.toPx())
        // Catmull-Rom through the band points, so the curve reads like a filter response rather than a bar chart.
        val path = Path()
        path.moveTo(x(0), y(gainsDb[0]))
        for (i in 0 until gainsDb.size - 1) {
            val p0 = Offset(x(maxOf(i - 1, 0)), y(gainsDb[maxOf(i - 1, 0)]))
            val p1 = Offset(x(i), y(gainsDb[i]))
            val p2 = Offset(x(i + 1), y(gainsDb[i + 1]))
            val p3 = Offset(x(minOf(i + 2, gainsDb.lastIndex)), y(gainsDb[minOf(i + 2, gainsDb.lastIndex)]))
            path.cubicTo(
                p1.x + (p2.x - p0.x) / SMOOTH,
                p1.y + (p2.y - p0.y) / SMOOTH,
                p2.x - (p3.x - p1.x) / SMOOTH,
                p2.y - (p3.y - p1.y) / SMOOTH,
                p2.x,
                p2.y,
            )
        }
        val area =
            Path().apply {
                addPath(path)
                lineTo(size.width, zero)
                lineTo(0f, zero)
                close()
            }
        drawPath(area, fill)
        drawPath(path, line, style = Stroke(width = CURVE_WIDTH.dp.toPx(), cap = StrokeCap.Round))
        gainsDb.forEachIndexed { i, g -> drawCircle(line, radius = DOT_RADIUS.dp.toPx(), center = Offset(x(i), y(g))) }
    }
}

private const val FILL_ALPHA = 0.14f
private const val CURVE_PAD = 0.12f
private const val SMOOTH = 6f
private const val GUIDE_WIDTH = 1
private const val CURVE_WIDTH = 3
private const val DOT_RADIUS = 3.5f
