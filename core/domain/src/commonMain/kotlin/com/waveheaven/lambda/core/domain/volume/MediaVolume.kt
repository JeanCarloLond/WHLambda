package com.waveheaven.lambda.core.domain.volume

/**
 * Port: the system's media volume and playback state (Android `STREAM_MUSIC`, a desktop app's session…).
 *
 * Calls are cheap and synchronous; implementations must be safe to call from a background thread.
 */
public interface MediaVolume {
    /** Valid volume indices, e.g. `0..15`. */
    public val indices: IntRange

    /** Output currently used for media. The volume is stored per output (spike #2, D4). */
    public fun currentRoute(): OutputRoute

    public fun currentIndex(): Int

    public fun setIndex(index: Int)

    /** Whether any app is playing media right now. */
    public fun isMusicActive(): Boolean

    /** Whether the user must set something up first, e.g. choose the music app on desktop (#17). */
    public fun needsSetup(): Boolean = false
}
