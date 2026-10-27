package com.waveheaven.lambda.core.designsystem.format

import kotlin.time.Duration

/** Compact, locale-neutral rendering such as `7 h 30 min`, `45 min` or `< 1 min`. */
fun formatDuration(duration: Duration): String {
    val totalMinutes = duration.inWholeMinutes
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return when {
        totalMinutes < 1 -> "< 1 min"
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours h"
        else -> "$hours h $minutes min"
    }
}

private const val MINUTES_PER_HOUR = 60L
