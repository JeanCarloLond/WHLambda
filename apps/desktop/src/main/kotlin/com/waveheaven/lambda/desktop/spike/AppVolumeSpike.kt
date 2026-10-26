// Spike #4 prototype: throw-away code on a branch that is never merged, so production quality rules are waived.
@file:Suppress("MagicNumber", "TooManyFunctions")

package com.waveheaven.lambda.desktop.spike

import com.waveheaven.lambda.core.audio.LevelMeter
import com.waveheaven.lambda.core.audio.MicrophoneCalibration
import java.lang.management.ManagementFactory
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.log10

/*
 * Throw-away prototype for spike #4: list the audio sessions of other applications, change the volume of one of
 * them, read it back, and meter the microphone through javax.sound.sampled. Not merged.
 *
 *   ./gradlew :apps:desktop:runSpike --args="<app name substring> [microphone seconds]"
 */

data class AppSession(
    val id: String,
    val app: String,
    val processId: Long?,
    val binary: String?,
    /** Linear amplitude gain, 0.0..1.0. */
    val volume: Double,
    val active: Boolean,
)

interface AppSessions {
    val name: String

    fun list(): List<AppSession>

    fun setVolume(
        id: String,
        linear: Double,
    )
}

fun main(args: Array<String>) {
    val target = args.getOrNull(0)
    val micSeconds = args.getOrNull(1)?.toInt() ?: 5
    val sessions: AppSessions =
        if (System.getProperty("os.name").lowercase().contains("win")) WindowsSessions() else PulseSessions()
    println("Backend: ${sessions.name}")

    val listed = timed("list sessions") { sessions.list() }
    listed.forEach { println("  $it") }
    repeat(5) { timed("list sessions again") { sessions.list() } }

    if (target != null) {
        val session = listed.firstOrNull { target.lowercase() in it.app.lowercase() || target.lowercase() in (it.binary ?: "").lowercase() }
        if (session == null) {
            println("No session matches '$target'")
        } else {
            val original = session.volume
            listOf(0.5, 0.25, 0.8, original).forEach { linear ->
                timed("set ${session.app} to $linear") { sessions.setVolume(session.id, linear) }
                val readBack = sessions.list().first { it.id == session.id }.volume
                println("  read back %.4f (%.2f dB)".format(readBack, 20 * log10(readBack)))
            }
        }
    }

    if (micSeconds > 0) meterMicrophone(micSeconds)
}

/** Meters the default capture device for [seconds]; samples stay in a reused buffer and are never stored. */
private fun meterMicrophone(seconds: Int) {
    val rate = 48_000
    val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
    println("Capture mixers: " + AudioSystem.getMixerInfo().joinToString { it.name })
    val line = AudioSystem.getTargetDataLine(format)
    line.open(format, rate / 10 * 2 * 4)
    line.start()
    val meter = LevelMeter(rate, MicrophoneCalibration(offsetDb = 0.0))
    val bytes = ByteArray(rate / 10 * 2)
    val samples = FloatArray(rate / 10)
    val threads = ManagementFactory.getThreadMXBean()
    val cpuStart = threads.currentThreadCpuTime
    val wallStart = System.nanoTime()
    var levels = 0
    while (levels < seconds) {
        val read = line.read(bytes, 0, bytes.size)
        val count = read / 2
        for (i in 0 until count) {
            samples[i] = ((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xff)).toShort() / 32768f
        }
        meter.process(samples, count) {
            levels++
            println("  mic: %.1f dB(A)FS".format(it.decibels))
        }
    }
    val cpuMs = (threads.currentThreadCpuTime - cpuStart) / 1e6
    val wallMs = (System.nanoTime() - wallStart) / 1e6
    println("  capture + A-weighting CPU: %.1f ms over %.0f ms (%.2f %%)".format(cpuMs, wallMs, 100 * cpuMs / wallMs))
    line.stop()
    line.close()
}

private fun <T> timed(
    label: String,
    block: () -> T,
): T {
    val start = System.nanoTime()
    val result = block()
    println("[%.1f ms] %s".format((System.nanoTime() - start) / 1e6, label))
    return result
}
