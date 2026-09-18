package io.github.chrisjmendoza.yearal.core.holidays.internal

import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.domain.holiday.EasterCalendar
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayCategory
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayDefinition
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayRule
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySet
import io.github.chrisjmendoza.yearal.core.domain.holiday.ObservedPolicy
import io.github.chrisjmendoza.yearal.core.domain.holiday.YearFilter
import io.github.chrisjmendoza.yearal.core.holidays.HolidayPackException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Turns a decoded [PackDto] into a [HolidaySet]. Every failure — a schema version this loader does not
 * know, an out-of-range field rejected by a domain constructor, an unparsable table date, an `ifc`
 * rule with both or neither form — becomes a [HolidayPackException] carrying the pack name and, for
 * anything inside a holiday entry, that holiday's id.
 */
internal object PackMapper {
    /** The only schema version this loader reads. */
    const val SUPPORTED_SCHEMA: Int = 1

    fun toHolidaySet(
        packName: String,
        dto: PackDto,
    ): HolidaySet {
        if (dto.schema != SUPPORTED_SCHEMA) {
            throw HolidayPackException(
                packName,
                null,
                "schema ${dto.schema} is not supported (expected $SUPPORTED_SCHEMA)",
            )
        }
        val holidays = dto.holidays.map { toDefinition(packName, it) }
        return wrap(packName, null) {
            HolidaySet(
                id = dto.id,
                region = dto.region,
                name = dto.name,
                sources = dto.sources,
                holidays = holidays,
            )
        }
    }

    private fun toDefinition(
        packName: String,
        dto: HolidayDto,
    ): HolidayDefinition =
        wrap(packName, dto.id) {
            HolidayDefinition(
                id = dto.id,
                name = dto.name,
                rule = toRule(dto.rule),
                since = dto.since,
                until = dto.until,
                yearFilter = dto.yearFilter?.let { YearFilter(mod = it.mod, eq = it.eq) },
                observed = dto.observed.toDomain(),
                durationDays = dto.durationDays,
                startsEveBefore = dto.startsEveBefore,
                approximate = dto.approximate,
                category = dto.category.toDomain(),
            )
        }

    private fun toRule(dto: RuleDto): HolidayRule =
        when (dto) {
            is RuleDto.Fixed -> {
                HolidayRule.Fixed(month = dto.month, day = dto.day)
            }

            is RuleDto.NthWeekday -> {
                HolidayRule.NthWeekday(month = dto.month, weekday = dto.weekday.toDomain(), n = dto.n)
            }

            is RuleDto.WeekdayRelative -> {
                HolidayRule.WeekdayRelative(
                    weekday = dto.weekday.toDomain(),
                    month = dto.month,
                    day = dto.day,
                    direction = dto.direction.toDomain(),
                )
            }

            is RuleDto.Offset -> {
                HolidayRule.Offset(days = dto.days, base = toRule(dto.base))
            }

            is RuleDto.Easter -> {
                HolidayRule.Easter(calendar = dto.calendar.toDomain(), offset = dto.offset)
            }

            is RuleDto.Table -> {
                HolidayRule.Table(dates = toTableDates(dto.dates))
            }

            is RuleDto.Ifc -> {
                toIfcRule(dto)
            }
        }

    private fun toTableDates(dates: Map<String, String>): Map<Int, LocalDate> {
        val result = LinkedHashMap<Int, LocalDate>(dates.size)
        for ((yearText, dateText) in dates) {
            val year = yearText.toIntOrNull() ?: throw IllegalArgumentException("Table key \"$yearText\" is not a year")
            val date =
                try {
                    LocalDate.parse(dateText)
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("Table date \"$dateText\" for year $year is not an ISO-8601 date", e)
                }
            result[year] = date
        }
        return result
    }

    private fun toIfcRule(dto: RuleDto.Ifc): HolidayRule.Ifc {
        val special = dto.special
        val hasMonthDay = dto.month != null || dto.day != null
        return when {
            special != null && hasMonthDay -> {
                throw IllegalArgumentException("An ifc rule has either \"special\" or \"month\" + \"day\", not both")
            }

            special != null -> {
                when (special) {
                    IfcSpecialDto.YEAR_DAY -> HolidayRule.Ifc.YearDay
                    IfcSpecialDto.LEAP_DAY -> HolidayRule.Ifc.LeapDay
                }
            }

            dto.month == null || dto.day == null -> {
                throw IllegalArgumentException("An ifc rule needs \"special\", or both \"month\" and \"day\"")
            }

            else -> {
                require(dto.month in 1..IfcMonth.MONTHS_PER_YEAR) {
                    "IFC month out of range: ${dto.month} (1..${IfcMonth.MONTHS_PER_YEAR})"
                }
                HolidayRule.Ifc.Regular(month = IfcMonth.of(dto.month), day = dto.day)
            }
        }
    }

    private fun WeekdayDto.toDomain(): DayOfWeek =
        when (this) {
            WeekdayDto.MON -> DayOfWeek.MONDAY
            WeekdayDto.TUE -> DayOfWeek.TUESDAY
            WeekdayDto.WED -> DayOfWeek.WEDNESDAY
            WeekdayDto.THU -> DayOfWeek.THURSDAY
            WeekdayDto.FRI -> DayOfWeek.FRIDAY
            WeekdayDto.SAT -> DayOfWeek.SATURDAY
            WeekdayDto.SUN -> DayOfWeek.SUNDAY
        }

    private fun DirectionDto.toDomain(): HolidayRule.WeekdayRelative.Direction =
        when (this) {
            DirectionDto.ON_OR_AFTER -> HolidayRule.WeekdayRelative.Direction.ON_OR_AFTER
            DirectionDto.ON_OR_BEFORE -> HolidayRule.WeekdayRelative.Direction.ON_OR_BEFORE
        }

    private fun EasterCalendarDto.toDomain(): EasterCalendar =
        when (this) {
            EasterCalendarDto.WESTERN -> EasterCalendar.WESTERN
            EasterCalendarDto.ORTHODOX -> EasterCalendar.ORTHODOX
        }

    private fun ObservedDto.toDomain(): ObservedPolicy =
        when (this) {
            ObservedDto.NONE -> ObservedPolicy.NONE
            ObservedDto.US_FEDERAL -> ObservedPolicy.US_FEDERAL
            ObservedDto.NEXT_MONDAY -> ObservedPolicy.NEXT_MONDAY
            ObservedDto.SUNDAY_TO_MONDAY -> ObservedPolicy.SUNDAY_TO_MONDAY
        }

    private fun CategoryDto.toDomain(): HolidayCategory =
        when (this) {
            CategoryDto.PUBLIC -> HolidayCategory.PUBLIC
            CategoryDto.BANK -> HolidayCategory.BANK
            CategoryDto.OBSERVANCE -> HolidayCategory.OBSERVANCE
            CategoryDto.RELIGIOUS -> HolidayCategory.RELIGIOUS
            CategoryDto.IFC -> HolidayCategory.IFC
        }

    /** Runs [block], converting the domain model's `IllegalArgumentException` into a pack error. */
    private inline fun <T> wrap(
        packName: String,
        holidayId: String?,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            throw HolidayPackException(packName, holidayId, e.message ?: "invalid definition", e)
        }
}
