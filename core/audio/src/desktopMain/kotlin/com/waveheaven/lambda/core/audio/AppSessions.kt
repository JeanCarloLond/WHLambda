package com.waveheaven.lambda.core.audio

/** One application's playback stream as the operating system mixes it. */
internal data class AppStream(
    val id: String,
    /** Human-readable application name, e.g. `Spotify` or `Firefox`. */
    val app: String,
    /** Stream volume as linear amplitude, 0.0 to 1.0. */
    val linearVolume: Double,
    val isPlaying: Boolean,
)

/**
 * The operating system's per-application mixer (ADR-0009): PulseAudio or PipeWire on Linux, WASAPI sessions on
 * Windows. Calls are synchronous and cheap (a few milliseconds); call them from a background thread.
 */
internal interface AppSessions {
    fun streams(): List<AppStream>

    fun setVolume(
        streamId: String,
        linear: Double,
    )

    /** Identifies the output device in use, so the controller keeps state per device. */
    fun outputId(): String

    /** The output device's own volume in decibels (0 dB at full volume). */
    fun outputGainDb(): Double

    companion object {
        fun forThisOs(): AppSessions =
            if (System.getProperty("os.name").lowercase().startsWith("win")) WasapiSessions() else PulseSessions()
    }
}
