package io.github.chrisjmendoza.yearal.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/*
 * Every navigation destination in the app, as Navigation 3 keys (docs/ARCHITECTURE.md §4 "Screens and
 * navigation"). Features navigate to each other only through these keys, never by depending on one
 * another (CLAUDE.md rule 10). Keys are serializable so a back stack survives process death and can be
 * synthesized from a widget or notification intent.
 *
 * Dates travel as Gregorian epoch days (CLAUDE.md rule 4); the receiving screen converts through
 * `:core:calendar`.
 */

/** The Today screen: hero IFC date, Gregorian equivalent, both weekdays, agenda. */
@Serializable
data object TodayKey : NavKey

/**
 * One month of the calendar grid.
 *
 * @property year IFC year, 1583..9999 in the UI.
 * @property month IFC month number 1..13 (7 = Sol). **Not** a Gregorian month number.
 */
@Serializable
data class MonthKey(
    val year: Int,
    val month: Int,
) : NavKey

/** The Year overview: 13 mini-months plus the intercalary days. */
@Serializable
data class YearKey(
    val year: Int,
) : NavKey

/**
 * Day detail for one date.
 *
 * @property epochDay the Gregorian date as days since 1970-01-01 (`LocalDate.toEpochDay()`).
 */
@Serializable
data class DayKey(
    val epochDay: Long,
) : NavKey

/**
 * The Gregorian ↔ IFC converter.
 *
 * @property prefillEpochDay a Gregorian epoch day to start from, or `null` for the converter's default.
 */
@Serializable
data class ConverterKey(
    val prefillEpochDay: Long? = null,
) : NavKey

/** The events list and search. */
@Serializable
data object EventListKey : NavKey

/**
 * The event editor.
 *
 * @property eventId the event to edit, or `null` to create one. IDs only, never event content
 * (CLAUDE.md rule 8).
 * @property prefillEpochDay the Gregorian epoch day a new event should start on, or `null`.
 */
@Serializable
data class EventEditorKey(
    val eventId: Long? = null,
    val prefillEpochDay: Long? = null,
) : NavKey

/** The "More" tab hub, which links to Holidays, Settings and Learn. */
@Serializable
data object MoreKey : NavKey

/** Holiday sets: browse, toggle, per-year list. */
@Serializable
data object HolidaysKey : NavKey

/** Settings. */
@Serializable
data object SettingsKey : NavKey

/** Learn / About: the IFC rules and why the weekdays differ. */
@Serializable
data object LearnKey : NavKey

/** Privacy: what the app stores, what its permissions are for, and what it never does. */
@Serializable
data object PrivacyKey : NavKey
