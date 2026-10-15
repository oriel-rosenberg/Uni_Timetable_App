package com.orirose.timetable

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.orirose.timetable.ui.TimetableNavHost
import com.orirose.timetable.ui.day.LessonLink
import com.orirose.timetable.ui.importing.ImportViewModel
import com.orirose.timetable.ui.theme.TimetableTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val importViewModel: ImportViewModel by viewModels { ImportViewModel.Factory }
    private val lessonToShow get() = (application as TimetableApplication).container.lessonToShow

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) showLessonFrom(intent)
        setContent {
            TimetableTheme {
                TimetableNavHost(importViewModel, lessonToShow, onLessonShown = { lessonToShow.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showLessonFrom(intent)
    }

    /** A tapped reminder names its lesson. Anything malformed is ignored: the worst an intent can do is pick a day. */
    private fun showLessonFrom(intent: Intent) {
        if (intent.action != ACTION_SHOW_LESSON) return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val day = intent.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
        if (day !in SHOWABLE_DAYS) return
        lessonToShow.value = LessonLink(LocalDate.ofEpochDay(day), key)
    }

    companion object {
        const val ACTION_SHOW_LESSON = "com.orirose.timetable.SHOW_LESSON"
        const val EXTRA_DATE = "date"
        const val EXTRA_KEY = "key"

        // Epoch days of the years 2000–2100.
        private val SHOWABLE_DAYS = 10_957L..47_482L
    }
}
