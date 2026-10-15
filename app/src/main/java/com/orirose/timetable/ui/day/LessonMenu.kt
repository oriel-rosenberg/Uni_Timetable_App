package com.orirose.timetable.ui.day

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.orirose.timetable.R

enum class LessonAction { REMINDER, EDIT, DELETE }

/** What a long press on a lesson offers. A new action for lessons is a new entry here. */
@Composable
fun LessonMenu(
    expanded: Boolean,
    hasReminder: Boolean,
    onAction: (LessonAction) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(if (hasReminder) R.string.menu_change_reminder else R.string.menu_remind)) },
            onClick = { onAction(LessonAction.REMINDER) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_edit)) },
            onClick = { onAction(LessonAction.EDIT) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_delete)) },
            onClick = { onAction(LessonAction.DELETE) },
        )
    }
}
