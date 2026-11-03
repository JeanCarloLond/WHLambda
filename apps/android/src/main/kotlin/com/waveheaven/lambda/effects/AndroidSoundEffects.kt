package com.waveheaven.lambda.effects

import android.content.Context
import com.waveheaven.lambda.core.audio.AndroidEffectsSupport
import com.waveheaven.lambda.core.audio.AndroidPlaybackSessions
import com.waveheaven.lambda.core.audio.SoundEffectsEngine
import com.waveheaven.lambda.core.domain.effects.SoundEffects
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * [SoundEffects] on Android: [SoundEffectsService] runs the [engine]; this class starts and stops it and adds what
 * the phone allows to the engine's status.
 */
class AndroidSoundEffects(
    private val context: Context,
    engine: SoundEffectsEngine,
    private val sessions: AndroidPlaybackSessions,
    scope: CoroutineScope,
) : SoundEffects {
    private val fullDetection = MutableStateFlow(sessions.fullDetection())

    override val isSupported: Boolean = AndroidEffectsSupport.isSupported

    override val detectionCommand: String = "adb shell pm grant ${context.packageName} android.permission.DUMP"

    override val status: StateFlow<SoundEffectsStatus> =
        combine(engine.status, fullDetection) { running, full ->
            running.copy(fullDetection = full, surroundSupported = AndroidEffectsSupport.surroundSupported)
        }.stateIn(scope, SharingStarted.Eagerly, SoundEffectsStatus())

    override fun start() {
        if (isSupported) context.startForegroundService(SoundEffectsService.intent(context, SoundEffectsService.ACTION_START))
    }

    override fun stop() {
        context.stopService(SoundEffectsService.intent(context, SoundEffectsService.ACTION_START))
    }

    override fun refresh() {
        fullDetection.value = sessions.fullDetection()
    }
}
