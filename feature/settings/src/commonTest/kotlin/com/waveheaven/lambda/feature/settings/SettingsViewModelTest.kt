package com.waveheaven.lambda.feature.settings

import com.waveheaven.lambda.core.domain.exposure.ExposureMinute
import com.waveheaven.lambda.core.domain.exposure.InMemoryExposureLog
import com.waveheaven.lambda.core.domain.exposure.dBA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// setMain is still experimental, but it is the supported way to give viewModelScope a test dispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val log = InMemoryExposureLog()
    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(log)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `deleting asks for confirmation first`() =
        runTest {
            log.record(ExposureMinute(Instant.parse("2026-10-26T08:15:00Z"), 1.minutes, 85.dBA, 6.0, null))

            viewModel.onAction(SettingsAction.DeleteDataClicked)

            assertTrue(viewModel.state.value.isConfirmingDeletion)
            assertEquals(1, log.minutesSince(Instant.DISTANT_PAST).size)
        }

    @Test
    fun `confirming erases every aggregate and shows a notice once`() =
        runTest {
            log.record(ExposureMinute(Instant.parse("2026-10-26T08:15:00Z"), 1.minutes, 85.dBA, 6.0, null))

            viewModel.onAction(SettingsAction.DeleteDataClicked)
            viewModel.onAction(SettingsAction.DeleteDataConfirmed)

            assertTrue(log.minutesSince(Instant.DISTANT_PAST).isEmpty())
            assertEquals(SettingsUiState(showDeletionNotice = true), viewModel.state.value)

            viewModel.onAction(SettingsAction.DeletionNoticeShown)
            assertFalse(viewModel.state.value.showDeletionNotice)
        }

    @Test
    fun `dismissing keeps the data`() =
        runTest {
            log.record(ExposureMinute(Instant.parse("2026-10-26T08:15:00Z"), 1.minutes, 85.dBA, 6.0, null))

            viewModel.onAction(SettingsAction.DeleteDataClicked)
            viewModel.onAction(SettingsAction.DeleteDataDismissed)

            assertFalse(viewModel.state.value.isConfirmingDeletion)
            assertEquals(1, log.minutesSince(Instant.DISTANT_PAST).size)
        }
}
