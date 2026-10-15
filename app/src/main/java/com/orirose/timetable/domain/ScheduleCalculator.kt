package com.orirose.timetable.domain

import com.orirose.timetable.data.model.Course
import com.orirose.timetable.data.model.Lesson
import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.data.model.SpecialDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One lesson on one concrete date, as it really takes place: [lesson] and [course] already include
 * any change made to this occurrence. [key] identifies it for ticks, reminders and changes.
 *
 * @property reminderMinutes how long before the start to remind, or `null` for no reminder
 * @property reminderRepeats the lesson has a reminder before every occurrence, whatever this one does
 */
data class Occurrence(
    val lesson: Lesson,
    val course: Course,
    val date: LocalDate,
    val key: String,
    val reminderMinutes: Int? = null,
    val reminderRepeats: Boolean = false,
)

enum class DayKind { TEACHING, OFF, OUTSIDE_TERM }

/** What the day grid shows for a date. [lessons] are the timed ones, in order. */
data class DayInfo(
    val date: LocalDate,
    val kind: DayKind,
    val label: String?,
    val lessons: List<Occurrence>,
)

/**
 * Works out which lessons fall on which dates: the weekly timetable, as the academic calendar
 * allows, with the user's own changes on top.
 */
class ScheduleCalculator(val schedule: Schedule) {

    private val term = schedule.term
    private val coursesById = schedule.courses.associateBy { it.id }
    private val changesByKey = schedule.changes.associateBy { it.key }
    private val timedLessons = schedule.lessons.filter { it.isTimed }
    private val oneOffLessons = timedLessons.filter { it.isOneOff }
    private val weeklyLessons = timedLessons.filterNot { it.isOneOff }
    private val untimedLessons = schedule.lessons.filterNot { it.isTimed }
    private val weeks = HashMap<LocalDate, List<Occurrence>>()

    /**
     * The dates any lesson can fall on: the term, stretched to take in lessons the user added
     * before or after it, but by no more than [MAX_STRETCH_DAYS] either way.
     */
    val span: ClosedRange<LocalDate> = run {
        val earliest = term.start.minusDays(MAX_STRETCH_DAYS)
        val latest = term.end.plusDays(MAX_STRETCH_DAYS)
        val from = timedLessons.mapNotNull { it.from }.minOrNull()?.coerceAtLeast(earliest)
        val until = timedLessons.mapNotNull { it.until }.maxOrNull()?.coerceAtMost(latest)
        minOf(term.start, from ?: term.start)..maxOf(term.end, until ?: term.end)
    }

    fun day(date: LocalDate): DayInfo {
        val special = specialDay(date)
        val kind = when {
            date !in term -> DayKind.OUTSIDE_TERM
            special?.off == true -> DayKind.OFF
            else -> DayKind.TEACHING
        }
        val lessons = week(date.weekStart()).filter { it.date == date && it.lesson.isTimed }
        return DayInfo(date, kind, special?.label, lessons)
    }

    /**
     * Everything that has already happened and isn't ticked, oldest first: timed lessons once they
     * have ended, untimed ones from the day they become due.
     */
    fun pending(now: LocalDateTime, completed: Set<String>): List<Occurrence> {
        val today = now.toLocalDate()
        val time = now.toLocalTime()
        val lastWeek = minOf(today, span.endInclusive).weekStart()
        val result = ArrayList<Occurrence>()
        var week = span.start.weekStart()
        while (!week.isAfter(lastWeek)) {
            week(week).filterTo(result) { occurrence ->
                val over = occurrence.date.isBefore(today) ||
                    (occurrence.date == today && occurrence.lesson.end?.isAfter(time) != true)
                over && occurrence.key !in completed
            }
            week = week.plusWeeks(1)
        }
        return result
    }

    /**
     * Every occurrence that belongs to the week starting on [sunday], ordered by date and time.
     * A changed occurrence stays in its own week, so this is all that can fall on those seven days.
     */
    fun week(sunday: LocalDate): List<Occurrence> = synchronized(weeks) {
        weeks.getOrPut(sunday) { buildWeek(sunday) }
    }

    private fun buildWeek(sunday: LocalDate): List<Occurrence> {
        val result = ArrayList<Occurrence>()
        for (offset in 0L..6L) {
            val date = sunday.plusDays(offset)
            // A lesson added for one date is shown on that date, whatever the calendar says about it.
            for (lesson in oneOffLessons) {
                if (lesson.from == date) {
                    occurrence(lesson, OccurrenceKeys.regular(term.id, lesson.id, sunday), sunday, date)?.let(result::add)
                }
            }
            val special = specialDay(date)
            if (special?.off == true) continue
            val inTerm = date in term
            val weekday = special?.followsDay ?: date.dayOfWeek
            val substituted = weekday != date.dayOfWeek
            val closesAt = special?.classesEndAt
            for (lesson in weeklyLessons) {
                if (lesson.day != weekday || !lesson.runsOn(date)) continue
                // Outside the term only a lesson the user dated themselves takes place.
                if (!inTerm && lesson.from == null) continue
                if (closesAt != null && lesson.start!! >= closesAt) continue
                val key = if (substituted) {
                    OccurrenceKeys.substituted(term.id, lesson.id, sunday, date)
                } else {
                    OccurrenceKeys.regular(term.id, lesson.id, sunday)
                }
                occurrence(lesson, key, sunday, date)?.let(result::add)
            }
        }
        for (lesson in untimedLessons) {
            val date = dueDate(lesson, sunday)?.takeIf(lesson::runsOn) ?: continue
            occurrence(lesson, OccurrenceKeys.regular(term.id, lesson.id, sunday), sunday, date)?.let(result::add)
        }
        return result.sortedWith(
            compareBy({ it.date }, { it.lesson.start ?: LocalTime.MIN }, { it.lesson.end ?: LocalTime.MIN }),
        )
    }

    /** The occurrence the pattern puts on [patternDate], with its change applied; `null` if cancelled. */
    private fun occurrence(lesson: Lesson, key: String, sunday: LocalDate, patternDate: LocalDate): Occurrence? {
        val change = changesByKey[key]
        if (change?.cancelled == true) return null

        val date = change?.day?.let { sunday.plusDays(it.sundayOffset()) } ?: patternDate
        var start = lesson.start
        var end = lesson.end
        if (lesson.isTimed && change != null) {
            val changedStart = change.start ?: start!!
            val changedEnd = change.end ?: end!!
            if (changedStart < changedEnd) {
                start = changedStart
                end = changedEnd
            }
        }
        val course = coursesById.getValue(lesson.course)
        val reminders = schedule.occurrenceReminders
        return Occurrence(
            lesson = lesson.copy(
                day = date.dayOfWeek,
                start = start,
                end = end,
                type = change?.type ?: lesson.type,
                teachers = change?.teachers ?: lesson.teachers,
                place = (change?.place ?: lesson.place)?.ifBlank { null },
            ),
            course = change?.name?.let { course.copy(name = it) } ?: course,
            date = date,
            key = key,
            reminderMinutes = if (key in reminders) reminders[key] else schedule.lessonReminders[lesson.id],
            reminderRepeats = lesson.id in schedule.lessonReminders,
        )
    }

    private fun specialDay(date: LocalDate): SpecialDay? = schedule.calendar.days.firstOrNull { date in it }

    private fun isTeachingDay(date: LocalDate): Boolean = date in term && specialDay(date)?.off != true

    /**
     * An untimed lesson happens once in every week that has teaching: on its own weekday, or on the
     * next teaching day of that week (up to Friday) if that one is off.
     */
    private fun dueDate(lesson: Lesson, sunday: LocalDate): LocalDate? =
        (lesson.day.sundayOffset()..FRIDAY_OFFSET).map(sunday::plusDays).firstOrNull(::isTeachingDay)

    private companion object {
        const val FRIDAY_OFFSET = 5L
        const val MAX_STRETCH_DAYS = 366L
    }
}
