package com.orirose.timetable.ui

import com.orirose.timetable.domain.LessonDetails
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.ui.day.DayActions
import java.time.LocalDate

/** Stands in for the ViewModel in screen tests and notes, in words, what the screen asked for. */
class RecordedActions : DayActions {
    val calls = mutableListOf<String>()

    override fun setCompleted(key: String, done: Boolean) {
        calls += "tick $key $done"
    }

    override fun setReminder(occurrence: Occurrence, minutesBefore: Int, everyTime: Boolean) {
        calls += "remind ${occurrence.key} $minutesBefore $everyTime"
    }

    override fun removeReminder(occurrence: Occurrence, everyTime: Boolean) {
        calls += "unremind ${occurrence.key} $everyTime"
    }

    override fun delete(occurrence: Occurrence, everyTime: Boolean) {
        calls += "delete ${occurrence.key} $everyTime"
    }

    override fun edit(occurrence: Occurrence, details: LessonDetails, everyTime: Boolean) {
        calls += "edit ${occurrence.key} ${details.name} ${details.day} ${details.place} $everyTime"
    }

    override fun add(details: LessonDetails, firstDate: LocalDate, until: LocalDate?) {
        calls += "add ${details.name} ${details.start}-${details.end} $firstDate until $until"
    }
}
