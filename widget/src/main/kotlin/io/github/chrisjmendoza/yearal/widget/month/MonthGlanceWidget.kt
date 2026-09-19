package io.github.chrisjmendoza.yearal.widget.month

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.calendar.GRID_COLUMNS
import io.github.chrisjmendoza.yearal.core.designsystem.calendar.GRID_ROWS
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.theme.BrandDarkColorScheme
import io.github.chrisjmendoza.yearal.core.designsystem.theme.BrandLightColorScheme
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import io.github.chrisjmendoza.yearal.widget.R
import io.github.chrisjmendoza.yearal.widget.di.WidgetEntryPoint
import io.github.chrisjmendoza.yearal.widget.today.launchAppIntent
import io.github.chrisjmendoza.yearal.widget.today.todayDate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * The perpetual Month-grid home-screen widget (FEATURES S2-S5; docs/ARCHITECTURE.md §5 "Widget types"
 * item 2; ROADMAP M5 T3): the current IFC month as a 4x7 grid with today highlighted, the trailing
 * Leap Day / Year Day band, and, at the larger responsive size, both weekday header rows and the
 * month's Gregorian span.
 *
 * Every [provideGlance] call -- the initial placement,
 * [androidx.glance.appwidget.GlanceAppWidget.updateAll] from
 * [io.github.chrisjmendoza.yearal.widget.WidgetRolloverListener] or from
 * [io.github.chrisjmendoza.yearal.widget.DebouncedWidgetUpdater] after an event write (ROADMAP M5 T6),
 * and the `updatePeriodMillis` backstop in `month_widget_info.xml` -- reads [Clock] and [ZoneProvider]
 * through [WidgetEntryPoint] and recomputes "today", and so which month to show, inside the composable
 * content itself (CLAUDE.md rule 2), exactly like
 * [io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget][io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget]:
 * there is no cached date or month anywhere in this class.
 *
 * **Event dots (ROADMAP M5 T6).** [provideGlance] also takes one bounded-time
 * [fetchMonthEventPresence] snapshot of the shown month's event dates and passes it down to the
 * composable. This one call, not a continuous collection, is why the date itself is still recomputed
 * inside the composable rather than here: the presence snapshot is allowed to be a render behind (it
 * self-corrects on the next re-render, like every other widget update path in this module), but the
 * *date* shown never is.
 */
class MonthGlanceWidget : GlanceAppWidget() {
    /**
     * Two breakpoints (task requires at least two; docs/ARCHITECTURE.md §5 "All widgets use
     * `SizeMode.Responsive`"): [COMPACT] shows the grid with a single actual-weekday header row and no
     * Gregorian span line; [FULL] adds the nominal weekday header row too (`BOTH`, the app default when
     * space allows -- ARCHITECTURE "Reconciled decisions" #7) and the Gregorian span line.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val clock = entryPoint.clock()
        val zoneProvider = entryPoint.zoneProvider()
        // Read once per provideGlance call, not inside the composable: see TodayGlanceWidget's KDoc for
        // why (Compose lint's NonObservableLocale check; Glance content never recomposes on its own for a
        // locale change anyway, and LOCALE_CHANGED already forces a fresh provideGlance).
        val formatter = IfcDateFormatter(context.resources, Locale.getDefault())
        val tapHint = context.getString(R.string.today_widget_tap_hint)
        val hasEventsLabel = context.getString(R.string.month_widget_has_events_hint)
        // One snapshot, bounded and caught (fetchMonthEventPresence's KDoc): a slow or broken database
        // must never hang or crash this render, only cost it its dots for one cycle.
        val today = todayDate(clock, zoneProvider)
        val monthRange = IfcYearMonth.from(today.ifcDate).gregorianRange
        val eventDates = fetchMonthEventPresence(entryPoint.observeAgendaUseCase(), monthRange)
        provideContent {
            MonthWidgetContent(clock, zoneProvider, formatter, tapHint, eventDates, hasEventsLabel)
        }
    }

    /**
     * The API 35+ system widget-picker preview (ROADMAP M5 T5; docs/ARCHITECTURE.md §5 "Picker
     * previews"), rendered through [io.github.chrisjmendoza.yearal.widget.preview.WidgetPreviewUpdater]'s
     * `setWidgetPreview` call. Reuses [MonthWidgetContent] unchanged, fed [PREVIEW_CLOCK] /
     * [PREVIEW_ZONE_PROVIDER] / [PREVIEW_EVENT_DATES] fixed on the same September 2026 sample as
     * `res/layout/month_widget_preview.xml`, so the two previews cannot drift apart. Skips the real
     * [fetchMonthEventPresence] call on purpose -- a picker preview never touches live event data.
     */
    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val formatter = IfcDateFormatter(context.resources, Locale.getDefault())
        val tapHint = context.getString(R.string.today_widget_tap_hint)
        val hasEventsLabel = context.getString(R.string.month_widget_has_events_hint)
        provideContent {
            MonthWidgetContent(
                PREVIEW_CLOCK,
                PREVIEW_ZONE_PROVIDER,
                formatter,
                tapHint,
                PREVIEW_EVENT_DATES,
                hasEventsLabel,
            )
        }
    }

    /** Breakpoints shared with `month_widget_info.xml`'s min/max resize attributes. */
    companion object {
        /** About 4 columns x 3 rows of home-screen cells (the platform's `70dp * cells - 30dp` rule of
         * thumb, task's "sensible min/target cells (about 4x3)"). No Gregorian span line; a single
         * actual-weekday header row. */
        val COMPACT: DpSize = DpSize(250.dp, 180.dp)

        /** About 5 columns x 5 rows. Adds the nominal weekday header row and the Gregorian span line. */
        val FULL: DpSize = DpSize(320.dp, 320.dp)

        /**
         * A fixed instant resolving, in [PREVIEW_ZONE_PROVIDER]'s zone, to Gregorian September 17,
         * 2026 -- IFC September day 8, the same date [MonthWidgetStateTest] hand-checks and
         * `res/layout/month_widget_preview.xml`'s KDoc names. A regular day, deliberately not Leap Day
         * or Year Day (CLAUDE.md rule 6's concern for a *static* preview, `today_widget_preview.xml`'s
         * KDoc).
         */
        internal val PREVIEW_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC)

        /** Paired with [PREVIEW_CLOCK]; UTC keeps the sample deterministic regardless of test/device zone. */
        internal val PREVIEW_ZONE_PROVIDER = ZoneProvider { ZoneOffset.UTC }

        /**
         * Illustrative event dates only (today, September 17, and September 30 -- IFC days 8 and 21),
         * matching `res/layout/month_widget_preview.xml`'s mock-up dots exactly. Never real data: a
         * picker preview must not depend on the database (see [providePreview]'s KDoc).
         */
        internal val PREVIEW_EVENT_DATES = setOf(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 30))
    }
}

/**
 * The widget's content. Recomputes "today" itself on every composition from [clock] and
 * [zoneProvider] -- no parameter here is a cached date -- derives the month to show from it, and reads
 * [LocalSize] to decide which extras fit, matching [MonthGlanceWidget]'s responsive breakpoints.
 * [formatter], [tapHint], [eventDates] and [hasEventsLabel] are read/fetched once per
 * [MonthGlanceWidget.provideGlance] call.
 *
 * @param eventDates the Gregorian dates with an event this month (ROADMAP M5 T6,
 *   [fetchMonthEventPresence]); empty when the presence snapshot timed out or failed, which simply
 *   renders every cell without a dot.
 * @param hasEventsLabel the localized "has events" hint for [MonthWidgetState.contentDescription].
 */
@Composable
private fun MonthWidgetContent(
    clock: Clock,
    zoneProvider: ZoneProvider,
    formatter: IfcDateFormatter,
    tapHint: String,
    eventDates: Set<LocalDate>,
    hasEventsLabel: String,
) {
    val colors =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Material You dynamic colour (FEATURES S4).
            GlanceTheme.colors
        } else {
            // Brand palette fallback below API 31 (docs/ARCHITECTURE.md §5, §1).
            ColorProviders(light = BrandLightColorScheme, dark = BrandDarkColorScheme)
        }
    GlanceTheme(colors = colors) {
        val context = LocalContext.current
        // Read fresh on every composition, per CLAUDE.md rule 2 -- never `remember`, never a value
        // computed once outside this function and passed down.
        val today = todayDate(clock, zoneProvider)
        val state = buildMonthWidgetState(today, formatter, tapHint, eventDates, hasEventsLabel)
        val size = LocalSize.current
        val isFull = size.width >= MonthGlanceWidget.FULL.width && size.height >= MonthGlanceWidget.FULL.height

        var modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(8.dp)
                .semantics { contentDescription = state.contentDescription }
        // launchAppIntent is null only if the platform cannot resolve this app's own launcher activity;
        // see TodayGlanceWidget's KDoc. Reused rather than duplicated (docs/security-and-privacy.md §6.4:
        // one explicit-intent helper, not two).
        launchAppIntent(context)?.let { intent ->
            modifier = modifier.clickable(actionStartActivity(intent))
        }

        Column(modifier = modifier) {
            Text(
                text = state.monthTitle,
                style =
                    TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface,
                    ),
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp),
            )
            if (isFull) {
                WeekdayHeaderRow(state.nominalWeekdayHeaders, GlanceTheme.colors.onSurface)
            }
            WeekdayHeaderRow(state.actualWeekdayHeaders, GlanceTheme.colors.onSurfaceVariant)
            for (row in 0 until GRID_ROWS) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (column in 0 until GRID_COLUMNS) {
                        DayNumberCell(state.days[row * GRID_COLUMNS + column])
                    }
                }
            }
            state.intercalary?.let { IntercalaryRow(it) }
            if (isFull) {
                Text(
                    text = state.gregorianSpanLabel,
                    style =
                        TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        ),
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}

/** One row of seven short weekday names, evenly spaced. Shared by the nominal and the actual header. */
@Composable
private fun WeekdayHeaderRow(
    names: List<String>,
    color: ColorProvider,
) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        names.forEach { name ->
            Text(
                text = name,
                style = TextStyle(fontSize = 10.sp, color = color, textAlign = TextAlign.Center),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/**
 * One day number, [dayCell]. Today is marked by shape and weight, never colour alone (CLAUDE.md rule 3;
 * task: "shape/weight, not colour alone"): a rounded, filled pill plus bold text. [dayCell.hasEvent]
 * adds a small dot glyph beneath the number (ROADMAP M5 T6) -- its **presence**, not a colour, is the
 * signal (CLAUDE.md rule 3), and every cell reserves the same line for it (an empty [EVENT_DOT_GLYPH]
 * text otherwise) so a dot never shifts the grid's row height. Carries no semantics of its own, exactly
 * like the day number beside it (docs/ARCHITECTURE.md §5, "not the app's full-grid pattern of one rich
 * description per cell").
 */
@Composable
private fun RowScope.DayNumberCell(dayCell: MonthDayCellState) {
    val colors = GlanceTheme.colors
    Box(
        modifier = GlanceModifier.defaultWeight().padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(
                text = dayCell.dayOfMonth.toString(),
                style =
                    TextStyle(
                        fontSize = 12.sp,
                        fontWeight = if (dayCell.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (dayCell.isToday) colors.onPrimary else colors.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .let { base ->
                            if (dayCell.isToday) {
                                base.background(colors.primary).cornerRadius(6.dp)
                            } else {
                                base
                            }
                        },
            )
            Text(
                text = if (dayCell.hasEvent) EVENT_DOT_GLYPH else "",
                style =
                    TextStyle(
                        fontSize = 8.sp,
                        color = if (dayCell.isToday) colors.onPrimary else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    ),
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A small, non-linguistic bullet marking a day with an event (ROADMAP M5 T6). Not a translatable string
 * (CLAUDE.md rule 9 covers language content; a decorative glyph is not one, the same treatment already
 * given to the punctuation joining [MonthWidgetState.contentDescription]'s sentences).
 */
private const val EVENT_DOT_GLYPH: String = "•"

/**
 * The full-width Leap Day / Year Day band, [intercalary]. Uses the tertiary-container colour, matching
 * the app's own `IntercalaryBand` (`:core:designsystem`); when today falls on this day it switches to
 * the primary container plus bold text -- shape and weight, not colour alone, matching [DayNumberCell].
 * [intercalary.hasEvent] appends the same [EVENT_DOT_GLYPH] used on a regular day cell (ROADMAP M5 T6),
 * since Leap Day and Year Day can carry events like any other day (CLAUDE.md rule 6).
 */
@Composable
private fun IntercalaryRow(intercalary: MonthIntercalaryState) {
    val colors = GlanceTheme.colors
    val containerColor = if (intercalary.isToday) colors.primaryContainer else colors.tertiaryContainer
    val contentColor = if (intercalary.isToday) colors.onPrimaryContainer else colors.onTertiaryContainer
    Column(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .background(containerColor)
                .cornerRadius(8.dp)
                .padding(6.dp),
    ) {
        Text(
            text = if (intercalary.hasEvent) "${intercalary.label} $EVENT_DOT_GLYPH" else intercalary.label,
            style =
                TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                ),
        )
        Text(
            text = intercalary.subtitle,
            style = TextStyle(fontSize = 10.sp, color = contentColor),
        )
    }
}
