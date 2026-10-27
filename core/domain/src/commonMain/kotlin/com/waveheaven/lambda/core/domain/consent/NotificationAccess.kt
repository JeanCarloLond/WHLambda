package com.waveheaven.lambda.core.domain.consent

/**
 * Port: the operating system's permission to show notifications (Android 13+ `POST_NOTIFICATIONS`). The adaptive-volume
 * notification and dose alerts need it; without it the loop still runs but the user cannot see or control it from the
 * notification shade.
 */
public interface NotificationAccess {
    public fun isGranted(): Boolean

    /** Shows the system prompt (or answers at once when the system will not show it) and reports the result. */
    public fun request(onResult: (granted: Boolean) -> Unit)
}
