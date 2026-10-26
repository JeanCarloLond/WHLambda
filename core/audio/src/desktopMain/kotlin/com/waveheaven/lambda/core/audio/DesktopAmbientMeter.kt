package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.audio.AmbientMeter
import com.waveheaven.lambda.core.domain.audio.MicrophoneUnavailableException
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.DataInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

/**
 * [AmbientMeter] on desktop (ADR-0009). Never captures from a Bluetooth headset, which would switch it to its call
 * profile and ruin the music (spike #4, finding 5):
 *
 * - **Linux:** `parec` on the first non-Bluetooth input (the built-in microphone first), streaming float samples over
 *   a pipe that lives only in memory.
 * - **Windows:** javax.sound, on a capture device whose name is not a headset's.
 *
 * Desktop microphones and gains vary too much for a nominal calibration (spike #3, C5), so the level stays approximate
 * until the user calibrates ([adjustmentDb]).
 */
class DesktopAmbientMeter(
    private val adjustmentDb: suspend () -> Double = { 0.0 },
) : AmbientMeter {
    private val isWindows = System.getProperty("os.name").lowercase().startsWith("win")

    override fun levels(): Flow<SoundLevel> =
        flow {
            val meter = LevelMeter(SAMPLE_RATE, MicrophoneCalibration(DESKTOP_NOMINAL_OFFSET_DB + adjustmentDb()))
            val samples = FloatArray(CHUNK_SAMPLES)
            val input = if (isWindows) javaSoundInput() else parecInput()
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(samples)
                    var completed: SoundLevel? = null
                    meter.process(samples, count) { completed = it }
                    completed?.let { emit(it) }
                }
            } finally {
                input.close()
            }
        }.flowOn(Dispatchers.IO)

    /** Reads mono float samples into a buffer; returns how many. */
    private interface SampleInput : AutoCloseable {
        fun read(into: FloatArray): Int
    }

    private fun parecInput(): SampleInput {
        val source =
            runCatching { chooseSource(runPactl(listOf("-f", "json", "list", "sources"))) }.getOrNull()
                ?: throw MicrophoneUnavailableException("No microphone other than a Bluetooth headset")
        val process =
            runCatching {
                ProcessBuilder(
                    "parec",
                    "--device=$source",
                    "--raw",
                    "--format=float32le",
                    "--rate=$SAMPLE_RATE",
                    "--channels=1",
                    "--latency-msec=100",
                ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            }.getOrElse { throw MicrophoneUnavailableException("parec could not start", it) }
        val stream = DataInputStream(process.inputStream.buffered())
        val bytes = ByteArray(CHUNK_SAMPLES * Float.SIZE_BYTES)
        return object : SampleInput {
            override fun read(into: FloatArray): Int {
                try {
                    stream.readFully(bytes)
                } catch (e: EOFException) {
                    throw MicrophoneUnavailableException("The microphone stream ended", e)
                }
                ByteBuffer
                    .wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                    .get(into)
                return into.size
            }

            override fun close() {
                process.destroy()
                stream.close()
            }
        }
    }

    private fun javaSoundInput(): SampleInput {
        val format = AudioFormat(SAMPLE_RATE.toFloat(), BITS, 1, true, false)
        val mixer =
            AudioSystem
                .getMixerInfo()
                .filter { info -> AudioSystem.getMixer(info).targetLineInfo.any { it.lineClass == TargetDataLine::class.java } }
                .firstOrNull { info -> HEADSET_WORDS.none { info.name.contains(it, ignoreCase = true) } }
                ?: throw MicrophoneUnavailableException("No microphone other than a headset")
        val line =
            runCatching {
                AudioSystem.getTargetDataLine(
                    format,
                    mixer,
                )
            }.getOrElse { throw MicrophoneUnavailableException("The capture device could not be opened", it) }
        line.open(format, CHUNK_SAMPLES * 2 * BUFFER_CHUNKS)
        line.start()
        val bytes = ByteArray(CHUNK_SAMPLES * 2)
        return object : SampleInput {
            override fun read(into: FloatArray): Int {
                val read = line.read(bytes, 0, bytes.size) / 2
                for (i in 0 until read) {
                    into[i] = ((bytes[2 * i + 1].toInt() shl BYTE) or (bytes[2 * i].toInt() and LOW_BYTE)).toShort() / FULL_SCALE_16_BIT
                }
                return read
            }

            override fun close() {
                line.stop()
                line.close()
            }
        }
    }

    companion object {
        /**
         * A typical MEMS microphone gives −26 dBFS at 94 dB SPL at unity gain, hence 120 dB. Real desktop inputs add
         * analog boost and OS gain, so this is only a starting point (spike #3, C5).
         */
        const val DESKTOP_NOMINAL_OFFSET_DB = 120.0

        private const val SAMPLE_RATE = 48_000

        /** 100 ms per read keeps cancellation responsive. */
        private const val CHUNK_SAMPLES = 4_800
        private const val BUFFER_CHUNKS = 4
        private const val BITS = 16
        private const val BYTE = 8
        private const val LOW_BYTE = 0xff
        private const val FULL_SCALE_16_BIT = 32768f
        private val HEADSET_WORDS = listOf("Hands-Free", "Headset", "AirPods", "Buds", "Bluetooth")

        /**
         * The input to meter from `pactl -f json list sources`: never a monitor or a Bluetooth device, the built-in
         * microphone first. `null` when there is none.
         */
        fun chooseSource(json: String): String? {
            val candidates =
                Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }.filter { source ->
                    val properties = source["properties"]?.jsonObject
                    val bus = properties?.get("device.bus")?.jsonPrimitive?.contentOrNull
                    val clazz = properties?.get("device.class")?.jsonPrimitive?.contentOrNull
                    val name = source["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    bus != "bluetooth" && clazz != "monitor" && !name.endsWith(".monitor") && !name.startsWith("bluez")
                }
            val internal =
                candidates.firstOrNull {
                    it["properties"]
                        ?.jsonObject
                        ?.get("device.form_factor")
                        ?.jsonPrimitive
                        ?.contentOrNull == "internal"
                }
            return (internal ?: candidates.firstOrNull())?.get("name")?.jsonPrimitive?.contentOrNull
        }
    }
}
