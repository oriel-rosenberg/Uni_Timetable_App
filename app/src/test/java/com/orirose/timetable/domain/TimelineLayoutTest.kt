package com.orirose.timetable.domain

import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.data.model.Course
import com.orirose.timetable.data.model.Lesson
import com.orirose.timetable.date
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class TimelineLayoutTest {

    private val calculator = ScheduleCalculator(bundledSchedule())

    private fun lesson(start: String, end: String): Occurrence {
        val lesson = Lesson(id = "$start-$end", course = "c", day = DayOfWeek.SUNDAY, start = LocalTime.parse(start), end = LocalTime.parse(end))
        return Occurrence(lesson, Course("c", "C"), date(2026, 10, 11), key = lesson.id)
    }

    private fun minutes(time: String) = LocalTime.parse(time).let { it.hour * 60 + it.minute }

    @Test
    fun `overlapping lessons share the width and the rest get all of it`() {
        val wednesday = calculator.day(date(2026, 10, 14)).lessons
        val placed = TimelineLayout.place(wednesday).associate { it.occurrence.lesson.id to (it.column to it.columnCount) }

        assertEquals(0 to 2, placed["67718-2-01"]) // 13:00–14:45
        assertEquals(1 to 2, placed["3053-1-01"]) // 14:00–15:45
        assertEquals(0 to 1, placed["67504-1-01-wed"]) // 16:00–16:45
    }

    @Test
    fun `back-to-back lessons do not count as overlapping`() {
        val placed = TimelineLayout.place(listOf(lesson("10:00", "11:45"), lesson("11:45", "13:00")))
        assertEquals(listOf(1, 1), placed.map { it.columnCount })
    }

    @Test
    fun `the grid covers nine to seven and stretches for lessons outside it`() {
        assertEquals(MinuteRange(minutes("09:00"), minutes("19:00")), TimelineLayout.dayRange(listOf(lesson("12:00", "13:45"))))
        assertEquals(
            MinuteRange(minutes("08:00"), minutes("21:00")),
            TimelineLayout.dayRange(listOf(lesson("08:30", "10:00"), lesson("18:30", "20:15"))),
        )
    }

    @Test
    fun `a day opens on its busy part`() {
        val tuesday = calculator.day(date(2026, 10, 13)).lessons // 12:00–13:45 and 15:00–15:45
        assertEquals(MinuteRange(minutes("11:45"), minutes("16:00")), TimelineLayout.defaultWindow(tuesday))
    }

    @Test
    fun `a day with one short lesson still opens on four hours around it`() {
        val window = TimelineLayout.defaultWindow(listOf(lesson("15:00", "15:45")))
        assertEquals(4 * 60, window.length)
        assertEquals(true, window.start < minutes("15:00") && window.end > minutes("15:45"))
    }

    @Test
    fun `the opening window stays inside the day`() {
        assertEquals(MinuteRange(minutes("09:00"), minutes("13:00")), TimelineLayout.defaultWindow(listOf(lesson("09:00", "09:45"))))
        assertEquals(MinuteRange(minutes("15:00"), minutes("19:00")), TimelineLayout.defaultWindow(listOf(lesson("18:00", "19:00"))))
    }
}
