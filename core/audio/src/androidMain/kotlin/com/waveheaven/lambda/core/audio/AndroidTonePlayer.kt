package com.waveheaven.lambda.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.core.domain.hearing.TonePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * [TonePlayer] with a static `AudioTrack` on the media stream, so the tones follow the media volume the user set while
 * listening to the reference tone.
 */
class AndroidTonePlayer : TonePlayer {
    override suspend fun play(
        ear: Ear?,
        frequencyHz: Int,
        levelDb: Double,
        durationMillis: Long,
    ) {
        val samples = ToneSynth.stereo(ear, frequencyHz, TonePlayer.REFERENCE_DBFS + levelDb, durationMillis, SAMPLE_RATE)
        val track =
            withContext(Dispatchers.IO) {
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    ).setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    ).setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
                    .build()
                    .also { it.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) }
            }
        try {
            track.play()
            delay(durationMillis)
        } finally {
            track.stop()
            track.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
