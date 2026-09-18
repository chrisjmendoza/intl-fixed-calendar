package io.github.chrisjmendoza.yearal.core.domain.holiday

import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import java.time.DayOfWeek
import java.time.LocalDate

// Hand-written fixtures shared by the holiday tests. Realistic definitions come from
// docs/holidays-and-import.md §2.5 (US starter set) and §5 (IFC set).

internal fun holiday(
    id: String,
    rule: HolidayRule,
    since: Int? = null,
    until: Int? = null,
    yearFilter: YearFilter? = null,
    observed: ObservedPolicy = ObservedPolicy.NONE,
    durationDays: Int = 1,
    category: HolidayCategory = HolidayCategory.OBSERVANCE,
): HolidayDefinition =
    HolidayDefinition(
        id = id,
        name = mapOf("en" to id),
        rule = rule,
        since = since,
        until = until,
        yearFilter = yearFilter,
        observed = observed,
        durationDays = durationDays,
        category = category,
    )

internal fun pack(
    id: String,
    vararg holidays: HolidayDefinition,
): HolidaySet =
    HolidaySet(
        id = id,
        region = null,
        name = mapOf("en" to id),
        sources = emptyList(),
        holidays = holidays.toList(),
    )

/** A set with a single holiday, for testing one rule in isolation through the real engine. */
internal fun single(
    rule: HolidayRule,
    id: String = "x",
): HolidaySet = pack("test", holiday(id, rule))

internal fun date(
    year: Int,
    month: Int,
    day: Int,
): LocalDate = LocalDate.of(year, month, day)

/** The actual dates of a set's occurrences for a rule year, in engine order. */
internal fun HolidayEngine.dates(
    set: HolidaySet,
    year: Int,
): List<LocalDate> = occurrences(set, year).map { it.date }

// --- US starter set, docs/holidays-and-import.md §2.5 -------------------------------------------------

private val publicHoliday = HolidayCategory.PUBLIC
private val usFederal = ObservedPolicy.US_FEDERAL

internal val newYear =
    holiday("us.new_year", HolidayRule.Fixed(1, 1), observed = usFederal, category = publicHoliday)
internal val mlk =
    holiday("us.mlk", HolidayRule.NthWeekday(1, DayOfWeek.MONDAY, 3), since = 1986, category = publicHoliday)
internal val memorial =
    holiday("us.memorial", HolidayRule.NthWeekday(5, DayOfWeek.MONDAY, -1), since = 1971, category = publicHoliday)
internal val juneteenth =
    holiday("us.juneteenth", HolidayRule.Fixed(6, 19), since = 2021, observed = usFederal, category = publicHoliday)
internal val independence =
    holiday("us.independence", HolidayRule.Fixed(7, 4), observed = usFederal, category = publicHoliday)
internal val thanksgiving =
    holiday(
        "us.thanksgiving",
        HolidayRule.NthWeekday(11, DayOfWeek.THURSDAY, 4),
        since = 1942,
        category = publicHoliday,
    )
internal val christmas =
    holiday("us.christmas", HolidayRule.Fixed(12, 25), observed = usFederal, category = publicHoliday)
internal val inauguration =
    holiday(
        "us.inauguration",
        HolidayRule.Fixed(1, 20),
        yearFilter = YearFilter(4, 1),
        observed = ObservedPolicy.SUNDAY_TO_MONDAY,
    )
internal val election =
    holiday(
        "us.election",
        HolidayRule.Offset(1, HolidayRule.NthWeekday(11, DayOfWeek.MONDAY, 1)),
        yearFilter = YearFilter(2, 0),
    )
internal val blackFriday =
    holiday("us.black_friday", HolidayRule.Offset(1, HolidayRule.NthWeekday(11, DayOfWeek.THURSDAY, 4)))
internal val kwanzaa = holiday("us.kwanzaa", HolidayRule.Fixed(12, 26), durationDays = 7)

internal val usSet =
    pack(
        "us",
        newYear,
        mlk,
        memorial,
        juneteenth,
        independence,
        thanksgiving,
        christmas,
        inauguration,
        election,
        blackFriday,
        kwanzaa,
    )

// --- IFC set, docs/holidays-and-import.md §2.5 and §5 -------------------------------------------------

internal val yearDay = holiday("ifc.year_day", HolidayRule.Ifc.YearDay, category = HolidayCategory.IFC)
internal val leapDay = holiday("ifc.leap_day", HolidayRule.Ifc.LeapDay, category = HolidayCategory.IFC)
internal val solDay = holiday("ifc.sol_day", HolidayRule.Ifc.Regular(IfcMonth.SOL, 1), category = HolidayCategory.IFC)

internal val ifcSet = pack("ifc", yearDay, leapDay, solDay)
