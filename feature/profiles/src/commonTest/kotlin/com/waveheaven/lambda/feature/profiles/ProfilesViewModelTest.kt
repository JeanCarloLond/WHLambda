package com.waveheaven.lambda.feature.profiles

import com.waveheaven.lambda.core.domain.profile.BuiltInProfileException
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
class ProfilesViewModelTest {
    private val store = FakeStore()
    private lateinit var viewModel: ProfilesViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ProfilesViewModel(store, newId = { "new-id" })
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lists every profile with the active one`() =
        runTest {
            observe()

            val state = viewModel.state.value
            assertEquals(listOf("commute", "gym", "office", "home"), state.profiles.map { it.id })
            assertEquals("commute", state.activeId)
            assertEquals(
                ProfileItemUi("office", "Office", isBuiltIn = true, targetSnrDb = 6, minPercent = 5, maxPercent = 60),
                state.profiles[2],
            )
        }

    @Test
    fun `selecting a profile activates it`() =
        runTest {
            observe()

            viewModel.onAction(ProfilesAction.Select("gym"))

            assertEquals("gym", viewModel.state.value.activeId)
        }

    @Test
    fun `creating a profile saves and selects it`() =
        runTest {
            observe()

            viewModel.onAction(ProfilesAction.CreateClicked)
            assertFalse(
                viewModel.state.value.editor!!
                    .canSave,
            )
            viewModel.onAction(ProfilesAction.NameChanged("  Study  "))
            viewModel.onAction(ProfilesAction.MarginChanged(4.4f))
            viewModel.onAction(ProfilesAction.VolumeRangeChanged(6f..41f))
            viewModel.onAction(ProfilesAction.SaveClicked)

            val saved = store.saved.single()
            assertEquals(
                ListeningProfile("new-id", "Study", isBuiltIn = false, targetSnrDb = 4.0, minVolume = 0.05, maxVolume = 0.4),
                saved,
            )
            assertEquals("new-id", viewModel.state.value.activeId)
            assertNull(viewModel.state.value.editor)
        }

    @Test
    fun `editing keeps the id and only user profiles can be edited`() =
        runTest {
            store.save(ListeningProfile("study", "Study", false, 4.0, 0.05, 0.4))
            observe()

            viewModel.onAction(ProfilesAction.EditClicked("gym"))
            assertNull(viewModel.state.value.editor)

            viewModel.onAction(ProfilesAction.EditClicked("study"))
            viewModel.onAction(ProfilesAction.NameChanged("Library"))
            viewModel.onAction(ProfilesAction.SaveClicked)

            assertEquals("study", store.saved.last().id)
            assertEquals("Library", store.saved.last().name)
        }

    @Test
    fun `an empty name or an empty range cannot be saved`() =
        runTest {
            observe()
            viewModel.onAction(ProfilesAction.CreateClicked)
            viewModel.onAction(ProfilesAction.NameChanged("Study"))
            viewModel.onAction(ProfilesAction.VolumeRangeChanged(50f..52f)) // snaps to 50–50

            viewModel.onAction(ProfilesAction.SaveClicked)

            assertTrue(store.saved.isEmpty())
            assertFalse(
                viewModel.state.value.editor!!
                    .canSave,
            )
        }

    @Test
    fun `deleting removes a user profile and dismissing closes the editor`() =
        runTest {
            store.save(ListeningProfile("study", "Study", false, 4.0, 0.05, 0.4))
            observe()

            viewModel.onAction(ProfilesAction.DeleteClicked("study"))
            viewModel.onAction(ProfilesAction.CreateClicked)
            viewModel.onAction(ProfilesAction.EditorDismissed)

            assertEquals(4, viewModel.state.value.profiles.size)
            assertNull(viewModel.state.value.editor)
        }

    private fun TestScope.observe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private class FakeStore : ListeningProfileStore {
        private val custom = MutableStateFlow<List<ListeningProfile>>(emptyList())
        private val activeId = MutableStateFlow(ListeningProfile.DEFAULT.id)
        val saved = mutableListOf<ListeningProfile>()

        override fun profiles(): Flow<List<ListeningProfile>> = custom.map { ListeningProfile.BUILT_IN + it }

        override fun activeProfile(): Flow<ListeningProfile> =
            combine(profiles(), activeId) { all, id -> all.firstOrNull { it.id == id } ?: ListeningProfile.DEFAULT }

        override suspend fun save(profile: ListeningProfile) {
            if (profile.isBuiltIn) throw BuiltInProfileException(profile.id)
            saved += profile
            custom.value = custom.value.filterNot { it.id == profile.id } + profile
        }

        override suspend fun delete(id: String) {
            custom.value = custom.value.filterNot { it.id == id }
        }

        override suspend fun setActive(id: String) {
            activeId.value = id
        }
    }
}
