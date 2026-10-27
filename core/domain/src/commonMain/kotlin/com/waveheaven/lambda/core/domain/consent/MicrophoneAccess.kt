package com.waveheaven.lambda.core.domain.consent

/**
 * Port: the operating system's permission to use the microphone. On Android this is the `RECORD_AUDIO` runtime
 * permission; on desktop there is no prompt.
 */
public interface MicrophoneAccess {
    public fun isGranted(): Boolean

    /** Shows the system prompt (or answers at once when the system will not show it) and reports the result. */
    public fun request(onResult: (granted: Boolean) -> Unit)

    /** Opens the system page where the user can grant access after denying it (WH-074). */
    public fun openSystemSettings()
}
