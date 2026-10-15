package com.orirose.timetable.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** A reminder that is to go off at [at], for a lesson starting at [lessonStart]. */
data class PlannedReminder(
    val occurrence: Occurrence,
    val at: LocalDateTime,
    val lessonStart: LocalDateTime,
)

/** Turns the reminders set in a schedule into moments in time. */
object ReminderPlanner {

    /**
     * A reminder that comes late (set for the very start, or held back by the phone) is still
     * shown this long into the lesson.
     */
    const val LATE_MINUTES = 10L

    /** Every reminder for a lesson that hasn't started, or started less than [LATE_MINUTES] ago, soonest first. */
    fun upcoming(calculator: ScheduleCalculator, now: LocalDateTime): List<PlannedReminder> {
        val span = calculator.span
        val result = ArrayList<PlannedReminder>()
        val cutoff = now.minusMinutes(LATE_MINUTES)
        var week = maxOf(cutoff.toLocalDate(), span.start).weekStart()
        val lastWeek = span.endInclusive.weekStart()
        while (!week.isAfter(lastWeek)) {
            for (occurrence in calculator.week(week)) {
                val minutes = occurrence.reminderMinutes ?: continue
                val start = occurrence.date.atTime(occurrence.lesson.start ?: continue)
                if (start.isAfter(cutoff)) result += PlannedReminder(occurrence, start.minusMinutes(minutes.toLong()), start)
            }
            week = week.plusWeeks(1)
        }
        return result.sortedBy { it.at }
    }

    /**
     * Of [upcoming], the reminders to show right now: their moment has come since [lastChecked].
     * One whose moment passed before that was either shown already or set too late to matter.
     */
    fun due(upcoming: List<PlannedReminder>, lastChecked: LocalDateTime, now: LocalDateTime): List<PlannedReminder> =
        upcoming.filter { it.at.isAfter(lastChecked) && !it.at.isAfter(now) }

    /** When the next reminder after [now] goes off, if there is one. */
    fun next(upcoming: List<PlannedReminder>, now: LocalDateTime): LocalDateTime? =
        upcoming.firstOrNull { it.at.isAfter(now) }?.at

    const val SNOOZE_MINUTES = 5L

    /** When a reminder snoozed at [now] comes back: in five minutes, but no later than the lesson's start. */
    fun snoozeUntil(now: LocalDateTime, lessonStart: LocalDateTime): LocalDateTime =
        minOf(now.plusMinutes(SNOOZE_MINUTES), lessonStart)

    /** The time left, in whole minutes rounded up, so 9 minutes 40 seconds reads as 10. */
    fun minutesLeft(now: LocalDateTime, lessonStart: LocalDateTime): Int =
        ((Duration.between(now, lessonStart).seconds + 59) / 60).toInt()

    /** When a reminder that is showing should next have its time left updated: the next whole minute. */
    fun nextRefresh(now: LocalDateTime): LocalDateTime = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)

    /** The occurrence that [key] names, as it is now, or `null` if it no longer exists. */
    fun find(calculator: ScheduleCalculator, key: String): Occurrence? {
        val week = OccurrenceKeys.weekOf(key) ?: return null
        return calculator.week(week).firstOrNull { it.key == key }
    }
}
