package io.github.chrisjmendoza.yearal.core.scheduling.reminder

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.scheduling.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts reminder notifications (FEATURES E4) the way `docs/security-and-privacy.md` §3.3 requires:
 * one channel, `VISIBILITY_PRIVATE` with a **redacted public version** so a lock screen that hides
 * sensitive content shows "Event reminder" and a time and no event text (FEATURES P3), no
 * full-screen intent, and a tap that opens the app through an explicit, immutable `PendingIntent`
 * that carries nothing (§6.4; CLAUDE.md rule 8).
 *
 * **Notifications are optional, not required.** On API 33 and later, posting without
 * `POST_NOTIFICATIONS` is a no-op: nothing is posted, nothing is logged and nothing throws, and the
 * caller still re-arms its alarm, so the app keeps working exactly as before when the user says no
 * (FEATURES P2). The in-context permission request belongs to the event editor, not here.
 */
@Singleton
internal class ReminderNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /**
         * Posts one notification per entry of [due], reading the title from [events].
         *
         * Entries whose event is not in [events] are skipped (it was deleted between the query and
         * the delivery). Ids are stable, so posting the same reminder twice replaces the notification
         * instead of adding one.
         *
         * @param due the reminders whose time has come, in delivery order.
         * @param events the events of [due], by id, as loaded by this same recomputation.
         * @param deviceZone the zone times are shown in.
         */
        fun post(
            due: List<PendingReminder>,
            events: Map<Long, Event>,
            deviceZone: ZoneId,
        ) {
            if (due.isEmpty()) return
            // API 33+ makes notifications a runtime permission. The check is inline so that Android
            // Lint's MissingPermission can see it guarding the notify() call below.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(channel())
            for (reminder in due) {
                val event = events[reminder.eventId] ?: continue
                manager.notify(notificationId(reminder), build(reminder, event, deviceZone))
            }
        }

        /**
         * The channel every reminder is posted on, rebuilt and handed to the platform on each post.
         * `createNotificationChannel` is idempotent — creating an existing channel updates its name
         * and description and never resets what the user changed (importance, sound).
         *
         * `IMPORTANCE_HIGH` so a reminder can be a heads-up notification, which is what a calendar
         * reminder is for. That is a normal high-importance notification, **not** a full-screen
         * intent: Play reserves those for alarm and calling apps
         * (`docs/security-and-privacy.md` §3.3).
         */
        private fun channel(): NotificationChannelCompat =
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.reminder_channel_name))
                .setDescription(context.getString(R.string.reminder_channel_description))
                .build()

        /** The notification for one reminder: title and time only, never the description (§3.3). */
        private fun build(
            reminder: PendingReminder,
            event: Event,
            deviceZone: ZoneId,
        ): Notification {
            val time = timeText(reminder, deviceZone)
            return baseBuilder()
                .setContentTitle(event.title.ifBlank { context.getString(R.string.reminder_untitled_event) })
                .setContentText(time)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(time))
                .setAutoCancel(true)
                .build()
        }

        /**
         * The redacted copy the system shows instead of the real one when the lock screen hides
         * sensitive content: a fixed localized label and the time, and **no title, notes or
         * location** (FEATURES P3; `docs/security-and-privacy.md` §3.3).
         */
        private fun publicVersion(time: String): Notification =
            baseBuilder()
                .setContentTitle(context.getString(R.string.reminder_public_title))
                .setContentText(time)
                .build()

        /** What both copies share: the icon and the tap target. */
        private fun baseBuilder(): NotificationCompat.Builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_reminder_notification)
                .setShowWhen(false)
                .apply { contentIntent()?.let(::setContentIntent) }

        /**
         * When the occurrence starts, as the user reads it: a localized short time, or "All day".
         *
         * The device's 12/24-hour override is not consulted; the format follows the locale, like the
         * rest of the app's `java.time` formatting.
         */
        private fun timeText(
            reminder: PendingReminder,
            deviceZone: ZoneId,
        ): String =
            if (reminder.allDay) {
                context.getString(R.string.reminder_all_day)
            } else {
                DateTimeFormatter
                    .ofLocalizedTime(FormatStyle.SHORT)
                    .withLocale(Locale.getDefault())
                    .format(reminder.reference.withZoneSameInstant(deviceZone))
            }

        /**
         * Opens the app's own launcher activity, resolved through `PackageManager` exactly as the
         * widgets do (`:core:scheduling` cannot name `MainActivity`, which lives in `:app`).
         *
         * Explicit, immutable and **without extras**: no event id, date or text rides along
         * (`docs/security-and-privacy.md` §6.4; CLAUDE.md rule 8). Routing a tap to the event's own
         * day is `IntentRouter`'s job in a later task, and needs the extras allow-list of §6.3 first.
         *
         * `null` only if the platform cannot resolve this app's launcher, in which case the
         * notification is still posted and simply does nothing when tapped — there is no safe
         * implicit intent to fall back to.
         */
        private fun contentIntent(): PendingIntent? {
            val launch =
                context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    // Required to start an activity from a notification, outside an activity context.
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?: return null
            return PendingIntent.getActivity(
                context,
                CONTENT_REQUEST_CODE,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        companion object {
            /** The app's single reminder channel (`docs/security-and-privacy.md` §3.3). */
            const val CHANNEL_ID: String = "reminders"

            /** Request code of the shared "open the app" `PendingIntent`; every tap target is identical. */
            private const val CONTENT_REQUEST_CODE: Int = 100

            /**
             * A stable notification id for a reminder, derived from **ids and dates only**: the same
             * reminder always replaces its own notification instead of adding a second one, across
             * processes and reboots.
             */
            fun notificationId(reminder: PendingReminder): Int {
                var hash = reminder.eventId.hashCode()
                hash = 31 * hash + reminder.occurrenceDate.toEpochDay().hashCode()
                hash = 31 * hash + reminder.minutesBefore
                return hash
            }
        }
    }
