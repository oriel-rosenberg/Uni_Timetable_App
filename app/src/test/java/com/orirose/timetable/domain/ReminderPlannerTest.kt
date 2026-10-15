package com.orirose.timetable.domain

import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderPlannerTest {

    // Ten minutes before the Tuesday 12:00 recitation, every week.
    private val calculator = bundledSchedule().let { schedule ->
        val recitation = ScheduleCalculator(schedule).day(date(2026, 10, 13)).lessons.first { it.lesson.id == "67577-2-09" }
        ScheduleCalculator(ScheduleEdits.setReminder(schedule, recitation, minutesBefore = 10, everyTime = true))
    }

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute, second)

    @Test
    fun `reminders are planned ahead of each lesson, skipping days off`() {
        val upcoming = ReminderPlanner.upcoming(calculator, at(13, 9, 0))

        assertEquals(at(13, 11, 50), upcoming[0].at)
        assertEquals(at(13, 12, 0), upcoming[0].lessonStart)
        assertEquals(at(20, 11, 50), upcoming[1].at)
        assertEquals(LocalDateTime.of(2026, 11, 3, 11, 50), upcoming[2].at) // 27.10 is election day
        assertEquals(12, upcoming.size) // the term's twelve teaching Tuesdays
    }

    @Test
    fun `a reminder is due once its moment has come since the last check`() {
        val now = at(13, 11, 50, second = 2)
        val upcoming = ReminderPlanner.upcoming(calculator, now)

        assertEquals(listOf(at(13, 11, 50)), ReminderPlanner.due(upcoming, lastChecked = at(13, 11, 40), now = now).map { it.at })
        // Already dealt with a moment ago: not shown twice.
        assertTrue(ReminderPlanner.due(upcoming, lastChecked = at(13, 11, 50, second = 1), now = now).isEmpty())
        assertEquals(at(20, 11, 50), ReminderPlanner.next(upcoming, now))
    }

    @Test
    fun `nothing is due before the moment, and the alarm is set for it`() {
        val now = at(13, 11, 49)
        val upcoming = ReminderPlanner.upcoming(calculator, now)

        assertTrue(ReminderPlanner.due(upcoming, lastChecked = at(13, 9, 0), now = now).isEmpty())
        assertEquals(at(13, 11, 50), ReminderPlanner.next(upcoming, now))
    }

    @Test
    fun `a reminder that comes late still shows, until ten minutes into the lesson`() {
        // The phone held the 11:50 alarm back until 12:03: the reminder still comes.
        val late = ReminderPlanner.upcoming(calculator, at(13, 12, 3))
        assertEquals(listOf(at(13, 11, 50)), ReminderPlanner.due(late, lastChecked = at(13, 11, 40), now = at(13, 12, 3)).map { it.at })

        assertEquals(at(20, 11, 50), ReminderPlanner.upcoming(calculator, at(13, 12, 10)).first().at)
    }

    @Test
    fun `a reminder set for the very start goes off`() {
        val schedule = bundledSchedule()
        val recitation = ScheduleCalculator(schedule).day(date(2026, 10, 13)).lessons.first { it.lesson.id == "67577-2-09" }
        val atStart = ScheduleCalculator(ScheduleEdits.setReminder(schedule, recitation, minutesBefore = 0, everyTime = false))

        val fired = at(13, 12, 0, second = 1) // an alarm is never quite on the dot
        val upcoming = ReminderPlanner.upcoming(atStart, fired)
        assertEquals(listOf(at(13, 12, 0)), ReminderPlanner.due(upcoming, lastChecked = at(13, 11, 59), now = fired).map { it.at })
    }

    @Test
    fun `a snooze lasts five minutes but never past the start`() {
        assertEquals(at(13, 11, 55), ReminderPlanner.snoozeUntil(at(13, 11, 50), lessonStart = at(13, 12, 0)))
        assertEquals(at(13, 12, 0), ReminderPlanner.snoozeUntil(at(13, 11, 57), lessonStart = at(13, 12, 0)))
    }

    @Test
    fun `time left is in whole minutes, rounded up, and updated each minute`() {
        assertEquals(10, ReminderPlanner.minutesLeft(at(13, 11, 50, second = 2), at(13, 12, 0)))
        assertEquals(9, ReminderPlanner.minutesLeft(at(13, 11, 51), at(13, 12, 0)))
        assertEquals(0, ReminderPlanner.minutesLeft(at(13, 12, 0), at(13, 12, 0)))
        assertEquals(at(13, 11, 51), ReminderPlanner.nextRefresh(at(13, 11, 50, second = 2)))
    }

    @Test
    fun `after the last lesson there is nothing to wait for`() {
        val afterTerm = LocalDateTime.of(2027, 2, 1, 12, 0)
        assertNull(ReminderPlanner.next(ReminderPlanner.upcoming(calculator, afterTerm), afterTerm))
    }
}
