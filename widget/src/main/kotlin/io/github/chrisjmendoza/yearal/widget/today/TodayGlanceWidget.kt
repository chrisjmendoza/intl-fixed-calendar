package io.github.chrisjmendoza.yearal.widget.today

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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.android.EntryPointAccessors
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.theme.BrandDarkColorScheme
import io.github.chrisjmendoza.yearal.core.designsystem.theme.BrandLightColorScheme
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import io.github.chrisjmendoza.yearal.widget.R
import io.github.chrisjmendoza.yearal.widget.di.WidgetEntryPoint
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * The "Today" home-screen widget (FEATURES S1, S3, S4, S5; docs/ARCHITECTURE.md §5). Resizable from a
 * 2x1 "date only" cell up to a 3x2 cell that also shows the Gregorian equivalent and the actual
 * weekday.
 *
 * Every [provideGlance] call — the initial placement,
 * [androidx.glance.appwidget.GlanceAppWidget.updateAll] from
 * [io.github.chrisjmendoza.yearal.widget.WidgetRolloverListener], and the `updatePeriodMillis`
 * backstop in `today_widget_info.xml` — reads [Clock] and [ZoneProvider] through [WidgetEntryPoint] and
 * computes "today" inside the composable content itself (CLAUDE.md rule 2), so a stale render is not
 * possible: there is no cached date anywhere in this class.
 */
class TodayGlanceWidget : GlanceAppWidget() {
    /**
     * Three breakpoints (docs/ARCHITECTURE.md §5: "All widgets use `SizeMode.Responsive` with three
     * sizes"): [SMALL] shows the IFC date alone, [MEDIUM] adds the Gregorian line, [LARGE] adds the
     * labelled actual weekday too.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val clock = entryPoint.clock()
        val zoneProvider = entryPoint.zoneProvider()
        // Read here, not inside the @Composable content below: androidx.compose.ui's NonObservableLocale
        // lint check is right that Locale.getDefault() inside a composable does not recompose on a
        // locale change, but Glance content never does either way -- a LOCALE_CHANGED broadcast reaches
        // this widget through WidgetRolloverListener, which calls updateAll and re-invokes
        // provideGlance from scratch, so reading it once per call already self-corrects (CLAUDE.md rule 9).
        val formatter = IfcDateFormatter(context.resources, Locale.getDefault())
        val tapHint = context.getString(R.string.today_widget_tap_hint)
        provideContent {
            TodayWidgetContent(clock, zoneProvider, formatter, tapHint)
        }
    }

    /**
     * The API 35+ system widget-picker preview (ROADMAP M5 T5; docs/ARCHITECTURE.md §5 "Picker
     * previews"), rendered through [io.github.chrisjmendoza.yearal.widget.preview.WidgetPreviewUpdater]'s
     * `setWidgetPreview` call. Reuses [TodayWidgetContent] unchanged -- the same code that renders the
     * real widget -- fed a [PREVIEW_CLOCK]/[PREVIEW_ZONE_PROVIDER] pair fixed on the same illustrative
     * sample date as `res/layout/today_widget_preview.xml` (IFC Sol 13, 2026), so the dynamic and static
     * previews can never drift apart the way two separately hand-built mock-ups could.
     */
    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val formatter = IfcDateFormatter(context.resources, Locale.getDefault())
        val tapHint = context.getString(R.string.today_widget_tap_hint)
        provideContent {
            TodayWidgetContent(PREVIEW_CLOCK, PREVIEW_ZONE_PROVIDER, formatter, tapHint)
        }
    }

    /** Breakpoints shared with `today_widget_info.xml`'s min/max resize attributes. */
    companion object {
        /** 2 cells wide, 1 cell tall: the IFC date only. */
        val SMALL: DpSize = DpSize(110.dp, 40.dp)

        /** 3 cells wide, 1 cell tall: adds the Gregorian date line. */
        val MEDIUM: DpSize = DpSize(180.dp, 40.dp)

        /** 3 cells wide, 2 cells tall: adds the labelled actual weekday. */
        val LARGE: DpSize = DpSize(180.dp, 110.dp)

        /**
         * A fixed instant resolving, in [PREVIEW_ZONE_PROVIDER]'s zone, to Gregorian June 30, 2026 --
         * IFC Sol 13, 2026, the same sample date named in `res/layout/today_widget_preview.xml`'s KDoc.
         * [Clock.fixed] and a literal [ZoneProvider] are ordinary `java.time`/domain values, not a
         * fake `Clock.systemUTC()` call (CLAUDE.md rule 2 is about *live* dates; a picker preview is
         * deliberately never live).
         */
        internal val PREVIEW_CLOCK: Clock = Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC)

        /** Paired with [PREVIEW_CLOCK]; UTC keeps the sample deterministic regardless of test/device zone. */
        internal val PREVIEW_ZONE_PROVIDER = ZoneProvider { ZoneOffset.UTC }
    }
}

/**
 * The widget's content. Recomputes "today" itself on every composition, from [clock] and
 * [zoneProvider] — no parameter here is a cached date — and reads [LocalSize] to decide which lines
 * fit, matching [TodayGlanceWidget]'s responsive breakpoints. [formatter] and [tapHint] are read once
 * per [TodayGlanceWidget.provideGlance] call, since neither depends on the date.
 */
@Composable
private fun TodayWidgetContent(
    clock: Clock,
    zoneProvider: ZoneProvider,
    formatter: IfcDateFormatter,
    tapHint: String,
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
        val date = todayDate(clock, zoneProvider)
        val state = buildTodayWidgetState(date, formatter, tapHint)
        val size = LocalSize.current

        var modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .semantics { contentDescription = state.contentDescription }
        // launchAppIntent is null only if the platform cannot resolve this app's own launcher activity
        // (docs/security-and-privacy.md §6.4 requires an explicit intent, so there is nothing safe to
        // launch in that case); the widget still shows the date, just without a tap action.
        launchAppIntent(context)?.let { intent ->
            modifier = modifier.clickable(actionStartActivity(intent))
        }

        Column(
            modifier = modifier,
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = state.primaryLabel,
                style =
                    TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface,
                    ),
            )
            if (size.height > TodayGlanceWidget.SMALL.height || size.width > TodayGlanceWidget.SMALL.width) {
                Text(
                    text = state.gregorianLabel,
                    style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
            if (size.height >= TodayGlanceWidget.LARGE.height) {
                Text(
                    text = state.actualWeekdayLabel,
                    style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}
