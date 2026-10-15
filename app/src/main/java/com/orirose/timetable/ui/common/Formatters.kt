package com.orirose.timetable.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.orirose.timetable.R
import com.orirose.timetable.data.model.LessonType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WeekdayLetters = mapOf(
    DayOfWeek.SUNDAY to "א",
    DayOfWeek.MONDAY to "ב",
    DayOfWeek.TUESDAY to "ג",
    DayOfWeek.WEDNESDAY to "ד",
    DayOfWeek.THURSDAY to "ה",
    DayOfWeek.FRIDAY to "ו",
    DayOfWeek.SATURDAY to "ש",
)

private val HourMinute = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)

// Unicode isolates: keep a run of text left-to-right inside a right-to-left line.
private const val LEFT_TO_RIGHT_ISOLATE = "⁦"
private const val POP_ISOLATE = "⁩"

/** Weekday letter and short date, e.g. "ג 13.10". */
fun LocalDate.shortLabel(): String = "${WeekdayLetters.getValue(dayOfWeek)} $dayOfMonth.$monthValue"

/**
 * E.g. "12:00 – 13:45", with the start time first even in Hebrew text. The only place it can wrap
 * is after the dash, so a block too narrow for the whole range shows "12:00 –" over "13:45", never
 * a cut-off time.
 */
fun timeRange(start: LocalTime, end: LocalTime): String =
    "$LEFT_TO_RIGHT_ISOLATE${start.format(HourMinute)} – ${end.format(HourMinute)}$POP_ISOLATE"

val LessonType.labelRes: Int
    get() = when (this) {
        LessonType.LECTURE -> R.string.type_lecture
        LessonType.RECITATION -> R.string.type_recitation
        LessonType.OTHER -> R.string.type_other
    }

@Composable
fun LessonType.label(): String = stringResource(labelRes)

/** The weekday's letter, e.g. "ג". */
val DayOfWeek.letter: String get() = WeekdayLetters.getValue(this)

/** E.g. "12:00". */
fun LocalTime.label(): String = format(HourMinute)

/** "היום", "אתמול", "לפני יומיים", "לפני 5 ימים". */
@Composable
fun daysAgoLabel(days: Int): String = when {
    days <= 0 -> stringResource(R.string.days_ago_today)
    days == 1 -> stringResource(R.string.days_ago_yesterday)
    days == 2 -> stringResource(R.string.days_ago_two)
    else -> stringResource(R.string.days_ago_many, days)
}
