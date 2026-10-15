package com.orirose.timetable.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.orirose.timetable.MainActivity
import com.orirose.timetable.R
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.domain.ReminderPlanner
import com.orirose.timetable.domain.ScheduleCalculator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns the reminders in the schedule into notifications at the right time, and keeps the ones
 * showing up to date.
 *
 * Two alarms at most are set. The reminder alarm, which wakes the phone, goes off for the next
 * reminder or snooze. The refresh alarm goes off every minute while a reminder is showing, to
 * update its time left; it doesn't wake a sleeping phone, it simply fires as soon as the screen
 * comes on. Whatever happens (an alarm, a snooze, a schedule change, a restart), [sync] brings
 * everything up to date and sets them again.
 */
class LessonReminders(private val context: Context, private val clock: Clock) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(context)
    private val state = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val text = ReminderText(context.resources)

    init {
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.reminders_channel))
                .build(),
        )
    }

    fun sync(calculator: ScheduleCalculator) {
        val now = LocalDateTime.now(clock)
        val upcoming = ReminderPlanner.upcoming(calculator, now)
        val alerted = HashSet<String>()
        val showingBefore = showingKeys()

        // Reminders whose moment has come.
        for (reminder in ReminderPlanner.due(upcoming, lastChecked(now), now)) {
            show(reminder.occurrence, now, alert = true, snoozedUntil = null)
            alerted += reminder.occurrence.key
        }

        // Snoozes that are over: the old notification makes way for a new one that alerts again.
        // One the user has since swiped away or tapped was dealt with, so its snooze just lapses.
        val (over, waiting) = snoozes().entries.partition { !it.value.isAfter(now) }
        for ((key, _) in over) {
            if (key !in showingBefore) continue
            notifications.cancel(key, NOTIFICATION_ID)
            ReminderPlanner.find(calculator, key)?.let { show(it, now, alert = true, snoozedUntil = null) }
            alerted += key
        }
        val snoozed = waiting.filter { it.key in showingBefore }.associate { it.key to it.value }
        saveSnoozes(snoozed)

        // Everything else still showing gets its time left brought up to date, quietly.
        for (key in showingKeys() - alerted) {
            val occurrence = ReminderPlanner.find(calculator, key)
            if (occurrence == null) notifications.cancel(key, NOTIFICATION_ID) else show(occurrence, now, alert = false, snoozedUntil = snoozed[key])
        }

        state.edit { putLong(LAST_CHECKED, now.toEpochMilli()) }
        val nextReminder = listOfNotNull(ReminderPlanner.next(upcoming, now), snoozed.values.minOrNull()).minOrNull()
        setAlarm(ACTION_REMIND, nextReminder, wakeUp = true)
        val anyCountingDown = showingKeys().any { key -> ReminderPlanner.find(calculator, key)?.let { start(it).isAfter(now) } == true }
        setAlarm(ACTION_REFRESH, if (anyCountingDown) ReminderPlanner.nextRefresh(now) else null, wakeUp = false)
    }

    /** The snooze button: the notification stays, and comes back in five minutes as a new one. */
    fun snooze(calculator: ScheduleCalculator, key: String) {
        val now = LocalDateTime.now(clock)
        val occurrence = ReminderPlanner.find(calculator, key) ?: return
        val start = start(occurrence)
        if (!now.isBefore(start)) return
        val until = ReminderPlanner.snoozeUntil(now, start)
        saveSnoozes(snoozes() + (key to until))
        show(occurrence, now, alert = false, snoozedUntil = until)
        sync(calculator)
    }

    /** The last time [sync] ran, or [now] if it never did or the clock has since been set back. */
    private fun lastChecked(now: LocalDateTime): LocalDateTime {
        val saved = state.getLong(LAST_CHECKED, Long.MAX_VALUE)
        if (saved > now.toEpochMilli()) return now
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(saved), clock.zone)
    }

    /**
     * Posts, or updates in place, the reminder for [occurrence]. Only [alert] makes a sound; an
     * update just changes the text. Once the lesson is over the reminder goes.
     */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers the notification permission
    private fun show(occurrence: Occurrence, now: LocalDateTime, alert: Boolean, snoozedUntil: LocalDateTime?) {
        val start = start(occurrence)
        val end = occurrence.date.atTime(occurrence.lesson.end ?: occurrence.lesson.start!!)
        if (!now.isBefore(end)) {
            notifications.cancel(occurrence.key, NOTIFICATION_ID)
            return
        }
        if (!notifications.areNotificationsEnabled()) return

        val started = !now.isBefore(start)
        val body = text.body(occurrence)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(if (started) text.started() else text.title(ReminderPlanner.minutesLeft(now, start)))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(!alert)
            .setShowWhen(false)
            .setTimeoutAfter(Duration.between(now, end).toMillis())
            .setContentIntent(openLesson(occurrence))
            .setAutoCancel(true)
        snoozedUntil?.let { builder.setSubText(text.snoozedUntil(it.format(HourMinute))) }
        if (!started) builder.addAction(R.drawable.ic_alarm, text.snoozeAction(), snoozeIntent(occurrence.key))
        notifications.notify(occurrence.key, NOTIFICATION_ID, builder.build())
    }

    /** The occurrence keys of the reminders now showing. One the user swiped away or tapped is gone. */
    private fun showingKeys(): Set<String> =
        notifications.activeNotifications.filter { it.notification.channelId == CHANNEL }.mapNotNullTo(HashSet()) { it.tag }

    private fun start(occurrence: Occurrence): LocalDateTime = occurrence.date.atTime(occurrence.lesson.start!!)

    /** Opens the app on this lesson. */
    private fun openLesson(occurrence: Occurrence): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_SHOW_LESSON)
            .putExtra(MainActivity.EXTRA_DATE, occurrence.date.toEpochDay())
            .putExtra(MainActivity.EXTRA_KEY, occurrence.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            occurrence.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The snooze button's intent. The key goes in the data, so each lesson's button is its own. */
    private fun snoozeIntent(key: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_SNOOZE)
            .setData(Uri.fromParts(KEY_SCHEME, key, null))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAlarm(action: String, at: LocalDateTime?, wakeUp: Boolean) {
        val alarm = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) {
            alarmManager.cancel(alarm)
            return
        }
        // Exact, so that "10 minutes before" means it; inexact only if the phone refuses exact alarms.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        val millis = at.toEpochMilli()
        when {
            wakeUp && exact -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarm)
            wakeUp -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarm)
            exact -> alarmManager.setExact(AlarmManager.RTC, millis, alarm)
            else -> alarmManager.set(AlarmManager.RTC, millis, alarm)
        }
    }

    private fun snoozes(): Map<String, LocalDateTime> =
        state.getStringSet(SNOOZES, emptySet()).orEmpty().mapNotNull { entry ->
            val key = entry.substringBeforeLast(SNOOZE_SEPARATOR)
            val millis = entry.substringAfterLast(SNOOZE_SEPARATOR).toLongOrNull() ?: return@mapNotNull null
            key to LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), clock.zone)
        }.toMap()

    private fun saveSnoozes(snoozes: Map<String, LocalDateTime>) {
        state.edit { putStringSet(SNOOZES, snoozes.mapTo(HashSet()) { (key, at) -> "$key$SNOOZE_SEPARATOR${at.toEpochMilli()}" }) }
    }

    private fun LocalDateTime.toEpochMilli(): Long = atZone(clock.zone).toInstant().toEpochMilli()

    companion object {
        const val ACTION_REMIND = "com.orirose.timetable.REMIND"
        const val ACTION_REFRESH = "com.orirose.timetable.REFRESH_REMINDERS"
        const val KEY_SCHEME = "lesson"

        private const val CHANNEL = "lesson_reminders"
        private const val NOTIFICATION_ID = 1
        private const val LAST_CHECKED = "last_checked"
        private const val SNOOZES = "snoozes"
        private const val SNOOZE_SEPARATOR = '@'
        private val HourMinute = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)
    }
}
