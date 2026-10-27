package com.waveheaven.lambda.feature.hearingcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.core.domain.hearing.HearingCheck
import com.waveheaven.lambda.core.domain.hearing.HearingCheckStore
import com.waveheaven.lambda.core.domain.hearing.StoredHearingCheck
import com.waveheaven.lambda.core.domain.hearing.TonePlayer
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Runs the hearing check (#19): reference volume, then [HearingCheck]'s tones at random intervals, counting a press
 * of "I hear it" only while a tone plays or shortly after. The result is stored on the device only (WH-222) and can
 * become the EQ curve of a new listening profile.
 */
class HearingCheckViewModel(
    private val store: HearingCheckStore,
    private val player: TonePlayer,
    private val profiles: ListeningProfileStore,
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(HearingCheckUiState())
    val state: StateFlow<HearingCheckUiState> = mutableState.asStateFlow()

    private var test: Job? = null
    private var listening = false
    private var heard = false
    private var lastResult: StoredHearingCheck? = null

    init {
        viewModelScope.launch {
            lastResult = store.latest()
            mutableState.update { it.copy(result = lastResult?.toUi()) }
        }
    }

    fun onAction(action: HearingCheckAction) {
        when (action) {
            HearingCheckAction.Begin -> {
                mutableState.update { it.copy(step = HearingStep.VOLUME) }
            }

            HearingCheckAction.PlayReference -> {
                viewModelScope.launch { player.play(null, REFERENCE_HZ, levelDb = 0.0, durationMillis = REFERENCE_MILLIS) }
            }

            HearingCheckAction.StartTest -> {
                startTest()
            }

            HearingCheckAction.Heard -> {
                if (listening) heard = true
            }

            HearingCheckAction.Cancel -> {
                cancel()
            }

            is HearingCheckAction.SaveAsProfile -> {
                saveAsProfile(action.name)
            }

            HearingCheckAction.DeleteResults -> {
                deleteResults()
            }
        }
    }

    private fun startTest() {
        test?.cancel()
        mutableState.update { it.copy(step = HearingStep.TESTING, progress = 0f, savedAsProfile = false) }
        test =
            viewModelScope.launch {
                val check = HearingCheck()
                while (true) {
                    val tone = check.next() ?: break
                    mutableState.update { it.copy(ear = tone.ear, progress = check.progress.toFloat()) }
                    delay(random.nextLong(MIN_GAP_MILLIS, MAX_GAP_MILLIS))
                    heard = false
                    listening = true
                    player.play(tone.ear, tone.frequencyHz, tone.levelDb, TONE_MILLIS)
                    delay(RESPONSE_MILLIS)
                    listening = false
                    check.answer(heard)
                }
                val stored = StoredHearingCheck(clock.now(), check.result())
                store.save(stored)
                lastResult = stored
                mutableState.update { it.copy(step = HearingStep.RESULT, ear = null, progress = 1f, result = stored.toUi()) }
            }
    }

    private fun cancel() {
        test?.cancel()
        listening = false
        mutableState.update { HearingCheckUiState(result = lastResult?.toUi()) }
    }

    private fun saveAsProfile(name: String) {
        val result = lastResult?.result ?: return
        viewModelScope.launch {
            val base = profiles.activeProfile().first()
            val profile =
                base.copy(
                    id = "hearing-${clock.now().toEpochMilliseconds()}",
                    name = name,
                    isBuiltIn = false,
                    eq = result.compensationCurve(),
                )
            profiles.save(profile)
            profiles.setActive(profile.id)
            mutableState.update { it.copy(savedAsProfile = true) }
        }
    }

    private fun deleteResults() {
        viewModelScope.launch {
            store.deleteAll()
            lastResult = null
            mutableState.update { HearingCheckUiState() }
        }
    }

    private fun StoredHearingCheck.toUi() =
        ResultUi(
            date = takenAt.toLocalDateTime(timeZone()).date.toString(),
            rows =
                HearingCheck.FREQUENCIES_HZ.map { f ->
                    ThresholdRow(
                        frequencyHz = f,
                        right = result.threshold(Ear.RIGHT, f)?.roundToInt(),
                        left = result.threshold(Ear.LEFT, f)?.roundToInt(),
                    )
                },
            assessment = result.assessment(),
        )

    private companion object {
        const val REFERENCE_HZ = 1000
        const val REFERENCE_MILLIS = 2_000L
        const val TONE_MILLIS = 1_000L

        /** After a tone ends, a press still counts for this long. */
        const val RESPONSE_MILLIS = 1_500L

        /** Random silence before each tone, so its timing cannot be guessed. */
        const val MIN_GAP_MILLIS = 800L
        const val MAX_GAP_MILLIS = 2_500L
    }
}
