package io.github.chrisjmendoza.yearal.feature.events.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRecurrence
import io.github.chrisjmendoza.yearal.core.domain.event.IntercalaryDay
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.github.chrisjmendoza.yearal.core.navigation.EventEditorKey
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.github.chrisjmendoza.yearal.core.testing.FakeDateTicker
import io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository
import io.github.chrisjmendoza.yearal.core.testing.FakeEventUidGenerator
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.feature.events.notification.NotificationPermissionGate
import io.kotest.matchers.collections.shouldBeEmpty
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
import java.time.ZoneId
import java.util.Locale

/**
 * [EventEditorViewModel] against the fakes (`docs/contracts/Events.md` §7), written from ROADMAP M4 T4
 * and the frozen contract: recurrence options offered per date, the Leap Day policy asked only on a
 * real Leap Day, end-before-start rejected, length limits, stale-exdate dropping, the "today" default
 * crossing midnight, prefill validation, process death and fail-soft on a broken save.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EventEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var formatter: IfcDateFormatter
    private val specToday = LocalDate.of(2026, 9, 17)

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
        key: EventEditorKey = EventEditorKey(),
        repository: EventRepository = FakeEventRepository(),
        uidGenerator: FakeEventUidGenerator = FakeEventUidGenerator(),
        zoneProvider: FakeZoneProvider = FakeZoneProvider(ZoneId.of("UTC")),
        ticker: DateTicker = FakeDateTicker(specToday),
        handle: SavedStateHandle = SavedStateHandle(),
        notificationPermission: NotificationPermissionGate = FakeNotificationPermissionGate(needsPermission = false),
    ) = EventEditorViewModel(
        key,
        handle,
        repository,
        uidGenerator,
        zoneProvider,
        ticker,
        formatter,
        notificationPermission,
    )

    /** Keeps [viewModel] subscribed and returns a reader of its settled, loaded state. */
    private fun TestScope.observe(viewModel: EventEditorViewModel): () -> EventEditorUiState.Loaded {
        backgroundScope.launch { viewModel.uiState.collect {} }
        return {
            runCurrent()
            viewModel.uiState.value.shouldBeInstanceOf<EventEditorUiState.Loaded>()
        }
    }

    // ----- Default start follows today, and crosses midnight -----

    @Test
    fun `a new event with no prefill starts on today and follows it across midnight`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(LocalDate.of(2026, 12, 31))
            val viewModel = viewModel(ticker = ticker)
            val state = observe(viewModel)

            state().startDate shouldBe LocalDate.of(2026, 12, 31)
            state().isNew shouldBe true

            ticker.set(LocalDate.of(2027, 1, 1))

            state().startDate shouldBe LocalDate.of(2027, 1, 1)
        }

    @Test
    fun `once the user picks a start date it no longer follows today`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(specToday)
            val viewModel = viewModel(ticker = ticker)
            val state = observe(viewModel)

            viewModel.setStartDate(LocalDate.of(2026, 6, 30))
            ticker.set(specToday.plusDays(1))

            state().startDate shouldBe LocalDate.of(2026, 6, 30)
        }

    // ----- Prefill -----

    @Test
    fun `a valid prefill becomes the initial start date`() =
        runTest(dispatcher) {
            val prefill = LocalDate.of(2024, 6, 17)
            val state = observe(viewModel(key = EventEditorKey(prefillEpochDay = prefill.toEpochDay())))

            state().startDate shouldBe prefill
        }

    @Test
    fun `a prefill outside 1 to 9999 is ignored and today is used instead`() =
        runTest(dispatcher) {
            val state = observe(viewModel(key = EventEditorKey(prefillEpochDay = Long.MAX_VALUE)))

            state().startDate shouldBe specToday
        }

    // ----- Recurrence options offered per date -----

    @Test
    fun `monthly IFC is offered on a regular day and hidden on Year Day and Leap Day`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)

            viewModel.setStartDate(EventFixtures.SOL_13_2026)
            state().monthlyIfcAvailable shouldBe true

            viewModel.setStartDate(EventFixtures.YEAR_DAY_2026)
            state().monthlyIfcAvailable shouldBe false

            viewModel.setStartDate(EventFixtures.LEAP_DAY_2024)
            state().monthlyIfcAvailable shouldBe false
        }

    @Test
    fun `the Leap Day policy is asked for only when the start is a real Leap Day`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            viewModel.setRecurrenceKind(RecurrenceKind.YEARLY_IFC)

            viewModel.setStartDate(EventFixtures.SOL_13_2026)
            state().isLeapDayAnchor shouldBe false

            viewModel.setStartDate(EventFixtures.LEAP_DAY_2024)
            state().isLeapDayAnchor shouldBe true

            // 2025-06-17 is an ordinary day (June 28 in the IFC), not Leap Day.
            viewModel.setStartDate(LocalDate.of(2025, 6, 17))
            state().isLeapDayAnchor shouldBe false
        }

    // ----- end < start is rejected -----

    @Test
    fun `a timed end before the start blocks saving`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val viewModel = viewModel(repository = repo)
            val state = observe(viewModel)
            viewModel.setAllDay(false)
            viewModel.setStartMinuteOfDay(10 * 60)
            viewModel.setEndMinuteOfDay(9 * 60)

            state().endBeforeStart shouldBe true
            state().canSave shouldBe false

            viewModel.save()
            runCurrent()
            repo.currentEvents shouldBe emptyList()
        }

    @Test
    fun `an all-day end date before the start blocks saving`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            viewModel.setStartDate(LocalDate.of(2026, 6, 30))
            viewModel.setAllDayEndDate(LocalDate.of(2026, 6, 29))

            state().allDayEndBeforeStart shouldBe true
            state().canSave shouldBe false
        }

    @Test
    fun `an until date before the start blocks saving`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            viewModel.setStartDate(LocalDate.of(2026, 6, 30))
            viewModel.setRecurrenceKind(RecurrenceKind.WEEKLY)
            viewModel.setRecurrenceEndKind(RecurrenceEndKind.UNTIL)
            viewModel.setUntilDate(LocalDate.of(2026, 1, 1))

            state().untilBeforeStart shouldBe true
            state().canSave shouldBe false
        }

    // ----- Length limits -----

    @Test
    fun `title, notes and location are clipped to the model's limits`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)

            viewModel.setTitle("a".repeat(600))
            viewModel.setDescription("b".repeat(11_000))
            viewModel.setLocation("c".repeat(600))

            state().title.length shouldBe Event.MAX_TITLE_LENGTH
            state().description.length shouldBe Event.MAX_DESCRIPTION_LENGTH
            state().location.length shouldBe Event.MAX_LOCATION_LENGTH
        }

    // ----- Save: create, edit, delete -----

    @Test
    fun `saving a new event stores it with a fresh uid and navigates away`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val uidGenerator = FakeEventUidGenerator()
            val viewModel = viewModel(repository = repo, uidGenerator = uidGenerator)
            observe(viewModel)
            runCurrent()
            viewModel.setTitle("Picnic")
            viewModel.setStartDate(LocalDate.of(2026, 6, 30))
            runCurrent()

            var navigated = false
            backgroundScope.launch {
                viewModel.editorEvents.collect {
                    if (it ==
                        EventEditorEvent.Saved
                    ) {
                        navigated = true
                    }
                }
            }
            viewModel.save()
            runCurrent()

            repo.currentEvents.single().title shouldBe "Picnic"
            repo.currentEvents.single().uid shouldBe "uid-1"
            navigated shouldBe true
        }

    @Test
    fun `editing an existing event keeps its id and uid`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val stored = repo.seed(listOf(EventFixtures.sol13Yearly())).single()
            val viewModel = viewModel(key = EventEditorKey(eventId = stored.id), repository = repo)
            val state = observe(viewModel)
            state().title shouldBe stored.title

            viewModel.setTitle("Renamed")
            viewModel.save()
            runCurrent()

            val reloaded = repo.getEvent(stored.id)
            reloaded?.id shouldBe stored.id
            reloaded?.uid shouldBe stored.uid
            reloaded?.title shouldBe "Renamed"
        }

    @Test
    fun `an event that no longer exists shows NotFound`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val viewModel = viewModel(key = EventEditorKey(eventId = 999), repository = repo)
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()

            viewModel.uiState.value shouldBe EventEditorUiState.NotFound
        }

    @Test
    fun `deleting an existing event removes it and navigates away`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val stored = repo.seed(listOf(EventFixtures.sol13Yearly())).single()
            val viewModel = viewModel(key = EventEditorKey(eventId = stored.id), repository = repo)
            observe(viewModel)

            var deleted = false
            backgroundScope.launch {
                viewModel.editorEvents.collect {
                    if (it ==
                        EventEditorEvent.Deleted
                    ) {
                        deleted = true
                    }
                }
            }
            viewModel.requestDelete()
            runCurrent()
            viewModel.confirmDelete()
            runCurrent()

            repo.getEvent(stored.id) shouldBe null
            deleted shouldBe true
        }

    @Test
    fun `cancelling delete keeps the event and closes the dialog`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val stored = repo.seed(listOf(EventFixtures.sol13Yearly())).single()
            val viewModel = viewModel(key = EventEditorKey(eventId = stored.id), repository = repo)
            val state = observe(viewModel)

            viewModel.requestDelete()
            state().showDeleteConfirm shouldBe true
            viewModel.cancelDelete()
            state().showDeleteConfirm shouldBe false
            repo.getEvent(stored.id) shouldBe stored
        }

    // ----- Unsaved-changes guard -----

    @Test
    fun `back with no changes leaves at once`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            observe(viewModel)
            var left = false
            backgroundScope.launch {
                viewModel.editorEvents.collect {
                    if (it ==
                        EventEditorEvent.NavigatedAway
                    ) {
                        left = true
                    }
                }
            }

            viewModel.requestBack()
            runCurrent()

            left shouldBe true
        }

    @Test
    fun `back after an edit shows the discard guard instead of leaving`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            state()
            viewModel.setTitle("Something")
            runCurrent()

            var left = false
            backgroundScope.launch {
                viewModel.editorEvents.collect {
                    if (it ==
                        EventEditorEvent.NavigatedAway
                    ) {
                        left = true
                    }
                }
            }
            viewModel.requestBack()
            runCurrent()

            left shouldBe false
            state().showDiscardConfirm shouldBe true

            viewModel.confirmDiscard()
            runCurrent()
            left shouldBe true
        }

    // ----- Stale exdates dropped on save when the start or the rule changes -----

    @Test
    fun `saving after moving the start drops stale exdates`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val withExdate = EventFixtures.sol13Yearly().copy(exdates = setOf(LocalDate.of(2027, 6, 30)))
            val stored = repo.seed(listOf(withExdate)).single()
            val viewModel = viewModel(key = EventEditorKey(eventId = stored.id), repository = repo)
            observe(viewModel)
            runCurrent()

            // Also moves the (otherwise stale) one-day all-day end date along with the start, so this
            // test is purely about the exdate-dropping rule, not the separate end-before-start guard.
            viewModel.setStartDate(LocalDate.of(2026, 7, 1))
            viewModel.setAllDayEndDate(LocalDate.of(2026, 7, 1))
            runCurrent()
            viewModel.save()
            runCurrent()

            repo.getEvent(stored.id)?.exdates shouldBe emptySet()
        }

    // ----- Fail soft: the event was deleted elsewhere before save -----

    @Test
    fun `saving an event deleted elsewhere fails soft and keeps the draft`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val stored = repo.seed(listOf(EventFixtures.sol13Yearly())).single()
            val viewModel = viewModel(key = EventEditorKey(eventId = stored.id), repository = repo)
            val state = observe(viewModel)
            state()
            viewModel.setTitle("Still editing")
            runCurrent()
            repo.deleteEvent(stored.id)

            viewModel.save()
            runCurrent()

            state().saveFailed shouldBe true
            state().title shouldBe "Still editing"
        }

    // ----- Process death -----

    @Test
    fun `a new event's draft survives process death`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(handle = handle)
            observe(first)
            first.setTitle("Half-typed")
            first.setStartDate(LocalDate.of(2026, 6, 30))
            first.setRecurrenceKind(RecurrenceKind.YEARLY_IFC)
            runCurrent()

            val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val second = viewModel(handle = restored)
            val state = observe(second)

            state().title shouldBe "Half-typed"
            state().startDate shouldBe LocalDate.of(2026, 6, 30)
            state().recurrenceKind shouldBe RecurrenceKind.YEARLY_IFC
        }

    @Test
    fun `editing an existing event survives process death with the edited draft, not the stored one`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val stored = repo.seed(listOf(EventFixtures.sol13Yearly())).single()
            val handle = SavedStateHandle()
            val first = viewModel(key = EventEditorKey(eventId = stored.id), repository = repo, handle = handle)
            observe(first)
            runCurrent()
            first.setTitle("Edited before death")
            runCurrent()

            val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val second =
                viewModel(key = EventEditorKey(eventId = stored.id), repository = repo, handle = restoredHandle)
            val state = observe(second)

            state().title shouldBe "Edited before death"
        }

    // ----- Leap Day policy carried through to the saved event -----

    @Test
    fun `each Leap Day policy is saved on the built event`() =
        runTest(dispatcher) {
            for (policy in LeapDayPolicy.entries) {
                val repo = FakeEventRepository()
                val viewModel = viewModel(repository = repo)
                observe(viewModel)
                runCurrent()
                viewModel.setStartDate(EventFixtures.LEAP_DAY_2024)
                viewModel.setRecurrenceKind(RecurrenceKind.YEARLY_IFC)
                viewModel.setLeapDayPolicy(policy)
                runCurrent()
                viewModel.save()
                runCurrent()

                val saved = repo.currentEvents.single()
                val rule = saved.recurrence as IfcRecurrence.YearlyOnIntercalary
                val day = rule.day as IntercalaryDay.LeapDay
                day.commonYearPolicy shouldBe policy
            }
        }

    // ----- Calendar id is preserved from the loaded event -----

    @Test
    fun `a new event uses the built-in calendar`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val viewModel = viewModel(repository = repo)
            observe(viewModel)
            runCurrent()
            viewModel.setStartDate(LocalDate.of(2026, 6, 30))
            runCurrent()
            viewModel.save()
            runCurrent()

            repo.currentEvents.single().calendarId shouldBe EventCalendar.DEFAULT_ID
        }

    // ----- POST_NOTIFICATIONS state machine (FEATURES E4, P2): first chip -> request; granted; denied
    // -> explanation, save still works; second chip -> no re-prompt; below API 33 -> no request.

    @Test
    fun `the first reminder chip requests POST_NOTIFICATIONS when the device needs the runtime grant`() =
        runTest(dispatcher) {
            val viewModel = viewModel(notificationPermission = FakeNotificationPermissionGate(needsPermission = true))
            observe(viewModel)
            runCurrent()
            val requests = mutableListOf<EventEditorEvent>()
            backgroundScope.launch { viewModel.editorEvents.collect { requests += it } }

            viewModel.toggleReminder(0)
            runCurrent()

            requests shouldBe listOf(EventEditorEvent.RequestNotificationPermission)
        }

    @Test
    fun `a second reminder chip does not request permission again`() =
        runTest(dispatcher) {
            val viewModel = viewModel(notificationPermission = FakeNotificationPermissionGate(needsPermission = true))
            observe(viewModel)
            runCurrent()
            val requests = mutableListOf<EventEditorEvent>()
            backgroundScope.launch { viewModel.editorEvents.collect { requests += it } }

            viewModel.toggleReminder(0)
            runCurrent()
            viewModel.toggleReminder(1440)
            runCurrent()

            requests shouldBe listOf(EventEditorEvent.RequestNotificationPermission)
        }

    @Test
    fun `removing then re-adding a chip does not request permission again either`() =
        runTest(dispatcher) {
            val viewModel = viewModel(notificationPermission = FakeNotificationPermissionGate(needsPermission = true))
            observe(viewModel)
            runCurrent()
            val requests = mutableListOf<EventEditorEvent>()
            backgroundScope.launch { viewModel.editorEvents.collect { requests += it } }

            viewModel.toggleReminder(0) // add
            runCurrent()
            viewModel.toggleReminder(0) // remove
            runCurrent()
            viewModel.toggleReminder(0) // add again
            runCurrent()

            requests shouldBe listOf(EventEditorEvent.RequestNotificationPermission)
        }

    @Test
    fun `below API 33 no permission is ever requested`() =
        runTest(dispatcher) {
            val viewModel = viewModel(notificationPermission = FakeNotificationPermissionGate(needsPermission = false))
            observe(viewModel)
            runCurrent()
            val requests = mutableListOf<EventEditorEvent>()
            backgroundScope.launch { viewModel.editorEvents.collect { requests += it } }

            viewModel.toggleReminder(0)
            runCurrent()

            requests.shouldBeEmpty()
        }

    @Test
    fun `denial shows a dismissible notice and never blocks saving, and granting clears it`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val viewModel = viewModel(repository = repo, notificationPermission = FakeNotificationPermissionGate(true))
            val state = observe(viewModel)
            viewModel.setTitle("Reminder test")
            viewModel.setStartDate(LocalDate.of(2026, 6, 30))
            viewModel.toggleReminder(0)
            runCurrent()

            viewModel.onNotificationPermissionResult(granted = false)
            state().showNotificationPermissionNotice shouldBe true

            viewModel.save()
            runCurrent()
            repo.currentEvents.single().title shouldBe "Reminder test"

            viewModel.dismissNotificationPermissionNotice()
            state().showNotificationPermissionNotice shouldBe false

            viewModel.onNotificationPermissionResult(granted = true)
            state().showNotificationPermissionNotice shouldBe false
        }

    // ----- Individually deleted occurrences ("delete this occurrence" from Day detail): count and restore-all -----

    @Test
    fun `exdateCount reflects the loaded event, and restoreAllOccurrences clears every exdate`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val withExdates =
                EventFixtures
                    .sol13Yearly()
                    .copy(exdates = setOf(LocalDate.of(2027, 6, 30), LocalDate.of(2028, 6, 30)))
            val stored = repo.seed(listOf(withExdates)).single()
            val viewModel = viewModel(key = EventEditorKey(eventId = stored.id), repository = repo)
            val state = observe(viewModel)

            state().exdateCount shouldBe 2

            viewModel.restoreAllOccurrences()
            runCurrent()

            state().exdateCount shouldBe 0
            repo
                .getEvent(stored.id)
                ?.exdates
                .orEmpty()
                .shouldBeEmpty()
        }

    @Test
    fun `restoreAllOccurrences is a no-op for a new event or one with nothing to restore`() =
        runTest(dispatcher) {
            val repo = FakeEventRepository()
            val stored = repo.seed(listOf(EventFixtures.sol13Yearly())).single()
            val viewModel = viewModel(key = EventEditorKey(eventId = stored.id), repository = repo)
            val state = observe(viewModel)
            state().exdateCount shouldBe 0

            viewModel.restoreAllOccurrences()
            runCurrent()

            state().exdateCount shouldBe 0
            repo.getEvent(stored.id) shouldBe stored
        }
}

/** A settable [NotificationPermissionGate] for the permission state-machine tests. */
private class FakeNotificationPermissionGate(
    private val needsPermission: Boolean,
) : NotificationPermissionGate {
    override fun needsRuntimePermission(): Boolean = needsPermission
}
