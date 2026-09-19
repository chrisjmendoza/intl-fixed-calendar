package io.github.chrisjmendoza.yearal.widget.today

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import java.time.Clock
import java.time.LocalDate

/**
 * Today's date in both calendars (CLAUDE.md rule 1: the IFC date comes from [IfcDate.from], nothing
 * here computes one itself).
 *
 * @property ifcDate today in the IFC.
 * @property gregorianDate the same physical day in the Gregorian calendar; [ifcDate] is derived from it.
 */
data class TodayDate(
    val ifcDate: IfcDate,
    val gregorianDate: LocalDate,
)

/**
 * Reads "today" from [clock] and [zoneProvider] right now. Mirrors the pattern in
 * [io.github.chrisjmendoza.yearal.core.domain.RealDateTicker]: the instant and the zone are each read
 * fresh, never cached, so a call after a `TIME_CHANGED` or `ZONE_CHANGED` rollover trigger sees the new
 * value without the caller doing anything special (CLAUDE.md rule 2).
 *
 * **Call this again for every render.** [io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget]
 * calls it directly inside its `@Composable` content, not once in `provideGlance` and not behind
 * `remember`, because the whole point of `docs/ARCHITECTURE.md` §5's layered rollover is that a call to
 * this function is what makes a stale render impossible — caching it anywhere would silently reintroduce
 * the "widget just stays on whatever date it was when added" bug named in FEATURES S3 and S1.
 */
fun todayDate(
    clock: Clock,
    zoneProvider: ZoneProvider,
): TodayDate {
    val gregorianDate = clock.instant().atZone(zoneProvider.currentZone()).toLocalDate()
    return TodayDate(ifcDate = IfcDate.from(gregorianDate), gregorianDate = gregorianDate)
}

/**
 * What the Today widget shows, formatted once by [IfcDateFormatter] so the Glance content itself
 * renders text only and computes nothing (CLAUDE.md rules 1 and 9; mirrors `TodayUiState.Loaded` in
 * `:feature:calendar`).
 *
 * @property date the underlying [TodayDate] this state was built from.
 * @property primaryLabel the large IFC date: month and day for a regular day (`Sol 13`), or the
 *   intercalary name with its year for [IfcDate.LeapDay] / [IfcDate.YearDay] (`Year Day, 2026`,
 *   `Leap Day, 2024`) — an intercalary day has no month or day number of its own to show without one
 *   (CLAUDE.md rule 6).
 * @property gregorianLabel the Gregorian equivalent with its real weekday (`Thu, Dec 31, 2026`), shown
 *   beneath the primary label on wider or taller sizes.
 * @property actualWeekdayLabel the real-world weekday, explicitly labelled so it is never mistaken for
 *   an IFC weekday (CLAUDE.md rule 3), shown only at the largest size.
 * @property contentDescription the merged TalkBack description for the whole tappable widget: both
 *   dates, the labelled weekdays, "Today.", and a hint that double-tapping opens the app.
 */
data class TodayWidgetState(
    val date: TodayDate,
    val primaryLabel: String,
    val gregorianLabel: String,
    val actualWeekdayLabel: String,
    val contentDescription: String,
)

/**
 * Builds the [TodayWidgetState] for [date]. Every word comes from [formatter] (bundled resources and
 * `java.time` locale-aware names) or [tapHint] (a string resource the caller reads); this function
 * itself formats and computes nothing.
 */
fun buildTodayWidgetState(
    date: TodayDate,
    formatter: IfcDateFormatter,
    tapHint: String,
): TodayWidgetState {
    val ifcDate = date.ifcDate
    val primaryLabel = if (ifcDate.isIntercalary) formatter.formatLong(ifcDate) else formatter.formatDay(ifcDate)
    return TodayWidgetState(
        date = date,
        primaryLabel = primaryLabel,
        gregorianLabel = formatter.formatGregorianMedium(date.gregorianDate),
        actualWeekdayLabel = formatter.actualWeekday(ifcDate),
        contentDescription = "${formatter.dayDescription(ifcDate, isToday = true)} $tapHint",
    )
}
