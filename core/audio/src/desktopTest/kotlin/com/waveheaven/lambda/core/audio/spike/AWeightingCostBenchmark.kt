package com.waveheaven.lambda.core.audio.spike

import com.waveheaven.lambda.core.audio.LevelMeter
import com.waveheaven.lambda.core.audio.MicrophoneCalibration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test

/*
 * Throw-away benchmark for spike #3: cost and agreement of the two A-weighting options.
 *   - IIR: the production LevelMeter (3 cascaded biquads, sample by sample).
 *   - FFT: Welch power spectrum (4096-point Hann frames, 50 % overlap) weighted per bin with the IEC formula.
 *
 *   ./gradlew :core:audio:desktopTest --tests '*AWeightingCostBenchmark*' -i
 */
class AWeightingCostBenchmark {
    private val rate = 48_000
    private val seconds = 60

    @Test
    fun `compare IIR and FFT A-weighting`() {
        val signals =
            mapOf(
                "pink noise" to pinkNoise(rate * seconds),
                "1 kHz tone" to tone(1000.0),
                "100 Hz tone" to tone(100.0),
                "6 kHz tone" to tone(6000.0),
            )
        repeat(3) { warmUp -> signals.forEach { (name, samples) -> run(name, samples, report = warmUp == 2) } }
    }

    private fun run(
        name: String,
        samples: FloatArray,
        report: Boolean,
    ) {
        val iirLevels = mutableListOf<Double>()
        val iirNanos =
            timed {
                val meter = LevelMeter(rate, MicrophoneCalibration(0.0))
                // 100 ms reads, as AndroidAmbientMeter does.
                for (offset in samples.indices step CHUNK) {
                    meter.process(samples.copyOfRange(offset, offset + CHUNK)) { iirLevels += it.decibels }
                }
            }
        val fftLevels = mutableListOf<Double>()
        val fftNanos = timed { for (s in 0 until seconds) fftLevels += fftLevel(samples, s * rate, rate) }
        if (!report) return
        val iirNsPerSample = iirNanos.toDouble() / samples.size
        val fftNsPerSample = fftNanos.toDouble() / samples.size
        val diff = iirLevels.drop(1).zip(fftLevels.drop(1)).maxOf { (a, b) -> kotlin.math.abs(a - b) }
        println(
            "%-12s IIR %.1f ns/sample (%.2f ms CPU per s of audio) | FFT %.1f ns/sample (%.2f ms) | max |IIR-FFT| %.2f dB | level %.2f dB"
                .format(
                    name,
                    iirNsPerSample,
                    iirNsPerSample * rate / 1e6,
                    fftNsPerSample,
                    fftNsPerSample * rate / 1e6,
                    diff,
                    iirLevels.last(),
                ),
        )
    }

    /** Mean-square A-weighted level of one 1 s window, from a Welch spectrum. */
    private fun fftLevel(
        samples: FloatArray,
        start: Int,
        rate: Int,
    ): Double {
        val n = FRAME
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        val windowPower = window.sumOf { it * it }
        val weights = DoubleArray(n / 2 + 1) { k -> aWeightingGain(k.toDouble() * rate / n).pow(2) }
        var sum = 0.0
        var frames = 0
        var offset = start
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        while (offset + n <= start + rate) {
            for (i in 0 until n) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            var frame = 0.0
            for (k in 0..n / 2) {
                val power = re[k] * re[k] + im[k] * im[k]
                frame += weights[k] * power * if (k == 0 || k == n / 2) 1.0 else 2.0
            }
            sum += frame / (n * windowPower)
            frames++
            offset += n / 2
        }
        return 10 * log10(sum / frames)
    }

    private fun aWeightingGain(f: Double): Double {
        if (f == 0.0) return 0.0
        val f2 = f * f
        val ra = 12194.0.pow(2) * f2 * f2 / ((f2 + 20.6.pow(2)) * sqrt((f2 + 107.7.pow(2)) * (f2 + 737.9.pow(2))) * (f2 + 12194.0.pow(2)))
        return ra * 10.0.pow(2.0 / 20)
    }

    private val twiddleRe = DoubleArray(FRAME / 2) { cos(-2 * PI * it / FRAME) }
    private val twiddleIm = DoubleArray(FRAME / 2) { sin(-2 * PI * it / FRAME) }

    /** In-place iterative radix-2 FFT with precomputed twiddle factors. */
    private fun fft(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val stride = n / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = twiddleRe[k * stride]
                    val wi = twiddleIm[k * stride]
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                    val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                }
            }
            len = len shl 1
        }
    }

    private fun tone(frequency: Double) = FloatArray(rate * seconds) { n -> (0.1 * sin(2 * PI * frequency * n / rate)).toFloat() }

    /** Paul Kellet's pink-noise filter over white noise. */
    private fun pinkNoise(size: Int): FloatArray {
        val random = Random(42)
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        return FloatArray(size) {
            val white = random.nextDouble(-1.0, 1.0)
            b0 = 0.99765 * b0 + white * 0.0990460
            b1 = 0.96300 * b1 + white * 0.2965164
            b2 = 0.57000 * b2 + white * 1.0526913
            (0.02 * (b0 + b1 + b2 + white * 0.1848)).toFloat()
        }
    }

    private fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private companion object {
        const val FRAME = 4096
        const val CHUNK = 4_800
    }
}
