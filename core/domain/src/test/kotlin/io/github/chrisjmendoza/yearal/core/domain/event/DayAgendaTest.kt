package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayCategory
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayDefinition
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayOccurrence
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayRule
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// DayAgenda and AgendaEntry: the per-date bucket of docs/ARCHITECTURE.md §3.4 and the ordering frozen in
// docs/contracts/Events.md.
class DayAgendaTest {
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val yearDay = LocalDate.of(2026, 12, 31)

    private fun timedEvent(
        id: Long,
        start: LocalDateTime,
        minutes: Int,
        zone: ZoneId? = null,
    ) = Event(
        id = id,
        uid = "uid-$id",
        title = "Event $id",
        timing = EventTiming.Timed(start.toLocalDate(), start.hour * 60 + start.minute, minutes, zone),
    )

    private fun allDayEvent(
        id: Long,
        first: LocalDate,
        days: Int = 1,
    ) = Event(id = id, uid = "uid-$id", title = "Event $id", timing = EventTiming.AllDay(first, days))

    private fun entry(
        event: Event,
        zone: ZoneId = newYork,
    ) = AgendaEntry(event, event.firstOccurrence(), colorArgb = 0, deviceZone = zone)

    private fun holiday(
        id: String,
        date: LocalDate,
    ) = HolidayOccurrence(
        holiday =
            HolidayDefinition(
                id = id,
                name = mapOf("en" to id),
                rule = HolidayRule.Ifc.YearDay,
                category = HolidayCategory.IFC,
            ),
        setId = "ifc",
        date = date,
        observed = false,
        dayIndex = 0,
    )

    @Test
    fun `an entry exposes the resolved times and device-zone dates of its occurrence`() {
        // 20:00 December 30 in New York is 10:00 on Year Day in Tokyo.
        val event = timedEvent(1, LocalDateTime.of(2026, 12, 30, 20, 0), 60, newYork)
        val inTokyo = entry(event, tokyo)
        assertSoftly {
            inTokyo.start.toLocalDateTime() shouldBe LocalDateTime.of(2026, 12, 31, 10, 0)
            inTokyo.end.toLocalDateTime() shouldBe LocalDateTime.of(2026, 12, 31, 11, 0)
            inTokyo.firstDate shouldBe yearDay
            inTokyo.lastDate shouldBe yearDay
            inTokyo.isAllDay shouldBe false
            entry(event, newYork).firstDate shouldBe LocalDate.of(2026, 12, 30)
        }
    }

    @Test
    fun `an entry must pair an occurrence with its own event`() {
        val event = allDayEvent(1, yearDay)
        shouldThrow<IllegalArgumentException> {
            AgendaEntry(event, allDayEvent(2, yearDay).firstOccurrence(), 0, newYork)
        }
    }

    @Test
    fun `of sorts entries all-day first, then by instant, then by event id`() {
        val lateTimed = entry(timedEvent(1, yearDay.atTime(18, 0), 30))
        val earlyTimed = entry(timedEvent(5, yearDay.atTime(8, 0), 30))
        val sameTimeHigherId = entry(timedEvent(3, yearDay.atTime(18, 0), 90))
        val allDay = entry(allDayEvent(9, yearDay))
        val multiDayStartedEarlier = entry(allDayEvent(8, LocalDate.of(2026, 12, 30), days = 3))
        // A zoned event at 07:00 in New York as seen from New York sorts before the floating 08:00 one.
        val zoned = entry(timedEvent(7, yearDay.atTime(7, 0), 30, newYork))

        val agenda =
            DayAgenda.of(
                yearDay,
                listOf(lateTimed, earlyTimed, sameTimeHigherId, allDay, multiDayStartedEarlier, zoned),
            )

        agenda.entries.map { it.event.id } shouldContainExactly listOf(8L, 9L, 7L, 5L, 1L, 3L)
        agenda.isEmpty shouldBe false
    }

    @Test
    fun `of sorts holidays in their natural order`() {
        val agenda = DayAgenda.of(yearDay, holidays = listOf(holiday("z-day", yearDay), holiday("a-day", yearDay)))
        agenda.holidays.map { it.holiday.id } shouldContainExactly listOf("a-day", "z-day")
        agenda.entries shouldBe emptyList()
        agenda.isEmpty shouldBe false
        DayAgenda.of(yearDay).isEmpty shouldBe true
    }

    @Test
    fun `the constructor rejects foreign dates and unsorted lists`() {
        val early = entry(timedEvent(1, yearDay.atTime(8, 0), 30))
        val late = entry(timedEvent(2, yearDay.atTime(18, 0), 30))
        val otherDay = entry(timedEvent(3, LocalDate.of(2026, 12, 30).atTime(8, 0), 30))
        assertSoftly {
            shouldThrow<IllegalArgumentException> { DayAgenda(yearDay, listOf(late, early), emptyList()) }
            shouldThrow<IllegalArgumentException> { DayAgenda(yearDay, listOf(otherDay), emptyList()) }
            shouldThrow<IllegalArgumentException> { DayAgenda.of(yearDay, listOf(otherDay)) }
            shouldThrow<IllegalArgumentException> {
                DayAgenda(yearDay, emptyList(), listOf(holiday("a", LocalDate.of(2026, 12, 30))))
            }
            shouldThrow<IllegalArgumentException> {
                DayAgenda(yearDay, emptyList(), listOf(holiday("z", yearDay), holiday("a", yearDay)))
            }
        }
    }

    @Test
    fun `a multi-day entry belongs to the agenda of every date it touches`() {
        // December 30, Year Day, January 1.
        val threeDays = entry(allDayEvent(1, LocalDate.of(2026, 12, 30), days = 3))
        assertSoftly {
            for (date in listOf(LocalDate.of(2026, 12, 30), yearDay, LocalDate.of(2027, 1, 1))) {
                DayAgenda.of(date, listOf(threeDays)).entries shouldContainExactly listOf(threeDays)
            }
            shouldThrow<IllegalArgumentException> { DayAgenda.of(LocalDate.of(2027, 1, 2), listOf(threeDays)) }
        }
    }
}
