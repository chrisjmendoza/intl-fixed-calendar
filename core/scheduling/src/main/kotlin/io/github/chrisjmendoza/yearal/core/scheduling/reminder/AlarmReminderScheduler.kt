package io.github.chrisjmendoza.yearal.core.scheduling.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceExpander
import io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import io.github.chrisjmendoza.yearal.core.scheduling.armWakeup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The app's [ReminderScheduler] (ROADMAP M6 T1; FEATURES E4): the **single next-alarm** pattern of
 * `docs/ARCHITECTURE.md` §3.2 "Reminders".
 *
 * One `AlarmManager` alarm is armed for the earliest reminder that is still ahead, across every event
 * — never one alarm per reminder, so the 500-alarm cap is never approached. When that alarm is
 * delivered, [ReminderAlarmReceiver] calls [reschedule] again, which posts everything that has come
 * due and arms the next one. [reschedule] is also the [DayRolloverListener] this class contributes,
 * so a reboot, a clock or zone change, an app update and every midnight recompute it; the repository
 * calls it after every successful write (`docs/contracts/Events.md` §5).
 *
 * **Nothing is cached between calls except how far delivery has got.** "Now", the device zone, the
 * candidate events and their occurrences are all recomputed from the injected [Clock],
 * [ZoneProvider], [EventRepository] and [RecurrenceExpander] on every call, which is what makes the
 * result correct after a late delivery, a backwards clock change or a flight across zones
 * (`DayRolloverListener`: "a call is a hint, not a fact").
 *
 * ## Late reminders
 *
 * A reminder whose instant passed while the device was off, in Doze, or before the process existed is
 * **delivered late if it is no more than [LATE_GRACE] old, and dropped otherwise**. Late delivery is
 * what makes a reminder survive a reboot at all (a reboot cancels every alarm and the app is only
 * called again at `BOOT_COMPLETED`), and the bound is what keeps a phone that was off for a week from
 * emptying a fortnight of reminders into the shade at once. [LATE_GRACE] is comfortably longer than
 * the worst legitimate delay of the windowed fallback
 * ([io.github.chrisjmendoza.yearal.core.scheduling.DayRolloverScheduler.WINDOW]).
 *
 * [deliveredUpTo] is the high-water mark that keeps this from repeating itself: reminders at or
 * before it are never considered again, so recomputing twice in a row posts nothing twice and can
 * never re-arm an alarm for an instant that has just been handled. It is **in-memory**, so a fresh
 * process starts with an empty mark and may re-post a reminder from the last [LATE_GRACE] once; a
 * notification id is stable, so that replaces the same notification rather than adding one.
 *
 * ## What is *not* done here
 *
 * Asking for `POST_NOTIFICATIONS` (the event editor does that, in context) and routing a tap to the
 * event (`IntentRouter`, a later task). When the permission is denied, this class still runs in full
 * and simply posts nothing (FEATURES P2).
 */
@Singleton
internal class AlarmReminderScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        // A Provider, not the repository itself: the production EventRepository injects a
        // ReminderScheduler to call after each write, so taking it directly would be a Dagger cycle.
        private val repository: Provider<EventRepository>,
        private val expander: RecurrenceExpander,
        private val clock: Clock,
        private val zoneProvider: ZoneProvider,
        private val notifier: ReminderNotifier,
    ) : ReminderScheduler,
        DayRolloverListener {
        /** Serialises recomputations, so two triggers at once cannot both post the same reminder. */
        private val mutex = Mutex()

        /**
         * Instant up to and including which reminders have been dealt with — posted, or found too old
         * to post. See "Late reminders" on the class.
         */
        private var deliveredUpTo: Instant = Instant.MIN

        /**
         * Posts every reminder that has come due since the last call, then arms one alarm for the
         * earliest reminder still ahead, or cancels the alarm when there is none.
         *
         * Main-safe (the repository's query is), idempotent, and quiet for an empty store.
         */
        override suspend fun reschedule() {
            mutex.withLock { refresh() }
        }

        /**
         * The day-rollover hook: the same recomputation, for every [DayRolloverTrigger]. A reboot or
         * an app update cancelled the alarm, a clock or zone change moved every instant, and midnight
         * is simply the next chance to notice. Which trigger it was makes no difference — the whole
         * computation is redone from the clock either way.
         */
        override suspend fun onDayRollover(trigger: DayRolloverTrigger) {
            reschedule()
        }

        /** The body of [reschedule]; always called under [mutex]. */
        private suspend fun refresh() {
            val now = clock.instant()
            val deviceZone = zoneProvider.currentZone()
            val today = now.atZone(deviceZone).toLocalDate()
            val candidates = repository.get().getReminderCandidates(today)
            val pending =
                ReminderPlanner.pending(
                    events = candidates,
                    expander = expander,
                    deviceZone = deviceZone,
                    // A zoned occurrence can be shown up to ZONE_SKEW_DAYS from its own date, and its
                    // reminder can therefore still be ahead while its own date is already behind.
                    from = today.minusDays(EventRepository.ZONE_SKEW_DAYS),
                    after = deliveredUpTo,
                    now = now,
                )
            val due = pending.takeWhile { !it.triggerAt.isAfter(now) }
            if (due.isNotEmpty()) {
                val notTooLate = due.filter { !it.triggerAt.isBefore(now.minus(LATE_GRACE)) }
                notifier.post(notTooLate, candidates.byId(), deviceZone)
                deliveredUpTo = now
            }
            val next = pending.firstOrNull { it.triggerAt.isAfter(now) }
            if (next == null) cancel() else arm(next.triggerAt)
        }

        /**
         * Arms the one reminder alarm for [triggerAt], replacing whatever was armed before. Exact
         * when the app may use exact alarms, a windowed alarm when it may not — the shared policy of
         * [armWakeup].
         */
        private fun arm(triggerAt: Instant) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.armWakeup(triggerAt, reminderOperation(context), WINDOW)
        }

        /** Cancels the alarm; nothing is due, so nothing should wake the device. */
        private fun cancel() {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(reminderOperation(context))
        }

        /** Constants shared with [ReminderAlarmReceiver] and the tests. */
        companion object {
            /**
             * Action of the reminder alarm broadcast. The intent is explicit, so this is not used for
             * routing; [ReminderAlarmReceiver] checks it so that it acts on nothing but this alarm.
             */
            const val ACTION_REMINDER: String =
                "io.github.chrisjmendoza.yearal.core.scheduling.action.REMINDER"

            /**
             * How late a reminder may be and still be posted; older ones are dropped silently. See
             * "Late reminders" on the class.
             */
            val LATE_GRACE: Duration = Duration.ofMinutes(15)

            /** Delivery window when exact alarms are unavailable: the same 10 minutes as the rollover. */
            val WINDOW: Duration = Duration.ofMinutes(10)

            /** The app has one reminder alarm, so one fixed request code — distinct from the rollover's. */
            const val REQUEST_CODE: Int = 1

            /**
             * The one `PendingIntent` of the reminder alarm: explicit (component set), immutable, and
             * **without extras** — which reminder fired is recomputed from the store, never carried
             * (`docs/security-and-privacy.md` §6.4; CLAUDE.md rule 8). `FLAG_UPDATE_CURRENT` returns
             * the existing instance on later calls, so arming replaces rather than adds.
             */
            fun reminderOperation(context: Context): PendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_REMINDER),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
        }
    }

/** The candidate events by id, for looking up a title at delivery time. */
private fun List<Event>.byId(): Map<Long, Event> = associateBy { it.id }
