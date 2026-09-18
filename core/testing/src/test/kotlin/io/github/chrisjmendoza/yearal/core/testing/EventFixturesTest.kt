package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRecurrence
import io.github.chrisjmendoza.yearal.core.domain.event.IntercalaryDay
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// The fixtures claim IFC positions in their names; this checks the claims against :core:calendar, and that
// the small fakes behave as documented.
class EventFixturesTest {
    @Test
    fun `the named dates are the IFC dates they say they are`() {
        assertSoftly {
            IfcDate.from(EventFixtures.SOL_13_2026) shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 13)
            IfcDate.from(EventFixtures.SOL_13_2026).nominalDayOfWeek shouldBe DayOfWeek.FRIDAY
            IfcDate.from(EventFixtures.SOL_13_2026).actualDayOfWeek shouldBe DayOfWeek.TUESDAY
            IfcDate.from(EventFixtures.YEAR_DAY_2026).shouldBeInstanceOf<IfcDate.YearDay>()
            IfcDate.from(EventFixtures.YEAR_DAY_2026).nominalDayOfWeek.shouldBeNull()
            IfcDate.from(EventFixtures.YEAR_DAY_2026).actualDayOfWeek shouldBe DayOfWeek.THURSDAY
            IfcDate.from(EventFixtures.LEAP_DAY_2024).shouldBeInstanceOf<IfcDate.LeapDay>()
            IfcDate.from(EventFixtures.LEAP_DAY_2024).actualDayOfWeek shouldBe DayOfWeek.MONDAY
            IfcDate.from(EventFixtures.LEAP_DAY_2028).shouldBeInstanceOf<IfcDate.LeapDay>()
            IfcDate.from(EventFixtures.LEAP_DAY_2028).actualDayOfWeek shouldBe DayOfWeek.SATURDAY
        }
    }

    @Test
    fun `there is a Leap Day fixture for every policy`() {
        for (policy in LeapDayPolicy.entries) {
            val event = EventFixtures.leapDayYearly(policy = policy)
            event.startDate shouldBe EventFixtures.LEAP_DAY_2024
            event.recurrence shouldBe IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy))
        }
    }

    @Test
    fun `the floating multi-day fixture covers IFC December 28, Year Day and January 1`() {
        val trip = EventFixtures.floatingMultiDay()
        val dates = trip.firstOccurrence().dates(ZoneId.of("Pacific/Kiritimati"))
        assertSoftly {
            trip.timing.zone.shouldBeNull()
            trip.isAllDay shouldBe true
            IfcDate.from(dates.start) shouldBe IfcDate.Regular(2026, IfcMonth.DECEMBER, 28)
            IfcDate.from(dates.start.plusDays(1)).shouldBeInstanceOf<IfcDate.YearDay>()
            IfcDate.from(dates.endInclusive) shouldBe IfcDate.Regular(2027, IfcMonth.JANUARY, 1)
        }
    }

    @Test
    fun `the timed fixtures are zoned and floating as described`() {
        val call = EventFixtures.timedZoned()
        val choir = EventFixtures.weeklyGregorian()
        assertSoftly {
            call.timing shouldBe EventTiming.Timed(LocalDate.of(2026, 3, 8), 570, 45, EventFixtures.NEW_YORK)
            call.firstOccurrence().start(ZoneId.of("Asia/Tokyo")).toLocalTime() shouldBe LocalTime.of(22, 30)
            choir.timing.zone.shouldBeNull()
            choir.startDate.dayOfWeek shouldBe DayOfWeek.MONDAY
        }
    }

    @Test
    fun `all fixtures have distinct uids and can be stored together`() =
        runTest {
            val all = EventFixtures.all()
            all.map { it.uid }.toSet().size shouldBe all.size
            all.size shouldBe 9
            FakeEventRepository().seed(all).map { it.id } shouldBe (1L..9L).toList()
        }

    @Test
    fun `the uid generator counts from 1 and the reminder scheduler counts calls`() =
        runTest {
            val uids = FakeEventUidGenerator()
            listOf(uids.newUid(), uids.newUid()) shouldBe listOf("uid-1", "uid-2")
            uids.issued shouldBe 2
            FakeEventUidGenerator("evt").newUid() shouldBe "evt1"

            val scheduler = FakeReminderScheduler()
            scheduler.reschedule()
            scheduler.reschedule()
            scheduler.rescheduleCount shouldBe 2
        }
}
