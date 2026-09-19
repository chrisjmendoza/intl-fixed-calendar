package io.github.chrisjmendoza.yearal.core.data.event.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import io.github.chrisjmendoza.yearal.core.data.event.entity.EventEntity
import io.github.chrisjmendoza.yearal.core.data.event.entity.EventWithRelations
import kotlinx.coroutines.flow.Flow

/**
 * Row access for `events`, joined with `event_exdates` and `reminders` through
 * [EventWithRelations] wherever the whole aggregate is read. Every list here is
 * `ORDER BY start_epoch_day, start_minute_of_day, id` (`Event.LIST_ORDER`,
 * `docs/ARCHITECTURE.md` §3.2). The candidate queries implement §3.4: `lo`/`hi` are the caller's
 * range already padded by `EventRepository.ZONE_SKEW_DAYS`, and only visible calendars are joined in.
 * All parameterised; no string concatenation (`docs/security-and-privacy.md`).
 */
@Dao
public interface EventDao {
    /** Every event, hidden calendars included, with its exdates and reminders. */
    @Transaction
    @Query("SELECT * FROM events ORDER BY start_epoch_day, start_minute_of_day, id")
    public fun observeAllWithRelations(): Flow<List<EventWithRelations>>

    /** The event with [id] and its relations, or `null`; re-emits whenever any of the three tables change. */
    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    public fun observeOneWithRelations(id: Long): Flow<EventWithRelations?>

    /** The event with [id] and its relations right now, or `null`. */
    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    public suspend fun getWithRelations(id: Long): EventWithRelations?

    /** The bare row with [id] (no relations), for existence checks and updates. */
    @Query("SELECT * FROM events WHERE id = :id")
    public suspend fun findById(id: Long): EventEntity?

    /** The bare row with [uid], for the uid-uniqueness check; `null` if no event has it. */
    @Query("SELECT * FROM events WHERE uid = :uid")
    public suspend fun findByUid(uid: String): EventEntity?

    /** Inserts [entity]; `entity.id` must be `0`. Returns the assigned id. */
    @Insert
    public suspend fun insert(entity: EventEntity): Long

    /** Replaces the row with `entity.id`. Returns the number of rows changed. */
    @Update
    public suspend fun update(entity: EventEntity): Int

    /** Sets only `updated_at`, for `addExdate`/`removeExdate`, which touch no other column. */
    @Query("UPDATE events SET updated_at = :updatedAt WHERE id = :id")
    public suspend fun touchUpdatedAt(
        id: Long,
        updatedAt: Long,
    ): Int

    /** Deletes the row with [id] (cascading to its exdates and reminders). Returns rows changed. */
    @Query("DELETE FROM events WHERE id = :id")
    public suspend fun deleteById(id: Long): Int

    /** Deletes every event; used by `deleteAllData`. */
    @Query("DELETE FROM events")
    public suspend fun deleteAll()

    /**
     * The candidate query of `docs/ARCHITECTURE.md` §3.4, restricted to visible calendars: events with
     * `start_epoch_day <= hi` that are either non-recurring with `end_epoch_day >= lo`, or recurring
     * with a `recurrence_until_epoch_day` that is `NULL` or `>= lo`. A superset; exact filtering is the
     * expander's job.
     */
    @Transaction
    @Query(
        """
        SELECT events.* FROM events
        INNER JOIN calendars ON calendars.id = events.calendar_id
        WHERE calendars.visible = 1
          AND events.start_epoch_day <= :hi
          AND (
            (events.recurrence_type = 0 AND events.end_epoch_day >= :lo)
            OR (events.recurrence_type != 0 AND (events.recurrence_until_epoch_day IS NULL OR events.recurrence_until_epoch_day >= :lo))
          )
        ORDER BY events.start_epoch_day, events.start_minute_of_day, events.id
        """,
    )
    public fun observeAgendaCandidatesWithRelations(
        lo: Long,
        hi: Long,
    ): Flow<List<EventWithRelations>>

    /**
     * The reminder-candidate query: visible calendars, at least one reminder (the `INNER JOIN` on
     * `reminders`, de-duplicated), and either non-recurring with `end_epoch_day >= lo` or recurring
     * with a `recurrence_until_epoch_day` that is `NULL` or `>= lo`. No upper bound: this is a
     * forward-looking scan, not a windowed range.
     */
    @Transaction
    @Query(
        """
        SELECT DISTINCT events.* FROM events
        INNER JOIN calendars ON calendars.id = events.calendar_id
        INNER JOIN reminders ON reminders.event_id = events.id
        WHERE calendars.visible = 1
          AND (
            (events.recurrence_type = 0 AND events.end_epoch_day >= :lo)
            OR (events.recurrence_type != 0 AND (events.recurrence_until_epoch_day IS NULL OR events.recurrence_until_epoch_day >= :lo))
          )
        ORDER BY events.start_epoch_day, events.start_minute_of_day, events.id
        """,
    )
    public suspend fun getReminderCandidatesWithRelations(lo: Long): List<EventWithRelations>
}
