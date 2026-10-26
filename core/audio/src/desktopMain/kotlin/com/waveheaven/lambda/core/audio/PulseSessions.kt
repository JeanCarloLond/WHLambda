package com.waveheaven.lambda.core.audio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.math.cbrt
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Linux: PulseAudio, or PipeWire through `pipewire-pulse`, via the `pactl` command and its JSON output (spike #4).
 * Pulse volumes are cubic: the raw value is `cbrt(linear) · 65536`, so they are converted at the edge.
 */
internal class PulseSessions(
    private val pactl: (List<String>) -> String = ::runPactl,
) : AppSessions {
    override fun streams(): List<AppStream> = parseSinkInputs(pactl(listOf("-f", "json", "list", "sink-inputs")))

    override fun setVolume(
        streamId: String,
        linear: Double,
    ) {
        pactl(listOf("set-sink-input-volume", streamId, toRaw(linear).toString()))
    }

    override fun outputId(): String = pactl(listOf("get-default-sink")).trim()

    override fun outputGainDb(): Double {
        val sinks = Json.parseToJsonElement(pactl(listOf("-f", "json", "list", "sinks"))).jsonArray
        val name = outputId()
        val sink = sinks.map { it.jsonObject }.firstOrNull { it.string("name") == name } ?: return 0.0
        return DECIBELS_PER_DECADE * log10(linearOf(sink).coerceAtLeast(MIN_LINEAR))
    }

    companion object {
        private const val NORM = 65536.0

        /** Pulse maps raw volume to amplitude with a cube, for a perceptually even scale. */
        private const val CUBIC = 3
        private const val DECIBELS_PER_DECADE = 20.0
        private const val MIN_LINEAR = 1e-5

        fun toRaw(linear: Double): Long = (cbrt(linear.coerceIn(0.0, 1.0)) * NORM).roundToLong()

        fun fromRaw(raw: Long): Double = (raw / NORM).pow(CUBIC)

        /** Playback streams from `pactl -f json list sink-inputs`. */
        fun parseSinkInputs(json: String): List<AppStream> =
            (Json.parseToJsonElement(json) as JsonArray).map { element ->
                val input = element.jsonObject
                val properties = input["properties"]?.jsonObject ?: JsonObject(emptyMap())
                AppStream(
                    id = input.getValue("index").jsonPrimitive.content,
                    app = properties.string("application.name") ?: properties.string("application.process.binary") ?: "?",
                    linearVolume = linearOf(input),
                    isPlaying = input["corked"]?.jsonPrimitive?.booleanOrNull?.not() ?: true,
                )
            }

        /** Loudest channel of a sink or stream's `volume` object, as linear amplitude. */
        private fun linearOf(node: JsonObject): Double =
            node["volume"]
                ?.jsonObject
                ?.values
                ?.maxOfOrNull {
                    fromRaw(
                        it.jsonObject
                            .getValue("value")
                            .jsonPrimitive.long,
                    )
                }
                ?: 1.0

        private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    }
}

/** Runs `pactl` in the C locale and returns its output; fails on a non-zero exit. */
internal fun runPactl(args: List<String>): String = runTool(listOf("pactl") + args)

internal fun runTool(command: List<String>): String {
    val process =
        ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply { environment()["LC_ALL"] = "C" }
            .start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "${command.joinToString(" ")} failed: $output" }
    return output
}
