package com.orirose.timetable

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.orirose.timetable.data.CompletionRepository
import com.orirose.timetable.data.ScheduleRepository
import com.orirose.timetable.domain.ScheduleCalculator
import com.orirose.timetable.reminders.LessonReminders
import com.orirose.timetable.ui.day.LessonLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TimetableApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** The app's few long-lived objects, built by hand. New shared dependencies go here. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val clock: Clock = DeviceClock
    val scheduleRepository = ScheduleRepository(context, scope)
    val completionRepository = CompletionRepository(context.completionStore)

    /** The lesson a tapped reminder asks the day view to show. */
    val lessonToShow = MutableStateFlow<LessonLink?>(null)

    private val reminders = LessonReminders(context, clock)

    init {
        // Reminders follow the schedule: whenever it loads or changes, the next alarm is set again.
        scope.launch {
            scheduleRepository.schedule.filterNotNull().collect { reminders.sync(ScheduleCalculator(it)) }
        }
    }

    /** Shows what has come due and sets the next alarm; for when something outside the app woke it. */
    suspend fun syncReminders() {
        reminders.sync(currentCalculator())
    }

    /** The snooze button on a reminder. */
    suspend fun snoozeReminder(key: String) {
        reminders.snooze(currentCalculator(), key)
    }

    private suspend fun currentCalculator() = ScheduleCalculator(scheduleRepository.schedule.filterNotNull().first())
}

/** The phone's clock, following its time zone even when that changes while the app is alive. */
private object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}

private val Context.completionStore by preferencesDataStore(name = "completions")

/** For ViewModel factories. */
fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as TimetableApplication).container
