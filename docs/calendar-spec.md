# International Fixed Calendar — Calendar Specification

Status: **authoritative** for the conversion library and its unit tests.
Spec date: 2026-09-17 (IFC: September 8, 2026).

This document defines the International Fixed Calendar (IFC) as implemented by this app: the rules, the
Gregorian ↔ IFC conversion algorithms, a proposed Kotlin type model, reference tables, machine-generated test
vectors, and the design decisions the app must settle. Where this document says **MUST**, the implementation
and tests are expected to conform.

Terminology used throughout:

| Term | Meaning |
|---|---|
| Gregorian date | A date in the proleptic Gregorian (ISO-8601) calendar, i.e. `java.time.LocalDate`. |
| Regular day | One of the 364 IFC days that belong to a month (13 months × 28 days). |
| Intercalary day | A day that belongs to no week: **Leap Day** or **Year Day**. Also called "blank" or "floating" days. |
| Nominal weekday | The weekday the IFC assigns to a regular day. Fixed forever by the day-of-month. |
| Actual weekday | The real-world weekday (the unbroken 7-day cycle) of that same physical day. |
| Day of year (`N`) | Ordinal day within the year, 1-based. Identical in both calendars. |
| Regular-day index (`r`) | Ordinal of a regular day among the regular days only, 1..364. |

---

## 1. Overview and brief history

The International Fixed Calendar (also called the **Cotsworth plan**, the **Eastman plan**, or the
**13-month calendar**) is a proposed reform of the Gregorian calendar. It divides the year into 13 months of
exactly 28 days (4 whole weeks) each, plus one day per year (two in leap years) that stands outside the week.
Because 13 × 28 = 364 = 52 × 7, every date falls on the same weekday every year: the calendar is *perennial*.
The IFC year is coextensive with the Gregorian year — same year number, same start, same leap-year rule — so
it is a re-labelling of the days inside each Gregorian year, not a different year count.

- **Moses B. Cotsworth** (1859–1943), a British accountant and statistician working with railway statistics,
  devised the plan to make monthly figures comparable; he presented it in 1902 and explained it in his book
  *The Rational Almanac* (sources date the book 1902 or 1905). Precursors include Hugh Jones's "Georgian
  calendar" (1745) and Auguste Comte's Positivist calendar (1849).
- Cotsworth founded the International Almanak Reform League (1912), relaunched as the **International Fixed
  Calendar League** in 1922–23. The **League of Nations** took up calendar reform in the 1920s; according to
  Wikipedia its committee selected the 13-month plan as the best of 130 proposals, and an international
  conference on calendar reform was held in October 1931. The plan never won final approval (1937), largely
  because the intercalary days break the continuous seven-day week; religious objections (e.g. from Chief
  Rabbi Joseph Hertz) were prominent. The League ceased operations shortly afterwards.
- **George Eastman** became the plan's main backer in the mid-1920s and instituted it at the **Eastman Kodak
  Company in 1928**, where a 13-period calendar stayed in internal use **until 1989** (the date given by most
  sources, including the Wikipedia IFC article; the Wikipedia biography of Cotsworth says 1982).
- No country has ever adopted the IFC.

Sources:

- Wikipedia, "International Fixed Calendar": <https://en.wikipedia.org/wiki/International_Fixed_Calendar>
- Wikipedia, "Moses B. Cotsworth": <https://en.wikipedia.org/wiki/Moses_B._Cotsworth>
- Yorkshire Philosophical Society, "Moses B. Cotsworth": <https://www.ypsyork.org/resources/yorkshire-scientists-and-innovators/moses-b-cotsworth/>
- Library and Archives Canada blog, "No Leap of Faith": <https://thediscoverblog.com/2024/02/29/no-leap-of-faith/>
- PetaPixel, "Kodak Used a Calendar That Had 13 Months": <https://petapixel.com/2012/06/11/kodak-used-a-calendar-that-had-13-months/>
- 13cal.net, history of the 13-month calendar: <https://13cal.net/13-month-calendar-history>
- Wikipedia, "Perennial calendar": <https://en.wikipedia.org/wiki/Perennial_calendar>

---

## 2. Formal rules

### 2.1 Year

- **R1.** The IFC year number equals the Gregorian year number. IFC January 1 is Gregorian January 1. The IFC
  year ends on the same day as the Gregorian year (Gregorian December 31).
- **R2.** Leap years follow the Gregorian rule: a year is a leap year if it is divisible by 4, except years
  divisible by 100, unless also divisible by 400. (1900 and 2100 are common years; 2000 is a leap year.)
- **R3.** A common year has 365 days = 364 regular days + Year Day. A leap year has 366 days = 364 regular
  days + Leap Day + Year Day.

### 2.2 Months

- **R4.** There are 13 months of exactly 28 days, in this order:

  | # | Month | # | Month |
  |---|---|---|---|
  | 1 | January | 8 | July |
  | 2 | February | 9 | August |
  | 3 | March | 10 | September |
  | 4 | April | 11 | October |
  | 5 | May | 12 | November |
  | 6 | June | 13 | December |
  | 7 | **Sol** | | |

  Sol sits between June and July. Consequently **month numbers 8–13 differ from Gregorian month numbers**
  (IFC July is month 8, IFC December is month 13). See §7.3 for the formatting consequences.

### 2.3 Weeks and nominal weekdays

- **R5.** Every month starts on a (nominal) Sunday and ends on a (nominal) Saturday, and consists of exactly
  four weeks. Every month has the same layout:

  | Sun | Mon | Tue | Wed | Thu | Fri | Sat |
  |---|---|---|---|---|---|---|
  | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
  | 8 | 9 | 10 | 11 | 12 | 13 | 14 |
  | 15 | 16 | 17 | 18 | 19 | 20 | 21 |
  | 22 | 23 | 24 | 25 | 26 | 27 | 28 |

- **R6.** The nominal weekday of a regular day depends only on its day-of-month `d`:
  `index = (d − 1) mod 7`, with 0 = Sunday, 1 = Monday, … 6 = Saturday. (The 13th is always a Friday.)
- **R7.** The year has exactly 52 nominal weeks. Week `w` (1..52) of a regular day with regular-day index `r`
  is `w = (r − 1) div 7 + 1`; equivalently `w = (month − 1) × 4 + (d − 1) div 7 + 1`.

### 2.4 Intercalary ("floating") days

- **R8. Year Day.** Occurs every year. It is the last day of the year: it follows December 28 and precedes
  January 1 of the next year. It is Gregorian **December 31**; day of year 365 (common) or 366 (leap). It
  belongs to **no week and has no nominal weekday**. It sits between Saturday December 28 and Sunday January 1.
  By convention it is attached to December and may be written "December 29".
- **R9. Leap Day.** Occurs only in leap years. It follows June 28 and precedes Sol 1. It is Gregorian
  **June 17**; day of year 169. It belongs to **no week and has no nominal weekday**. It sits between Saturday
  June 28 and Sunday Sol 1. By convention it is attached to June and is written "June 29".
- **R10.** Both intercalary days belong to their IFC year (R1) and are counted in the day of year. They are
  not counted in the regular-day index, the week number, or the nominal weekday cycle.

Note that the IFC does **not** intercalate where the Gregorian calendar does. Gregorian February 29 is an
ordinary IFC regular day (March 4); the IFC's extra day comes on June 17. Consequences:

| Gregorian date range | Relationship to IFC date |
|---|---|
| Jan 1 – Feb 28 | Same IFC date every year. |
| Feb 29 – Jun 17 | In leap years each Gregorian date maps to an IFC date **one day later** than in common years (e.g. Mar 1 → March 4 in common years, March 5 in leap years). |
| Jun 18 – Dec 31 | Same IFC date every year (e.g. Dec 25 is always December 23). |

Seen from the IFC side: IFC January 1 – March 3 and Sol 1 – Year Day always fall on the same Gregorian dates;
IFC March 4 – June 28 fall one Gregorian day **earlier** in leap years (see §5).

### 2.5 Source notes and disputed points

- All of R1–R9 match the Wikipedia article's "Rules" section, which explicitly writes the Leap Day as
  "June 29 – between Saturday June 28 and Sunday Sol 1" and places Year Day "after December 28, i.e. equal to
  December 31 Gregorian".
- "December 29" for Year Day is a convention by analogy with June 29; Wikipedia's article does not itself use
  that label. This spec adopts both "29" labels for numeric notation only (§7.3).
- Historical dates vary between sources (book 1902 vs 1905; League 1922 vs 1923; Kodak end 1989 vs 1982).
  These do not affect the calendar rules.
- Other 13-month proposals place the leap day elsewhere (usually at year end). Those are different calendars
  (§8), not the IFC.

---

## 3. Conversion algorithms

Conventions: `div` is integer division and `mod` the remainder, applied here only to non-negative operands.
`isLeap(y)` is the Gregorian rule R2 (`java.time.Year.isLeap(y)`). `N` is the Gregorian day of year
(`LocalDate.getDayOfYear()`, 1-based). All constants derive from 6 × 28 = 168 (last regular day before the
Leap Day slot) and 13 × 28 = 364.

### 3.1 Gregorian → IFC

```text
function gregorianToIfc(g: GregorianDate) -> IfcDate
    y    := g.year
    N    := g.dayOfYear                  // 1..365 or 1..366
    leap := isLeap(y)

    if N == (leap ? 366 : 365):  return YearDay(y)
    if leap and N == 169:        return LeapDay(y)

    r := (leap and N > 169) ? N - 1 : N  // regular-day index, 1..364
    month := (r - 1) div 28 + 1          // 1..13
    day   := (r - 1) mod 28 + 1          // 1..28
    return Regular(y, month, day)
```

This function is total: every Gregorian date in the supported range maps to exactly one IFC date.

### 3.2 IFC → Gregorian

```text
function ifcDayOfYear(d: IfcDate) -> int
    leap := isLeap(d.year)
    case d of
        YearDay(y):            return leap ? 366 : 365
        LeapDay(y):            require leap                  // else: invalid date
                               return 169
        Regular(y, month, day):
            r := (month - 1) * 28 + day                      // 1..364
            return (leap and r > 168) ? r + 1 : r            // skip over the Leap Day slot

function ifcToGregorian(d: IfcDate) -> GregorianDate
    return GregorianDate.ofYearDay(d.year, ifcDayOfYear(d))  // LocalDate.ofYearDay
```

Equivalent statement of the leap adjustment: in a leap year, regular days in Sol through December
(`month >= 7`) have `N = r + 1`; January through June have `N = r`.

### 3.3 Validation rules

An IFC date is valid if and only if all of the following hold. Constructors and parsers **MUST** reject
anything else (throw `DateTimeException`, mirroring `java.time`).

| # | Rule |
|---|---|
| V1 | `year` is within the supported range (§7.1: 1..9999). |
| V2 | Regular day: `month` in 1..13 and `day` in 1..28. There is no day 29, 30 or 31 in any month as a regular day. |
| V3 | Leap Day: valid only if `isLeap(year)`. `LeapDay(2025)`, `LeapDay(1900)`, `LeapDay(2100)` are invalid. |
| V4 | Year Day: valid in every supported year. |
| V5 | Numeric/pseudo-field form (§7.3): `(month=6, day=29)` is accepted only in leap years and means Leap Day; `(month=13, day=29)` is accepted in every year and means Year Day; `day=29` with any other month is invalid; `day=0` and `day>=30` are always invalid. |
| V6 | No lenient/overflow resolution: "Sol 29" is an error, not July 1. (Clamping applies only to date *arithmetic*, §7.7.) |

### 3.4 Invariants (property tests)

For every Gregorian date `g` in the supported range and every valid IFC date `i`:

1. `ifcToGregorian(gregorianToIfc(g)) == g` and `gregorianToIfc(ifcToGregorian(i)) == i` (bijection per year).
2. `gregorianToIfc(g).year == g.year` and `ifcDayOfYear(gregorianToIfc(g)) == g.dayOfYear`.
3. Ordering is preserved: `g1 < g2` ⇔ `ifc(g1) < ifc(g2)` comparing by `(year, dayOfYear)`, and also comparing
   by the numeric triple `(year, monthNumber, dayOfMonth)` with intercalary days as day 29 (§7.3).
4. Each year has exactly 364 regular days, exactly one Year Day, and exactly one Leap Day iff leap.
5. Jan 1 ↦ January 1; Dec 31 ↦ Year Day; Dec 30 ↦ December 28; Jun 18 ↦ Sol 1 — in every year.
6. Jun 17 ↦ Leap Day in leap years and ↦ June 28 in common years.
7. The nominal weekday of day-of-month 13 is Friday; of 1 is Sunday; of 28 is Saturday.
8. The actual weekday of an IFC date equals `ifcToGregorian(i).dayOfWeek` — it is **never** derived from
   the nominal weekday.

---

## 4. Proposed Kotlin type model (design sketch)

Design sketch only — names and signatures may change during implementation, the semantics may not. Pure
Kotlin/JVM, depends only on `java.time` (available natively at minSdk 26; no desugaring needed). No Android
imports, so the module is unit-testable on the JVM.

```kotlin
package ifc.core   // placeholder

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.Year

/** The 13 IFC months in calendar order. SOL is month 7; JULY is month 8; DECEMBER is month 13. */
enum class IfcMonth {
    JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, SOL,
    JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER;

    /** 1..13. NOT interchangeable with java.time.Month.value for months after June. */
    val number: Int get() = ordinal + 1

    /** Gregorian month of the same *name* (for localized display names), or null for SOL. */
    val gregorianNamesake: Month?
        get() = when (this) {
            SOL -> null
            else -> Month.valueOf(name)
        }

    companion object {
        const val DAYS_PER_MONTH = 28
        fun of(number: Int): IfcMonth =
            entries.getOrNull(number - 1) ?: throw DateTimeException("Invalid IFC month: $number")
    }
}

/**
 * A date in the International Fixed Calendar. Exactly one of three shapes:
 * a regular day inside a month, or one of the two intercalary days that belong to no week.
 * Immutable value type; total order by (year, dayOfYear).
 */
sealed interface IfcDate : Comparable<IfcDate> {
    val year: Int

    /** 1..365/366. Always equal to toLocalDate().dayOfYear. */
    val dayOfYear: Int

    /** Month number 1..13. Intercalary days report the month they are attached to: LeapDay -> 6, YearDay -> 13. */
    val monthNumber: Int

    /** 1..28 for regular days; 29 for intercalary days ("June 29", "December 29"). */
    val dayOfMonth: Int

    /**
     * The weekday the IFC assigns to this date: fixed by dayOfMonth, identical every month and year.
     * NULL for intercalary days, which belong to no week.
     * This is NOT the real-world weekday. See [actualDayOfWeek].
     */
    val nominalDayOfWeek: DayOfWeek?

    /** IFC week of year 1..52; NULL for intercalary days. Not an ISO-8601 week number. */
    val weekOfYear: Int?

    /** 13-week quarter 1..4. Intercalary days are attached to the preceding quarter (LeapDay -> 2, YearDay -> 4). */
    val quarter: Int

    val isIntercalary: Boolean

    /** The same physical day in the Gregorian (ISO) calendar. */
    fun toLocalDate(): LocalDate = LocalDate.ofYearDay(year, dayOfYear)

    /** The real-world weekday of this physical day (continuous 7-day cycle). Never null. */
    val actualDayOfWeek: DayOfWeek get() = toLocalDate().dayOfWeek

    override fun compareTo(other: IfcDate): Int =
        compareValuesBy(this, other, IfcDate::year, IfcDate::dayOfYear)

    /** One of the 364 days that belong to a month and a week. */
    data class Regular(
        override val year: Int,
        val month: IfcMonth,
        override val dayOfMonth: Int,            // 1..28
    ) : IfcDate {
        init {
            requireYear(year)
            if (dayOfMonth !in 1..28) throw DateTimeException("Invalid IFC day of month: $dayOfMonth")
        }
        override val monthNumber get() = month.number
        /** Regular-day index 1..364 (intercalary days not counted). */
        val regularDayIndex: Int get() = (month.number - 1) * 28 + dayOfMonth
        override val dayOfYear: Int
            get() = regularDayIndex.let { r -> if (Year.isLeap(year.toLong()) && r > 168) r + 1 else r }
        override val nominalDayOfWeek: DayOfWeek
            get() = NOMINAL_WEEK[(dayOfMonth - 1) % 7]          // 1 -> SUNDAY ... 7 -> SATURDAY
        override val weekOfYear: Int get() = (regularDayIndex - 1) / 7 + 1
        override val quarter: Int get() = (regularDayIndex - 1) / 91 + 1
        override val isIntercalary get() = false
    }

    /** The day after June 28 in leap years (Gregorian June 17). No week, no weekday. */
    data class LeapDay(override val year: Int) : IfcDate {
        init {
            requireYear(year)
            if (!Year.isLeap(year.toLong())) throw DateTimeException("Leap Day does not exist in common year $year")
        }
        override val dayOfYear get() = 169
        override val monthNumber get() = 6
        override val dayOfMonth get() = 29
        override val nominalDayOfWeek: DayOfWeek? get() = null
        override val weekOfYear: Int? get() = null
        override val quarter get() = 2
        override val isIntercalary get() = true
    }

    /** The day after December 28, last day of every year (Gregorian December 31). No week, no weekday. */
    data class YearDay(override val year: Int) : IfcDate {
        init { requireYear(year) }
        override val dayOfYear get() = if (Year.isLeap(year.toLong())) 366 else 365
        override val monthNumber get() = 13
        override val dayOfMonth get() = 29
        override val nominalDayOfWeek: DayOfWeek? get() = null
        override val weekOfYear: Int? get() = null
        override val quarter get() = 4
        override val isIntercalary get() = true
    }

    companion object {
        const val MIN_YEAR = 1
        const val MAX_YEAR = 9999
        internal val NOMINAL_WEEK = listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
        )

        /** Gregorian -> IFC (§3.1). Total over the supported range. */
        fun from(date: LocalDate): IfcDate { /* §3.1 */ TODO() }

        /** From the numeric pseudo-fields (§7.3): day 29 resolves to LeapDay / YearDay, else validation error. */
        fun of(year: Int, monthNumber: Int, dayOfMonth: Int): IfcDate { /* §3.3 V5 */ TODO() }

        fun ofYearDay(year: Int, dayOfYear: Int): IfcDate = from(LocalDate.ofYearDay(year, dayOfYear))

        /** "Today" is a function of a clock AND a zone (§7.8); never call LocalDate.now() without them in library code. */
        fun now(clock: java.time.Clock): IfcDate = from(LocalDate.now(clock))

        internal fun requireYear(year: Int) {
            if (year !in MIN_YEAR..MAX_YEAR) throw DateTimeException("IFC year out of range: $year")
        }
    }
}

fun LocalDate.toIfcDate(): IfcDate = IfcDate.from(this)
```

Why a sealed interface rather than a `(year, month, day)` triple with a magic day 29: the intercalary days
have *no* weekday and *no* week, and the type system should force every `when` in the UI (grid rendering,
formatting, accessibility text) to handle them. The pseudo-fields `monthNumber`/`dayOfMonth = 29` exist only
for numeric formatting, parsing, sorting and month arithmetic.

### 4.1 Nominal weekday vs. actual weekday — the critical UX distinction

The IFC achieves its perpetual weekdays by taking Year Day (and Leap Day) **out of the week**. The real world
does not do this: the seven-day cycle runs unbroken through December 31 and June 17. Therefore the IFC's
weekdays drift against the real ones by one day after every intercalary day, and **in most years an IFC
"Sunday" is not a real Sunday**.

Worked example (today): Gregorian **Thursday**, 17 September 2026 is IFC **September 8, 2026**, whose nominal
weekday is **Sunday**. A user who reads "Sunday" and skips work has been harmed by the app.

Facts the implementation can rely on:

- Within a single IFC month the offset between nominal and actual weekday is **constant**, because
  intercalary days only ever fall *between* months. Within a year the offset is constant from January 1 to
  June 28, and (in leap years only) shifts by one from Sol 1 onward.
- The offset for the first half of year `y` is determined by the actual weekday of Gregorian January 1 of `y`
  (nominal Sunday). It advances by 1 each common year and by 2 each leap year.
- The two coincide for a whole common year only when Gregorian January 1 is a Sunday (years 2000–2060 with
  January 1 on a Sunday: 2006, 2012 (leap), 2017, 2023, 2034, 2040 (leap), 2045, 2051; in the leap years among them the match ends at Leap Day).
- Despite these regularities, code **MUST** obtain the actual weekday from `toLocalDate().dayOfWeek` and the
  nominal weekday from `dayOfMonth`; it must never derive one from the other.

Requirements for the app:

1. The API has **no bare `dayOfWeek`** property on IFC types. Only `nominalDayOfWeek` (nullable) and
   `actualDayOfWeek` (never null). Code review should reject any use that blurs them.
2. Everything tied to the real world — the "today" highlight, events, reminders, alarms, "weekend" shading
   derived from the user's working week — uses the **actual** weekday / the Gregorian `LocalDate`.
3. The IFC month grid's column headers are **nominal** weekdays and are labelled as such. Because the offset is
   constant within a month, the grid **should show a second header row with the actual weekdays** for that
   month/year (e.g. nominal `Sun Mon Tue …` above actual `Thu Fri Sat …` for September 2026).
4. Any date detail view shows both, explicitly labelled, e.g.
   "IFC weekday: Sunday · Actual weekday: Thursday · Gregorian: Thu, Sep 17, 2026".
5. Intercalary days display "no IFC weekday" (not blank, not "Sunday"), plus their actual weekday.
6. Default long-form date strings do **not** include the nominal weekday unless it is labelled (§7.3).
7. Accessibility (TalkBack) strings must speak the label, e.g. "IFC Sunday, actual Thursday".

---

## 5. Month boundary tables

Machine-generated by the reference script (§6.1). Gregorian start/end of each IFC month.

### 5.1 Common year (365 days) — e.g. 2026

| # | IFC month | Gregorian start | Gregorian end | Days |
|---|---|---|---|---|
| 1 | January | Jan 1 | Jan 28 | 28 |
| 2 | February | Jan 29 | Feb 25 | 28 |
| 3 | March | Feb 26 | Mar 25 | 28 |
| 4 | April | Mar 26 | Apr 22 | 28 |
| 5 | May | Apr 23 | May 20 | 28 |
| 6 | June | May 21 | Jun 17 | 28 |
| 7 | Sol | Jun 18 | Jul 15 | 28 |
| 8 | July | Jul 16 | Aug 12 | 28 |
| 9 | August | Aug 13 | Sep 9 | 28 |
| 10 | September | Sep 10 | Oct 7 | 28 |
| 11 | October | Oct 8 | Nov 4 | 28 |
| 12 | November | Nov 5 | Dec 2 | 28 |
| 13 | December | Dec 3 | Dec 30 | 28 |
| — | **Year Day** (December 29) | Dec 31 | Dec 31 | 1 |

### 5.2 Leap year (366 days) — e.g. 2024

| # | IFC month | Gregorian start | Gregorian end | Days |
|---|---|---|---|---|
| 1 | January | Jan 1 | Jan 28 | 28 |
| 2 | February | Jan 29 | Feb 25 | 28 |
| 3 | March | Feb 26 | Mar 24 | 28 |
| 4 | April | Mar 25 | Apr 21 | 28 |
| 5 | May | Apr 22 | May 19 | 28 |
| 6 | June | May 20 | Jun 16 | 28 |
| — | **Leap Day** (June 29) | Jun 17 | Jun 17 | 1 |
| 7 | Sol | Jun 18 | Jul 15 | 28 |
| 8 | July | Jul 16 | Aug 12 | 28 |
| 9 | August | Aug 13 | Sep 9 | 28 |
| 10 | September | Sep 10 | Oct 7 | 28 |
| 11 | October | Oct 8 | Nov 4 | 28 |
| 12 | November | Nov 5 | Dec 2 | 28 |
| 13 | December | Dec 3 | Dec 30 | 28 |
| — | **Year Day** (December 29) | Dec 31 | Dec 31 | 1 |

Differences between the two tables are confined to the *end* of March and all of April, May and June (one
Gregorian day earlier in leap years), plus the existence of Leap Day. January, February, the start of March,
and everything from Sol 1 onward are identical.

### 5.3 Quarters (13 nominal weeks = 91 regular days each)

Common year (2026):

| Quarter | IFC weeks | IFC start | IFC end | Gregorian start | Gregorian end |
|---|---|---|---|---|---|
| Q1 | 1–13 | January 1 | April 7 | Jan 1 | Apr 1 |
| Q2 | 14–26 | April 8 | Sol 14 | Apr 2 | Jul 1 |
| Q3 | 27–39 | Sol 15 | September 21 | Jul 2 | Sep 30 |
| Q4 | 40–52 | September 22 | December 28 | Oct 1 | Dec 30 |

Leap year (2024):

| Quarter | IFC weeks | IFC start | IFC end | Gregorian start | Gregorian end |
|---|---|---|---|---|---|
| Q1 | 1–13 | January 1 | April 7 | Jan 1 | Mar 31 |
| Q2 | 14–26 | April 8 | Sol 14 | Apr 1 | Jul 1 |
| Q3 | 27–39 | Sol 15 | September 21 | Jul 2 | Sep 30 |
| Q4 | 40–52 | September 22 | December 28 | Oct 1 | Dec 30 |

Intercalary days are outside the 13-week quarters; for reporting they are attached to the preceding quarter
(Leap Day → Q2: it falls between June 28 and Sol 1, inside Q2's span, making Q2 92 days long in leap years; Year Day → Q4, making Q4 always 92 days long). Quarter boundaries do
not coincide with month boundaries because 13 is prime (each quarter is 3 months + 1 week).

---

## 6. Test vectors

### 6.1 Provenance

**All tables in §5, §6 and §9 were machine-generated, not hand-computed.** A Python 3 reference script
implemented both directions (§3.1, §3.2) and the validation rules, then:

- ran a full round-trip check over **every day of 1899-01-01 … 2101-12-31 (74,144 days)**: Gregorian → IFC →
  Gregorian is the identity; IFC → Gregorian → IFC is the identity over every valid IFC date (74,144 dates);
  the IFC year and day of year equal the Gregorian ones; the numeric triple `(year, month, day)` is strictly
  increasing day by day; every 13th is a nominal Friday; `LeapDay` is rejected in every common year;
- repeated the same check over the entire supported range **0001-01-01 … 9999-12-31 (3,652,059 days)**;
- cross-checked the formulas against an independent *definitional* construction (enumerate the IFC dates of
  each year in calendar order — 28 days × 13 months, Leap Day inserted after June in leap years, Year Day
  last — and pair them with consecutive Gregorian days from January 1) for all years 1–9999, and against the
  month table in the Wikipedia article.

**Result: all checks passed with zero mismatches.** Python's `datetime.date` uses the same proleptic
Gregorian calendar as `java.time.LocalDate`, so these vectors apply unchanged to the Kotlin implementation.
The Kotlin test suite should (a) assert every row below in both directions and (b) re-run the exhaustive
round-trip over 1..9999 as a property test (it takes well under a second on the JVM).

Column notes: "IFC numeric" is the `YYYY-MM-DD` form of §7.3 (months 01–13; Leap Day = `06-29`, Year Day =
`13-29`). "Real weekday" is the actual Gregorian weekday; "IFC nominal weekday" and "IFC week" are "—" for
intercalary days (null in the API). Total vectors: **105** (27 + 28 + 50; a few dates
appear in more than one table on purpose).

### 6.2 Every month boundary — common year 2026

| Gregorian (ISO) | IFC | IFC numeric | Day of year | Real weekday | IFC nominal weekday | IFC week | Note |
|---|---|---|---|---|---|---|---|
| 2026-01-01 | January 1, 2026 | `2026-01-01` | 1 | Thursday | Sunday | 1 | first day of January |
| 2026-01-28 | January 28, 2026 | `2026-01-28` | 28 | Wednesday | Saturday | 4 | last day of January |
| 2026-01-29 | February 1, 2026 | `2026-02-01` | 29 | Thursday | Sunday | 5 | first day of February |
| 2026-02-25 | February 28, 2026 | `2026-02-28` | 56 | Wednesday | Saturday | 8 | last day of February |
| 2026-02-26 | March 1, 2026 | `2026-03-01` | 57 | Thursday | Sunday | 9 | first day of March |
| 2026-03-25 | March 28, 2026 | `2026-03-28` | 84 | Wednesday | Saturday | 12 | last day of March |
| 2026-03-26 | April 1, 2026 | `2026-04-01` | 85 | Thursday | Sunday | 13 | first day of April |
| 2026-04-22 | April 28, 2026 | `2026-04-28` | 112 | Wednesday | Saturday | 16 | last day of April |
| 2026-04-23 | May 1, 2026 | `2026-05-01` | 113 | Thursday | Sunday | 17 | first day of May |
| 2026-05-20 | May 28, 2026 | `2026-05-28` | 140 | Wednesday | Saturday | 20 | last day of May |
| 2026-05-21 | June 1, 2026 | `2026-06-01` | 141 | Thursday | Sunday | 21 | first day of June |
| 2026-06-17 | June 28, 2026 | `2026-06-28` | 168 | Wednesday | Saturday | 24 | last day of June |
| 2026-06-18 | Sol 1, 2026 | `2026-07-01` | 169 | Thursday | Sunday | 25 | first day of Sol |
| 2026-07-15 | Sol 28, 2026 | `2026-07-28` | 196 | Wednesday | Saturday | 28 | last day of Sol |
| 2026-07-16 | July 1, 2026 | `2026-08-01` | 197 | Thursday | Sunday | 29 | first day of July |
| 2026-08-12 | July 28, 2026 | `2026-08-28` | 224 | Wednesday | Saturday | 32 | last day of July |
| 2026-08-13 | August 1, 2026 | `2026-09-01` | 225 | Thursday | Sunday | 33 | first day of August |
| 2026-09-09 | August 28, 2026 | `2026-09-28` | 252 | Wednesday | Saturday | 36 | last day of August |
| 2026-09-10 | September 1, 2026 | `2026-10-01` | 253 | Thursday | Sunday | 37 | first day of September |
| 2026-10-07 | September 28, 2026 | `2026-10-28` | 280 | Wednesday | Saturday | 40 | last day of September |
| 2026-10-08 | October 1, 2026 | `2026-11-01` | 281 | Thursday | Sunday | 41 | first day of October |
| 2026-11-04 | October 28, 2026 | `2026-11-28` | 308 | Wednesday | Saturday | 44 | last day of October |
| 2026-11-05 | November 1, 2026 | `2026-12-01` | 309 | Thursday | Sunday | 45 | first day of November |
| 2026-12-02 | November 28, 2026 | `2026-12-28` | 336 | Wednesday | Saturday | 48 | last day of November |
| 2026-12-03 | December 1, 2026 | `2026-13-01` | 337 | Thursday | Sunday | 49 | first day of December |
| 2026-12-30 | December 28, 2026 | `2026-13-28` | 364 | Wednesday | Saturday | 52 | last day of December |
| 2026-12-31 | Year Day 2026 | `2026-13-29` | 365 | Thursday | — | — | Year Day |

### 6.3 Every month boundary — leap year 2024

| Gregorian (ISO) | IFC | IFC numeric | Day of year | Real weekday | IFC nominal weekday | IFC week | Note |
|---|---|---|---|---|---|---|---|
| 2024-01-01 | January 1, 2024 | `2024-01-01` | 1 | Monday | Sunday | 1 | first day of January |
| 2024-01-28 | January 28, 2024 | `2024-01-28` | 28 | Sunday | Saturday | 4 | last day of January |
| 2024-01-29 | February 1, 2024 | `2024-02-01` | 29 | Monday | Sunday | 5 | first day of February |
| 2024-02-25 | February 28, 2024 | `2024-02-28` | 56 | Sunday | Saturday | 8 | last day of February |
| 2024-02-26 | March 1, 2024 | `2024-03-01` | 57 | Monday | Sunday | 9 | first day of March |
| 2024-03-24 | March 28, 2024 | `2024-03-28` | 84 | Sunday | Saturday | 12 | last day of March |
| 2024-03-25 | April 1, 2024 | `2024-04-01` | 85 | Monday | Sunday | 13 | first day of April |
| 2024-04-21 | April 28, 2024 | `2024-04-28` | 112 | Sunday | Saturday | 16 | last day of April |
| 2024-04-22 | May 1, 2024 | `2024-05-01` | 113 | Monday | Sunday | 17 | first day of May |
| 2024-05-19 | May 28, 2024 | `2024-05-28` | 140 | Sunday | Saturday | 20 | last day of May |
| 2024-05-20 | June 1, 2024 | `2024-06-01` | 141 | Monday | Sunday | 21 | first day of June |
| 2024-06-16 | June 28, 2024 | `2024-06-28` | 168 | Sunday | Saturday | 24 | last day of June |
| 2024-06-17 | Leap Day 2024 | `2024-06-29` | 169 | Monday | — | — | Leap Day |
| 2024-06-18 | Sol 1, 2024 | `2024-07-01` | 170 | Tuesday | Sunday | 25 | first day of Sol |
| 2024-07-15 | Sol 28, 2024 | `2024-07-28` | 197 | Monday | Saturday | 28 | last day of Sol |
| 2024-07-16 | July 1, 2024 | `2024-08-01` | 198 | Tuesday | Sunday | 29 | first day of July |
| 2024-08-12 | July 28, 2024 | `2024-08-28` | 225 | Monday | Saturday | 32 | last day of July |
| 2024-08-13 | August 1, 2024 | `2024-09-01` | 226 | Tuesday | Sunday | 33 | first day of August |
| 2024-09-09 | August 28, 2024 | `2024-09-28` | 253 | Monday | Saturday | 36 | last day of August |
| 2024-09-10 | September 1, 2024 | `2024-10-01` | 254 | Tuesday | Sunday | 37 | first day of September |
| 2024-10-07 | September 28, 2024 | `2024-10-28` | 281 | Monday | Saturday | 40 | last day of September |
| 2024-10-08 | October 1, 2024 | `2024-11-01` | 282 | Tuesday | Sunday | 41 | first day of October |
| 2024-11-04 | October 28, 2024 | `2024-11-28` | 309 | Monday | Saturday | 44 | last day of October |
| 2024-11-05 | November 1, 2024 | `2024-12-01` | 310 | Tuesday | Sunday | 45 | first day of November |
| 2024-12-02 | November 28, 2024 | `2024-12-28` | 337 | Monday | Saturday | 48 | last day of November |
| 2024-12-03 | December 1, 2024 | `2024-13-01` | 338 | Tuesday | Sunday | 49 | first day of December |
| 2024-12-30 | December 28, 2024 | `2024-13-28` | 365 | Monday | Saturday | 52 | last day of December |
| 2024-12-31 | Year Day 2024 | `2024-13-29` | 366 | Tuesday | — | — | Year Day |

### 6.4 Special cases

Days around Leap Day in leap and common years, Dec 30/31, Feb 28/29/Mar 1, century years (1900 and 2100
common, 2000 leap), range limits, and today's date.

| Gregorian (ISO) | IFC | IFC numeric | Day of year | Real weekday | IFC nominal weekday | IFC week | Note |
|---|---|---|---|---|---|---|---|
| 2026-09-17 | September 8, 2026 | `2026-10-08` | 260 | Thursday | Sunday | 38 | today (spec date) |
| 2024-06-16 | June 28, 2024 | `2024-06-28` | 168 | Sunday | Saturday | 24 | leap year: day before Leap Day |
| 2024-06-17 | Leap Day 2024 | `2024-06-29` | 169 | Monday | — | — | leap year: Leap Day |
| 2024-06-18 | Sol 1, 2024 | `2024-07-01` | 170 | Tuesday | Sunday | 25 | leap year: day after Leap Day |
| 2025-06-16 | June 27, 2025 | `2025-06-27` | 167 | Monday | Friday | 24 | common year: Jun 16 |
| 2025-06-17 | June 28, 2025 | `2025-06-28` | 168 | Tuesday | Saturday | 24 | common year: Jun 17 is an ordinary day |
| 2025-06-18 | Sol 1, 2025 | `2025-07-01` | 169 | Wednesday | Sunday | 25 | common year: Jun 18 |
| 2024-02-28 | March 3, 2024 | `2024-03-03` | 59 | Wednesday | Tuesday | 9 | leap year: Feb 28 |
| 2024-02-29 | March 4, 2024 | `2024-03-04` | 60 | Thursday | Wednesday | 9 | leap year: Gregorian leap day is an ordinary IFC day |
| 2024-03-01 | March 5, 2024 | `2024-03-05` | 61 | Friday | Thursday | 9 | leap year: Mar 1 |
| 2025-02-28 | March 3, 2025 | `2025-03-03` | 59 | Friday | Tuesday | 9 | common year: Feb 28 |
| 2025-03-01 | March 4, 2025 | `2025-03-04` | 60 | Saturday | Wednesday | 9 | common year: Mar 1 |
| 2024-12-30 | December 28, 2024 | `2024-13-28` | 365 | Monday | Saturday | 52 | leap year: Dec 30 |
| 2024-12-31 | Year Day 2024 | `2024-13-29` | 366 | Tuesday | — | — | leap year: Year Day |
| 2025-01-01 | January 1, 2025 | `2025-01-01` | 1 | Wednesday | Sunday | 1 | New Year after Year Day |
| 2025-12-30 | December 28, 2025 | `2025-13-28` | 364 | Tuesday | Saturday | 52 | common year: Dec 30 |
| 2025-12-31 | Year Day 2025 | `2025-13-29` | 365 | Wednesday | — | — | common year: Year Day |
| 1900-01-01 | January 1, 1900 | `1900-01-01` | 1 | Monday | Sunday | 1 | 1900: century, NOT leap |
| 1900-02-28 | March 3, 1900 | `1900-03-03` | 59 | Wednesday | Tuesday | 9 | 1900: Feb 28 |
| 1900-03-01 | March 4, 1900 | `1900-03-04` | 60 | Thursday | Wednesday | 9 | 1900: Mar 1 (no Feb 29) |
| 1900-06-17 | June 28, 1900 | `1900-06-28` | 168 | Sunday | Saturday | 24 | 1900: Jun 17 ordinary day |
| 1900-06-18 | Sol 1, 1900 | `1900-07-01` | 169 | Monday | Sunday | 25 | 1900: Jun 18 |
| 1900-12-31 | Year Day 1900 | `1900-13-29` | 365 | Monday | — | — | 1900: Year Day (day 365) |
| 2000-01-01 | January 1, 2000 | `2000-01-01` | 1 | Saturday | Sunday | 1 | 2000: century, leap |
| 2000-02-29 | March 4, 2000 | `2000-03-04` | 60 | Tuesday | Wednesday | 9 | 2000: Feb 29 exists |
| 2000-03-01 | March 5, 2000 | `2000-03-05` | 61 | Wednesday | Thursday | 9 | 2000: Mar 1 |
| 2000-06-16 | June 28, 2000 | `2000-06-28` | 168 | Friday | Saturday | 24 | 2000: day before Leap Day |
| 2000-06-17 | Leap Day 2000 | `2000-06-29` | 169 | Saturday | — | — | 2000: Leap Day |
| 2000-06-18 | Sol 1, 2000 | `2000-07-01` | 170 | Sunday | Sunday | 25 | 2000: day after Leap Day |
| 2000-12-31 | Year Day 2000 | `2000-13-29` | 366 | Sunday | — | — | 2000: Year Day (day 366) |
| 2100-01-01 | January 1, 2100 | `2100-01-01` | 1 | Friday | Sunday | 1 | 2100: century, NOT leap |
| 2100-02-28 | March 3, 2100 | `2100-03-03` | 59 | Sunday | Tuesday | 9 | 2100: Feb 28 |
| 2100-03-01 | March 4, 2100 | `2100-03-04` | 60 | Monday | Wednesday | 9 | 2100: Mar 1 (no Feb 29) |
| 2100-06-17 | June 28, 2100 | `2100-06-28` | 168 | Thursday | Saturday | 24 | 2100: Jun 17 ordinary day |
| 2100-12-31 | Year Day 2100 | `2100-13-29` | 365 | Friday | — | — | 2100: Year Day (day 365) |
| 1899-12-31 | Year Day 1899 | `1899-13-29` | 365 | Sunday | — | — | range start year: Year Day |
| 2101-01-01 | January 1, 2101 | `2101-01-01` | 1 | Saturday | Sunday | 1 | range end year: Jan 1 |
| 1928-01-01 | January 1, 1928 | `1928-01-01` | 1 | Sunday | Sunday | 1 | Kodak adoption year (leap) |
| 1928-06-17 | Leap Day 1928 | `1928-06-29` | 169 | Sunday | — | — | 1928 Leap Day |
| 2026-02-13 | February 16, 2026 | `2026-02-16` | 44 | Friday | Monday | 7 | Gregorian Friday the 13th |
| 2026-01-13 | January 13, 2026 | `2026-01-13` | 13 | Tuesday | Friday | 2 | IFC Friday the 13th (nominal) |
| 2026-07-04 | Sol 17, 2026 | `2026-07-17` | 185 | Saturday | Tuesday | 27 | US Independence Day |
| 2026-12-25 | December 23, 2026 | `2026-13-23` | 359 | Friday | Monday | 52 | Christmas Day |
| 2024-12-25 | December 23, 2024 | `2024-13-23` | 360 | Wednesday | Monday | 52 | Christmas Day, leap year (same IFC date) |
| 2024-03-20 | March 24, 2024 | `2024-03-24` | 80 | Wednesday | Tuesday | 12 | fixed-Gregorian date before Jun 17 shifts in leap years |
| 2025-03-20 | March 23, 2025 | `2025-03-23` | 79 | Thursday | Monday | 12 | same Gregorian date, common year |
| 0001-01-01 | January 1, 1 | `0001-01-01` | 1 | Monday | Sunday | 1 | minimum supported date |
| 9999-12-31 | Year Day 9999 | `9999-13-29` | 365 | Friday | — | — | maximum supported date |
| 1582-10-15 | October 8, 1582 | `1582-11-08` | 288 | Friday | Sunday | 42 | first Gregorian-calendar day (proleptic before) |
| 1970-01-01 | January 1, 1970 | `1970-01-01` | 1 | Thursday | Sunday | 1 | Unix epoch |

### 6.5 Negative vectors (MUST be rejected)

| Input | Reason |
|---|---|
| `LeapDay(2025)`, `LeapDay(2026)` | Common year (V3). |
| `LeapDay(1900)`, `LeapDay(2100)` | Century common years (V3). |
| `of(2025, 6, 29)` | Day 29 of June in a common year (V5). |
| `of(2024, 7, 29)` (Sol 29), `of(2024, 1, 29)` | Day 29 only exists for June (leap years) and December (V5). |
| `of(2024, 13, 30)`, `of(2024, 6, 30)`, `of(2024, 2, 31)` | Day ≥ 30 never exists (V5). |
| `of(2024, 1, 0)`, `of(2024, 0, 1)`, `of(2024, 14, 1)` | Day/month out of range (V2). |
| `Regular(2024, JUNE, 29)` | Regular days are 1..28; Leap Day must be the `LeapDay` type (V2). |
| `of(0, 1, 1)`, `of(10000, 1, 1)`, `from(LocalDate.of(-1, 1, 1))` | Year out of supported range (V1). |
| parse `"2026-13-29x"`, `"2026-7-1"`, `"26-07-01"` | Strict numeric syntax `YYYY-MM-DD` (§7.3). |

Positive counterparts: `of(2024, 6, 29) == LeapDay(2024)`, `of(2000, 6, 29) == LeapDay(2000)`,
`of(2025, 13, 29) == YearDay(2025)`, `of(2024, 13, 29) == YearDay(2024)`.

---

## 7. Edge cases and design decisions

Each item lists the options and a **Recommendation**.

### 7.1 Supported year range

The algorithm is valid for any year of the proleptic Gregorian calendar; the IFC itself was only ever proposed
for the 20th century onward, so *every* pre-1902 IFC date is an anachronistic back-projection anyway.

- Option A: 1583+ (first full year of the Gregorian calendar). Avoids implying historical validity, but is an
  arbitrary cut (many countries switched much later: Britain 1752, Russia 1918, Greece 1923).
- Option B: years 1..9999, proleptic Gregorian = exactly the ISO chronology of `java.time`. Four-digit years
  keep formatting/parsing trivial; no era (BCE) handling.
- Option C: full `LocalDate` range (±999,999,999). Pointless complexity (year 0, negative years, >4-digit years).

**Recommendation:** Library: **Option B, years 1..9999**, validated, `DateTimeException` outside. The library
never converts to or from the Julian calendar. UI: pickers and year navigation limited to **1583..9999**, with a
one-line note in the Learn screen that dates before a country's Gregorian adoption are proleptic.

### 7.2 Displaying intercalary days in a 7-column month grid

Every month is a 4 × 7 grid; Leap Day (after June, leap years) and Year Day (after December) fit no column.

- Option A: a cell in a 5th row under "Sunday" (or an 8th column). Rejected: visually asserts a weekday / an
  8-day week, which is exactly what the rules deny; an 8th column also breaks narrow layouts.
- Option B: a separate pseudo-month page between June and Sol / after December. Rejected: breaks the
  "13 pages per year" pager model and hides the day from the month it is conventionally attached to.
- Option C: **a full-width row beneath the fourth week** of June (leap years) and December, spanning all seven
  columns, visually distinct from day cells, labelled "Leap Day" / "Year Day" with the Gregorian date and the
  actual weekday (e.g. "Year Day · Thu, Dec 31, 2026 · no IFC weekday").
- Option D: a banner above/below the grid outside the calendar component. Workable, but then the day is not a
  selectable date cell like the others.

**Recommendation:** **Option C.** The intercalary row is a first-class selectable "day" (tap → same day-detail
screen, can hold events, can be "today" and gets the today highlight). Reserve the row's vertical space in
*every* month so the pager height never jumps (in other months the slot can stay empty or show the month's
Gregorian span). Year view: 13 mini-grids plus the two intercalary markers in the same positions. The week
always starts on **Sunday** in IFC grids — this is part of the calendar's definition (R5), so the device's
locale "first day of week" setting is deliberately ignored for IFC grids (it still applies to any Gregorian
grid the app shows).

### 7.3 Date formatting conventions

Hazard: for months after June the IFC month *number* differs from the Gregorian month of the same *name*
(IFC September = 10), and an IFC numeric date is visually indistinguishable from a Gregorian one
(`2026-10-08` is IFC September 8 = Gregorian September 17, but reads as Gregorian October 8).

**Recommendation:**

| Style | Regular day | Leap Day | Year Day |
|---|---|---|---|
| Long | `September 8, 2026` | `Leap Day, 2024` | `Year Day, 2026` |
| Medium/short | `Sep 8, 2026` · `Sol 8, 2026` | `Leap Day 2024` | `Year Day 2026` |
| Numeric (canonical) | `IFC 2026-10-08` | `IFC 2024-06-29` | `IFC 2026-13-29` |
| Ordinal | `2026-260` | `2024-169` | `2026-365` |

- Long/medium follow the locale's Gregorian field order (e.g. `8 September 2026` for en-GB) using the
  localized month name. Wherever an IFC string appears next to Gregorian dates or could be read out of
  context (share text, widgets, notifications), add the marker "IFC".
- The **nominal weekday is not part of the default long format**. When shown it is labelled:
  `IFC Sunday, September 8, 2026`. Never print `Sunday, September 8, 2026` unlabelled.
- Numeric form: strictly `YYYY-MM-DD`, big-endian, zero-padded, month `01`–`13`, day `01`–`28`, plus `06-29`
  (Leap Day, leap years only) and `13-29` (Year Day). The visible prefix `IFC ` is mandatory in UI/share
  output; the parser accepts the form with or without the prefix. **Never** emit locale-style numeric dates
  (`10/08/2026`, `08.10.2026`) for IFC.
- Why `06-29` / `13-29` for the intercalary days (rather than `2024-LD`, `Sol 0`, a 14th month, or `YD`):
  it matches the historical "June 29" naming, it keeps a plain `(int, int, int)` triple, and it
  **sorts correctly** both lexicographically and numerically (`06-28 < 06-29 < 07-01`; `13-29` is last).
  Verified strictly monotonic over all days 1..9999 (§6.1).
- Ordinal date `YYYY-DDD` is **identical in both calendars** (R1, R10) and is a handy unambiguous bridge for
  debugging and power users.
- Storage/interchange is never an IFC string (§7.9).

### 7.4 Day of year and week of year

- Day of year: 1..365/366, **equal to the Gregorian day of year**, intercalary days included (Leap Day = 169;
  Year Day = 365/366). Note that in leap years Sol 1 is day 170, not 169.
- Week of year: 1..52, fixed: week `w` always covers the same IFC dates (week 1 = January 1–7, week 25 =
  Sol 1–7, week 52 = December 22–28). Month `m` always contains weeks `4m − 3 … 4m`. Intercalary days have **no**
  week number (`null`).
- IFC weeks are **not ISO-8601 weeks** (ISO weeks start Monday, follow the real week, and a year can have 53).

**Recommendation:** expose `dayOfYear: Int`, `weekOfYear: Int?`; display as "Day 260 · Week 38 of 52"; for
intercalary days display "Day 169 · outside the weeks". Do not use ISO `Www` notation for IFC weeks.

### 7.5 Quarters

Four quarters of exactly 13 weeks / 91 regular days (table in §5.3): Q1 = January 1 – April 7, Q2 = April 8 –
Sol 14, Q3 = Sol 15 – September 21, Q4 = September 22 – December 28. Quarters cannot align with month
boundaries (13 months is not divisible by 4) — a classic criticism of the plan.

**Recommendation:** `quarter = (r − 1) div 91 + 1` for regular days; Leap Day reports Q2 and Year Day Q4
(attached to the quarter they follow/sit in), giving actual lengths 91/91(92)/91/92. Quarters are
informational only (detail screen); no quarter view in v1.

### 7.6 Locale and translation

- The twelve Gregorian-named months: reuse the platform's localized names via
  `gregorianNamesake.getDisplayName(TextStyle.FULL / SHORT, locale)` (prefer the `*_STANDALONE` styles for
  headers). No app translations needed for them.
- **Sol**: Latin for "sun"; treated as a proper noun and conventionally left untranslated. Put it in string
  resources (`month_sol`, `month_sol_short`) anyway so a translator can localize or transliterate it
  (non-Latin scripts will need a transliteration). Short form is `Sol` (already three letters).
- "Leap Day", "Year Day", "IFC", "IFC weekday", "Actual weekday", "no IFC weekday": string resources,
  translated normally (e.g. es "Día bisiesto" / "Día del año"; de "Schalttag" / "Jahrestag" — to be confirmed by
  translators).
- Do **not** use narrow (single-letter) month names: `J F M A M J S J A S O N D` has three J's and two S's
  (Sol/September) adjacent enough to confuse.
- Weekday names (nominal and actual) come from `DayOfWeek.getDisplayName(...)`.
- Format patterns (field order, punctuation) are per-locale string resources with placeholders, not string
  concatenation. Numerals: use the locale's digits for long/medium forms; the canonical numeric form always
  uses ASCII digits.

**Recommendation:** as above; ship English first, with every user-visible calendar term in resources from day one.

### 7.7 Date arithmetic semantics

Principle: **day-based arithmetic is done on the Gregorian `LocalDate`; month/year arithmetic is done on the
IFC pseudo-fields `(year, monthNumber 1..13, dayOfMonth 1..29)` with `java.time`-style clamping.**

- `plusDays(n)`: `from(toLocalDate().plusDays(n))`. Intercalary days count as days. Exact and reversible.
- `plusWeeks(n)`: `plusDays(7n)` — real weeks. This does **not** preserve the nominal weekday across an
  intercalary day (June 25, 2024 [nominal Wed] + 1 week = Sol 3, 2024 [nominal Tue]). That is correct: users
  live in the real week.
- `plusMonths(n)`: `t = year × 13 + (monthNumber − 1) + n`; `year' = floorDiv(t, 13)`;
  `month' = floorMod(t, 13) + 1`; keep `dayOfMonth`; if it is 29 and `(month', year')` has no day 29, **clamp
  to 28**; resolve with `IfcDate.of`. For regular days no clamping ever occurs (every month has 28 days) and the
  nominal weekday is preserved. The elapsed real time is 28 days, or 29 when the step crosses an intercalary
  day.
- `plusYears(n)`: same with `year' = year + n`.
- This mirrors `LocalDate` (Jan 31 + 1 month = Feb 28; Feb 29 + 1 year = Feb 28). Resulting behavior of the
  intercalary days:

  | Expression | Result | Why |
  |---|---|---|
  | Leap Day 2024 + 1 month | Sol 28, 2024 | Sol has no 29 → clamp. |
  | Leap Day 2024 − 1 month | May 28, 2024 | clamp. |
  | Leap Day 2024 + 1 year | June 28, 2025 | 2025 has no June 29 → clamp (like Feb 29 + 1 year). |
  | Leap Day 2024 + 4 years | Leap Day 2028 | June 29 exists. |
  | Leap Day 2096 + 4 years | June 28, 2100 | 2100 is a common year. |
  | Leap Day 2024 + 7 months | Year Day 2024 | December has a 29. Documented quirk, same family as Jan 31 + 2 months = Mar 31. |
  | Year Day 2026 + 1 month | January 28, 2027 | clamp. |
  | Year Day 2026 + 13 months = + 1 year | Year Day 2027 | December 29 exists every year. |
  | Year Day 2027 + 6 months | Leap Day 2028 | June 29 exists in 2028. |
  | June 28, 2024 + 1 day | Leap Day 2024 | day arithmetic. |
  | June 28, 2025 + 1 day | Sol 1, 2025 | day arithmetic. |

  As in `java.time`, month arithmetic is not reversible or associative around clamped values; that is accepted.
- Differences: `daysBetween` = `ChronoUnit.DAYS.between` on the `LocalDate`s (the only difference the UI needs
  in v1). A months/years `Period`-style difference, if ever needed, is defined on the pseudo-fields.
- This section is silent on `Long` overflow and on which exception type it produces; see
  `docs/adr/0002-ifc-date-arithmetic.md` for that decision (every `IfcDate` arithmetic method throws
  `DateTimeException`, never `ArithmeticException`, matching every other entry point in §3.3).
- **Recurrence:**
  - "Monthly on day d" (1..28) exists in every month — 13 occurrences a year, always the same nominal weekday.
  - "Yearly on Year Day" occurs every year (always Gregorian Dec 31).
  - "Yearly on Leap Day" — options: (a) occurs only in leap years; (b) falls back to June 28 in common years;
    (c) falls back to Sol 1. **Recommendation: (a) by default** — a non-existent date is skipped, which is
    also the iCalendar (RFC 5545) rule for invalid recurrence instances — with a per-event user option
    "In common years: skip / June 28 / Sol 1". The event editor must tell the user "occurs only in leap years
    (next: 2028)".
  - "Weekly" recurrences follow the **real** 7-day week (every 7 days). An "every IFC Friday" recurrence
    (nominal weekday; 8-day gaps across intercalary days) is a different rule type and, if offered at all,
    must be explicitly named. Not in v1.
  - Every recurring event stores which calendar anchors it. A Gregorian-anchored yearly event (a birthday on
    Apr 10) has an IFC date that differs by one day in leap years when it falls in Feb 29 – Jun 17 (§2.4);
    an IFC-anchored yearly event (every April 16) has a Gregorian date that differs by one day in leap years
    when it falls in March 4 – June 28. A Gregorian Feb 29 birthday is IFC March 4 and thus exists every year
    if the user chooses to re-anchor it.

### 7.8 Time zones and midnight rollover

The IFC has no day-boundary convention of its own: an IFC day is exactly the local civil day. The IFC date is
a pure function of the Gregorian **local** date, so "today" depends on the device's time zone, and two devices
in different zones legitimately show different IFC dates at the same instant.

**Recommendation:**

- The library converts `LocalDate` only. It never touches `Instant`, epoch milliseconds, or `Date`. The app
  layer computes `LocalDate.now(clock)` with `Clock.systemDefaultZone()` (injectable `Clock` for tests) and
  passes it in. Never derive dates by dividing epoch millis by 86,400,000 or by converting through UTC.
- Do not cache "today" for the life of the process. Recompute on resume and on the system broadcasts
  `ACTION_DATE_CHANGED`, `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED`.
- Widgets/notifications schedule their refresh for the next local midnight computed as
  `today.plusDays(1).atStartOfDay(zone)` (correct even where DST removes 00:00), and also refresh on the
  broadcasts above.
- If the app ever shows another zone's date ("IFC date in Tokyo"), that is `LocalDate.now(zoneId)` → convert;
  no other machinery is needed.
- Tests: fixed `Clock`s at 23:59:59 and 00:00:00 around Dec 31 → Jan 1 (Year Day → January 1), Jun 16 → 17 →
  18 in a leap year, in zones with extreme offsets (`Pacific/Kiritimati` UTC+14, `Etc/GMT+12` UTC−12), and
  across a DST transition.

### 7.9 Persistence and interchange

**Recommendation:** persist dates as ISO-8601 Gregorian `LocalDate` strings (or epoch-day longs), never as
IFC fields; IFC is a *view*. Exception: IFC-anchored recurrence rules (§7.7) store IFC pseudo-fields
(`monthNumber`, `dayOfMonth`, plus the common-year policy for Leap Day). Any export/share includes the
Gregorian date alongside the IFC date.

### 7.10 Other points to settle

- **"Today" on an intercalary day:** widgets and notifications must render "Year Day 2026" / "Leap Day 2028"
  correctly (no month-day layout assumptions). Covered by the sealed type (§4).
- **Date pickers:** an IFC picker must offer the intercalary days (June in leap years, December always) and
  must not offer Leap Day in common years. Switching the picker's year while Leap Day is selected clamps to
  June 28 (consistent with §7.7).
- **Holidays/weekends:** the historical proposal treated the intercalary days as holidays. The app should not
  assert real-world holidays or weekends from IFC data; shading of weekends, if any, follows the actual weekday.

---

## 8. Out of scope: variants and look-alikes (for the FAQ)

The app implements the IFC exactly as specified above and nothing else. Users regularly confuse it with:

- **Positivist calendar** (Auguste Comte, 1849): also 13 × 28, but months are named after historical figures
  (Moses, Homer, Aristotle, …), weeks start on Monday, **both** extra days (the yearly festival day and the
  leap-year festival day) come at the **end of the year**, and years are counted from 1789.
  <https://en.wikipedia.org/wiki/Positivist_calendar>
- **Georgian calendar** (Hugh Jones, 1745): an earlier 13-month proposal; precursor, not the IFC.
- **World Calendar** (Elisabeth Achelis, 1930): **12** months in equal quarters of 31/30/30 days; "Worldsday"
  after December 30 and "Leapyear Day" after June 30, both outside the week. Nearly the same weekday scheme as
  the IFC, completely different months. <https://en.wikipedia.org/wiki/World_Calendar>
- **Pax calendar** (James Colligan, 1930): 13 months with the extra month "Columbus" near year end, and a
  **leap week** ("Pax") instead of blank days, so the seven-day week is never broken.
  <https://en.wikipedia.org/wiki/Pax_Calendar>
- **Leap-week calendars** in general (Hanke–Henry Permanent Calendar, Symmetry454, …): keep the real week
  intact, so their years do not coincide with Gregorian years. <https://en.wikipedia.org/wiki/Leap_week_calendar>
- **IFC-like variants that move things**: leap day placed at the end of the year (after Year Day) or kept at
  the end of February; Year Day placed at the start of the year as a "day zero"; the 13th month placed last
  or given another name (e.g. "Luna", "Undecimber"); weeks starting on Monday. Any of these changes the date
  mapping and is **not** what this app shows.
- **13 Moon / Dreamspell calendar** (José Argüelles): 13 × 28 starting on Gregorian July 26 with a "Day Out of
  Time" on July 25. Unrelated in origin and alignment.
- **13-period fiscal calendars** (4-4-5, 13 × 4 weeks) used in retail/accounting: periods of whole *real*
  weeks with a 53rd week every few years; no blank days. Related in motivation, different in mechanics.
- **Calendars that merely have 13 months** (Ethiopian, Coptic: 12 × 30 + 5/6 days): unrelated.

FAQ one-liner: "If your 13-month calendar has a month called **Sol** between June and July, a **Year Day** on
Gregorian December 31 and a **Leap Day** on Gregorian June 17, it is the IFC. Otherwise it is a different
calendar."

---

## 9. Fun facts for the About / Learn screen

All date facts below were machine-checked with the reference script (§6.1).

- **Every month has a Friday the 13th** — 13 of them a year. (Nominal Friday: day 13 → `(13 − 1) mod 7 = 5`.)
- Every month is the same 4 × 7 grid, starting on Sunday and ending on Saturday. One printed month page serves
  for every month of every year, forever.
- Your IFC birthday falls on the same (nominal) weekday every year. The 1st, 8th, 15th and 22nd are always
  Sundays; the 7th, 14th, 21st and 28th always Saturdays.
- 13 × 28 = 364 = 52 weeks exactly. The 365th day, **Year Day**, has no weekday at all — it sits between
  Saturday, December 28 and Sunday, January 1. It is always Gregorian New Year's Eve.
- In leap years there is a second weekday-less day, **Leap Day**, after June 28 — Gregorian June 17, not
  February 29. Each intercalary day makes a "long weekend": Saturday, blank day, Sunday.
- **Sol** is named for the sun: the month contains the June solstice (around Sol 3–4).
- The IFC day-of-year number always equals the Gregorian one, and the ordinal date (e.g. `2026-260`) is
  identical in both calendars.
- Fixed Gregorian dates outside Feb 29 – Jun 17 have a fixed IFC date: Christmas is always **December 23**,
  US Independence Day is always **Sol 17**, Halloween is **October 24**.

| Gregorian date | Occasion | IFC date, common year (2026) | IFC date, leap year (2024) |
|---|---|---|---|
| Jan 1 | New Year's Day | January 1 | January 1 |
| Feb 14 | Valentine's Day | February 17 | February 17 |
| Feb 29 | Gregorian leap day | (does not exist) | March 4 |
| Mar 17 | St. Patrick's Day | March 20 | March 21 |
| May 1 | May Day | May 9 | May 10 |
| Jun 17 | IFC Leap Day slot | June 28 | Leap Day |
| Jun 21 | June solstice (approx.) | Sol 4 | Sol 4 |
| Jul 4 | US Independence Day | Sol 17 | Sol 17 |
| Oct 31 | Halloween | October 24 | October 24 |
| Dec 25 | Christmas Day | December 23 | December 23 |
| Dec 31 | New Year's Eve | Year Day | Year Day |

- People born on Gregorian February 29 get a birthday every year in the IFC: **March 4**. The IFC's own
  "leaplings" are those born on June 17 of a leap year.
- The real week and the IFC week agree for a whole year only when January 1 is a real Sunday and the year is
  a common year — most recently 2023, next in 2034. (2026 starts on a Thursday, so IFC "Sundays" are real
  Thursdays all year.)
- 1928, the year Kodak adopted the calendar, began on a real Sunday.
- Moses Cotsworth was a railway accountant: he wanted every month to have the same number of days and of
  each weekday so monthly statistics could be compared fairly.
- George Eastman's Kodak used a 13-period calendar internally from 1928 until 1989 — 57 years after
  Eastman's death.
- According to Wikipedia, the League of Nations judged the plan the best of 130 calendar-reform proposals,
  yet it was never adopted by any country, largely because the blank days break the unbroken seven-day week
  observed by several religions.
- One of the plan's critics in the US Congress was named Sol — Representative Sol Bloom.
- With 13 equal months, a monthly salary would be paid 13 times a year and rent would be due 13 times — and
  quarters would be 13 weeks each but never a whole number of months, because 13 is prime.
