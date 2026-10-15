package com.orirose.timetable.reminders

import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.date
import com.orirose.timetable.domain.ScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReminderTextTest {

    private val text = ReminderText(RuntimeEnvironment.getApplication().resources)

    @Test
    fun `durations read naturally`() {
        assertEquals("דקה", text.duration(1))
        assertEquals("10 דקות", text.duration(10))
        assertEquals("שעה", text.duration(60))
        assertEquals("שעתיים", text.duration(120))
        assertEquals("3 שעות", text.duration(180))
        assertEquals("שעה ו־10 דקות", text.duration(70))
        assertEquals("שעתיים ודקה", text.duration(121))
    }

    @Test
    fun `a reminder says how long, what, and where`() {
        val recitation = ScheduleCalculator(bundledSchedule()).day(date(2026, 10, 13)).lessons.first()

        assertEquals("עוד 10 דקות", text.title(minutesLeft = 10))
        assertEquals("עכשיו", text.title(minutesLeft = 0))
        assertEquals("תרגול במבוא ללמידת מכונה בכיתה הנדסה - C221", text.body(recitation))
    }

    @Test
    fun `a lesson with no place leaves the room out`() {
        val recitation = ScheduleCalculator(bundledSchedule()).day(date(2026, 10, 13)).lessons.first()
        val nowhere = recitation.copy(lesson = recitation.lesson.copy(place = null))

        assertEquals("תרגול במבוא ללמידת מכונה", text.body(nowhere))
    }
}
