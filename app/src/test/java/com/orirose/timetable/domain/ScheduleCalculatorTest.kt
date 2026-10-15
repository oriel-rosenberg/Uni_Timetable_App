package com.orirose.timetable.domain

import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.data.model.Lesson
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

class ScheduleCalculatorTest {

    private val schedule = bundledSchedule()
    private val calculator = ScheduleCalculator(schedule)

    // The bundled calendar covers the whole year, so the second semester's rules can be tested with the same lessons.
    private val secondSemester = schedule.copy(term = Term("test-b", "סמסטר ב׳", date(2027, 3, 7), date(2027, 6, 29)))

    private fun ScheduleCalculator.lessonIds(date: LocalDate) = day(date).lessons.map { it.lesson.id }

    @Test
    fun `each weekday shows its lessons in time order`() {
        assertEquals(listOf("67504-1-01-sun", "67718-1-01"), calculator.lessonIds(date(2026, 10, 11)))
        assertEquals(listOf("67561-1-01", "67577-1-03"), calculator.lessonIds(date(2026, 10, 12)))
        assertEquals(listOf("67577-2-09", "67561-2-01"), calculator.lessonIds(date(2026, 10, 13)))
        assertEquals(listOf("67718-2-01", "3053-1-01", "67504-1-01-wed"), calculator.lessonIds(date(2026, 10, 14)))
        assertEquals(listOf("67125-2-02", "67504-2-05"), calculator.lessonIds(date(2026, 10, 15)))
        assertEquals(emptyList<String>(), calculator.lessonIds(date(2026, 10, 16)))
        assertEquals(emptyList<String>(), calculator.lessonIds(date(2026, 10, 17)))
    }

    @Test
    fun `days off carry their label and no lessons`() {
        val election = calculator.day(date(2026, 10, 27))
        assertEquals(DayKind.OFF, election.kind)
        assertEquals("שבתון בחירות", election.label)
        assertTrue(election.lessons.isEmpty())

        for (day in 6..12) {
            val winterBreak = calculator.day(date(2026, 12, day))
            assertEquals(DayKind.OFF, winterBreak.kind)
            assertTrue(winterBreak.lessons.isEmpty())
        }
    }

    @Test
    fun `there are no lessons outside the term`() {
        val before = calculator.day(date(2026, 10, 8)) // a Thursday
        assertEquals(DayKind.OUTSIDE_TERM, before.kind)
        assertTrue(before.lessons.isEmpty())

        val after = calculator.day(date(2027, 1, 17)) // a Sunday
        assertEquals(DayKind.OUTSIDE_TERM, after.kind)
        assertTrue(after.lessons.isEmpty())
    }

    @Test
    fun `calendar entries fall on the weekdays the university lists`() {
        assertEquals(DayOfWeek.SUNDAY, schedule.term.start.dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, schedule.term.end.dayOfWeek)

        val listed = mapOf(
            "2026-10-27" to DayOfWeek.TUESDAY,
            "2026-12-06" to DayOfWeek.SUNDAY,
            "2026-12-25" to DayOfWeek.FRIDAY,
            "2027-03-11" to DayOfWeek.THURSDAY,
            "2027-03-23" to DayOfWeek.TUESDAY,
            "2027-04-18" to DayOfWeek.SUNDAY,
            "2027-05-03" to DayOfWeek.MONDAY,
            "2027-05-04" to DayOfWeek.TUESDAY,
            "2027-05-10" to DayOfWeek.MONDAY,
            "2027-05-11" to DayOfWeek.TUESDAY,
            "2027-05-17" to DayOfWeek.MONDAY,
            "2027-06-10" to DayOfWeek.THURSDAY,
            "2027-06-20" to DayOfWeek.SUNDAY,
            "2027-06-27" to DayOfWeek.SUNDAY,
            "2027-06-28" to DayOfWeek.MONDAY,
        )
        assertEquals(listed, schedule.calendar.days.associate { it.from.toString() to it.from.dayOfWeek })
    }

    @Test
    fun `pending holds only what has ended, oldest first`() {
        val tuesdayAtOne = LocalDateTime.of(2026, 10, 13, 13, 0)
        assertEquals(
            listOf("67125-1-01", "67504-1-01-sun", "67718-1-01", "67561-1-01", "67577-1-03"),
            calculator.pending(tuesdayAtOne, emptySet()).map { it.lesson.id },
        )

        // Tuesday's 12:00–13:45 recitation joins the moment it is over.
        val afterRecitation = calculator.pending(tuesdayAtOne.withMinute(45), emptySet())
        assertEquals("67577-2-09", afterRecitation.last().lesson.id)
        assertEquals(date(2026, 10, 13), afterRecitation.last().date)
    }

    @Test
    fun `a ticked lesson leaves the pending list`() {
        val now = LocalDateTime.of(2026, 10, 13, 13, 0)
        val all = calculator.pending(now, emptySet())
        val ticked = all[1].key

        val remaining = calculator.pending(now, setOf(ticked))

        assertEquals(all.size - 1, remaining.size)
        assertFalse(remaining.any { it.key == ticked })
    }

    @Test
    fun `the online lecture is due once in every teaching week`() {
        val afterTerm = LocalDateTime.of(2027, 2, 1, 12, 0)
        val online = calculator.pending(afterTerm, emptySet()).filter { it.lesson.id == "67125-1-01" }

        assertEquals(13, online.size)
        assertTrue(online.all { it.date.dayOfWeek == DayOfWeek.SUNDAY })
        assertTrue(online.none { it.date == date(2026, 12, 6) }) // winter break
    }

    @Test
    fun `an untimed lesson moves to the next teaching day when its own day is off`() {
        // 20.6.2027, a Sunday, is Student Day.
        val pending = ScheduleCalculator(secondSemester).pending(LocalDateTime.of(2027, 6, 30, 12, 0), emptySet())
        val thatWeek = pending.single { it.lesson.id == "67125-1-01" && it.date in date(2027, 6, 20)..date(2027, 6, 26) }
        assertEquals(date(2027, 6, 21), thatWeek.date)
    }

    @Test
    fun `the whole term adds up`() {
        // Teaching days in the term: 13 Sundays, 13 Mondays, 12 Tuesdays, 13 Wednesdays, 13 Thursdays;
        // lessons per day 2, 2, 2, 3, 2; plus 13 online lectures.
        val afterTerm = LocalDateTime.of(2027, 2, 1, 12, 0)
        assertEquals(13 * 2 + 13 * 2 + 12 * 2 + 13 * 3 + 13 * 2 + 13, calculator.pending(afterTerm, emptySet()).size)
    }

    @Test
    fun `a substituted day shows another weekday's lessons`() {
        val spring = ScheduleCalculator(secondSemester)
        val wednesdayLessons = spring.lessonIds(date(2027, 3, 10))
        val thursdayLessons = spring.lessonIds(date(2027, 3, 18))
        assertEquals(3, wednesdayLessons.size)

        assertEquals(wednesdayLessons, spring.lessonIds(date(2027, 6, 27))) // a Sunday on Wednesday's timetable
        assertEquals(thursdayLessons, spring.lessonIds(date(2027, 6, 28))) // a Monday on Thursday's timetable
    }

    @Test
    fun `an occurrence is named by its week, and a substituted one by its date as well`() {
        // Tuesday 13.10.2026 is in the week of Sunday 11.10.
        assertEquals("huji-5787-a|67577-2-09|2026-10-11", calculator.day(date(2026, 10, 13)).lessons.first().key)

        val substituted = ScheduleCalculator(secondSemester).day(date(2027, 6, 27)).lessons.first()
        assertEquals("test-b|67718-2-01|2027-06-27|2027-06-27", substituted.key)
        assertEquals(DayOfWeek.SUNDAY, substituted.lesson.day)
    }

    @Test
    fun `an early-closing day drops the lessons that start after it closes`() {
        val evening = Lesson(
            id = "evening", course = "67504", day = DayOfWeek.MONDAY,
            start = LocalTime.of(16, 0), end = LocalTime.of(17, 45),
        )
        val spring = ScheduleCalculator(secondSemester.copy(lessons = secondSemester.lessons + evening))

        // 3.5.2027 closes at 18:00, 10.5.2027 at 16:00; both are Mondays.
        assertEquals(listOf("67561-1-01", "67577-1-03", "evening"), spring.lessonIds(date(2027, 5, 3)))
        assertEquals(listOf("67561-1-01", "67577-1-03"), spring.lessonIds(date(2027, 5, 10)))
    }
}
