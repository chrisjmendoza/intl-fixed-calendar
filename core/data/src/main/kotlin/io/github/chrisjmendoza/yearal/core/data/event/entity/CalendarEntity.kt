package io.github.chrisjmendoza.yearal.core.data.event.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar

/**
 * The `calendars` row of `docs/ARCHITECTURE.md` §3.2. Row [EventCalendar.DEFAULT_ID] is the built-in
 * local calendar, seeded when the database is created ([io.github.chrisjmendoza.yearal.core.data.event.YearalDatabaseCallback])
 * and restored by `RoomEventRepository.deleteAllData`.
 *
 * [source] and [category]-shaped enum columns are stored as their Kotlin enum
 * [Enum.name] (`"LOCAL"` / `"ICS"`), not as the raw ordinal, so the text survives a reordering of the
 * enum's declared cases; see the mappers in `event/mapper/` for the conversion and its fail-soft
 * handling of an unknown stored name.
 *
 * @property id row id; `0` before insert (Room assigns it). [PrimaryKey.autoGenerate] leaves the id
 *   alone when it is not `0`, which lets the seed row keep [EventCalendar.DEFAULT_ID].
 * @property name may be empty (`docs/adr/0005-events-contract.md` decision 8).
 * @property colorArgb `0xAARRGGBB`.
 * @property source `"LOCAL"` or `"ICS"` ([io.github.chrisjmendoza.yearal.core.domain.event.CalendarSource]).
 * @property visible `false` hides the calendar's events from agenda and reminder candidates.
 */
@Entity(tableName = "calendars")
public data class CalendarEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "visible")
    val visible: Boolean,
)
