package com.orirose.timetable.ui.backlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orirose.timetable.R
import com.orirose.timetable.ui.common.TickBox
import com.orirose.timetable.ui.common.daysAgoLabel
import com.orirose.timetable.ui.common.label
import com.orirose.timetable.ui.common.shortLabel
import com.orirose.timetable.ui.theme.Accent
import com.orirose.timetable.ui.theme.rememberCourseAccents
import kotlinx.coroutines.launch

@Composable
fun BacklogScreen(
    onBack: () -> Unit,
    onReplaceSchedule: () -> Unit,
    viewModel: BacklogViewModel = viewModel(factory = BacklogViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state?.let { BacklogScreenContent(it, viewModel::setCompleted, onBack, onReplaceSchedule) }
}

/** Every lesson not yet ticked, oldest first. Ticking one removes it, with a moment to undo. */
@Composable
fun BacklogScreenContent(
    state: BacklogUiState,
    onToggle: (key: String, done: Boolean) -> Unit,
    onBack: () -> Unit,
    onReplaceSchedule: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accents = rememberCourseAccents(state.schedule)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val doneMessage = stringResource(R.string.marked_done)
    val undoLabel = stringResource(R.string.undo)

    fun markDone(key: String) {
        onToggle(key, true)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(doneMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onToggle(key, false)
        }
    }

    Scaffold(
        topBar = { BacklogTopBar(onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(state.items, key = { it.occurrence.key }) { item ->
                PendingRow(
                    item = item,
                    accent = accents.of(item.occurrence.course.id),
                    onDone = { markDone(item.occurrence.key) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (state.items.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.6f), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.backlog_empty),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            item(key = "replace") {
                Box(Modifier.fillParentMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onReplaceSchedule) {
                        Text(stringResource(R.string.replace_schedule), color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BacklogTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
        }
        Text(
            text = stringResource(R.string.backlog_title),
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun PendingRow(item: PendingItem, accent: Accent, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val occurrence = item.occurrence
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 38.dp).clip(RoundedCornerShape(2.dp)).background(accent.strong))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = occurrence.course.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${occurrence.lesson.type.label()} · ${occurrence.date.shortLabel()}",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = daysAgoLabel(item.daysAgo),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        TickBox(checked = false, onCheckedChange = { onDone() }, accent = accent.strong)
    }
}
