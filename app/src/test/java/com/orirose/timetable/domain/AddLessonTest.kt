package com.orirose.timetable.domain

import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.data.ScheduleParser
import com.orirose.timetable.data.model.LessonType
import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.data.model.Term
import com.orirose.timetable.date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AddLessonTest {

    private val schedule = bundledSchedule()

    private fun details(name: String = "סדנת כתיבה", day: DayOfWeek = DayOfWeek.TUESDAY) = LessonDetails(
        name = name, type = LessonType.LECTURE, day = day,
        start = LocalTime.of(17, 0), end = LocalTime.of(18, 30), place = "שפרינצק 101", teachers = listOf("ד״ר כהן"),
    )

    private fun Schedule.lessonIdsOn(date: LocalDate) = ScheduleCalculator(this).day(date).lessons.map { it.lesson.id }

    @Test
    fun `a lesson added once is on that date only`() {
        val added = ScheduleEdits.addLesson(schedule, details(), firstDate = date(2026, 10, 20), until = null)

        val lesson = added.lessons.last()
        assertTrue(lesson.isOneOff)
        assertTrue(lesson.id in added.lessonIdsOn(date(2026, 10, 20)))
        assertFalse(lesson.id in added.lessonIdsOn(date(2026, 10, 27)))
        assertFalse(lesson.id in added.lessonIdsOn(date(2026, 10, 13)))
    }

    @Test
    fun `a lesson added once shows even on a day off`() {
        val added = ScheduleEdits.addLesson(schedule, details(), firstDate = date(2026, 10, 27), until = null) // election day
        assertEquals(listOf(added.lessons.last().id), added.lessonIdsOn(date(2026, 10, 27)))
    }

    @Test
    fun `a weekly lesson runs from its first date to its last, skipping days off`() {
        val added = ScheduleEdits.addLesson(schedule, details(), firstDate = date(2026, 10, 20), until = date(2026, 11, 10))
        val id = added.lessons.last().id

        assertFalse(id in added.lessonIdsOn(date(2026, 10, 13)))
        assertTrue(id in added.lessonIdsOn(date(2026, 10, 20)))
        assertFalse(id in added.lessonIdsOn(date(2026, 10, 27))) // election day
        assertTrue(id in added.lessonIdsOn(date(2026, 11, 3)))
        assertTrue(id in added.lessonIdsOn(date(2026, 11, 10)))
        assertFalse(id in added.lessonIdsOn(date(2026, 11, 17)))
    }

    @Test
    fun `a new name makes a new course, a known one joins it`() {
        val fresh = ScheduleEdits.addLesson(schedule, details(), date(2026, 10, 20), null)
        assertEquals(schedule.courses.size + 1, fresh.courses.size)
        assertEquals("my-1", fresh.courses.last().id)
        assertEquals("my-1-1", fresh.lessons.last().id)

        val known = ScheduleEdits.addLesson(schedule, details(name = " אלגוריתמים "), date(2026, 10, 20), null)
        assertEquals(schedule.courses, known.courses)
        assertEquals("67504", known.lessons.last().course)
        assertEquals("67504-1", known.lessons.last().id)

        val twice = ScheduleEdits.addLesson(fresh, details(), date(2026, 10, 21), null)
        assertEquals(listOf("my-1-1", "my-1-2"), twice.lessons.takeLast(2).map { it.id })
    }

    @Test
    fun `an added lesson survives being stored and read back`() {
        val added = ScheduleEdits.addLesson(schedule, details(), date(2026, 10, 20), date(2027, 1, 12))
        assertEquals(added, ScheduleParser.parse(ScheduleParser.write(added)))
    }

    @Test
    fun `until the end of the semester means the semester's last such weekday`() {
        // Semester A ends on Friday 15.1.2027.
        assertEquals(date(2027, 1, 10), ScheduleEdits.defaultUntil(schedule.term, DayOfWeek.SUNDAY))
        assertEquals(date(2027, 1, 14), ScheduleEdits.defaultUntil(schedule.term, DayOfWeek.THURSDAY))

        // Semester B ends on Tuesday 29.6.2027.
        val semesterB = Term("b", "סמסטר ב׳", date(2027, 3, 7), date(2027, 6, 29))
        assertEquals(date(2027, 6, 29), ScheduleEdits.defaultUntil(semesterB, DayOfWeek.TUESDAY))
        assertEquals(date(2027, 6, 24), ScheduleEdits.defaultUntil(semesterB, DayOfWeek.THURSDAY))
    }

    @Test
    fun `a lesson added after the semester shows, is listed when missed, and reminds`() {
        // Semester A ends on Friday 15.1.2027; an exam is added for Sunday 17.1.
        val exam = date(2027, 1, 17)
        val added = ScheduleEdits.addLesson(schedule, details(day = DayOfWeek.SUNDAY), firstDate = exam, until = null)
        val calculator = ScheduleCalculator(added)
        val occurrence = calculator.day(exam).lessons.single()

        val pending = calculator.pending(LocalDateTime.of(2027, 1, 20, 9, 0), emptySet())
        assertTrue(pending.any { it.key == occurrence.key })

        val reminded = ScheduleCalculator(ScheduleEdits.setReminder(added, occurrence, minutesBefore = 60, everyTime = false))
        val upcoming = ReminderPlanner.upcoming(reminded, LocalDateTime.of(2027, 1, 16, 9, 0))
        assertEquals(listOf(exam.atTime(16, 0)), upcoming.map { it.at })
    }

    @Test
    fun `a weekly lesson added after the semester runs on its own dates`() {
        val added = ScheduleEdits.addLesson(schedule, details(day = DayOfWeek.SUNDAY), date(2027, 1, 17), until = date(2027, 1, 31))
        val id = added.lessons.last().id
        assertTrue(id in added.lessonIdsOn(date(2027, 1, 17)))
        assertTrue(id in added.lessonIdsOn(date(2027, 1, 24)))
        assertTrue(id in added.lessonIdsOn(date(2027, 1, 31)))
        // The bundled lessons still stop with the semester.
        assertEquals(listOf(id), added.lessonIdsOn(date(2027, 1, 24)))
    }

    @Test
    fun `moving a weekly added lesson to another day keeps its first and last weeks`() {
        // Added on Wednesday 21.10 until Wednesday 11.11; moved for good to Sunday.
        val added = ScheduleEdits.addLesson(schedule, details(day = DayOfWeek.WEDNESDAY), date(2026, 10, 21), date(2026, 11, 11))
        val id = added.lessons.last().id
        val moved = ScheduleEdits.editLesson(added, id, details(day = DayOfWeek.SUNDAY))

        assertTrue(id in moved.lessonIdsOn(date(2026, 10, 18))) // the first week's Sunday
        assertTrue(id in moved.lessonIdsOn(date(2026, 11, 8))) // the last week's Sunday
        assertFalse(id in moved.lessonIdsOn(date(2026, 11, 15)))
    }

    @Test
    fun `moving a one-off lesson for good keeps it in its week`() {
        val added = ScheduleEdits.addLesson(schedule, details(), date(2026, 10, 20), null)
        val moved = ScheduleEdits.editLesson(added, added.lessons.last().id, details(day = DayOfWeek.THURSDAY))
        assertEquals(listOf(added.lessons.last().id), moved.lessonIdsOn(date(2026, 10, 22)).filter { it.startsWith("my-") })
        assertTrue(moved.lessonIdsOn(date(2026, 10, 20)).none { it.startsWith("my-") })
    }
}
