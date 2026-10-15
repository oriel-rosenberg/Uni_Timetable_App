package com.orirose.timetable.domain

import com.orirose.timetable.data.model.Course
import com.orirose.timetable.data.model.Lesson
import com.orirose.timetable.data.model.LessonType
import com.orirose.timetable.data.model.OccurrenceChange
import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.data.model.Term
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Everything about a timed lesson that the user can edit. */
data class LessonDetails(
    val name: String,
    val type: LessonType,
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val place: String,
    val teachers: List<String>,
)

/** A lesson as it is on one date. */
fun Occurrence.details(): LessonDetails = LessonDetails(
    name = course.name,
    type = lesson.type,
    day = lesson.day,
    start = lesson.start!!,
    end = lesson.end!!,
    place = lesson.place.orEmpty(),
    teachers = lesson.teachers,
)

/** A timed lesson as it is every week, or `null` if there is no such lesson. */
fun Schedule.lessonDetails(lessonId: String): LessonDetails? {
    val lesson = lessons.firstOrNull { it.id == lessonId && it.isTimed } ?: return null
    val course = courses.firstOrNull { it.id == lesson.course } ?: return null
    return LessonDetails(course.name, lesson.type, lesson.day, lesson.start!!, lesson.end!!, lesson.place.orEmpty(), lesson.teachers)
}

/**
 * The changes a user can make to a schedule from the day grid. Each returns a new schedule; none
 * touches ticks, which stay attached to occurrences through their keys.
 */
object ScheduleEdits {

    /**
     * Adds a lesson the user made: once, on [firstDate], or weekly from [firstDate] to [until].
     * A name that matches an existing course joins that course; otherwise a new course is made.
     */
    fun addLesson(schedule: Schedule, details: LessonDetails, firstDate: LocalDate, until: LocalDate?): Schedule {
        val name = details.name.trim()
        val existing = schedule.courses.firstOrNull { it.name.trim() == name }
        val course = existing ?: Course(freeId(schedule.courses.map { it.id }, "my-"), name)
        val lesson = Lesson(
            id = freeId(schedule.lessons.map { it.id }, "${course.id}-"),
            course = course.id,
            type = details.type,
            day = firstDate.dayOfWeek,
            start = details.start,
            end = details.end,
            teachers = details.teachers,
            place = details.place.ifBlank { null },
            from = firstDate,
            until = until ?: firstDate,
        )
        return schedule.copy(
            courses = if (existing == null) schedule.courses + course else schedule.courses,
            lessons = schedule.lessons + lesson,
        )
    }

    /** The last [day] of the term: where a lesson added "until the end of the semester" stops. */
    fun defaultUntil(term: Term, day: DayOfWeek): LocalDate =
        term.end.with(TemporalAdjusters.previousOrSame(day))

    /** The first of `prefix1`, `prefix2`, … that isn't taken. */
    private fun freeId(taken: List<String>, prefix: String): String =
        generateSequence(1) { it + 1 }.map { "$prefix$it" }.first { it !in taken }

    /** Reminds [minutesBefore] the start of this occurrence, or of every occurrence of its lesson. */
    fun setReminder(schedule: Schedule, occurrence: Occurrence, minutesBefore: Int, everyTime: Boolean): Schedule =
        if (everyTime) {
            schedule.copy(
                lessonReminders = schedule.lessonReminders + (occurrence.lesson.id to minutesBefore),
                occurrenceReminders = schedule.occurrenceReminders.withoutLesson(schedule, occurrence.lesson.id),
            )
        } else {
            schedule.copy(occurrenceReminders = schedule.occurrenceReminders + (occurrence.key to minutesBefore))
        }

    fun removeReminder(schedule: Schedule, occurrence: Occurrence, everyTime: Boolean): Schedule {
        val lessonId = occurrence.lesson.id
        return when {
            everyTime -> schedule.copy(
                lessonReminders = schedule.lessonReminders - lessonId,
                occurrenceReminders = schedule.occurrenceReminders.withoutLesson(schedule, lessonId),
            )
            // The lesson reminds every time: record that this one occurrence is the exception.
            lessonId in schedule.lessonReminders ->
                schedule.copy(occurrenceReminders = schedule.occurrenceReminders + (occurrence.key to null))
            else -> schedule.copy(occurrenceReminders = schedule.occurrenceReminders - occurrence.key)
        }
    }

    /** Cancels one occurrence; the lesson carries on in other weeks. */
    fun deleteOccurrence(schedule: Schedule, occurrence: Occurrence): Schedule {
        val change = schedule.changeFor(occurrence.key).copy(cancelled = true)
        return schedule.withChange(change).copy(occurrenceReminders = schedule.occurrenceReminders - occurrence.key)
    }

    /** Removes a weekly lesson altogether, with its reminders and the changes made to it. */
    fun deleteLesson(schedule: Schedule, lessonId: String): Schedule {
        val prefix = OccurrenceKeys.lessonPrefix(schedule.term.id, lessonId)
        return schedule.copy(
            lessons = schedule.lessons.filterNot { it.id == lessonId },
            changes = schedule.changes.filterNot { it.key.startsWith(prefix) },
            lessonReminders = schedule.lessonReminders - lessonId,
            occurrenceReminders = schedule.occurrenceReminders.withoutLesson(schedule, lessonId),
        )
    }

    /** Makes one occurrence differ from its lesson. Only what actually differs is recorded. */
    fun editOccurrence(schedule: Schedule, occurrence: Occurrence, details: LessonDetails): Schedule {
        val lessonId = occurrence.lesson.id
        val weekly = schedule.lessonDetails(lessonId) ?: return schedule
        val patternDay = OccurrenceKeys.substitutedDate(occurrence.key)?.dayOfWeek ?: weekly.day
        val change = OccurrenceChange(
            key = occurrence.key,
            day = details.day.takeIf { it != patternDay },
            start = details.start.takeIf { it != weekly.start },
            end = details.end.takeIf { it != weekly.end },
            name = details.name.takeIf { it != weekly.name },
            type = details.type.takeIf { it != weekly.type },
            teachers = details.teachers.takeIf { it != weekly.teachers },
            place = details.place.takeIf { it != weekly.place },
        )
        return schedule.withChange(change)
    }

    /**
     * Changes a weekly lesson for every week. The name belongs to the course, so renaming here
     * renames the course's other lessons too.
     */
    fun editLesson(schedule: Schedule, lessonId: String, details: LessonDetails): Schedule {
        val lesson = schedule.lessons.firstOrNull { it.id == lessonId } ?: return schedule
        return schedule.copy(
            courses = schedule.courses.map { if (it.id == lesson.course) it.copy(name = details.name) else it },
            lessons = schedule.lessons.map {
                if (it.id != lessonId) {
                    it
                } else {
                    // Its first and last dates move with the day, each within its own week, so a new
                    // day neither loses the first or last week nor (for a one-off) the lesson itself.
                    fun moved(date: LocalDate?) = date?.weekStart()?.plusDays(details.day.sundayOffset())
                    it.copy(
                        type = details.type,
                        day = details.day,
                        start = details.start,
                        end = details.end,
                        teachers = details.teachers,
                        place = details.place.ifBlank { null },
                        from = moved(it.from),
                        until = moved(it.until),
                    )
                }
            },
        )
    }

    private fun Schedule.changeFor(key: String): OccurrenceChange =
        changes.firstOrNull { it.key == key } ?: OccurrenceChange(key)

    /** Records [change], replacing any earlier one for the same occurrence; an empty change is dropped. */
    private fun Schedule.withChange(change: OccurrenceChange): Schedule {
        val others = changes.filterNot { it.key == change.key }
        return copy(changes = if (change.isEmpty) others else others + change)
    }

    private fun <V> Map<String, V>.withoutLesson(schedule: Schedule, lessonId: String): Map<String, V> {
        val prefix = OccurrenceKeys.lessonPrefix(schedule.term.id, lessonId)
        return filterKeys { !it.startsWith(prefix) }
    }
}
