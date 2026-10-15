package com.orirose.timetable.ui.day

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// The day pager has one page per teaching day, Sunday to Thursday: there are no lessons on Friday
// or Saturday, so those days are not shown at all. Pages are counted from a Sunday well in the past.
private val FirstPageDate: LocalDate = LocalDate.of(2020, 1, 5)

internal const val DAYS_PER_WEEK = 5
internal const val DAY_PAGE_COUNT = DAYS_PER_WEEK * 52 * 40

/** The page for [date]; a Friday or Saturday leads to the Sunday after it. */
internal fun pageOfDate(date: LocalDate): Int {
    val days = ChronoUnit.DAYS.between(FirstPageDate, date)
    var week = Math.floorDiv(days, 7L)
    var day = Math.floorMod(days, 7L)
    if (day >= DAYS_PER_WEEK) {
        week += 1
        day = 0
    }
    return (week * DAYS_PER_WEEK + day).toInt().coerceIn(0, DAY_PAGE_COUNT - 1)
}

internal fun dateOfPage(page: Int): LocalDate =
    FirstPageDate.plusDays((page / DAYS_PER_WEEK) * 7L + page % DAYS_PER_WEEK)

/** Which week a page belongs to; it changes between a Thursday and the Sunday after it. */
internal fun weekOfPage(page: Int): Int = page / DAYS_PER_WEEK
