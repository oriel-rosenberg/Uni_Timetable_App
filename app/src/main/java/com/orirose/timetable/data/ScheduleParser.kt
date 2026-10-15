package com.orirose.timetable.data

import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.domain.OccurrenceKeys
import kotlinx.serialization.json.Json
import java.time.DateTimeException

class ScheduleFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Turns the text of a `schedule.json` into a [Schedule], refusing anything the app could not show correctly. */
object ScheduleParser {

    private val json = Json {
        ignoreUnknownKeys = true // newer files may carry fields this version doesn't know
        coerceInputValues = true // e.g. an unknown lesson type becomes "other"
    }

    /** The text to store for [schedule]; [parse] reads it back. */
    fun write(schedule: Schedule): String = json.encodeToString(Schedule.serializer(), schedule)

    @Throws(ScheduleFormatException::class)
    fun parse(text: String): Schedule {
        val schedule = try {
            json.decodeFromString<Schedule>(text)
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw ScheduleFormatException(e.message ?: "Not a schedule file", e)
        } catch (e: DateTimeException) {
            throw ScheduleFormatException(e.message ?: "Bad date or time", e)
        }
        validate(schedule)
        return schedule
    }

    private fun validate(schedule: Schedule) {
        check(schedule.formatVersion == Schedule.CURRENT_FORMAT_VERSION) {
            "Unsupported formatVersion ${schedule.formatVersion}"
        }
        check(OccurrenceKeys.isValidId(schedule.term.id)) { "term.id is empty or contains '|'" }
        check(!schedule.term.start.isAfter(schedule.term.end)) { "term.start is after term.end" }
        check(!schedule.term.end.isAfter(schedule.term.start.plusDays(MAX_TERM_DAYS))) { "The term is longer than a year" }

        schedule.calendar.days.forEach { day ->
            check(!day.from.isAfter(day.to)) { "Calendar entry '${day.label}': from is after to" }
        }

        val courseIds = schedule.courses.map { it.id }
        check(courseIds.size == courseIds.toSet().size) { "Duplicate course id" }

        val lessonIds = schedule.lessons.map { it.id }
        check(lessonIds.size == lessonIds.toSet().size) { "Duplicate lesson id" }

        schedule.lessons.forEach { lesson ->
            check(OccurrenceKeys.isValidId(lesson.id)) { "A lesson id is empty or contains '|'" }
            check(lesson.course in courseIds) { "Lesson ${lesson.id}: unknown course '${lesson.course}'" }
            check((lesson.start == null) == (lesson.end == null)) { "Lesson ${lesson.id}: needs both start and end, or neither" }
            if (lesson.start != null && lesson.end != null) {
                check(lesson.start < lesson.end) { "Lesson ${lesson.id}: start is not before end" }
            }
            if (lesson.from != null && lesson.until != null) {
                check(!lesson.from.isAfter(lesson.until)) { "Lesson ${lesson.id}: from is after until" }
            }
        }
    }

    // A semester is months long; anything much longer is a mistake, and would make every lookup slow.
    private const val MAX_TERM_DAYS = 366L

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw ScheduleFormatException(message())
    }
}
