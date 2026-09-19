package io.github.chrisjmendoza.yearal.core.data.event.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import io.github.chrisjmendoza.yearal.core.data.event.entity.EventExdateEntity

/** Row access for `event_exdates`. `RoomEventRepository` owns the "delete this occurrence" semantics. */
@Dao
public interface ExdateDao {
    /** Every exdate of [eventId], for the aggregate mapper. */
    @Query("SELECT * FROM event_exdates WHERE event_id = :eventId")
    public suspend fun findForEvent(eventId: Long): List<EventExdateEntity>

    /** Inserts one row; part of `upsertEvent`'s whole-aggregate replace. */
    @Insert
    public suspend fun insertAll(entities: List<EventExdateEntity>)

    /** Inserts one exdate; used by `addExdate`. */
    @Insert
    public suspend fun insert(entity: EventExdateEntity)

    /** Deletes every exdate of [eventId], before `upsertEvent` re-inserts the current set. */
    @Query("DELETE FROM event_exdates WHERE event_id = :eventId")
    public suspend fun deleteAllForEvent(eventId: Long)

    /** Deletes one exdate; used by `removeExdate`. Returns the number of rows changed. */
    @Query("DELETE FROM event_exdates WHERE event_id = :eventId AND epoch_day = :epochDay")
    public suspend fun delete(
        eventId: Long,
        epochDay: Long,
    ): Int

    /** How many exdate rows [eventId] has; `0` once its event is deleted (the cascade). */
    @Query("SELECT COUNT(*) FROM event_exdates WHERE event_id = :eventId")
    public suspend fun countForEvent(eventId: Long): Int
}
