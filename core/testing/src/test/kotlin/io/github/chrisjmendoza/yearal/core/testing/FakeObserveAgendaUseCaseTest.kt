package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.event.DayAgenda
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayCategory
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayDefinition
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayOccurrence
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayRule
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate

// FakeObserveAgendaUseCase against the ObserveAgendaUseCase contract: sparse ascending keys inside the
// range, multi-day entries under every date, presence without holidays, re-emission on change.
@OptIn(ExperimentalCoroutinesApi::class)
class FakeObserveAgendaUseCaseTest {
    // IFC December 2026 with its Year Day band: Gregorian December 3 – December 31 (29 days).
    private val december = LocalDate.of(2026, 12, 3)..LocalDate.of(2026, 12, 31)
    private val january = LocalDate.of(2027, 1, 1)..LocalDate.of(2027, 1, 28)

    private fun yearDayHoliday() =
        HolidayOccurrence(
            holiday =
                HolidayDefinition(
                    id = "year-day",
                    name = mapOf("en" to "Year Day"),
                    rule = HolidayRule.Ifc.YearDay,
                    category = HolidayCategory.IFC,
                ),
            setId = "ifc",
            date = EventFixtures.YEAR_DAY_2026,
            observed = false,
            dayIndex = 0,
        )

    @Test
    fun `starts empty and records the ranges it was asked for`() =
        runTest {
            val agenda = FakeObserveAgendaUseCase()
            agenda(december).first().shouldBeEmpty()
            agenda.presence(january).first().shouldBeEmpty()
            agenda.requestedRanges shouldContainExactly listOf(december, january)
        }

    @Test
    fun `a multi-day entry is under every date it touches, clipped to the requested range`() =
        runTest {
            val agenda = FakeObserveAgendaUseCase()
            // December 30, Year Day, January 1.
            val trip = EventFixtures.entry(EventFixtures.floatingMultiDay(id = 9))
            agenda.putEntry(trip)

            val inDecember = agenda(december).first()
            val inJanuary = agenda(january).first()
            assertSoftly {
                inDecember.keys.toList() shouldContainExactly
                    listOf(LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31))
                inJanuary.keys.toList() shouldContainExactly listOf(LocalDate.of(2027, 1, 1))
                inDecember.getValue(EventFixtures.YEAR_DAY_2026).entries shouldContainExactly listOf(trip)
                inDecember.getValue(EventFixtures.YEAR_DAY_2026).date shouldBe EventFixtures.YEAR_DAY_2026
            }
        }

    @Test
    fun `entries and holidays share a day, in contract order, and keys ascend`() =
        runTest {
            val agenda = FakeObserveAgendaUseCase()
            val party = EventFixtures.entry(EventFixtures.yearDayYearly(id = 1))
            val trip = EventFixtures.entry(EventFixtures.floatingMultiDay(id = 9))
            agenda.putHoliday(yearDayHoliday())
            agenda.putEntry(party)
            agenda.putEntry(trip)

            val result = agenda(december).first()
            val yearDay = result.getValue(EventFixtures.YEAR_DAY_2026)
            assertSoftly {
                result.keys.toList() shouldContainExactly
                    listOf(LocalDate.of(2026, 12, 30), EventFixtures.YEAR_DAY_2026)
                // Both all-day: the trip started a day earlier, so it sorts first.
                yearDay.entries shouldContainExactly listOf(trip, party)
                yearDay.holidays.map { it.holiday.id } shouldContainExactly listOf("year-day")
            }
        }

    @Test
    fun `presence counts event occurrences and ignores holidays`() =
        runTest {
            val agenda = FakeObserveAgendaUseCase()
            agenda.putHoliday(yearDayHoliday())
            agenda.presence(december).first().shouldBeEmpty()
            agenda(december).first().keys.toList() shouldContainExactly listOf(EventFixtures.YEAR_DAY_2026)

            agenda.putEntry(EventFixtures.entry(EventFixtures.sol13Yearly(id = 5)))
            agenda.putEntry(EventFixtures.entry(EventFixtures.floatingMultiDay(id = 9)))
            val year2026 = LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31)
            agenda.presence(year2026).first().toList() shouldContainExactly
                listOf(EventFixtures.SOL_13_2026, LocalDate.of(2026, 12, 30), EventFixtures.YEAR_DAY_2026)
        }

    @Test
    fun `collectors are re-emitted to when the staged agenda changes`() =
        runTest {
            val agenda = FakeObserveAgendaUseCase()
            val seen = mutableListOf<Set<LocalDate>>()
            val job = launch { agenda(december).collect { seen += it.keys.toSet() } }
            runCurrent()
            agenda.putEntry(EventFixtures.entry(EventFixtures.yearDayYearly(id = 1)))
            runCurrent()
            // A change outside the range is not an emission for this collector.
            agenda.putEntry(EventFixtures.entry(EventFixtures.sol13Yearly(id = 5)))
            runCurrent()
            agenda.clear()
            runCurrent()
            job.cancel()
            seen shouldContainExactly listOf(emptySet(), setOf(EventFixtures.YEAR_DAY_2026), emptySet())
        }

    @Test
    fun `setAgendas replaces everything and drops empty days, and an empty range is empty`() =
        runTest {
            val agenda = FakeObserveAgendaUseCase()
            agenda.putEntry(EventFixtures.entry(EventFixtures.sol13Yearly(id = 5)))
            agenda.setAgendas(
                listOf(
                    EventFixtures.dayAgenda(EventFixtures.YEAR_DAY_2026, EventFixtures.yearDayYearly(id = 1)),
                    DayAgenda.of(LocalDate.of(2026, 12, 25)),
                ),
            )
            val wholeYear = LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31)
            agenda(wholeYear).first().keys.toList() shouldContainExactly listOf(EventFixtures.YEAR_DAY_2026)
            agenda(LocalDate.of(2026, 12, 31)..LocalDate.of(2026, 1, 1)).first().shouldBeEmpty()
        }
}
