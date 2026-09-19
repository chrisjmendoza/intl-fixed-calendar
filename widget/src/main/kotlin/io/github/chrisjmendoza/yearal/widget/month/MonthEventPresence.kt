package io.github.chrisjmendoza.yearal.widget.month

import io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * A bounded-time, best-effort snapshot of [ObserveAgendaUseCase.presence] for the Month widget's event
 * dots (ROADMAP M5 T6; docs/ARCHITECTURE.md §5 "Widget types" item 2, "Data"). **One query per render**:
 * unlike a screen, [io.github.chrisjmendoza.yearal.widget.month.MonthGlanceWidget] does not keep
 * collecting this flow for the life of a Glance session (`docs/ARCHITECTURE.md` §5 reserves that pattern
 * for the future Agenda widget); it takes the first emission and moves on.
 *
 * A widget render **must never hang or crash** because the database is slow, empty, or the use case
 * throws: [range]'s dots are simply absent on a timeout or a failure, exactly as if [ObserveAgendaUseCase.presence]
 * had emitted an empty set. [CancellationException] is rethrown, never swallowed, so a cancelled
 * `provideGlance` call still cancels promptly.
 *
 * @param useCase normally resolved through [io.github.chrisjmendoza.yearal.widget.di.WidgetEntryPoint].
 * @param range the currently shown IFC month's Gregorian span, `IfcYearMonth.gregorianRange` (28 or 29
 *   days, the trailing Leap Day / Year Day band included).
 */
internal suspend fun fetchMonthEventPresence(
    useCase: ObserveAgendaUseCase,
    range: ClosedRange<LocalDate>,
): Set<LocalDate> =
    try {
        withTimeoutOrNull(PRESENCE_TIMEOUT_MILLIS) { useCase.presence(range).first() } ?: emptySet()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Any other failure (a throwing use case, a broken flow) renders without dots rather than
        // crashing the widget's background render.
        emptySet()
    }

/**
 * The bound the presence snapshot may take before the render proceeds without dots. Generous relative
 * to a normal Room query (docs/ARCHITECTURE.md §3.4's month-range query is sub-millisecond with 1,000
 * events, `RoomEventRepositoryTest`'s benchmark), but short enough that a widget re-render -- already a
 * background `WorkManager`/Glance session task, not a UI-thread deadline -- never stalls noticeably.
 */
internal const val PRESENCE_TIMEOUT_MILLIS: Long = 3_000
