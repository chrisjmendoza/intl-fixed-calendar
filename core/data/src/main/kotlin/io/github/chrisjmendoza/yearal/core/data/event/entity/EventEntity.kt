package io.github.chrisjmendoza.yearal.core.data.event.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * The `events` row of `docs/ARCHITECTURE.md` §3.2, exactly as specified there. All dates are Gregorian
 * epoch days ([java.time.LocalDate.toEpochDay]); no IFC value is stored except [ifcRule] (CLAUDE.md
 * rule 4). See `event/mapper/EventMappers.kt` for the conversion to and from
 * [io.github.chrisjmendoza.yearal.core.domain.event.Event] and its fail-soft handling of a row that no
 * longer satisfies the model's invariants.
 *
 * @property id row id; `0` before insert.
 * @property uid the `.ics` `UID`; unique ([Index] below).
 * @property calendarId the owning [CalendarEntity.id]; cascades on delete.
 * @property title one line; may be blank.
 * @property description free text; empty for none.
 * @property location free text; empty for none.
 * @property colorArgb `null` to inherit the calendar's colour.
 * @property category `"EVENT"` / `"OBSERVANCE"` / `"BIRTHDAY"`.
 * @property allDay whether [startMinuteOfDay] is meaningful; **`null` iff `true`** is the invariant
 *   [startMinuteOfDay] must hold.
 * @property startEpochDay local date the first occurrence starts on, in the event's own zone.
 * @property startMinuteOfDay minutes after local midnight, 0..1439; `null` **iff** [allDay].
 * @property durationMinutes all-day: `days × 1440`; timed: nominal wall-clock minutes.
 * @property endEpochDay denormalised last local date the first occurrence touches; the range-index
 *   column for `observeAgendaCandidates` and `getReminderCandidates`.
 * @property zoneId `null` = floating (device zone); always `null` when [allDay].
 * @property recurrenceType `0` none, `1` Gregorian `RRULE`, `2` IFC rule.
 * @property rrule the RFC 5545 `RRULE` value; non-`null` **iff** [recurrenceType] is `1`.
 * @property ifcRule [io.github.chrisjmendoza.yearal.core.domain.event.IfcRuleText] text; non-`null`
 *   **iff** [recurrenceType] is `2`.
 * @property recurrenceUntilEpochDay `RecurrenceExpander.recurrenceEndDate(event)`, a write-time
 *   parameter the mapper does not compute (`docs/contracts/Events.md` "T2"); `null` = unbounded,
 *   unsupported, or not recurring but unterminated (never used for [recurrenceType] `0`, where
 *   [endEpochDay] already carries the only occurrence's last date).
 * @property createdAt epoch milliseconds; repository-owned, from the injected `Clock`.
 * @property updatedAt epoch milliseconds; `≥ createdAt`.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendar_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("start_epoch_day"),
        Index("end_epoch_day"),
        Index(value = ["recurrence_type", "recurrence_until_epoch_day"]),
        Index("calendar_id"),
        Index(value = ["uid"], unique = true),
    ],
)
public data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "uid")
    val uid: String,
    @ColumnInfo(name = "calendar_id")
    val calendarId: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "location")
    val location: String,
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int?,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "all_day")
    val allDay: Boolean,
    @ColumnInfo(name = "start_epoch_day")
    val startEpochDay: Long,
    @ColumnInfo(name = "start_minute_of_day")
    val startMinuteOfDay: Int?,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,
    @ColumnInfo(name = "end_epoch_day")
    val endEpochDay: Long,
    @ColumnInfo(name = "zone_id")
    val zoneId: String?,
    @ColumnInfo(name = "recurrence_type")
    val recurrenceType: Int,
    @ColumnInfo(name = "rrule")
    val rrule: String?,
    @ColumnInfo(name = "ifc_rule")
    val ifcRule: String?,
    @ColumnInfo(name = "recurrence_until_epoch_day")
    val recurrenceUntilEpochDay: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
