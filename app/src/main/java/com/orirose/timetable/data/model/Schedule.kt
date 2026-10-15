@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class, DayOfWeekSerializer::class)

package com.orirose.timetable.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Everything the app knows about one semester. This is exactly the shape of `schedule.json`;
 * see docs/schedule-format.md.
 */
@Serializable
data class Schedule(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val calendar: AcademicCalendar = AcademicCalendar(),
    val term: Term,
    val courses: List<Course>,
    val lessons: List<Lesson>,
    // What the user changed on the phone. A file made by hand normally has none of these.
    val changes: List<OccurrenceChange> = emptyList(),
    val lessonReminders: Map<String, Int> = emptyMap(),
    val occurrenceReminders: Map<String, Int?> = emptyMap(),
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/**
 * How one occurrence of a lesson differs from its weekly pattern. [key] is the occurrence's key
 * (see `OccurrenceKeys`); a field left `null` is unchanged.
 *
 * @property cancelled this occurrence doesn't take place
 * @property day it takes place on another day of the same week
 */
@Serializable
data class OccurrenceChange(
    val key: String,
    val cancelled: Boolean = false,
    val day: DayOfWeek? = null,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val name: String? = null,
    val type: LessonType? = null,
    val teachers: List<String>? = null,
    val place: String? = null,
) {
    val isEmpty: Boolean
        get() = this == OccurrenceChange(key)
}

/** The university's year: the dates on which the weekly timetable does not simply repeat. */
@Serializable
data class AcademicCalendar(
    val name: String = "",
    val days: List<SpecialDay> = emptyList(),
)

/**
 * A date, or run of dates, that is special. With no effect set it is only a label on that day.
 *
 * @property off no lessons at all
 * @property classesEndAt lessons starting at or after this time are cancelled
 * @property followsDay lessons are those of another weekday
 */
@Serializable
data class SpecialDay(
    val from: LocalDate,
    val to: LocalDate = from,
    val label: String,
    val off: Boolean = false,
    val classesEndAt: LocalTime? = null,
    val followsDay: DayOfWeek? = null,
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
}

/** A semester. [id] keeps one semester's ticks apart from another's. */
@Serializable
data class Term(
    val id: String,
    val name: String,
    val start: LocalDate,
    val end: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

@Serializable
data class Course(
    val id: String,
    val name: String,
)

/**
 * A weekly lesson. Without [start] and [end] it has no place on the day grid (an online,
 * watch-any-time lesson) and [day] is only the weekday on which each week's instance becomes due.
 *
 * @property from the first date it may take place, if not the start of the term
 * @property until the last date it may take place, if not the end of the term; equal to [from] for
 *   a lesson that happens once
 */
@Serializable
data class Lesson(
    val id: String,
    val course: String,
    val type: LessonType = LessonType.OTHER,
    val day: DayOfWeek,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val teachers: List<String> = emptyList(),
    val place: String? = null,
    val from: LocalDate? = null,
    val until: LocalDate? = null,
) {
    val isTimed: Boolean get() = start != null && end != null

    val isOneOff: Boolean get() = from != null && from == until

    fun runsOn(date: LocalDate): Boolean =
        (from == null || !date.isBefore(from)) && (until == null || !date.isAfter(until))
}

@Serializable
enum class LessonType {
    @SerialName("lecture") LECTURE,
    @SerialName("recitation") RECITATION,
    @SerialName("other") OTHER,
}

fun LocalTime.minuteOfDay(): Int = hour * 60 + minute
