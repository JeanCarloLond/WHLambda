package com.waveheaven.lambda.feature.soundeffects

import com.waveheaven.lambda.core.domain.effects.EffectsMode
import com.waveheaven.lambda.core.domain.effects.EqPreset
import com.waveheaven.lambda.core.domain.effects.FakeSoundEffects
import com.waveheaven.lambda.core.domain.effects.InMemorySoundEffectsStore
import com.waveheaven.lambda.core.domain.effects.ReverbPreset
import com.waveheaven.lambda.core.domain.effects.SoundEffectsSettings
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStatus
import com.waveheaven.lambda.core.domain.profile.EqCurve
import com.waveheaven.lambda.core.domain.profile.EqPoint
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// setMain is still experimental, but it is the supported way to give viewModelScope a test dispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class SoundEffectsViewModelTest {
    private val store = InMemorySoundEffectsStore()
    private val profiles = FakeProfiles()
    private val effects = FakeSoundEffects()
    private lateinit var viewModel: SoundEffectsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SoundEffectsViewModel(store, profiles, effects)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts from the stored settings with everything off`() =
        runTest {
            observe()

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertFalse(state.enabled)
            assertEquals(EqPreset.FLAT, state.preset)
            assertEquals(List(10) { 0 }, state.bandGainsDb)
            assertFalse(state.raisesLevel)
            assertEquals(effects.detectionCommand, state.detectionCommand)
        }

    @Test
    fun `turning on saves first and then starts the effects, turning off stops them`() =
        runTest {
            observe()

            viewModel.onAction(SoundEffectsAction.EnabledChanged(true))
            assertTrue(store.settings.first().enabled)
            assertEquals(listOf("start"), effects.calls)
            assertTrue(viewModel.state.value.running)

            viewModel.onAction(SoundEffectsAction.EnabledChanged(false))
            assertFalse(store.settings.first().enabled)
            assertEquals(listOf("start", "stop"), effects.calls)
        }

    @Test
    fun `a preset shows its curve and moving a band makes it custom`() =
        runTest {
            observe()

            viewModel.onAction(SoundEffectsAction.PresetSelected(EqPreset.ROCK))
            assertEquals(listOf(5, 4, 3, 1, -1, -1, 1, 3, 4, 5), viewModel.state.value.bandGainsDb)

            viewModel.onAction(SoundEffectsAction.BandChanged(band = 5, gainDb = 6.4f))

            val stored = store.settings.first()
            assertEquals(EqPreset.CUSTOM, stored.preset)
            assertEquals(listOf(5.0, 4.0, 3.0, 1.0, -1.0, 6.0, 1.0, 3.0, 4.0, 5.0), stored.customGainsDb)
            assertEquals(EqPreset.CUSTOM, viewModel.state.value.preset)
        }

    @Test
    fun `slider values are rounded and kept in range`() =
        runTest {
            observe()

            viewModel.onAction(SoundEffectsAction.BassBoostChanged(6.6f))
            viewModel.onAction(SoundEffectsAction.ExtraVolumeChanged(40f))
            viewModel.onAction(SoundEffectsAction.SurroundChanged(57f))
            viewModel.onAction(SoundEffectsAction.BandChanged(band = 0, gainDb = 30f))
            viewModel.onAction(SoundEffectsAction.BandChanged(band = 10, gainDb = 3f)) // no such band: ignored

            val stored = store.settings.first()
            assertEquals(7.0, stored.bassBoostDb)
            assertEquals(10.0, stored.extraVolumeDb)
            assertEquals(0.6, stored.surround)
            assertEquals(12.0, stored.customGainsDb[0])
            assertEquals(60, viewModel.state.value.surroundPercent)
        }

    @Test
    fun `boosting shows the warning`() =
        runTest {
            observe()

            viewModel.onAction(SoundEffectsAction.BassBoostChanged(8f))

            assertTrue(viewModel.state.value.raisesLevel)
            assertEquals(8, viewModel.state.value.bassBoostDb)
        }

    @Test
    fun `reverb and mode are saved`() =
        runTest {
            observe()

            viewModel.onAction(SoundEffectsAction.ReverbSelected(ReverbPreset.LARGE_HALL))
            viewModel.onAction(SoundEffectsAction.ModeSelected(EffectsMode.GLOBAL))

            assertEquals(ReverbPreset.LARGE_HALL, store.settings.first().reverb)
            assertEquals(EffectsMode.GLOBAL, viewModel.state.value.mode)
        }

    @Test
    fun `a profile with its own curve is named, and its boost counts for the warning`() =
        runTest {
            observe()
            assertNull(viewModel.state.value.profileWithEq)

            profiles.active.value =
                ListeningProfile("me", "My hearing", isBuiltIn = false, 8.0, 0.1, 0.9, EqCurve(listOf(EqPoint(4_000.0, 10.0))))

            assertEquals("My hearing", viewModel.state.value.profileWithEq)
            assertTrue(viewModel.state.value.raisesLevel)
        }

    @Test
    fun `shows which apps have effects, the whole output and lost control`() =
        runTest {
            observe()

            effects.emit(SoundEffectsStatus(running = true, apps = listOf("Spotify", "VLC"), lostControl = listOf("YouTube Music")))
            assertEquals(listOf("Spotify", "VLC"), viewModel.state.value.apps)
            assertEquals(listOf("YouTube Music"), viewModel.state.value.lostControl)
            assertFalse(viewModel.state.value.wholeOutput)

            effects.emit(
                SoundEffectsStatus(
                    running = true,
                    fullDetection = true,
                    surroundSupported = true,
                    apps = listOf(SoundEffectsStatus.OUTPUT_MIX),
                    lostControl = listOf(SoundEffectsStatus.OUTPUT_MIX),
                ),
            )
            val state = viewModel.state.value
            assertTrue(state.wholeOutput)
            assertTrue(state.lostWholeOutput)
            assertEquals(emptyList(), state.apps)
            assertTrue(state.fullDetection)
            assertTrue(state.surroundSupported)
        }

    @Test
    fun `checking detection asks the platform again`() =
        runTest {
            viewModel.onAction(SoundEffectsAction.CheckDetection)

            assertEquals(listOf("refresh"), effects.calls)
        }

    @Test
    fun `a change made elsewhere, such as the notification turning effects off, shows`() =
        runTest {
            observe()
            viewModel.onAction(SoundEffectsAction.EnabledChanged(true))

            store.save(store.settings.first().copy(enabled = false))

            assertFalse(viewModel.state.value.enabled)
        }

    @Test
    fun `stored custom settings are shown as they are`() =
        runTest {
            store.save(SoundEffectsSettings(preset = EqPreset.CUSTOM, customGainsDb = List(10) { -3.0 }, extraVolumeDb = 2.0))
            observe()

            assertEquals(List(10) { -3 }, viewModel.state.value.bandGainsDb)
            assertEquals(2, viewModel.state.value.extraVolumeDb)
        }

    private fun TestScope.observe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private class FakeProfiles : ListeningProfileStore {
        val active = MutableStateFlow(ListeningProfile.DEFAULT)

        override fun profiles(): Flow<List<ListeningProfile>> = active.map { ListeningProfile.BUILT_IN + it }.map { it.distinct() }

        override fun activeProfile(): Flow<ListeningProfile> = active

        override suspend fun save(profile: ListeningProfile) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun setActive(id: String) = Unit
    }
}
