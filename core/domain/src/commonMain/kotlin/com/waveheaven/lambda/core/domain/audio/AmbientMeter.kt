package com.waveheaven.lambda.core.domain.audio

import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlinx.coroutines.flow.Flow

/**
 * Port: an on-device source of calibrated ambient sound levels.
 *
 * Implementations reduce microphone samples to levels in memory and discard them immediately; audio is never
 * stored or transmitted (ADR-0007).
 */
public interface AmbientMeter {
    /**
     * Cold flow of A-weighted equivalent levels, one per second. Collecting starts the microphone and
     * cancelling the collection releases it.
     *
     * @throws MicrophoneUnavailableException if the microphone cannot be opened (e.g. permission missing).
     */
    public fun levels(): Flow<SoundLevel>
}

/** The microphone could not be opened: permission not granted, or the device refused the capture. */
public class MicrophoneUnavailableException(
    message: String,
) : IllegalStateException(message)
