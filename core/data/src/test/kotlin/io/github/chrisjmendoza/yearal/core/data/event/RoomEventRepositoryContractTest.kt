package io.github.chrisjmendoza.yearal.core.data.event

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.testing.FakeRecurrenceExpander
import io.github.chrisjmendoza.yearal.core.testing.FakeReminderScheduler
import io.github.chrisjmendoza.yearal.core.testing.FakeWidgetUpdater
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Rule
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Runs [EventRepositoryContractTest] against [RoomEventRepository] over an in-memory Room 3 database
 * (`docs/ARCHITECTURE.md` §6: "Room 3 DAO tests on the JVM with `BundledSQLiteDriver` in memory"), so
 * the real storage implementation is proven to agree with
 * `io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository` on every shared behaviour
 * ([FakeEventRepositoryContractTest]). `RecurrenceExpander`, `ReminderScheduler` and `WidgetUpdater` are
 * the fakes from `:core:testing`, since this suite is about storage, not recurrence evaluation,
 * scheduling or widget rendering.
 *
 * **[Dispatchers.IO], not a `TestDispatcher`, for Room's own query context.** Room 3's connection pool
 * runs its own coroutine machinery (a mutex-guarded single connection for an in-memory database) on
 * whatever `CoroutineContext` `setQueryCoroutineContext` names; pairing that with a virtual-time test
 * dispatcher (`UnconfinedTestDispatcher`/`StandardTestDispatcher`) deadlocked every test in this class
 * (found while building this suite — see [RoomEventRepository]'s KDoc for the write-side half of the
 * fix). `runTest` in each test method still controls the *test body's* virtual time; only Room's
 * internal dispatch runs on a real thread pool.
 */
@RunWith(AndroidJUnit4::class)
public class RoomEventRepositoryContractTest : EventRepositoryContractTest() {
    /** Fails a hung test after 60 s instead of hanging the run forever; see the class KDoc. */
    @get:Rule
    public val timeout: Timeout = Timeout.seconds(60)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = Dispatchers.IO
    private val database = YearalDatabase.createInMemory(context, dispatcher)
    private val expander = FakeRecurrenceExpander()
    private val reminderScheduler = FakeReminderScheduler()
    private val widgetUpdater = FakeWidgetUpdater()

    override val clock: MutableClock = MutableClock(FIXED_INSTANT)
    override val repository: EventRepository =
        RoomEventRepository(
            database = database,
            calendarDao = database.calendarDao(),
            eventDao = database.eventDao(),
            exdateDao = database.exdateDao(),
            reminderDao = database.reminderDao(),
            clock = clock,
            recurrenceExpander = expander,
            reminderScheduler = reminderScheduler,
            widgetUpdater = widgetUpdater,
        )

    override suspend fun prepareForStorage(event: Event) {
        // upsertEvent asks RecurrenceExpander.recurrenceEndDate for every write; script the anchor as
        // the event's only occurrence, unbounded, since this suite does not assert on the pruning
        // column (that is RoomEventRepositoryTest's job, and the expander's own oracle is T3's).
        if (event.isRecurring) {
            expander.scriptDates(event, listOf(event.startDate), unbounded = true)
        }
    }

    @After
    public fun closeDatabase() {
        database.close()
    }
}
