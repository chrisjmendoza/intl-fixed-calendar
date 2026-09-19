package io.github.chrisjmendoza.yearal.core.data.event.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import io.github.chrisjmendoza.yearal.core.data.event.entity.ReminderEntity

/** Row access for `reminders`. Reminders have no domain id; they are always replaced whole. */
@Dao
public interface ReminderDao {
    /** Inserts rows; part of `upsertEvent`'s whole-aggregate replace. */
    @Insert
    public suspend fun insertAll(entities: List<ReminderEntity>)

    /** Deletes every reminder of [eventId], before `upsertEvent` re-inserts the current set. */
    @Query("DELETE FROM reminders WHERE event_id = :eventId")
    public suspend fun deleteAllForEvent(eventId: Long)

    /** How many reminder rows [eventId] has; `0` once its event is deleted (the cascade). */
    @Query("SELECT COUNT(*) FROM reminders WHERE event_id = :eventId")
    public suspend fun countForEvent(eventId: Long): Int
}
