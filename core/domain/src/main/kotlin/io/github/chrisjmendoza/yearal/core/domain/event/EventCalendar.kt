package io.github.chrisjmendoza.yearal.core.domain.event

/**
 * A named, coloured group of events (the `calendars` table of `docs/ARCHITECTURE.md` §3.2). Not to be
 * confused with a calendar *system*: this is a container, like "Work" or an imported `.ics` file.
 *
 * Version 1.0 has one user-facing calendar, the built-in local one with [DEFAULT_ID]. It always
 * exists, cannot be deleted, and stays [CalendarSource.LOCAL] (see [EventRepository]). An `.ics`
 * import (1.2) becomes a calendar of its own with [CalendarSource.ICS].
 *
 * @property id the row id; [NEW_ID] (`0`) for a calendar that has not been stored yet.
 * @property name the user's name for it, at most [MAX_NAME_LENGTH] characters. **May be blank**, and is
 *   blank for the built-in calendar until the user renames it: the UI then shows a localized label
 *   from resources (CLAUDE.md rule 9), which also keeps the label right after a language change.
 * @property colorArgb the colour of its events as `0xAARRGGBB`, unless an event overrides it with
 *   [Event.colorArgb].
 * @property source where its events come from.
 * @property visible `false` hides the calendar's events from agendas, grids and reminders without
 *   deleting them ([EventRepository.observeAgendaCandidates]).
 * @throws IllegalArgumentException if [id] is negative or [name] is too long.
 */
public data class EventCalendar(
    val id: Long = NEW_ID,
    val name: String,
    val colorArgb: Int = DEFAULT_COLOR_ARGB,
    val source: CalendarSource = CalendarSource.LOCAL,
    val visible: Boolean = true,
) {
    init {
        require(id >= 0) { "Calendar id must not be negative: $id" }
        require(name.length <= MAX_NAME_LENGTH) { "Calendar name longer than $MAX_NAME_LENGTH characters" }
    }

    /** Limits and well-known values. */
    public companion object {
        /** The [id] of a calendar that has not been stored yet. */
        public const val NEW_ID: Long = 0L

        /** The id of the built-in local calendar, which every repository contains. */
        public const val DEFAULT_ID: Long = 1L

        /** Longest [name], in characters; one line of text, capped like [Event.MAX_TITLE_LENGTH]. */
        public const val MAX_NAME_LENGTH: Int = 500

        /** Brand teal `#123F3D`, opaque: the colour of a calendar nobody has recoloured. */
        public const val DEFAULT_COLOR_ARGB: Int = 0xFF123F3D.toInt()

        /**
         * The built-in local calendar as it is first created and as
         * [EventRepository.deleteAllData] restores it: [DEFAULT_ID], blank name, default colour,
         * local, visible.
         */
        public val DEFAULT: EventCalendar = EventCalendar(id = DEFAULT_ID, name = "")
    }
}

/** Where an [EventCalendar]'s events come from (the `source` column). */
public enum class CalendarSource {
    /** Created and edited in the app. */
    LOCAL,

    /** Imported from an `.ics` file (release 1.2, `docs/holidays-and-import.md` §4.2). */
    ICS,
}
