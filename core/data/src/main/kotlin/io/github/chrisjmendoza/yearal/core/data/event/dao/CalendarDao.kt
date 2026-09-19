package io.github.chrisjmendoza.yearal.core.data.event.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.chrisjmendoza.yearal.core.data.event.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow

/**
 * Row access for the `calendars` table (`docs/ARCHITECTURE.md` §3.2). `RoomEventRepository` owns the
 * contract-level semantics (built-in calendar protection, id assignment, cascade); this DAO is plain
 * parameterised SQL, as `docs/security-and-privacy.md` requires.
 */
@Dao
public interface CalendarDao {
    /** Every row ordered by id; what `EventRepository.observeCalendars` is built on. */
    @Query("SELECT * FROM calendars ORDER BY id")
    public fun observeAll(): Flow<List<CalendarEntity>>

    /** The row with [id], or `null`. */
    @Query("SELECT * FROM calendars WHERE id = :id")
    public suspend fun findById(id: Long): CalendarEntity?

    /**
     * Inserts [entity]. An [entity] whose id is `0` is assigned the next id; a non-zero id is used
     * as-is (the seed row and `deleteAllData`'s restore of the built-in calendar rely on this).
     * Returns the id the row was stored under.
     */
    @Insert
    public suspend fun insert(entity: CalendarEntity): Long

    /** Replaces the row with `entity.id`. Returns the number of rows changed: `0` if there was none. */
    @Update
    public suspend fun update(entity: CalendarEntity): Int

    /** Deletes the row with [id] (cascading to its events). Returns the number of rows changed. */
    @Query("DELETE FROM calendars WHERE id = :id")
    public suspend fun deleteById(id: Long): Int

    /** Deletes every calendar; used by `deleteAllData` before the built-in row is re-seeded. */
    @Query("DELETE FROM calendars")
    public suspend fun deleteAll()
}
