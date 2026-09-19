package io.github.chrisjmendoza.yearal.core.data.event.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey

/**
 * The `event_exdates` row of `docs/ARCHITECTURE.md` §3.2: "delete this occurrence". The key is the
 * occurrence's own wall-clock start date ([io.github.chrisjmendoza.yearal.core.domain.event.Occurrence.occurrenceDate]),
 * never the device-zone date it is shown on (`docs/adr/0005-events-contract.md` decision 5).
 *
 * The composite primary key is `(eventId, epochDay)`, which also serves as the required index on the
 * foreign key's leading column, so no extra index is declared.
 *
 * @property eventId the owning [EventEntity.id]; cascades on delete.
 * @property epochDay the excluded occurrence's own start date, [java.time.LocalDate.toEpochDay].
 */
@Entity(
    tableName = "event_exdates",
    primaryKeys = ["event_id", "epoch_day"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
public data class EventExdateEntity(
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "epoch_day")
    val epochDay: Long,
)
