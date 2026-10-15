package com.orirose.timetable.domain

import com.orirose.timetable.data.model.minuteOfDay

/** A stretch of a day, in minutes since midnight. */
data class MinuteRange(val start: Int, val end: Int) {
    val length: Int get() = end - start
}

/** A lesson's slot on the day grid: overlapping lessons share the width as [columnCount] columns. */
data class PlacedLesson(
    val occurrence: Occurrence,
    val range: MinuteRange,
    val column: Int,
    val columnCount: Int,
)

/** The geometry of the day grid that doesn't depend on pixels. All lessons passed in are timed. */
object TimelineLayout {

    const val DAY_START = 9 * 60
    const val DAY_END = 19 * 60

    private const val WINDOW_MARGIN = 15
    private const val MIN_WINDOW = 4 * 60

    /** The hours the grid covers: 9:00–19:00, stretched to whole hours if a lesson falls outside. */
    fun dayRange(lessons: List<Occurrence>): MinuteRange {
        val earliest = lessons.minOfOrNull { it.range.start } ?: DAY_START
        val latest = lessons.maxOfOrNull { it.range.end } ?: DAY_END
        return MinuteRange(
            start = minOf(DAY_START, earliest / 60 * 60),
            end = maxOf(DAY_END, (latest + 59) / 60 * 60),
        )
    }

    /**
     * The stretch a day opens on: from its first lesson to its last with a little air around,
     * never less than [MIN_WINDOW] so a lone short lesson isn't blown up to fill the screen.
     */
    fun defaultWindow(lessons: List<Occurrence>, day: MinuteRange = dayRange(lessons)): MinuteRange {
        if (lessons.isEmpty()) return day
        var start = lessons.minOf { it.range.start } - WINDOW_MARGIN
        var end = lessons.maxOf { it.range.end } + WINDOW_MARGIN
        val missing = MIN_WINDOW - (end - start)
        if (missing > 0) {
            start -= missing / 2
            end = start + MIN_WINDOW
        }
        // Slide back inside the day, then trim whatever still sticks out.
        val shift = maxOf(day.start - start, 0) - maxOf(end - day.end, 0)
        return MinuteRange(
            start = (start + shift).coerceAtLeast(day.start),
            end = (end + shift).coerceAtMost(day.end),
        )
    }

    /** Puts overlapping lessons side by side; lessons that don't overlap anything get the full width. */
    fun place(lessons: List<Occurrence>): List<PlacedLesson> {
        val result = mutableListOf<PlacedLesson>()
        val group = mutableListOf<Pair<Occurrence, Int>>() // lessons chained by overlaps, each with its column
        val columnEnds = mutableListOf<Int>()
        var groupEnd = Int.MIN_VALUE

        fun closeGroup() {
            group.mapTo(result) { (occurrence, column) -> PlacedLesson(occurrence, occurrence.range, column, columnEnds.size) }
            group.clear()
            columnEnds.clear()
            groupEnd = Int.MIN_VALUE
        }

        for (occurrence in lessons.sortedWith(compareBy({ it.range.start }, { it.range.end }))) {
            val range = occurrence.range
            if (range.start >= groupEnd) closeGroup()
            var column = columnEnds.indexOfFirst { it <= range.start }
            if (column == -1) {
                columnEnds += range.end
                column = columnEnds.lastIndex
            } else {
                columnEnds[column] = range.end
            }
            group += occurrence to column
            groupEnd = maxOf(groupEnd, range.end)
        }
        closeGroup()
        return result
    }

    private val Occurrence.range: MinuteRange
        get() = MinuteRange(lesson.start!!.minuteOfDay(), lesson.end!!.minuteOfDay())
}
