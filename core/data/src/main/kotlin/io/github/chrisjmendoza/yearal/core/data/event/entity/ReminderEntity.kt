package io.github.chrisjmendoza.yearal.core.data.event.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * The `reminders` row of `docs/ARCHITECTURE.md` §3.2. A [io.github.chrisjmendoza.yearal.core.domain.event.Reminder]
 * carries no id of its own; [id] exists only because Room primary keys are simplest as a single
 * surrogate column, and it never leaves this module.
 *
 * @property id row id; `0` before insert.
 * @property eventId the owning [EventEntity.id]; cascades on delete.
 * @property minutesBefore `≥ 0`; unique per event ([Index]).
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["event_id", "minutes_before"], unique = true),
    ],
)
public data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "minutes_before")
    val minutesBefore: Int,
)
