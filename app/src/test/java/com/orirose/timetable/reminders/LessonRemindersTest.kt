package com.orirose.timetable.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.date
import com.orirose.timetable.domain.ScheduleCalculator
import com.orirose.timetable.domain.ScheduleEdits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The notifications themselves, on Robolectric's notification and alarm managers, with the clock in hand. */
@RunWith(RobolectricTestRunner::class)
class LessonRemindersTest {

    private val context = RuntimeEnvironment.getApplication()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    private val clock = MovableClock()
    private val reminders = LessonReminders(context, clock)

    // Ten minutes before the Tuesday 12:00 machine-learning recitation, every week.
    private val key = "huji-5787-a|67577-2-09|2026-10-11"
    private val calculator = bundledSchedule().let { schedule ->
        val recitation = ScheduleCalculator(schedule).day(date(2026, 10, 13)).lessons.first { it.key == key }
        ScheduleCalculator(ScheduleEdits.setReminder(schedule, recitation, minutesBefore = 10, everyTime = true))
    }

    private fun at(hour: Int, minute: Int, second: Int = 0) = LocalDateTime.of(2026, 10, 13, hour, minute, second)

    private fun syncAt(time: LocalDateTime) {
        clock.now = time
        reminders.sync(calculator)
    }

    private fun showing(): Notification? = notifications.activeNotifications.firstOrNull { it.tag == key }?.notification

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private val Notification.subText get() = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

    private fun nextAlarmAt(): LocalDateTime? = alarms.scheduledAlarms.minOfOrNull { it.triggerAtTime }
        ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), clock.zone) }

    @Test
    fun `a reminder comes with a snooze button and counts down each minute`() {
        syncAt(at(11, 49))
        assertNull(showing())
        assertEquals(at(11, 50), nextAlarmAt())

        syncAt(at(11, 50, second = 1))
        val shown = showing()!!
        assertEquals("עוד 10 דקות", shown.title)
        assertEquals(listOf("נודניק 5 דק׳"), shown.actions.map { it.title.toString() })
        assertEquals(at(11, 51), nextAlarmAt()) // the minute update

        syncAt(at(11, 51))
        assertEquals("עוד 9 דקות", showing()!!.title)
    }

    @Test
    fun `a snoozed reminder stays, then comes back as a new one five minutes later`() {
        syncAt(at(11, 49))
        syncAt(at(11, 50))
        assertEquals("עוד 10 דקות", showing()!!.title)
        clock.now = at(11, 51)
        reminders.snooze(calculator, key)

        assertEquals("נודניק עד 11:56", showing()!!.subText)
        assertTrue(alarms.scheduledAlarms.any { it.triggerAtTime == at(11, 56).toMillis() })

        syncAt(at(11, 56))
        val renewed = showing()!!
        assertNull(renewed.subText)
        assertEquals("עוד 4 דקות", renewed.title)
    }

    @Test
    fun `once the lesson starts the snooze button goes, and the reminder ends with the lesson`() {
        syncAt(at(11, 49))
        syncAt(at(11, 50))
        syncAt(at(12, 0, second = 30))
        val started = showing()!!
        assertEquals("השיעור מתחיל", started.title)
        assertEquals(0, started.actions?.size ?: 0)

        syncAt(at(13, 45))
        assertNull(showing())
    }

    @Test
    fun `a reminder swiped away is left alone`() {
        syncAt(at(11, 49))
        syncAt(at(11, 50))
        assertEquals("עוד 10 דקות", showing()!!.title)
        notifications.cancel(key, 1)
        syncAt(at(11, 52))
        assertNull(showing())
    }

    @Test
    fun `a snoozed reminder swiped away does not come back`() {
        syncAt(at(11, 49))
        syncAt(at(11, 50))
        clock.now = at(11, 51)
        reminders.snooze(calculator, key)
        notifications.cancel(key, 1) // swiped away while snoozed

        syncAt(at(11, 56))
        assertNull(showing())
    }

    private fun LocalDateTime.toMillis() = atZone(clock.zone).toInstant().toEpochMilli()

    private class MovableClock : Clock() {
        var now: LocalDateTime = LocalDateTime.of(2026, 10, 13, 9, 0)
        override fun getZone(): ZoneId = ZoneId.of("Asia/Jerusalem")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now.atZone(zone).toInstant()
    }
}
