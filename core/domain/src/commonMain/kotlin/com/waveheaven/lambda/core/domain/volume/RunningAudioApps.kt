package com.waveheaven.lambda.core.domain.volume

/** Port: on desktop, the applications playing audio right now, for the user to pick their music app (#17). */
public fun interface RunningAudioApps {
    public fun list(): List<String>
}
