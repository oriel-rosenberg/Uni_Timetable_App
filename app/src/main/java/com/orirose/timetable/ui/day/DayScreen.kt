package com.orirose.timetable.ui.day

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import com.orirose.timetable.ui.common.shortLabel
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orirose.timetable.R
import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.domain.ScheduleEdits
import com.orirose.timetable.domain.details
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.orirose.timetable.domain.lessonDetails
import com.orirose.timetable.ui.theme.rememberCourseAccents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val HIGHLIGHT_MILLIS = 2_000L

// "היום" and "להשלמה" are the same height, as is the round "+".
private val CornerButtonHeight = 48.dp
private const val WEEK_NOTICE_MILLIS = 1_100L
private const val DAY_CHANGE_MILLIS = 300
private const val WEEK_CHANGE_MILLIS = 650

@Composable
fun DayScreen(
    onOpenBacklog: () -> Unit,
    link: LessonLink? = null,
    onLinkShown: () -> Unit = {},
    viewModel: DayViewModel = viewModel(factory = DayViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state?.let { DayScreenContent(it, viewModel, onOpenBacklog, link = link, onLinkShown = onLinkShown) }
}

/**
 * The day view: a strip of days on top, one swipeable page per day below it.
 *
 * @param initialDate the day shown first; always today in the app
 * @param link a lesson to go to and mark out, as when its reminder is tapped
 */
@Composable
fun DayScreenContent(
    state: DayUiState,
    actions: DayActions,
    onOpenBacklog: () -> Unit,
    initialDate: LocalDate = state.today,
    link: LessonLink? = null,
    onLinkShown: () -> Unit = {},
) {
    val today = state.today
    val pagerState = rememberPagerState(initialPage = pageOfDate(initialDate)) { DAY_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val accents = rememberCourseAccents(state.calculator.schedule)
    val showsToday by remember(today) { derivedStateOf { pagerState.currentPage == pageOfDate(today) } }
    var dialog by remember { mutableStateOf<LessonDialog?>(null) }

    // A new day (midnight, or the app reopened later) brings the view back to today.
    var lastToday by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    LaunchedEffect(today) {
        if (lastToday != today.toEpochDay()) {
            lastToday = today.toEpochDay()
            pagerState.scrollToPage(pageOfDate(today))
        }
    }

    var highlighted by remember { mutableStateOf<LessonLink?>(null) }
    LaunchedEffect(link) {
        val target = link ?: return@LaunchedEffect
        dialog = null
        pagerState.scrollToPage(pageOfDate(target.date))
        highlighted = target
        onLinkShown()
    }
    LaunchedEffect(highlighted) {
        if (highlighted != null) {
            delay(HIGHLIGHT_MILLIS)
            highlighted = null
        }
    }

    // Crossing into another week is marked by showing, for a moment, the week's name.
    val week by remember { derivedStateOf { weekOfPage(pagerState.currentPage) } }
    var announcedWeek by remember { mutableStateOf<Int?>(null) }
    var lastWeek by remember { mutableIntStateOf(week) }
    LaunchedEffect(week) {
        if (week != lastWeek) {
            lastWeek = week
            announcedWeek = week
            delay(WEEK_NOTICE_MILLIS)
            announcedWeek = null
        }
    }

    /** Moves to [page], more slowly when that means changing week, so the change registers. */
    fun goTo(page: Int) {
        val crossesWeek = weekOfPage(page) != weekOfPage(pagerState.currentPage)
        scope.launch {
            pagerState.animateScrollToPage(page, animationSpec = tween(if (crossesWeek) WEEK_CHANGE_MILLIS else DAY_CHANGE_MILLIS))
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            DayStrip(
                pagerState = pagerState,
                today = today,
                isQuiet = { date -> state.calculator.day(date).lessons.isEmpty() },
                onSelect = ::goTo,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                key = { it },
            ) { page ->
                val date = dateOfPage(page)
                val info = remember(state.calculator, date) { state.calculator.day(date) }
                DayPage(
                    info = info,
                    completed = state.completed,
                    now = state.now.toLocalTime().takeIf { date == today },
                    accents = accents,
                    highlightKey = highlighted?.key?.takeIf { highlighted?.date == date },
                    onToggle = actions::setCompleted,
                    onAction = { occurrence, action ->
                        dialog = when (action) {
                            LessonAction.REMINDER -> LessonDialog.Reminder(occurrence)
                            LessonAction.EDIT -> LessonDialog.Edit(occurrence)
                            LessonAction.DELETE -> LessonDialog.Delete(occurrence)
                        }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = announcedWeek != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = 64.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            // Keeps its text while fading out, after the week has been cleared.
            val shown = remember { mutableIntStateOf(week) }
            announcedWeek?.let { shown.intValue = it }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                Text(
                    text = stringResource(R.string.week_of, dateOfPage(shown.intValue * DAYS_PER_WEEK).shortLabel()),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom, // היום sits level with להשלמה, not with the "+" above it,
        ) {
            AnimatedVisibility(visible = !showsToday, enter = fadeIn(), exit = fadeOut()) {
                TodayButton(onClick = { goTo(pageOfDate(today)) })
            }
            Spacer(Modifier.weight(1f))
            // The "+" lines up with the outer edge of "להשלמה", in the corner, rather than over its middle.
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AddButton(onClick = { dialog = LessonDialog.Add(viewed = dateOfPage(pagerState.currentPage)) })
                PendingButton(count = state.pendingCount, onClick = onOpenBacklog)
            }
        }
    }

    // Asked for here rather than in the pop-up, so the answer still arrives after the pop-up has closed.
    val ensureNotifications = rememberNotificationAccess()
    dialog?.let { LessonDialogHost(it, state.calculator.schedule, actions, ensureNotifications, onClose = { dialog = null }) }
}

/** Shows the open pop-up and passes its outcome on. */
@Composable
private fun LessonDialogHost(
    dialog: LessonDialog,
    schedule: Schedule,
    actions: DayActions,
    ensureNotifications: () -> Unit,
    onClose: () -> Unit,
) {
    when (dialog) {
        is LessonDialog.Add -> AddDialog(
            viewed = dialog.viewed,
            defaultUntil = { day -> ScheduleEdits.defaultUntil(schedule.term, day) },
            onAdd = { details, firstDate, until ->
                actions.add(details, firstDate, until)
                onClose()
            },
            onDismiss = onClose,
        )

        is LessonDialog.Reminder -> ReminderDialog(
            occurrence = dialog.occurrence,
            onSave = { minutesBefore, everyTime ->
                actions.setReminder(dialog.occurrence, minutesBefore, everyTime)
                ensureNotifications()
                onClose()
            },
            onRemove = { everyTime ->
                actions.removeReminder(dialog.occurrence, everyTime)
                onClose()
            },
            onDismiss = onClose,
        )

        is LessonDialog.Edit -> EditDialog(
            occurrence = dialog.occurrence,
            weekly = remember(dialog) { schedule.lessonDetails(dialog.occurrence.lesson.id) ?: dialog.occurrence.details() },
            onSave = { details, everyTime ->
                actions.edit(dialog.occurrence, details, everyTime)
                onClose()
            },
            onDismiss = onClose,
        )

        is LessonDialog.Delete -> DeleteDialog(
            occurrence = dialog.occurrence,
            onDelete = { everyTime ->
                actions.delete(dialog.occurrence, everyTime)
                onClose()
            },
            onDismiss = onClose,
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val description = stringResource(R.string.add_button)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(CornerButtonHeight).semantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TodayButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(CornerButtonHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = stringResource(R.string.today),
            modifier = Modifier.wrapContentHeight().padding(horizontal = 18.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PendingButton(count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.height(CornerButtonHeight),
        shape = CircleShape,
        color = colors.primary,
        contentColor = colors.onPrimary,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.pending_button), style = MaterialTheme.typography.labelLarge)
            if (count > 0) {
                Text(
                    text = count.toString(),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.onPrimary)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
