package com.orirose.timetable.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orirose.timetable.appContainer
import com.orirose.timetable.data.CompletionRepository
import com.orirose.timetable.data.ScheduleRepository
import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.domain.LessonDetails
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.domain.OccurrenceKeys
import com.orirose.timetable.domain.ScheduleCalculator
import com.orirose.timetable.domain.ScheduleEdits
import com.orirose.timetable.domain.minuteTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

data class DayUiState(
    val calculator: ScheduleCalculator,
    val completed: Set<String>,
    val now: LocalDateTime,
    val pendingCount: Int,
) {
    val today: LocalDate get() = now.toLocalDate()
}

/** A lesson to bring into view on the day grid. */
data class LessonLink(val date: LocalDate, val key: String)

/**
 * What the user can do to a lesson from the day view. Where there is an [everyTime] it chooses
 * between this one occurrence and the lesson in every week.
 */
interface DayActions {
    fun setCompleted(key: String, done: Boolean)
    fun setReminder(occurrence: Occurrence, minutesBefore: Int, everyTime: Boolean)
    fun removeReminder(occurrence: Occurrence, everyTime: Boolean)
    fun delete(occurrence: Occurrence, everyTime: Boolean)
    fun edit(occurrence: Occurrence, details: LessonDetails, everyTime: Boolean)

    /** Adds a lesson on [firstDate], and weekly from then until [until] when that is given. */
    fun add(details: LessonDetails, firstDate: LocalDate, until: LocalDate?)
}

class DayViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val completionRepository: CompletionRepository,
    clock: Clock,
) : ViewModel(), DayActions {

    /** `null` until the stored schedule and ticks have been read, a matter of milliseconds. */
    val uiState: StateFlow<DayUiState?> = combine(
        scheduleRepository.schedule.filterNotNull().map(::ScheduleCalculator),
        completionRepository.completed,
        minuteTicker(clock),
    ) { calculator, completed, now ->
        DayUiState(calculator, completed, now, pendingCount = calculator.pending(now, completed).size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override fun setCompleted(key: String, done: Boolean) {
        viewModelScope.launch { completionRepository.setCompleted(key, done) }
    }

    override fun setReminder(occurrence: Occurrence, minutesBefore: Int, everyTime: Boolean) =
        change { ScheduleEdits.setReminder(it, occurrence, minutesBefore, everyTime) }

    override fun removeReminder(occurrence: Occurrence, everyTime: Boolean) =
        change { ScheduleEdits.removeReminder(it, occurrence, everyTime) }

    override fun delete(occurrence: Occurrence, everyTime: Boolean) {
        if (everyTime) {
            // The lesson goes with its ticks, so a lesson added later can't inherit them.
            viewModelScope.launch {
                scheduleRepository.update { ScheduleEdits.deleteLesson(it, occurrence.lesson.id) }
                completionRepository.forget(OccurrenceKeys.lessonPrefixOf(occurrence.key))
            }
        } else {
            change { ScheduleEdits.deleteOccurrence(it, occurrence) }
        }
    }

    override fun edit(occurrence: Occurrence, details: LessonDetails, everyTime: Boolean) = change {
        if (everyTime) ScheduleEdits.editLesson(it, occurrence.lesson.id, details) else ScheduleEdits.editOccurrence(it, occurrence, details)
    }

    override fun add(details: LessonDetails, firstDate: LocalDate, until: LocalDate?) =
        change { ScheduleEdits.addLesson(it, details, firstDate, until) }

    private fun change(edit: (Schedule) -> Schedule) {
        viewModelScope.launch { scheduleRepository.update(edit) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                DayViewModel(container.scheduleRepository, container.completionRepository, container.clock)
            }
        }
    }
}
