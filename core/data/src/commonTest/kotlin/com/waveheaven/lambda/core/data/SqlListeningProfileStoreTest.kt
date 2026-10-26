package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.domain.profile.BuiltInProfileException
import com.waveheaven.lambda.core.domain.profile.EqCurve
import com.waveheaven.lambda.core.domain.profile.EqPoint
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SqlListeningProfileStoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = testDriver()
    private var now = Instant.parse("2026-10-26T08:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private val store = SqlListeningProfileStore(createDatabase(driver), dispatcher, clock)
    private val study = ListeningProfile("study", "Study", isBuiltIn = false, targetSnrDb = 4.0, minVolume = 0.05, maxVolume = 0.4)

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun `starts with the built-in profiles and commute selected`() =
        test {
            assertEquals(ListeningProfile.BUILT_IN, store.profiles().first())
            assertEquals(ListeningProfile.COMMUTE, store.activeProfile().first())
        }

    @Test
    fun `user profiles round-trip, EQ included, after the built-in ones`() =
        test {
            val withEq = study.copy(eq = EqCurve(listOf(EqPoint(250.0, -2.0), EqPoint(4000.0, 3.5))))

            store.save(withEq)

            assertEquals(ListeningProfile.BUILT_IN + withEq, store.profiles().first())
        }

    @Test
    fun `editing keeps the profile's place in the list`() =
        test {
            store.save(study)
            now += 1.minutes
            store.save(study.copy(id = "running", name = "Running"))
            now += 1.minutes

            store.save(study.copy(name = "Library"))

            assertEquals(
                listOf("Library", "Running"),
                store
                    .profiles()
                    .first()
                    .drop(4)
                    .map { it.name },
            )
        }

    @Test
    fun `selecting a profile makes it active`() =
        test {
            store.save(study)

            store.setActive("study")
            assertEquals(study, store.activeProfile().first())

            store.setActive("gym")
            assertEquals(ListeningProfile.GYM, store.activeProfile().first())
        }

    @Test
    fun `deleting the active profile falls back to the default`() =
        test {
            store.save(study)
            store.setActive("study")

            store.delete("study")

            assertEquals(ListeningProfile.DEFAULT, store.activeProfile().first())
            assertEquals(ListeningProfile.BUILT_IN, store.profiles().first())
        }

    @Test
    fun `built-in profiles cannot be changed or deleted, unknown ones not selected`() =
        test {
            assertFailsWith<BuiltInProfileException> { store.save(ListeningProfile.GYM.copy(targetSnrDb = 20.0)) }
            assertFailsWith<BuiltInProfileException> { store.save(study.copy(id = "gym")) }
            assertFailsWith<BuiltInProfileException> { store.delete("commute") }
            assertFailsWith<IllegalArgumentException> { store.setActive("nope") }
        }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }
}
