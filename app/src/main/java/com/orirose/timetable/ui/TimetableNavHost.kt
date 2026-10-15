package com.orirose.timetable.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orirose.timetable.ui.backlog.BacklogScreen
import com.orirose.timetable.ui.day.DayScreen
import com.orirose.timetable.ui.day.LessonLink
import com.orirose.timetable.ui.importing.ImportDialog
import com.orirose.timetable.ui.importing.ImportViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
private data object DayRoute

@Serializable
private data object BacklogRoute

/**
 * The app's screens. A new screen is a new route here.
 *
 * @param lessonToShow a lesson to bring into view, set when a reminder is tapped
 */
@Composable
fun TimetableNavHost(
    importViewModel: ImportViewModel,
    lessonToShow: StateFlow<LessonLink?>,
    onLessonShown: () -> Unit,
) {
    val navController = rememberNavController()
    val pickScheduleFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(importViewModel::open)
    }

    val link by lessonToShow.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        if (link != null) navController.popBackStack<DayRoute>(inclusive = false)
    }

    NavHost(navController, startDestination = DayRoute) {
        composable<DayRoute> {
            DayScreen(
                onOpenBacklog = { navController.navigate(BacklogRoute) { launchSingleTop = true } },
                link = link,
                onLinkShown = onLessonShown,
            )
        }
        composable<BacklogRoute> {
            BacklogScreen(
                onBack = { navController.popBackStack() },
                onReplaceSchedule = { pickScheduleFile.launch(SCHEDULE_MIME_TYPES) },
            )
        }
    }

    val importState by importViewModel.state.collectAsStateWithLifecycle()
    ImportDialog(importState, onConfirm = importViewModel::confirm, onDismiss = importViewModel::dismiss)
}

// File managers disagree on what a .json file is.
private val SCHEDULE_MIME_TYPES = arrayOf("application/json", "text/*", "application/octet-stream")
