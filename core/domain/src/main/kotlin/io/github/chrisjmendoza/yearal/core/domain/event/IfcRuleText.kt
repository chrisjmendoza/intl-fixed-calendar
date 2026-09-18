package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import java.time.DateTimeException
import java.time.LocalDate

/**
 * The text form of an [IfcRecurrence]: what the `ifc_rule` column stores and what `.ics` export
 * writes as the [ICS_PROPERTY] property (`docs/ARCHITECTURE.md` §3.2). Both the storage layer and
 * the exporter use this one implementation, so there is exactly one spelling of every rule.
 *
 * ## Grammar
 *
 * Segments are separated by `;`, in **this order only**; keys and values are upper-case ASCII; there
 * is no whitespace, no empty segment and no trailing `;`:
 *
 * ```text
 * rule      = "IFC" ";" position [ ";INTERVAL=" number ] [ ";UNTIL=" date / ";COUNT=" number ]
 * position  = "FREQ=YEARLY;MONTH=" month ";DAY=" day
 *           / "FREQ=YEARLY;INTERCALARY=YEAR_DAY"
 *           / "FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=" ( "JUNE_28" / "SKIP" / "SOL_1" )
 *           / "FREQ=MONTHLY;DAY=" day
 * month     = 1..13        ; IFC month number, Sol = 7. NOT a Gregorian month number.
 * day       = 1..28
 * number    = 1..2147483647
 * date      = 8DIGIT       ; Gregorian YYYYMMDD (the RFC 5545 DATE form), years 0001..9999, inclusive
 * ```
 *
 * Numbers are ASCII decimal digits without sign or leading zero. `INTERVAL` defaults to 1 when
 * absent. `COMMONYEAR` is **required** with `LEAP_DAY` and **forbidden** otherwise: data at rest
 * never depends on a default policy. `UNTIL` and `COUNT` are mutually exclusive.
 *
 * ## Guarantees
 *
 * - [format] is canonical: it omits `INTERVAL=1` and writes nothing that [parse] does not read.
 *   `parse(format(rule)) == rule` for every rule.
 * - [parse] is strict. The only accepted text that is not canonical is an explicit `INTERVAL=1`
 *   (`docs/ARCHITECTURE.md` §3.2 shows one); `format(parse(text))` drops it. Everything else —
 *   unknown or repeated keys, another key order, lower case, whitespace, out-of-range values, a
 *   missing or misplaced `COMMONYEAR`, both `UNTIL` and `COUNT`, text longer than [MAX_LENGTH] — is
 *   rejected, so a stored rule is never silently reinterpreted.
 * - The text holds **no event content** and no locale-dependent output (digits are always ASCII).
 *
 * The text names positions only. The anchor date, time, zone and exdates belong to the [Event].
 *
 * Spec: `docs/ARCHITECTURE.md` §3.2; `docs/contracts/Events.md` "IFC rule text";
 * `docs/adr/0005-events-contract.md` decision 1.
 */
public object IfcRuleText {
    /** The non-standard `.ics` property an [IfcRecurrence] is exported under (`docs/ARCHITECTURE.md` §3.2). */
    public const val ICS_PROPERTY: String = "X-IFC-RRULE"

    /**
     * Longest text [parse] looks at, in characters. The longest canonical rule is 92 characters;
     * anything longer is rejected before it is split, because rule text also arrives from restored
     * backups and imported files (`docs/security-and-privacy.md` §6.1).
     */
    public const val MAX_LENGTH: Int = 128

    private const val HEAD = "IFC"
    private const val SEPARATOR = ';'
    private const val FREQ = "FREQ"
    private const val FREQ_YEARLY = "YEARLY"
    private const val FREQ_MONTHLY = "MONTHLY"
    private const val MONTH = "MONTH"
    private const val DAY = "DAY"
    private const val INTERCALARY = "INTERCALARY"
    private const val YEAR_DAY = "YEAR_DAY"
    private const val LEAP_DAY = "LEAP_DAY"
    private const val COMMON_YEAR = "COMMONYEAR"
    private const val INTERVAL = "INTERVAL"
    private const val UNTIL = "UNTIL"
    private const val COUNT = "COUNT"

    private const val DATE_DIGITS = 8
    private const val MAX_NUMBER_DIGITS = 10

    /**
     * The canonical text of [rule], e.g. `IFC;FREQ=YEARLY;MONTH=7;DAY=13`,
     * `IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=JUNE_28;INTERVAL=4` or
     * `IFC;FREQ=MONTHLY;DAY=13;UNTIL=20301231`. Never fails: every [IfcRecurrence] has a text.
     */
    public fun format(rule: IfcRecurrence): String =
        buildString {
            append(HEAD)
            when (rule) {
                is IfcRecurrence.YearlyOnDate -> {
                    segment(FREQ, FREQ_YEARLY)
                    segment(MONTH, rule.month.number.toString())
                    segment(DAY, rule.day.toString())
                }

                is IfcRecurrence.YearlyOnIntercalary -> {
                    segment(FREQ, FREQ_YEARLY)
                    when (val day = rule.day) {
                        IntercalaryDay.YearDay -> {
                            segment(INTERCALARY, YEAR_DAY)
                        }

                        is IntercalaryDay.LeapDay -> {
                            segment(INTERCALARY, LEAP_DAY)
                            segment(COMMON_YEAR, day.commonYearPolicy.name)
                        }
                    }
                }

                is IfcRecurrence.MonthlyOnDay -> {
                    segment(FREQ, FREQ_MONTHLY)
                    segment(DAY, rule.day.toString())
                }
            }
            if (rule.interval != 1) segment(INTERVAL, rule.interval.toString())
            when (val end = rule.end) {
                RecurrenceEnd.Never -> Unit
                is RecurrenceEnd.Until -> segment(UNTIL, basicDate(end.date))
                is RecurrenceEnd.Count -> segment(COUNT, end.count.toString())
            }
        }

    /**
     * Parses rule text written by [format] (see the grammar on [IfcRuleText]).
     *
     * @throws IllegalArgumentException if [text] is not a valid rule. **The message quotes the offending
     *   part of [text]** (at most [MAX_LENGTH] characters), so do not log it when [text] came from an
     *   imported file; use [parseOrNull] there.
     */
    public fun parse(text: String): IfcRecurrence {
        require(text.length <= MAX_LENGTH) { "IFC rule text longer than $MAX_LENGTH characters" }
        val segments = Segments(text.split(SEPARATOR))
        require(segments.next() == HEAD) { "IFC rule text must start with \"$HEAD\": \"$text\"" }

        val rule: (Int, RecurrenceEnd) -> IfcRecurrence =
            when (val freq = segments.value(FREQ)) {
                FREQ_YEARLY -> {
                    if (segments.peekKey() == INTERCALARY) {
                        val day = parseIntercalary(segments)
                        ({ interval, end -> IfcRecurrence.YearlyOnIntercalary(day, interval, end) })
                    } else {
                        val month = IfcMonth.of(number(MONTH, segments.value(MONTH), IfcMonth.MONTHS_PER_YEAR))
                        val day = number(DAY, segments.value(DAY), IfcMonth.DAYS_PER_MONTH)
                        ({ interval, end -> IfcRecurrence.YearlyOnDate(month, day, interval, end) })
                    }
                }

                FREQ_MONTHLY -> {
                    val day = number(DAY, segments.value(DAY), IfcMonth.DAYS_PER_MONTH)
                    ({ interval, end -> IfcRecurrence.MonthlyOnDay(day, interval, end) })
                }

                else -> {
                    throw IllegalArgumentException("Unknown $FREQ in IFC rule text: \"$freq\"")
                }
            }

        val interval =
            if (segments.peekKey() == INTERVAL) number(INTERVAL, segments.value(INTERVAL), Int.MAX_VALUE) else 1
        val end =
            when (segments.peekKey()) {
                UNTIL -> RecurrenceEnd.Until(date(segments.value(UNTIL)))
                COUNT -> RecurrenceEnd.Count(number(COUNT, segments.value(COUNT), Int.MAX_VALUE))
                else -> RecurrenceEnd.Never
            }
        // Whatever is left is an unknown key, a repeated key, or a known key out of order.
        segments.requireExhausted()
        return rule(interval, end)
    }

    /** [parse], but `null` instead of an exception for text that is not a valid rule. */
    public fun parseOrNull(text: String): IfcRecurrence? =
        try {
            parse(text)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun parseIntercalary(segments: Segments): IntercalaryDay =
        when (val kind = segments.value(INTERCALARY)) {
            YEAR_DAY -> {
                IntercalaryDay.YearDay
            }

            LEAP_DAY -> {
                val name = segments.value(COMMON_YEAR)
                val policy =
                    LeapDayPolicy.entries.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException("Unknown $COMMON_YEAR in IFC rule text: \"$name\"")
                IntercalaryDay.LeapDay(policy)
            }

            else -> {
                throw IllegalArgumentException("Unknown $INTERCALARY in IFC rule text: \"$kind\"")
            }
        }

    private fun StringBuilder.segment(
        key: String,
        value: String,
    ) {
        append(SEPARATOR).append(key).append('=').append(value)
    }

    // Int.toString and manual padding keep the digits ASCII; String.format would localise them.
    private fun basicDate(date: LocalDate): String =
        date.year.toString().padStart(4, '0') +
            date.monthValue.toString().padStart(2, '0') +
            date.dayOfMonth.toString().padStart(2, '0')

    /** An unsigned decimal in `1..max` with no leading zero (so also no "0"), ASCII digits only. */
    private fun number(
        key: String,
        text: String,
        max: Int,
    ): Int {
        val wellFormed =
            text.length in 1..MAX_NUMBER_DIGITS && text.all { it in '0'..'9' } && text[0] != '0'
        require(wellFormed) { "$key in IFC rule text is not a positive number: \"$text\"" }
        val value = text.toLong()
        require(value <= max) { "$key in IFC rule text out of range 1..$max: $text" }
        return value.toInt()
    }

    private fun date(text: String): LocalDate {
        require(text.length == DATE_DIGITS && text.all { it in '0'..'9' }) {
            "$UNTIL in IFC rule text is not a YYYYMMDD date: \"$text\""
        }
        val year = text.substring(0, 4).toInt()
        val month = text.substring(4, 6).toInt()
        val day = text.substring(6, 8).toInt()
        return try {
            // Year 0000 is rejected by RecurrenceEnd.Until; impossible dates by LocalDate.
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            throw IllegalArgumentException("$UNTIL in IFC rule text is not a real date: \"$text\"", e)
        }
    }

    /** A cursor over the `;`-separated segments that only ever moves forward. */
    private class Segments(
        private val parts: List<String>,
    ) {
        private var index = 0

        fun next(): String? = parts.getOrNull(index++)

        /** The key of the next segment, or `null` at the end or when the segment has no `=`. */
        fun peekKey(): String? = parts.getOrNull(index)?.substringBefore('=', missingDelimiterValue = "")

        /** Consumes the next segment, which must be exactly `key=value`, and returns the value. */
        fun value(key: String): String {
            val segment = parts.getOrNull(index)
            require(segment != null && segment.startsWith("$key=")) {
                "Expected $key in IFC rule text but found ${segment?.let { "\"$it\"" } ?: "the end"}"
            }
            index++
            return segment.substring(key.length + 1)
        }

        fun requireExhausted() {
            require(index >= parts.size) { "Unexpected segment in IFC rule text: \"${parts[index]}\"" }
        }
    }
}
