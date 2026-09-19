package io.github.chrisjmendoza.yearal.feature.settings.learn

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.feature.settings.R

/**
 * The Learn / About screen (docs/FEATURES.md L2; docs/ROADMAP.md M3 T3): what the IFC is, why the
 * weekdays shown elsewhere in the app differ from the real ones, how dates are calculated, a brief
 * history, and an FAQ answering the confusions documented in docs/FEATURES.md Part 1. This is the
 * composable `:app` places behind `LearnKey`; the back arrow pops through [navigator].
 *
 * @param modifier applied to the screen's root [Scaffold].
 */
@Composable
fun LearnRoute(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    LearnScreen(onBack = navigator::goBack, modifier = modifier)
}

/**
 * The stateless Learn screen — the unit for previews, screenshot and Compose tests
 * (docs/ARCHITECTURE.md §4 "State management"). Every worked-example date comes from [LearnFacts],
 * which is computed through `:core:calendar` rather than typed as a literal (CLAUDE.md rule 1), and is
 * rendered through [rememberIfcDateFormatter] so it follows the same locale and formatting rules as
 * every other IFC date in the app (calendar-spec §7.3, §7.6).
 *
 * Opts in to the Material 3 experimental marker only because `TopAppBar`'s default arguments
 * (`TopAppBarDefaults`) still carry it.
 *
 * @param onBack the top app bar's back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberIfcDateFormatter()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.learn_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.learn_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
        ) {
            WhatIsIfcSection()
            HorizontalDivider()
            FloatingDaysSection(formatter)
            HorizontalDivider()
            WeekdaySection(formatter)
            HorizontalDivider()
            CalculationSection(formatter)
            HorizontalDivider()
            HistorySection()
            HorizontalDivider()
            FaqSection()
        }
    }
}

/** What the IFC is: 13 months of 28 days, Sol, and the "every month is the same" identity. */
@Composable
private fun WhatIsIfcSection() {
    SectionHeading(stringResource(R.string.learn_section_what))
    BodyParagraph(stringResource(R.string.learn_what_months))
    BodyParagraph(stringResource(R.string.learn_what_sol))
    BodyParagraph(stringResource(R.string.learn_what_friday_13))
}

/** Year Day and Leap Day: where they live, with a computed worked example of each (calendar-spec §2.4). */
@Composable
private fun FloatingDaysSection(formatter: IfcDateFormatter) {
    SectionHeading(stringResource(R.string.learn_section_floating))
    BodyParagraph(stringResource(R.string.learn_floating_intro))
    BodyParagraph(
        stringResource(
            R.string.learn_floating_year_day,
            formatter.formatLong(LearnFacts.yearDayExample),
            formatter.formatGregorianLong(LearnFacts.yearDayExample.toLocalDate()),
        ),
    )
    BodyParagraph(
        stringResource(
            R.string.learn_floating_leap_day,
            formatter.formatLong(LearnFacts.leapDayExample),
            formatter.formatGregorianLong(LearnFacts.leapDayExample.toLocalDate()),
        ),
    )
}

/** Why nominal and actual weekdays differ, with the spec's own worked example (calendar-spec §4.1). */
@Composable
private fun WeekdaySection(formatter: IfcDateFormatter) {
    SectionHeading(stringResource(R.string.learn_section_weekday))
    BodyParagraph(stringResource(R.string.learn_weekday_intro))
    val nominal = requireNotNull(LearnFacts.weekdayExampleIfc.nominalDayOfWeek)
    BodyParagraph(
        stringResource(
            R.string.learn_weekday_worked_example,
            formatter.formatGregorianLong(LearnFacts.weekdayExampleGregorian),
            formatter.formatLong(LearnFacts.weekdayExampleIfc),
            formatter.weekdayName(nominal),
            formatter.weekdayName(LearnFacts.weekdayExampleIfc.actualDayOfWeek),
        ),
    )
    BodyParagraph(stringResource(R.string.learn_weekday_conclusion))
}

/** How dates are calculated: day-of-year equality, the fixed Sol 1 / Year Day dates, the leap shift. */
@Composable
private fun CalculationSection(formatter: IfcDateFormatter) {
    SectionHeading(stringResource(R.string.learn_section_calculation))
    BodyParagraph(
        stringResource(R.string.learn_calc_day_of_year, formatter.formatNumeric(LearnFacts.weekdayExampleIfc)),
    )
    BodyParagraph(
        stringResource(
            R.string.learn_calc_sol1,
            formatter.formatLong(LearnFacts.sol1CommonYear),
            formatter.formatGregorianLong(LearnFacts.sol1CommonYear.toLocalDate()),
            formatter.formatLong(LearnFacts.sol1LeapYear),
            formatter.formatGregorianLong(LearnFacts.sol1LeapYear.toLocalDate()),
        ),
    )
    BodyParagraph(
        stringResource(
            R.string.learn_calc_year_day,
            formatter.formatLong(LearnFacts.yearDayExample),
            formatter.formatGregorianLong(LearnFacts.yearDayExample.toLocalDate()),
        ),
    )
    BodyParagraph(
        stringResource(
            R.string.learn_calc_leap_shift,
            formatter.formatLong(LearnFacts.march1CommonYear),
            LearnFacts.COMMON_YEAR_EXAMPLE,
            formatter.formatLong(LearnFacts.march1LeapYear),
            LearnFacts.LEAP_YEAR_EXAMPLE,
        ),
    )
    val friday = requireNotNull(LearnFacts.friday13Example.nominalDayOfWeek)
    BodyParagraph(
        stringResource(
            R.string.learn_calc_friday_13,
            formatter.formatLong(LearnFacts.friday13Example),
            formatter.weekdayName(friday),
        ),
    )
}

/** A brief, spec-faithful history: Cotsworth, the League of Nations, Eastman Kodak (calendar-spec §1). */
@Composable
private fun HistorySection() {
    SectionHeading(stringResource(R.string.learn_section_history))
    BodyParagraph(stringResource(R.string.learn_history_cotsworth))
    BodyParagraph(stringResource(R.string.learn_history_league_of_nations))
    BodyParagraph(stringResource(R.string.learn_history_kodak))
    BodyParagraph(stringResource(R.string.learn_history_never_adopted))
}

/** The FAQ answering the documented confusions from docs/FEATURES.md Part 1. */
@Composable
private fun FaqSection() {
    SectionHeading(stringResource(R.string.learn_section_faq))
    FaqItem(stringResource(R.string.learn_faq_lunar_question), stringResource(R.string.learn_faq_lunar_answer))
    FaqItem(
        stringResource(R.string.learn_faq_year_start_question),
        stringResource(R.string.learn_faq_year_start_answer),
    )
    FaqItem(
        stringResource(R.string.learn_faq_sol_vs_leap_question),
        stringResource(R.string.learn_faq_sol_vs_leap_answer),
    )
    FaqItem(
        stringResource(R.string.learn_faq_other_calendars_question),
        stringResource(R.string.learn_faq_other_calendars_answer),
    )
    FaqItem(
        stringResource(R.string.learn_faq_proleptic_question),
        stringResource(R.string.learn_faq_proleptic_answer),
    )
}

/**
 * One expandable FAQ entry. The whole question row is the toggle, so TalkBack reads it as one button
 * whose [stateDescription] announces expanded/collapsed; the answer is only in the tree while expanded,
 * which is also what keeps [FaqSection] short until the reader asks for more (no walls of text).
 */
@Composable
private fun FaqItem(
    question: String,
    answer: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val stateLabel =
        stringResource(if (expanded) R.string.learn_faq_state_expanded else R.string.learn_faq_state_collapsed)
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = stateLabel }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = question, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) FAQ_ICON_EXPANDED_ROTATION else 0f),
            )
        }
        if (expanded) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun BodyParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Degrees the FAQ chevron rotates to when its answer is expanded (pointing up instead of down). */
private const val FAQ_ICON_EXPANDED_ROTATION = 180f

@Preview(name = "Learn", showBackground = true, heightDp = 1200)
@Composable
internal fun LearnScreenPreview() {
    IfcTheme(dynamicColor = false) {
        LearnScreen(onBack = {})
    }
}

@Preview(name = "Learn — dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LearnScreenDarkPreview() {
    IfcTheme(darkTheme = true, dynamicColor = false) {
        LearnScreen(onBack = {})
    }
}

@Preview(name = "Learn — font 2.0", showBackground = true, heightDp = 2400, fontScale = 2f)
@Composable
internal fun LearnScreenLargeFontPreview() {
    IfcTheme(dynamicColor = false) {
        LearnScreen(onBack = {})
    }
}
