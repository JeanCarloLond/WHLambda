package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import com.waveheaven.lambda.core.domain.volume.CurveOutputLevelModel
import com.waveheaven.lambda.core.domain.volume.HeadphoneProfile
import com.waveheaven.lambda.core.domain.volume.OutputLevelModel
import com.waveheaven.lambda.core.domain.volume.OutputLevelModels
import com.waveheaven.lambda.core.domain.volume.OutputRoute
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Level at the ear on desktop: the headphone profile's maximum + the output device's volume + the app's step (ADR-0009,
 * P5). The device volume is read live (cached for a second), so turning the system volume down is reflected at once.
 * A computer cannot tell headphones from speakers, so unknown headphones are assumed: conservative (WH-031).
 */
class DesktopOutputLevelModels internal constructor(
    private val sessions: AppSessions,
    private val profile: HeadphoneProfile = HeadphoneProfile.UNKNOWN_HEADPHONES,
) : OutputLevelModels {
    constructor() : this(AppSessions.forThisOs())

    private var deviceGainDb = 0.0
    private var readAt: TimeMark? = null

    override fun modelFor(route: OutputRoute): OutputLevelModel =
        object : OutputLevelModel {
            override val indices: IntRange = 0..DesktopMediaVolume.MAX_INDEX
            override val uncertaintyDb: Double = profile.uncertaintyDb

            override fun levelAt(index: Int): SoundLevel {
                require(index in indices) { "Index $index outside $indices" }
                val appGainDb =
                    if (index ==
                        0
                    ) {
                        CurveOutputLevelModel.MUTE_FLOOR_DB
                    } else {
                        -(DesktopMediaVolume.MAX_INDEX - index) * DesktopMediaVolume.STEP_DB
                    }
                return SoundLevel(profile.maxOutput.decibels + deviceGain() + appGainDb)
            }
        }

    private fun deviceGain(): Double {
        val last = readAt
        if (last == null || last.elapsedNow() > CACHE) {
            deviceGainDb = runCatching { sessions.outputGainDb() }.getOrDefault(0.0).coerceIn(CurveOutputLevelModel.MUTE_FLOOR_DB, 0.0)
            readAt = TimeSource.Monotonic.markNow()
        }
        return deviceGainDb
    }

    private companion object {
        val CACHE = 1.seconds
    }
}
