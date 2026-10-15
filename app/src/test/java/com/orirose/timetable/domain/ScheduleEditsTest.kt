package com.orirose.timetable.domain

import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.data.ScheduleParser
import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleEditsTest {

    private val schedule = bundledSchedule()
    private val tuesday = date(2026, 10, 13)
    private val nextTuesday = date(2026, 10, 20)
    private val recitationId = "67577-2-09" // Tuesdays 12:00–13:45

    private fun Schedule.lessonIds(date: LocalDate) = ScheduleCalculator(this).day(date).lessons.map { it.lesson.id }

    private fun Schedule.recitation(date: LocalDate = tuesday): Occurrence =
        ScheduleCalculator(this).day(date).lessons.first { it.lesson.id == recitationId }

    @Test
    fun `deleting one occurrence leaves the other weeks alone`() {
        val edited = ScheduleEdits.deleteOccurrence(schedule, schedule.recitation())

        assertEquals(listOf("67561-2-01"), edited.lessonIds(tuesday))
        assertEquals(listOf(recitationId, "67561-2-01"), edited.lessonIds(nextTuesday))

        val pending = ScheduleCalculator(edited).pending(LocalDateTime.of(2026, 10, 21, 9, 0), emptySet())
        assertEquals(listOf(nextTuesday), pending.filter { it.lesson.id == recitationId }.map { it.date })
    }

    @Test
    fun `deleting a lesson removes it from every week, with its reminders and changes`() {
        val withReminder = ScheduleEdits.setReminder(schedule, schedule.recitation(), 10, everyTime = true)
        val withChange = ScheduleEdits.editOccurrence(
            withReminder, withReminder.recitation(), withReminder.recitation().details().copy(place = "זום"),
        )

        val edited = ScheduleEdits.deleteLesson(withChange, recitationId)

        assertEquals(listOf("67561-2-01"), edited.lessonIds(tuesday))
        assertEquals(listOf("67561-2-01"), edited.lessonIds(nextTuesday))
        assertTrue(edited.lessonReminders.isEmpty())
        assertTrue(edited.changes.isEmpty())
        assertEquals(schedule.courses, edited.courses) // the course stays, so colours don't shift
    }

    @Test
    fun `editing one occurrence changes that week only and keeps its identity`() {
        val original = schedule.recitation()
        val changed = original.details().copy(
            day = DayOfWeek.THURSDAY, start = LocalTime.of(16, 0), end = LocalTime.of(17, 0), place = "זום",
        )

        val edited = ScheduleEdits.editOccurrence(schedule, original, changed)

        assertFalse(recitationId in edited.lessonIds(tuesday))
        val moved = edited.recitation(date(2026, 10, 15))
        assertEquals(original.key, moved.key)
        assertEquals(LocalTime.of(16, 0), moved.lesson.start)
        assertEquals("זום", moved.lesson.place)
        assertEquals(original.lesson.teachers, moved.lesson.teachers)

        assertEquals(LocalTime.of(12, 0), edited.recitation(nextTuesday).lesson.start)

        // Only what differs from the weekly lesson is recorded.
        val change = edited.changes.single()
        assertEquals(DayOfWeek.THURSDAY, change.day)
        assertNull(change.name)
        assertNull(change.type)
        assertNull(change.teachers)
    }

    @Test
    fun `editing an occurrence back to the weekly values leaves no change behind`() {
        val original = schedule.recitation()
        val edited = ScheduleEdits.editOccurrence(schedule, original, original.details().copy(place = "זום"))

        val restored = ScheduleEdits.editOccurrence(edited, edited.recitation(), original.details())

        assertEquals(schedule, restored)
    }

    @Test
    fun `editing a lesson changes every week, keeps ticks attached and renames the course`() {
        val original = schedule.recitation()
        val weekly = schedule.lessonDetails(recitationId)!!

        val edited = ScheduleEdits.editLesson(schedule, recitationId, weekly.copy(day = DayOfWeek.WEDNESDAY, name = "למידת מכונה"))

        assertFalse(recitationId in edited.lessonIds(tuesday))
        val moved = edited.recitation(date(2026, 10, 14))
        assertEquals(original.key, moved.key) // so a tick made before the move still counts
        assertEquals("למידת מכונה", moved.course.name)
        assertEquals(DayOfWeek.WEDNESDAY, edited.recitation(date(2026, 10, 21)).lesson.day)

        // The same course's Monday lecture carries the new name too.
        val lecture = ScheduleCalculator(edited).day(date(2026, 10, 12)).lessons.first { it.lesson.id == "67577-1-03" }
        assertEquals("למידת מכונה", lecture.course.name)
    }

    @Test
    fun `a reminder before every occurrence, with one occurrence silenced`() {
        val everyTime = ScheduleEdits.setReminder(schedule, schedule.recitation(), 15, everyTime = true)
        assertEquals(15, everyTime.recitation().reminderMinutes)
        assertEquals(15, everyTime.recitation(nextTuesday).reminderMinutes)
        assertTrue(everyTime.recitation().reminderRepeats)

        val silenced = ScheduleEdits.removeReminder(everyTime, everyTime.recitation(), everyTime = false)
        assertNull(silenced.recitation().reminderMinutes)
        assertEquals(15, silenced.recitation(nextTuesday).reminderMinutes)

        val changedOnce = ScheduleEdits.setReminder(silenced, silenced.recitation(), 60, everyTime = false)
        assertEquals(60, changedOnce.recitation().reminderMinutes)
        assertEquals(15, changedOnce.recitation(nextTuesday).reminderMinutes)

        val none = ScheduleEdits.removeReminder(changedOnce, changedOnce.recitation(), everyTime = true)
        assertNull(none.recitation().reminderMinutes)
        assertNull(none.recitation(nextTuesday).reminderMinutes)
        assertEquals(schedule, none)
    }

    @Test
    fun `a reminder for one occurrence only`() {
        val once = ScheduleEdits.setReminder(schedule, schedule.recitation(), 10, everyTime = false)
        assertEquals(10, once.recitation().reminderMinutes)
        assertFalse(once.recitation().reminderRepeats)
        assertNull(once.recitation(nextTuesday).reminderMinutes)

        assertEquals(schedule, ScheduleEdits.removeReminder(once, once.recitation(), everyTime = false))
    }

    @Test
    fun `what the user changed survives being stored and read back`() {
        var edited = ScheduleEdits.setReminder(schedule, schedule.recitation(), 15, everyTime = true)
        edited = ScheduleEdits.removeReminder(edited, edited.recitation(nextTuesday), everyTime = false)
        edited = ScheduleEdits.editOccurrence(edited, edited.recitation(), edited.recitation().details().copy(place = "זום"))
        edited = ScheduleEdits.deleteOccurrence(edited, edited.recitation(date(2026, 11, 3)))

        assertEquals(edited, ScheduleParser.parse(ScheduleParser.write(edited)))
    }
}
