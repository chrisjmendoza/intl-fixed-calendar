package io.github.chrisjmendoza.yearal.core.domain.event

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * When an [Event] first happens and how long it lasts: either [AllDay] or [Timed]. The sealed type
 * makes the storage invariants of `docs/ARCHITECTURE.md` §3.2 unrepresentable instead of checked:
 * `start_minute_of_day` is `NULL` **iff** the event is all-day, and an all-day event has no zone.
 *
 * The start is a Gregorian wall-clock value (CLAUDE.md rule 4): "Events are anchored to Gregorian
 * wall-clock time. Instants are derived and never stored." Instants are derived by [Occurrence].
 *
 * Spec: `docs/ARCHITECTURE.md` §3.2; `docs/adr/0005-events-contract.md` decision 3.
 */
public sealed interface EventTiming {
    /** Local date the event starts on, in the event's own zone (`start_epoch_day`). */
    public val startDate: LocalDate

    /** Minutes after local midnight the event starts at, 0..1439, or **`null` iff all-day**. */
    public val startMinuteOfDay: Int?

    /**
     * Length in minutes (`duration_minutes`): `days × 1440` for [AllDay]; for [Timed], **nominal
     * wall-clock minutes** from start to end, so that a 09:00–10:00 event ends at 10:00 on every
     * occurrence, whatever daylight saving does in between.
     */
    public val durationMinutes: Int

    /**
     * The zone that keeps the wall time fixed (`zone_id`), or `null` for a **floating** event, which
     * follows the device zone. Always `null` for [AllDay].
     */
    public val zone: ZoneId?

    /**
     * An all-day event covering [days] whole local dates from [startDate]. It is always floating: it
     * names calendar dates, not instants, and is never converted between zones (an all-day event on
     * Year Day is on Year Day everywhere).
     *
     * @property startDate the first date.
     * @property days number of dates covered, 1..[MAX_DAYS].
     * @throws IllegalArgumentException if [days] is out of range.
     */
    public data class AllDay(
        override val startDate: LocalDate,
        val days: Int = 1,
    ) : EventTiming {
        init {
            require(days in 1..MAX_DAYS) { "All-day length must be 1..$MAX_DAYS days: $days" }
        }

        /** Always `null`: an all-day event has no start time. */
        override val startMinuteOfDay: Int? get() = null

        /** `days × 1440`. */
        override val durationMinutes: Int get() = days * MINUTES_PER_DAY

        /** Always `null`: all-day events are floating. */
        override val zone: ZoneId? get() = null
    }

    /**
     * An event with a start time. Resolution is one minute; seconds are unrepresentable.
     *
     * @property startDate local date of the start, in [zone] (or floating).
     * @property startMinuteOfDay minutes after local midnight, 0..1439. Never `null`.
     * @property durationMinutes nominal wall-clock length, `≥ 0`. Zero is a point in time.
     * @property zone the zone the wall time is fixed in, or `null` for floating (device zone).
     * @throws IllegalArgumentException if [startMinuteOfDay] is outside 0..1439 or
     *   [durationMinutes] is negative.
     */
    public data class Timed(
        override val startDate: LocalDate,
        override val startMinuteOfDay: Int,
        override val durationMinutes: Int,
        override val zone: ZoneId? = null,
    ) : EventTiming {
        init {
            require(startMinuteOfDay in 0 until MINUTES_PER_DAY) {
                "Start minute of day out of range 0..${MINUTES_PER_DAY - 1}: $startMinuteOfDay"
            }
            require(durationMinutes >= 0) { "Duration must not be negative: $durationMinutes" }
        }

        /** The wall-clock start, [startDate] at [startMinuteOfDay]. */
        public val start: LocalDateTime
            get() = startDate.atTime(LocalTime.ofSecondOfDay(startMinuteOfDay * SECONDS_PER_MINUTE))
    }

    /** Range constants and factories. */
    public companion object {
        /** Minutes in a nominal day, the unit all-day durations are stored in. */
        public const val MINUTES_PER_DAY: Int = 1440

        /** Longest [AllDay.days], the largest whole number of days whose minutes still fit an `Int`. */
        public const val MAX_DAYS: Int = Int.MAX_VALUE / MINUTES_PER_DAY

        private const val SECONDS_PER_MINUTE = 60L
        private const val MINUTES_PER_HOUR = 60

        /**
         * A [Timed] timing from a wall-clock [start] and an exclusive wall-clock [end], which is what an
         * editor's two pickers give. [start] is truncated to the minute and the length to whole minutes.
         *
         * @throws IllegalArgumentException if [end] is before [start] or more than `Int.MAX_VALUE`
         *   minutes after it.
         */
        public fun timed(
            start: LocalDateTime,
            end: LocalDateTime,
            zone: ZoneId? = null,
        ): Timed {
            val from = start.withSecond(0).withNano(0)
            val minutes = Duration.between(from, end).toMinutes()
            require(minutes in 0..Int.MAX_VALUE) { "End must be on or after start, within Int minutes" }
            return Timed(
                startDate = from.toLocalDate(),
                startMinuteOfDay = from.hour * MINUTES_PER_HOUR + from.minute,
                durationMinutes = minutes.toInt(),
                zone = zone,
            )
        }
    }
}
