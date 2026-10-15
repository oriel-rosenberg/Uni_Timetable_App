package com.orirose.timetable.ui.backlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orirose.timetable.appContainer
import com.orirose.timetable.data.CompletionRepository
import com.orirose.timetable.data.ScheduleRepository
import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.domain.ScheduleCalculator
import com.orirose.timetable.domain.minuteTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.temporal.ChronoUnit

data class PendingItem(val occurrence: Occurrence, val daysAgo: Int)

/** [items] are oldest first. */
data class BacklogUiState(val schedule: Schedule, val items: List<PendingItem>)

class BacklogViewModel(
    scheduleRepository: ScheduleRepository,
    private val completionRepository: CompletionRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<BacklogUiState?> = combine(
        scheduleRepository.schedule.filterNotNull().map(::ScheduleCalculator),
        completionRepository.completed,
        minuteTicker(clock),
    ) { calculator, completed, now ->
        val today = now.toLocalDate()
        BacklogUiState(
            schedule = calculator.schedule,
            items = calculator.pending(now, completed).map { PendingItem(it, ChronoUnit.DAYS.between(it.date, today).toInt()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCompleted(key: String, done: Boolean) {
        viewModelScope.launch { completionRepository.setCompleted(key, done) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                BacklogViewModel(container.scheduleRepository, container.completionRepository, container.clock)
            }
        }
    }
}
