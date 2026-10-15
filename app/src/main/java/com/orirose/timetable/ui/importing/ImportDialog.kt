package com.orirose.timetable.ui.importing

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.orirose.timetable.R

@Composable
fun ImportDialog(state: ImportState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ImportState.Idle -> Unit

        is ImportState.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_title)) },
            text = { Text(stringResource(R.string.import_summary, state.schedule.term.name, state.schedule.lessons.size)) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_confirm)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_cancel)) } },
        )

        is ImportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_failed_title)) },
            text = { Text(state.reason) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_ok)) } },
        )
    }
}
