package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.volume.MediaVolume
import com.waveheaven.lambda.core.domain.volume.OutputRoute
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * [MediaVolume] for one application chosen by the user (ADR-0009, P3), matched by name on every call because apps
 * open and close streams freely (spike #4, finding 2). All of the app's streams are set together.
 *
 * Volume is expressed in fixed steps of [STEP_DB] below full volume (P4): index [MAX_INDEX] is the app at 100 %,
 * index 1 is −46 dB and index 0 is silence. The controller's step logic then works unchanged on every platform.
 *
 * @param targetApp the application to adapt, as listed by [runningApps]; `null` until the user chooses one.
 */
class DesktopMediaVolume internal constructor(
    private val sessions: AppSessions,
    private val targetApp: () -> String?,
) : MediaVolume {
    constructor(targetApp: () -> String?) : this(AppSessions.forThisOs(), targetApp)

    override val indices: IntRange = 0..MAX_INDEX

    override fun currentRoute(): OutputRoute = OutputRoute("desktop:${sessions.outputId()}")

    override fun currentIndex(): Int {
        val loudest = targetStreams().maxOfOrNull { it.linearVolume } ?: return MAX_INDEX
        return indexOf(loudest)
    }

    override fun setIndex(index: Int) {
        val linear = linearOf(index.coerceIn(indices))
        targetStreams().forEach { sessions.setVolume(it.id, linear) }
    }

    override fun isMusicActive(): Boolean = targetStreams().any { it.isPlaying }

    override fun needsSetup(): Boolean = targetApp() == null

    /** Applications with a playback stream right now, for the user to choose from. */
    fun runningApps(): List<String> =
        sessions
            .streams()
            .map { it.app }
            .distinct()
            .sorted()

    private fun targetStreams(): List<AppStream> {
        val target = targetApp() ?: return emptyList()
        return sessions.streams().filter { it.app.equals(target, ignoreCase = true) }
    }

    companion object {
        const val STEP_DB = 2.0
        const val MAX_INDEX = 24
        private const val DECIBELS_PER_DECADE = 20.0
        private const val TEN = 10.0

        /** Linear amplitude of [index]: 0 for silence, `10^(−(MAX − i)·2 dB / 20)` otherwise. */
        fun linearOf(index: Int): Double = if (index <= 0) 0.0 else TEN.pow(-(MAX_INDEX - index) * STEP_DB / DECIBELS_PER_DECADE)

        /** Nearest step to a linear amplitude set by anyone, the user included. */
        fun indexOf(linear: Double): Int {
            if (linear <= linearOf(1) / 2) return 0
            val db = DECIBELS_PER_DECADE * log10(linear.coerceAtMost(1.0))
            return (MAX_INDEX + db / STEP_DB).roundToInt().coerceIn(1, MAX_INDEX)
        }
    }
}
