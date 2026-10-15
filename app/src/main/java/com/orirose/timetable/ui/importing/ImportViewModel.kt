package com.orirose.timetable.ui.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orirose.timetable.appContainer
import com.orirose.timetable.data.ScheduleFormatException
import com.orirose.timetable.data.ScheduleParser
import com.orirose.timetable.data.ScheduleRepository
import com.orirose.timetable.data.model.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ImportState {
    data object Idle : ImportState

    /** A valid file is waiting for the user's go-ahead. */
    data class Confirm(val schedule: Schedule, val text: String) : ImportState

    data class Failed(val reason: String) : ImportState
}

/** Replacing the schedule from a file: read, validate, ask, then apply. */
class ImportViewModel(private val repository: ScheduleRepository) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun open(uri: Uri) {
        viewModelScope.launch {
            _state.value = try {
                val text = repository.readText(uri)
                ImportState.Confirm(ScheduleParser.parse(text), text)
            } catch (e: ScheduleFormatException) {
                ImportState.Failed(e.message.orEmpty())
            } catch (e: IOException) {
                ImportState.Failed(e.message.orEmpty())
            } catch (e: SecurityException) {
                ImportState.Failed(e.message.orEmpty())
            }
        }
    }

    fun confirm() {
        val pending = _state.value as? ImportState.Confirm ?: return
        viewModelScope.launch {
            _state.value = try {
                repository.replace(pending.text)
                ImportState.Idle
            } catch (e: IOException) {
                ImportState.Failed(e.message.orEmpty())
            }
        }
    }

    fun dismiss() {
        _state.value = ImportState.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ImportViewModel(appContainer().scheduleRepository) }
        }
    }
}
