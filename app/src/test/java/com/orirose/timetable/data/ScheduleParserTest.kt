package com.orirose.timetable.data

import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.data.model.LessonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ScheduleParserTest {

    private fun file(
        lesson: String = """{ "id": "l", "course": "c", "type": "lecture", "day": "SUNDAY", "start": "12:00", "end": "13:45" }""",
        extra: String = "",
    ) = """
        {
          $extra
          "term": { "id": "t", "name": "T", "start": "2026-10-11", "end": "2027-01-15" },
          "courses": [ { "id": "c", "name": "C" } ],
          "lessons": [ $lesson ]
        }
    """.trimIndent()

    private fun assertRejected(text: String) {
        assertThrows(ScheduleFormatException::class.java) { ScheduleParser.parse(text) }
    }

    @Test
    fun `the bundled schedule is valid and complete`() {
        val schedule = bundledSchedule()
        assertEquals(6, schedule.courses.size)
        assertEquals(12, schedule.lessons.size)
        assertEquals(11, schedule.lessons.count { it.isTimed })
        assertEquals(15, schedule.calendar.days.size)
    }

    @Test
    fun `a minimal file parses`() {
        val lesson = ScheduleParser.parse(file()).lessons.single()
        assertEquals(LessonType.LECTURE, lesson.type)
        assertEquals(DayOfWeek.SUNDAY, lesson.day)
        assertEquals(LocalTime.of(12, 0), lesson.start)
        assertTrue(lesson.isTimed)
    }

    @Test
    fun `a lesson without times is an untimed one`() {
        val lesson = ScheduleParser.parse(file(lesson = """{ "id": "l", "course": "c", "day": "sunday" }""")).lessons.single()
        assertFalse(lesson.isTimed)
        assertEquals(LessonType.OTHER, lesson.type)
    }

    @Test
    fun `unknown lesson types and unknown fields are tolerated`() {
        val text = file(
            lesson = """{ "id": "l", "course": "c", "type": "workshop", "day": "MONDAY", "start": "10:00", "end": "11:00", "credits": 4 }""",
            extra = """"madeBy": "a newer version",""",
        )
        assertEquals(LessonType.OTHER, ScheduleParser.parse(text).lessons.single().type)
    }

    @Test
    fun `broken files are rejected`() {
        assertRejected("not json at all")
        assertRejected("{}")
        assertRejected(file(extra = """"formatVersion": 2,"""))
        assertRejected(file().replace("\"2027-01-15\"", "\"9999-12-31\"")) // a semester that never ends
        assertRejected(file(lesson = """{ "id": "l", "course": "nope", "day": "SUNDAY" }"""))
        assertRejected(file(lesson = """{ "id": "l", "course": "c", "day": "SUNDAY", "start": "12:00" }"""))
        assertRejected(file(lesson = """{ "id": "l", "course": "c", "day": "SUNDAY", "start": "14:00", "end": "12:00" }"""))
        assertRejected(file(lesson = """{ "id": "l", "course": "c", "day": "SUNDAY", "start": "25:00", "end": "26:00" }"""))
        assertRejected(file(lesson = """{ "id": "l", "course": "c", "day": "FUNDAY" }"""))
        assertRejected(file(lesson = """{ "id": "l", "course": "c", "day": "SUNDAY" }, { "id": "l", "course": "c", "day": "MONDAY" }"""))
    }
}
