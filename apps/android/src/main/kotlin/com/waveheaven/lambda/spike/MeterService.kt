// Spike #2 prototype: throw-away code on a branch that is never merged, so production quality rules are waived.
@file:Suppress("MagicNumber", "CyclomaticComplexMethod")

package com.waveheaven.lambda.spike

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * SPIKE #2 — throw-away code, never merged.
 *
 * Foreground service (type microphone) that once per second logs the microphone level (dBFS),
 * whether Android silences our capture, the screen state and the STREAM_MUSIC index.
 * With EXTRA_VOLUME_TEST it also alternates the media volume every 5 s and reads it back.
 *
 * Read results with: adb logcat -s WHSpike
 */
class MeterService : Service() {
    @Volatile private var running = false
    private var worker: Thread? = null
    private val logFile by lazy { java.io.File(filesDir, "spike.log") }

    /** Logs to logcat and appends to files/spike.log so unplugged runs can be read back later. */
    private fun log(message: String) {
        Log.i(TAG, message)
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        logFile.appendText("$stamp $message\n")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val volumeTest = intent?.getBooleanExtra(EXTRA_VOLUME_TEST, false) ?: false
        val volumePeriod = intent?.getIntExtra(EXTRA_VOLUME_PERIOD, DEFAULT_VOLUME_PERIOD_S) ?: DEFAULT_VOLUME_PERIOD_S
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (!running) {
            running = true
            worker = Thread({ meterLoop(volumeTest, volumePeriod) }, "WHSpikeMeter").apply { start() }
        }
        log("service started volumeTest=$volumeTest period=${volumePeriod}s")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.join(WORKER_JOIN_MS)
        log("service destroyed")
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun meterLoop(
        volumeTest: Boolean,
        period: Int,
    ) {
        val audio = getSystemService(AudioManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        val unprocessed = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val record =
            AudioRecord(
                source,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                maxOf(minBuffer, CHUNK * Float.SIZE_BYTES * 4),
            )
        record.startRecording()
        log(
            "recording source=${if (unprocessed) "UNPROCESSED" else "VOICE_RECOGNITION"} " +
                "state=${record.recordingState} device=${record.routedDevice?.productName}/${record.routedDevice?.type} " +
                "maxMusicIndex=${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)} " +
                "minMusicIndex=${audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)} fixedVolume=${audio.isVolumeFixed}",
        )

        val buffer = FloatArray(CHUNK)
        val baseIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        var second = 0
        while (running) {
            val start = SystemClock.elapsedRealtime()
            var sumSquares = 0.0
            var samples = 0
            while (samples < SAMPLE_RATE && running) {
                val read = record.read(buffer, 0, CHUNK, AudioRecord.READ_BLOCKING)
                if (read <= 0) {
                    log("read returned $read")
                    break
                }
                for (i in 0 until read) sumSquares += buffer[i] * buffer[i]
                samples += read
            }
            val rms = if (samples > 0) sqrt(sumSquares / samples) else 0.0
            val dbfs = if (rms > 0) 20 * log10(rms) else Double.NEGATIVE_INFINITY
            val silenced =
                audio.activeRecordingConfigurations
                    .firstOrNull { it.clientAudioSessionId == record.audioSessionId }
                    ?.isClientSilenced
            if (volumeTest && second > 0 && second % period == 0) {
                val target = if ((second / period) % 2 == 1) (baseIndex - 2).coerceAtLeast(0) else baseIndex
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                log("setVolume target=$target readBack=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}")
            }
            log(
                "t=$second dBFS=${"%.1f".format(dbfs)} silenced=$silenced interactive=${power.isInteractive} " +
                    "musicActive=${audio.isMusicActive} musicIndex=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} " +
                    "readMs=${SystemClock.elapsedRealtime() - start}",
            )
            second++
        }
        record.stop()
        record.release()
        if (volumeTest) audio.setStreamVolume(AudioManager.STREAM_MUSIC, baseIndex, 0)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Spike meter", NotificationManager.IMPORTANCE_LOW))
        return Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle("WaveHeaven spike")
            .setContentText("Measuring ambient level")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "WHSpike"
        const val ACTION_STOP = "com.waveheaven.lambda.spike.STOP"
        const val EXTRA_VOLUME_TEST = "volume_test"
        const val EXTRA_VOLUME_PERIOD = "volume_period"
        private const val CHANNEL_ID = "spike"
        private const val NOTIFICATION_ID = 42
        private const val SAMPLE_RATE = 48_000
        private const val CHUNK = 4_800
        private const val DEFAULT_VOLUME_PERIOD_S = 5
        private const val WORKER_JOIN_MS = 2_000L
    }
}
