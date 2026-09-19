package io.github.chrisjmendoza.yearal.core.data.event

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.chrisjmendoza.yearal.core.data.event.dao.CalendarDao
import io.github.chrisjmendoza.yearal.core.data.event.dao.EventDao
import io.github.chrisjmendoza.yearal.core.data.event.dao.ExdateDao
import io.github.chrisjmendoza.yearal.core.data.event.dao.ReminderDao
import io.github.chrisjmendoza.yearal.core.data.event.entity.CalendarEntity
import io.github.chrisjmendoza.yearal.core.data.event.entity.EventEntity
import io.github.chrisjmendoza.yearal.core.data.event.entity.EventExdateEntity
import io.github.chrisjmendoza.yearal.core.data.event.entity.ReminderEntity
import io.github.chrisjmendoza.yearal.core.domain.event.CalendarSource
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The Room 3 database of `docs/ARCHITECTURE.md` §3.2, schema version 1 (exported to
 * `core/data/schemas/` — `docs/contracts/Events.md` "T2", FEATURES Q9). `androidx.room3`, KSP-only,
 * coroutines-only (`docs/adr/0001-toolchain.md`); there is no legacy `SupportSQLiteOpenHelper` path.
 *
 * Built only through [create] / [createInMemory], never with a bare [Room.databaseBuilder] elsewhere,
 * so the seed callback and the driver are never forgotten.
 */
@androidx.room3.Database(
    entities = [CalendarEntity::class, EventEntity::class, EventExdateEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = true,
)
public abstract class YearalDatabase : RoomDatabase() {
    /** DAO for the `calendars` table. */
    public abstract fun calendarDao(): CalendarDao

    /** DAO for the `events` table (joined with its relations where the aggregate is needed). */
    public abstract fun eventDao(): EventDao

    /** DAO for the `event_exdates` table. */
    public abstract fun exdateDao(): ExdateDao

    /** DAO for the `reminders` table. */
    public abstract fun reminderDao(): ReminderDao

    public companion object {
        private const val FILE_NAME = "yearal.db"

        /**
         * The file-backed production database under `filesDir` (Auto Backup's default include set,
         * `docs/security-and-privacy.md` §4.1). [dispatcher] is where Room runs its queries
         * (`RoomDatabase.Builder.setQueryCoroutineContext`); it must not be the main thread.
         */
        public fun create(
            context: Context,
            dispatcher: CoroutineDispatcher,
        ): YearalDatabase =
            Room
                .databaseBuilder(context, YearalDatabase::class.java, FILE_NAME)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(dispatcher)
                .addCallback(SeedBuiltInCalendarCallback)
                .build()

        /** An in-memory database for tests: same schema, same seed callback, no file on disk. */
        public fun createInMemory(
            context: Context,
            dispatcher: CoroutineDispatcher,
        ): YearalDatabase =
            Room
                .inMemoryDatabaseBuilder(context, YearalDatabase::class.java)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(dispatcher)
                .addCallback(SeedBuiltInCalendarCallback)
                .build()
    }
}

/**
 * Inserts the built-in local calendar ([EventCalendar.DEFAULT]) as row 1, once, the moment the
 * database file (or in-memory database) is first created — "row 1 is created with the database"
 * (`docs/ARCHITECTURE.md` §3.2). `RoomEventRepository.deleteAllData` re-inserts the same row after
 * clearing every table, since a fresh `onCreate` never fires again on an existing file.
 *
 * Written against the raw [SQLiteConnection] (bind parameters, not string interpolation) because no
 * DAO exists before the database itself finishes building.
 */
internal object SeedBuiltInCalendarCallback : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        val statement =
            connection.prepare(
                "INSERT INTO calendars (id, name, color_argb, source, visible) VALUES (?, ?, ?, ?, ?)",
            )
        try {
            statement.bindLong(1, EventCalendar.DEFAULT_ID)
            statement.bindText(2, EventCalendar.DEFAULT.name)
            statement.bindLong(3, EventCalendar.DEFAULT_COLOR_ARGB.toLong())
            statement.bindText(4, CalendarSource.LOCAL.name)
            statement.bindLong(5, 1L)
            statement.step()
        } finally {
            statement.close()
        }
    }
}
