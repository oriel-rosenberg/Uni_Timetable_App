package com.orirose.timetable.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

/** The Sunday that starts this date's week. */
fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

/** Sunday = 0 … Saturday = 6. */
fun DayOfWeek.sundayOffset(): Long = (value % 7).toLong()

/**
 * The text that identifies one occurrence of a lesson, for ticks, reminders and changes.
 *
 * A weekly lesson happens once a week, so an occurrence is named by its week, not its date: moving
 * it to another day, once or for good, keeps whatever was attached to it. The exception is a day
 * that runs another weekday's timetable, which gives that week a second occurrence; that one also
 * carries its date.
 */
object OccurrenceKeys {
    private const val SEPARATOR = '|'

    fun regular(termId: String, lessonId: String, week: LocalDate): String = "$termId$SEPARATOR$lessonId$SEPARATOR$week"

    fun substituted(termId: String, lessonId: String, week: LocalDate, date: LocalDate): String =
        "${regular(termId, lessonId, week)}$SEPARATOR$date"

    /** What the keys of all of a lesson's occurrences start with. */
    fun lessonPrefix(termId: String, lessonId: String): String = "$termId$SEPARATOR$lessonId$SEPARATOR"

    /** What the keys of all occurrences of [key]'s lesson start with. */
    fun lessonPrefixOf(key: String): String {
        val parts = key.split(SEPARATOR)
        return lessonPrefix(parts[0], parts.getOrElse(1) { "" })
    }

    /** The Sunday of the occurrence's week. */
    fun weekOf(key: String): LocalDate? = key.split(SEPARATOR).getOrNull(2)?.let(::parseDate)

    /** The date the weekly pattern gave a substituted occurrence, or `null` for a regular one. */
    fun substitutedDate(key: String): LocalDate? = key.split(SEPARATOR).getOrNull(3)?.let(::parseDate)

    /** Ids must not contain the separator, or keys could not be told apart. */
    fun isValidId(id: String): Boolean = id.isNotBlank() && SEPARATOR !in id

    /**
     * The first version of the app keyed an occurrence by its own date; such keys become week keys.
     * Keys already in today's form come back unchanged.
     */
    fun normalize(key: String): String {
        val parts = key.split(SEPARATOR)
        if (parts.size != 3) return key
        val date = parseDate(parts[2]) ?: return key
        return regular(parts[0], parts[1], date.weekStart())
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }
}
