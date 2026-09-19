package io.github.chrisjmendoza.yearal.feature.events.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRecurrence
import io.github.chrisjmendoza.yearal.core.domain.event.IntercalaryDay
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.github.chrisjmendoza.yearal.core.domain.event.Recurrence
import io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceEnd
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository
import io.github.chrisjmendoza.yearal.core.testing.FakeEventUidGenerator
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * [EventDraft], [buildEvent], [buildTiming], [buildRecurrence] and the Leap-Day/monthly availability
 * helpers, written from `docs/contracts/Events.md` T4 guidance: an IFC rule is always rebuilt from the
 * current start date, exdates are dropped only when the start or the rule changed, and a Gregorian
 * `UNTIL`/`COUNT` round-trips through this editor's own canonical text.
 */
@RunWith(AndroidJUnit4::class)
class EventDraftTest {
    private val uid = FakeEventUidGenerator()

    // ----- Round-trip: loading an existing event and saving it unchanged reproduces it exactly -----

    // Every shape except weeklyGregorian round-trips byte-for-byte: the editor always rebuilds an IFC
    // rule from the current start (matching by construction) and its own canonical Gregorian text
    // ("FREQ=WEEKLY"/"FREQ=YEARLY" plus UNTIL/COUNT only) for the rest. weeklyGregorian's fixture text
    // carries an extra "BYDAY=MO" qualifier the editor's chooser has no UI for and therefore does not
    // preserve on an unchanged save — a deliberate, documented scope limit (docs/contracts/Events.md T4
    // says only to derive rules with yearlyOn/monthlyOn; it does not ask for raw RRULE preservation).
    @Test
    fun `every EventFixtures shape except weeklyGregorian round-trips through load and unchanged save`() =
        runTest {
            for (fixture in EventFixtures.all().filterNot { it.uid.startsWith("fixture-weekly-gregorian") }) {
                val repo = FakeEventRepository()
                val stored = repo.seed(listOf(fixture)).single()

                val draft = EventDraft.from(stored, deviceZoneId = ZoneId.of("UTC"))
                val rebuilt = buildEvent(draft, requireNotNull(draft.startDate), stored, uid)
                val resavedId = repo.upsertEvent(rebuilt)

                repo.getEvent(resavedId) shouldBe stored
            }
        }

    @Test
    fun `weeklyGregorian round-trips its own canonical text, dropping the BYDAY it has no UI for`() =
        runTest {
            val repo = FakeEventRepository()
            val stored = repo.seed(listOf(EventFixtures.weeklyGregorian())).single()

            val draft = EventDraft.from(stored, deviceZoneId = ZoneId.of("UTC"))
            draft.recurrenceKind shouldBe RecurrenceKind.WEEKLY
            val rebuilt = buildEvent(draft, requireNotNull(draft.startDate), stored, uid)
            rebuilt.recurrence shouldBe Recurrence.Gregorian("FREQ=WEEKLY")

            val resavedId = repo.upsertEvent(rebuilt)
            val resaved = requireNotNull(repo.getEvent(resavedId))

            resaved.copy(recurrence = stored.recurrence) shouldBe stored
        }

    // ----- Creating a new event -----

    @Test
    fun `a new all-day event builds with a fresh uid and no exdates`() {
        val draft =
            EventDraft(
                title = "Picnic",
                isAllDay = true,
                startDate = LocalDate.of(2026, 6, 30),
                fixedZoneId = ZoneId.of("UTC"),
            )
        val event = buildEvent(draft, LocalDate.of(2026, 6, 30), existing = null, newUid = uid)

        event.id shouldBe 0L
        event.uid shouldBe "uid-1"
        event.title shouldBe "Picnic"
        event.timing shouldBe EventTiming.AllDay(LocalDate.of(2026, 6, 30), 1)
        event.recurrence shouldBe Recurrence.None
        event.exdates shouldBe emptySet()
        event.calendarId shouldBe EventCalendar.DEFAULT_ID
    }

    @Test
    fun `a new timed event builds from start and end minutes on the same day`() {
        val draft =
            EventDraft(
                isAllDay = false,
                startDate = LocalDate.of(2026, 3, 8),
                startMinuteOfDay = 9 * 60 + 30,
                endMinuteOfDay = 10 * 60 + 15,
                fixedZoneId = ZoneId.of("UTC"),
            )
        val event = buildEvent(draft, LocalDate.of(2026, 3, 8), existing = null, newUid = uid)

        event.timing shouldBe
            EventTiming.Timed(LocalDate.of(2026, 3, 8), 9 * 60 + 30, 45, zone = null)
    }

    @Test
    fun `a fixed zone choice carries the fixed zone id into the timing`() {
        val draft =
            EventDraft(
                isAllDay = false,
                startDate = LocalDate.of(2026, 3, 8),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 10 * 60,
                zoneChoice = ZoneChoice.FIXED,
                fixedZoneId = EventFixtures.NEW_YORK,
            )
        val event = buildEvent(draft, LocalDate.of(2026, 3, 8), existing = null, newUid = uid)

        (event.timing as EventTiming.Timed).zone shouldBe EventFixtures.NEW_YORK
    }

    // ----- Multi-day all-day -----

    @Test
    fun `an all-day end date after the start covers every date between them`() {
        val draft =
            EventDraft(
                isAllDay = true,
                startDate = LocalDate.of(2026, 12, 30),
                allDayEndDate = LocalDate.of(2027, 1, 1),
                fixedZoneId = ZoneId.of("UTC"),
            )
        val event = buildEvent(draft, LocalDate.of(2026, 12, 30), existing = null, newUid = uid)

        event.timing shouldBe EventTiming.AllDay(LocalDate.of(2026, 12, 30), 3)
    }

    // ----- end < start is rejected by EventTiming.timed itself -----

    @Test
    fun `a timed end before the start throws, never silently swapping`() {
        val draft =
            EventDraft(
                isAllDay = false,
                startDate = LocalDate.of(2026, 3, 8),
                startMinuteOfDay = 10 * 60,
                endMinuteOfDay = 9 * 60,
                fixedZoneId = ZoneId.of("UTC"),
            )
        try {
            buildTiming(draft, LocalDate.of(2026, 3, 8))
            error("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // end before start is rejected by EventTiming.timed (core/domain), not silently accepted.
        }
    }

    // ----- Recurrence derivation: always rebuilt from the current start -----

    @Test
    fun `yearly IFC is rebuilt from the current start, matching the anchor exactly`() {
        val draft = EventDraft(recurrenceKind = RecurrenceKind.YEARLY_IFC, fixedZoneId = ZoneId.of("UTC"))
        buildRecurrence(draft, EventFixtures.SOL_13_2026) shouldBe IfcRecurrence.yearlyOn(EventFixtures.SOL_13_2026)
        buildRecurrence(draft, EventFixtures.YEAR_DAY_2026) shouldBe IfcRecurrence.yearlyOn(EventFixtures.YEAR_DAY_2026)
    }

    @Test
    fun `yearly IFC on Leap Day carries the chosen common-year policy`() {
        for (policy in LeapDayPolicy.entries) {
            val draft =
                EventDraft(
                    recurrenceKind = RecurrenceKind.YEARLY_IFC,
                    leapDayPolicy = policy,
                    fixedZoneId = ZoneId.of("UTC"),
                )
            val rule = buildRecurrence(draft, EventFixtures.LEAP_DAY_2024)
            rule shouldBe IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy))
        }
    }

    @Test
    fun `monthly IFC is null-safe and falls back to None on an intercalary start`() {
        val draft = EventDraft(recurrenceKind = RecurrenceKind.MONTHLY_IFC, fixedZoneId = ZoneId.of("UTC"))
        buildRecurrence(draft, EventFixtures.SOL_13_2026) shouldBe IfcRecurrence.MonthlyOnDay(13)
        // The editor hides "monthly" on Year Day and Leap Day (isMonthlyIfcAvailable), but the builder
        // itself must never crash if it is reached anyway.
        buildRecurrence(draft, EventFixtures.YEAR_DAY_2026) shouldBe Recurrence.None
    }

    @Test
    fun `weekly and yearly Gregorian build this editor's own canonical RRULE text`() {
        val weekly = EventDraft(recurrenceKind = RecurrenceKind.WEEKLY, fixedZoneId = ZoneId.of("UTC"))
        buildRecurrence(weekly, LocalDate.of(2026, 1, 5)) shouldBe Recurrence.Gregorian("FREQ=WEEKLY")

        val yearly = EventDraft(recurrenceKind = RecurrenceKind.YEARLY_GREGORIAN, fixedZoneId = ZoneId.of("UTC"))
        buildRecurrence(yearly, LocalDate.of(2026, 6, 18)) shouldBe Recurrence.Gregorian("FREQ=YEARLY")
    }

    @Test
    fun `an until end condition is encoded and parsed back for both rule families`() {
        val untilDraft =
            EventDraft(
                recurrenceKind = RecurrenceKind.WEEKLY,
                recurrenceEndKind = RecurrenceEndKind.UNTIL,
                untilDate = LocalDate.of(2030, 12, 31),
                fixedZoneId = ZoneId.of("UTC"),
            )
        val rule = buildRecurrence(untilDraft, LocalDate.of(2026, 1, 5))
        rule shouldBe Recurrence.Gregorian("FREQ=WEEKLY;UNTIL=20301231")

        val event =
            Event(
                uid = "u",
                title = "",
                timing = EventTiming.Timed(LocalDate.of(2026, 1, 5), 0, 0),
                recurrence = rule,
            )
        val restored = EventDraft.from(event, deviceZoneId = ZoneId.of("UTC"))
        restored.recurrenceEndKind shouldBe RecurrenceEndKind.UNTIL
        restored.untilDate shouldBe LocalDate.of(2030, 12, 31)
    }

    @Test
    fun `a count end condition is encoded and parsed back, and an IFC UNTIL round-trips its date`() {
        val countDraft =
            EventDraft(
                recurrenceKind = RecurrenceKind.YEARLY_IFC,
                recurrenceEndKind = RecurrenceEndKind.COUNT,
                count = 5,
                fixedZoneId = ZoneId.of("UTC"),
            )
        val rule = buildRecurrence(countDraft, EventFixtures.SOL_13_2026)
        (rule as IfcRecurrence).end shouldBe RecurrenceEnd.Count(5)

        val untilDraft =
            EventDraft(
                recurrenceKind = RecurrenceKind.YEARLY_IFC,
                recurrenceEndKind = RecurrenceEndKind.UNTIL,
                untilDate = LocalDate.of(2030, 6, 30),
                fixedZoneId = ZoneId.of("UTC"),
            )
        val untilRule = buildRecurrence(untilDraft, EventFixtures.SOL_13_2026) as IfcRecurrence
        untilRule.end shouldBe RecurrenceEnd.Until(LocalDate.of(2030, 6, 30))

        val event =
            Event(
                uid = "u",
                title = "",
                timing = EventTiming.AllDay(EventFixtures.SOL_13_2026),
                recurrence = untilRule,
            )
        EventDraft.from(event, deviceZoneId = ZoneId.of("UTC")).untilDate shouldBe LocalDate.of(2030, 6, 30)
    }

    // ----- Stale exdates are dropped exactly when the start or the rule changed -----

    @Test
    fun `exdates survive an unchanged save and are dropped when the start changes`() =
        runTest {
            val repo = FakeEventRepository()
            val withExdate =
                EventFixtures.sol13Yearly().copy(exdates = setOf(LocalDate.of(2027, 6, 30)))
            val stored = repo.seed(listOf(withExdate)).single()
            val draft = EventDraft.from(stored, ZoneId.of("UTC"))

            val unchanged = buildEvent(draft, requireNotNull(draft.startDate), stored, uid)
            unchanged.exdates shouldBe stored.exdates

            val movedStart = draft.copy(startDate = LocalDate.of(2026, 7, 1))
            val moved = buildEvent(movedStart, LocalDate.of(2026, 7, 1), stored, uid)
            moved.exdates shouldBe emptySet()
        }

    @Test
    fun `exdates are dropped when the recurrence kind changes even if the start does not`() =
        runTest {
            val repo = FakeEventRepository()
            val withExdate = EventFixtures.sol13Yearly().copy(exdates = setOf(LocalDate.of(2027, 6, 30)))
            val stored = repo.seed(listOf(withExdate)).single()
            val draft = EventDraft.from(stored, ZoneId.of("UTC"))

            val changedKind = draft.copy(recurrenceKind = RecurrenceKind.MONTHLY_IFC)
            val rebuilt = buildEvent(changedKind, requireNotNull(draft.startDate), stored, uid)
            rebuilt.exdates shouldBe emptySet()
        }

    // ----- Availability helpers (CLAUDE.md rule 6: every when handles Year Day and Leap Day) -----

    @Test
    fun `monthly IFC is unavailable exactly on Year Day and Leap Day`() {
        isMonthlyIfcAvailable(EventFixtures.SOL_13_2026) shouldBe true
        isMonthlyIfcAvailable(EventFixtures.YEAR_DAY_2026) shouldBe false
        isMonthlyIfcAvailable(EventFixtures.LEAP_DAY_2024) shouldBe false
    }

    @Test
    fun `the Leap Day policy is asked for only on a real Leap Day`() {
        isLeapDayAnchor(EventFixtures.LEAP_DAY_2024) shouldBe true
        isLeapDayAnchor(EventFixtures.LEAP_DAY_2028) shouldBe true
        isLeapDayAnchor(EventFixtures.YEAR_DAY_2026) shouldBe false
        isLeapDayAnchor(EventFixtures.SOL_13_2026) shouldBe false
        // A common year has no Leap Day at all: June 17 there is the ordinary June 28.
        isLeapDayAnchor(LocalDate.of(2025, 6, 17)) shouldBe false
    }

    // ----- EventDraft.from reads every EventTiming and Recurrence shape correctly -----

    @Test
    fun `EventDraft from reads a zoned timed event's zone as FIXED`() {
        val stored = EventFixtures.timedZoned(id = 1)
        val draft = EventDraft.from(stored, deviceZoneId = ZoneId.of("UTC"))

        draft.zoneChoice shouldBe ZoneChoice.FIXED
        draft.fixedZoneId shouldBe EventFixtures.NEW_YORK
        draft.isAllDay shouldBe false
        draft.startMinuteOfDay shouldBe 9 * 60 + 30
        draft.endMinuteOfDay shouldBe 9 * 60 + 30 + 45
    }

    @Test
    fun `EventDraft from a floating event defaults the fixed-zone field to the device zone`() {
        val stored = EventFixtures.sol13Yearly(id = 1)
        val draft = EventDraft.from(stored, deviceZoneId = ZoneId.of("Asia/Tokyo"))

        draft.zoneChoice shouldBe ZoneChoice.FLOATING
        draft.fixedZoneId shouldBe ZoneId.of("Asia/Tokyo")
    }

    @Test
    fun `EventDraft from a multi-day all-day event reads its end date`() {
        val stored = EventFixtures.floatingMultiDay(id = 1)
        val draft = EventDraft.from(stored, deviceZoneId = ZoneId.of("UTC"))

        draft.startDate shouldBe LocalDate.of(2026, 12, 30)
        draft.allDayEndDate shouldBe LocalDate.of(2027, 1, 1)
    }

    @Test
    fun `EventDraft from every RecurrenceKind shape reports the matching kind`() {
        EventDraft.from(EventFixtures.yearDayYearly(1), ZoneId.of("UTC")).recurrenceKind shouldBe
            RecurrenceKind.YEARLY_IFC
        EventDraft.from(EventFixtures.leapDayYearly(1), ZoneId.of("UTC")).recurrenceKind shouldBe
            RecurrenceKind.YEARLY_IFC
        EventDraft.from(EventFixtures.sol13Yearly(1), ZoneId.of("UTC")).recurrenceKind shouldBe
            RecurrenceKind.YEARLY_IFC
        EventDraft.from(EventFixtures.thirteenthMonthly(1), ZoneId.of("UTC")).recurrenceKind shouldBe
            RecurrenceKind.MONTHLY_IFC
        EventDraft.from(EventFixtures.weeklyGregorian(1), ZoneId.of("UTC")).recurrenceKind shouldBe
            RecurrenceKind.WEEKLY
    }

    @Test
    fun `EventDraft from reads each Leap Day policy back`() {
        for (policy in LeapDayPolicy.entries) {
            EventDraft.from(EventFixtures.leapDayYearly(1, policy), ZoneId.of("UTC")).leapDayPolicy shouldBe policy
        }
    }

    @Test
    fun `a fresh EventDraft's date is null, meaning it follows today`() {
        EventDraft(fixedZoneId = ZoneId.of("UTC")).startDate shouldBe null
    }
}
