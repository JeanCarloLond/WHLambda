package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.core.domain.hearing.TonePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** [TonePlayer] through javax.sound's default output, 16-bit stereo. */
class DesktopTonePlayer : TonePlayer {
    override suspend fun play(
        ear: Ear?,
        frequencyHz: Int,
        levelDb: Double,
        durationMillis: Long,
    ) = withContext(Dispatchers.IO) {
        val samples = ToneSynth.stereo(ear, frequencyHz, TonePlayer.REFERENCE_DBFS + levelDb, durationMillis, SAMPLE_RATE)
        val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { bytes.putShort((it * Short.MAX_VALUE).toInt().toShort()) }
        val format = AudioFormat(SAMPLE_RATE.toFloat(), BITS, CHANNELS, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format)
        try {
            line.start()
            line.write(bytes.array(), 0, bytes.position())
            line.drain()
        } finally {
            line.close()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val BITS = 16
        const val CHANNELS = 2
    }
}
