package io.github.chrisjmendoza.yearal.feature.events.list

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * [EventListViewModel] against [FakeEventRepository] (`docs/ROADMAP.md` M4 T5): list ordering follows
 * the contract's [Event.LIST_ORDER] unchanged, search filters title/notes/location case-insensitively,
 * hidden calendars are joined in (never dropped — this is a management view), and a blank title or
 * calendar name is carried through as-is for the screen to placeholder (`docs/contracts/Events.md` T5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EventListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var formatter: IfcDateFormatter

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        formatter = IfcDateFormatter(ApplicationProvider.getApplicationContext<Context>().resources, Locale.US)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakeEventRepository = FakeEventRepository(),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = EventListViewModel(handle, repository, formatter)

    private fun TestScope.observe(viewModel: EventListViewModel): () -> EventListUiState.Loaded {
        backgroundScope.launch { viewModel.uiState.collect {} }
        return {
            runCurrent()
            viewModel.uiState.value.shouldBeInstanceOf<EventListUiState.Loaded>()
        }
    }

    @Test
    fun `the list follows the repository's own LIST_ORDER, unchanged`() =
        runTest(dispatcher) {
            val repository = FakeEventRepository()
            repository.seed(EventFixtures.all())
            val state = observe(viewModel(repository))

            state().items.map { it.eventId } shouldContainExactly repository.currentEvents.map { it.id }
        }

    @Test
    fun `a blank title and a blank calendar name are carried through, not replaced`() =
        runTest(dispatcher) {
            val repository = FakeEventRepository()
            repository.seed(listOf(EventFixtures.allDay(title = "")))
            val state = observe(viewModel(repository))

            val item = state().items.single()
            item.title shouldBe ""
            item.calendarName shouldBe ""
        }

    @Test
    fun `search matches the title, notes and location case-insensitively`() =
        runTest(dispatcher) {
            val repository = FakeEventRepository()
            repository.seed(
                listOf(
                    Event(
                        uid = "1",
                        title = "Team Standup",
                        timing = EventTiming.AllDay(LocalDate.of(2026, 1, 1)),
                    ),
                    Event(
                        uid = "2",
                        title = "Dentist",
                        description = "Bring the STANDUP notes",
                        timing = EventTiming.AllDay(LocalDate.of(2026, 1, 2)),
                    ),
                    Event(
                        uid = "3",
                        title = "Unrelated",
                        location = "standup room",
                        timing = EventTiming.AllDay(LocalDate.of(2026, 1, 3)),
                    ),
                    Event(
                        uid = "4",
                        title = "Nothing matches",
                        timing = EventTiming.AllDay(LocalDate.of(2026, 1, 4)),
                    ),
                ),
            )
            val viewModel = viewModel(repository)
            val state = observe(viewModel)

            viewModel.setQuery("standup")
            state().items shouldHaveSize 3
            state().items.map { it.title } shouldContainExactly listOf("Team Standup", "Dentist", "Unrelated")

            viewModel.setQuery("")
            state().items shouldHaveSize 4
        }

    @Test
    fun `events of a hidden calendar are joined in and flagged, not dropped`() =
        runTest(dispatcher) {
            val repository = FakeEventRepository()
            val hiddenId = repository.upsertCalendar(EventCalendar(name = "Hidden", visible = false))
            repository.seed(listOf(EventFixtures.allDay(calendarId = hiddenId)))
            val state = observe(viewModel(repository))

            val item = state().items.single()
            item.calendarHidden shouldBe true
        }

    @Test
    fun `the colour falls back from the event to its calendar to the default`() =
        runTest(dispatcher) {
            val repository = FakeEventRepository()
            val customId = repository.upsertCalendar(EventCalendar(name = "Work", colorArgb = 0xFF112233.toInt()))
            repository.seed(
                listOf(
                    EventFixtures.allDay(calendarId = customId, title = "Custom colour"),
                    EventFixtures.allDay(title = "Default colour"),
                ),
            )
            val state = observe(viewModel(repository))

            val items = state().items
            items.first { it.eventId == 1L }.calendarColorArgb shouldBe 0xFF112233.toInt()
            items.first { it.eventId == 2L }.calendarColorArgb shouldBe EventCalendar.DEFAULT_COLOR_ARGB
        }

    @Test
    fun `search text survives process death`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val repository = FakeEventRepository()
            repository.seed(listOf(EventFixtures.allDay(title = "Keep me")))
            viewModel(repository, handle).setQuery("keep")

            val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val state = observe(viewModel(repository, restored))

            state().query shouldBe "keep"
            state().items shouldHaveSize 1
        }
}
