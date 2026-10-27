package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.core.domain.hearing.HearingResult
import com.waveheaven.lambda.core.domain.hearing.StoredHearingCheck
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SqlHearingCheckStoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = testDriver()
    private val store = SqlHearingCheckStore(createDatabase(driver), dispatcher)
    private val first = Instant.parse("2026-10-20T15:00:00Z")

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun `nothing stored at first`() = runTest(dispatcher) { assertNull(store.latest()) }

    @Test
    fun `keeps every check and returns the latest, not-heard tones included`() =
        runTest(dispatcher) {
            val older = check(first, right = -50.0)
            val newer = check(first + 6.days, right = -45.0, left8k = HearingResult.NOT_HEARD)
            store.save(older)
            store.save(newer)

            assertEquals(newer, store.latest())
        }

    @Test
    fun `deletes every result`() =
        runTest(dispatcher) {
            store.save(check(first, right = -50.0))

            store.deleteAll()

            assertNull(store.latest())
        }

    private fun check(
        at: Instant,
        right: Double,
        left8k: Double? = -40.0,
    ) = StoredHearingCheck(
        at,
        HearingResult(
            mapOf(
                Ear.RIGHT to mapOf(1000 to right, 8000 to -40.0),
                Ear.LEFT to mapOf(1000 to -48.0, 8000 to left8k),
            ),
        ),
    )
}
