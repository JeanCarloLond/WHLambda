package com.waveheaven.lambda.core.domain.effects

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Port: the user's equaliser and effects, stored on the device. */
public interface SoundEffectsStore {
    /** Defaults ([SoundEffectsSettings] with every effect off) until the user changes something. */
    public val settings: Flow<SoundEffectsSettings>

    public suspend fun save(settings: SoundEffectsSettings)
}

/** [SoundEffectsStore] kept in memory: for tests and previews. */
public class InMemorySoundEffectsStore(
    initial: SoundEffectsSettings = SoundEffectsSettings(),
) : SoundEffectsStore {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<SoundEffectsSettings> = state

    override suspend fun save(settings: SoundEffectsSettings) {
        state.value = settings
    }
}
