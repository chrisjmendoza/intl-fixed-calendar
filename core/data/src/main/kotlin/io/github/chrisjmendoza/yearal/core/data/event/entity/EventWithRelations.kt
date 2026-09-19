package io.github.chrisjmendoza.yearal.core.data.event.entity

import androidx.room3.Embedded
import androidx.room3.Relation

/**
 * An [EventEntity] joined with its [EventExdateEntity] and [ReminderEntity] rows: the Room shape of
 * the `Event` aggregate ("event + exdates + reminders", `docs/contracts/Events.md` "T2"). Every query
 * that returns this type is `@androidx.room3.Transaction`, so the three tables are read together
 * consistently, and every write of the aggregate goes through one `withWriteTransaction` block in
 * `RoomEventRepository`.
 */
public data class EventWithRelations(
    @Embedded
    val event: EventEntity,
    @Relation(entity = EventExdateEntity::class, parentColumns = ["id"], entityColumns = ["event_id"])
    val exdates: List<EventExdateEntity>,
    @Relation(entity = ReminderEntity::class, parentColumns = ["id"], entityColumns = ["event_id"])
    val reminders: List<ReminderEntity>,
)
