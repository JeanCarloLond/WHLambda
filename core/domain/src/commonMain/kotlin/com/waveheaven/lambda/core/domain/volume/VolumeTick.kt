package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.Dose
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlin.jvm.JvmInline
import kotlin.time.Duration

/**
 * An audio output, e.g. `speaker` or a Bluetooth headset address. Android stores a separate volume per
 * output, so the controller keeps separate state per route (spike #2, D4).
 */
@JvmInline
public value class OutputRoute(
    public val id: String,
)

/** One observation of the world, delivered about once per second. */
public data class VolumeTick(
    /** Monotonic time since an arbitrary origin; only differences matter. */
    val now: Duration,
    val route: OutputRoute,
    /** Calibrated ambient level for the last second. */
    val ambient: SoundLevel,
    /** Volume index currently set by the system for [route]. */
    val currentIndex: Int,
    val isMusicActive: Boolean,
    /** Dose already consumed in the current window of the configured standard. */
    val doseConsumed: Dose,
)
