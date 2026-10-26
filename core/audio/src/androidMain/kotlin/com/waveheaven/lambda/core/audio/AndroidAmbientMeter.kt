package com.waveheaven.lambda.core.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
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
 * Uses the `UNPROCESSED` source when the device supports it, otherwise `VOICE_RECOGNITION` (minimal processing, no
 * AGC), each with its own nominal calibration (spike #3, C3), at 48 kHz mono float. Capture is pinned to the built-in
 * microphone, so Bluetooth headphones are never switched to their low-quality call profile (spike #2, finding 4).
 * Samples live only in a reusable buffer and are reduced to levels by [LevelMeter] (ADR-0007). To keep metering with
 * the screen off, collect from a `microphone` foreground service.
 *
 * @param adjustmentDb the user's correction to the nominal calibration (WH-004), read whenever metering starts.
 */
class AndroidAmbientMeter(
    private val context: Context,
    private val adjustmentDb: suspend () -> Double = { 0.0 },
) : AmbientMeter {
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    override fun levels(): Flow<SoundLevel> =
        flow {
            val unprocessed = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
            val record = openRecord(source)
            try {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw MicrophoneUnavailableException("The microphone refused to start recording")
                }
                val nominal =
                    if (unprocessed) {
                        MicrophoneCalibration.ANDROID_UNPROCESSED_NOMINAL
                    } else {
                        MicrophoneCalibration.ANDROID_VOICE_RECOGNITION_NOMINAL
                    }
                val meter = LevelMeter(SAMPLE_RATE, MicrophoneCalibration(nominal.offsetDb + adjustmentDb()))
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
    private fun openRecord(source: Int): AudioRecord {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw MicrophoneUnavailableException("RECORD_AUDIO permission not granted")
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val record =
            AudioRecord(
                source,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                maxOf(minBuffer, CHUNK_SAMPLES * Float.SIZE_BYTES * BUFFER_CHUNKS),
            )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw MicrophoneUnavailableException("AudioRecord could not be initialised")
        }
        audio
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?.let(record::setPreferredDevice)
        return record
    }

    private companion object {
        const val SAMPLE_RATE = 48_000

        /** 100 ms per read keeps cancellation responsive. */
        const val CHUNK_SAMPLES = 4_800
        const val BUFFER_CHUNKS = 4
    }
}
