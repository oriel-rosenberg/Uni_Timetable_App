package com.orirose.timetable.reminders

import android.content.res.Resources
import com.orirose.timetable.R
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.domain.ReminderPlanner
import com.orirose.timetable.ui.common.labelRes

/** The wording of a reminder: "עוד 10 דקות" over "הרצאה באלגוריתמים בכיתה קפלן". */
class ReminderText(private val resources: Resources) {

    fun title(minutesLeft: Int): String =
        if (minutesLeft <= 0) {
            resources.getString(R.string.reminder_now)
        } else {
            resources.getString(R.string.reminder_in, duration(minutesLeft))
        }

    fun started(): String = resources.getString(R.string.reminder_started)

    fun snoozeAction(): String = resources.getString(R.string.reminder_snooze, ReminderPlanner.SNOOZE_MINUTES.toInt())

    fun snoozedUntil(time: String): String = resources.getString(R.string.reminder_snoozed_until, time)

    fun body(occurrence: Occurrence): String {
        val type = resources.getString(occurrence.lesson.type.labelRes)
        val place = occurrence.lesson.place
        return if (place == null) {
            resources.getString(R.string.reminder_body, type, occurrence.course.name)
        } else {
            resources.getString(R.string.reminder_body_place, type, occurrence.course.name, place)
        }
    }

    /** "דקה", "10 דקות", "שעה", "שעתיים", "3 שעות", "שעה ו־10 דקות", "שעתיים ודקה". */
    fun duration(totalMinutes: Int): String {
        val minuteCount = totalMinutes % 60
        val hours = when (val count = totalMinutes / 60) {
            0 -> null
            1 -> resources.getString(R.string.duration_hour)
            2 -> resources.getString(R.string.duration_two_hours)
            else -> resources.getString(R.string.duration_hours, count)
        }
        val minutes = when (minuteCount) {
            0 -> null
            1 -> resources.getString(R.string.duration_minute)
            else -> resources.getString(R.string.duration_minutes, minuteCount)
        }
        return when {
            hours == null || minutes == null -> hours ?: minutes.orEmpty()
            // "and" joins on to a word directly, but takes a hyphen before a numeral.
            minuteCount == 1 -> resources.getString(R.string.duration_hours_and_minute, hours, minutes)
            else -> resources.getString(R.string.duration_hours_and_minutes, hours, minutes)
        }
    }
}
