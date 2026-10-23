package com.waveheaven.lambda.core.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.waveheaven.lambda.core.domain.audio.AmbientMeter
import com.waveheaven.lambda.core.domain.audio.MicrophoneUnavailableException
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [AmbientMeter] backed by `AudioRecord`.
 *
 * Uses the `VOICE_RECOGNITION` source (minimal processing, no AGC; `UNPROCESSED` is often unsupported, see
 * spike #2) at 48 kHz mono float. Samples live only in a reusable buffer and are reduced to levels by
 * [LevelMeter] (ADR-0007). To keep metering with the screen off, collect from a `microphone` foreground service.
 */
class AndroidAmbientMeter(
    private val context: Context,
    private val calibration: MicrophoneCalibration = MicrophoneCalibration.ANDROID_VOICE_RECOGNITION_NOMINAL,
) : AmbientMeter {
    override fun levels(): Flow<SoundLevel> =
        flow {
            val record = openRecord()
            try {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw MicrophoneUnavailableException("The microphone refused to start recording")
                }
                val meter = LevelMeter(SAMPLE_RATE, calibration)
                val buffer = FloatArray(CHUNK_SAMPLES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (read < 0) throw MicrophoneUnavailableException("AudioRecord.read failed with code $read")
                    var completed: SoundLevel? = null
                    meter.process(buffer, read) { completed = it }
                    completed?.let { emit(it) }
                }
            } finally {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                record.release()
            }
        }.flowOn(Dispatchers.IO)

    // The permission is checked explicitly right before opening the recorder.
    @SuppressLint("MissingPermission")
    private fun openRecord(): AudioRecord {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw MicrophoneUnavailableException("RECORD_AUDIO permission not granted")
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val record =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                maxOf(minBuffer, CHUNK_SAMPLES * Float.SIZE_BYTES * BUFFER_CHUNKS),
            )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw MicrophoneUnavailableException("AudioRecord could not be initialised")
        }
        return record
    }

    private companion object {
        const val SAMPLE_RATE = 48_000

        /** 100 ms per read keeps cancellation responsive. */
        const val CHUNK_SAMPLES = 4_800
        const val BUFFER_CHUNKS = 4
    }
}
