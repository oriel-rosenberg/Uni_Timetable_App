package com.orirose.timetable.ui.day

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import com.orirose.timetable.domain.sundayOffset
import com.orirose.timetable.domain.weekStart
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orirose.timetable.R
import com.orirose.timetable.data.model.LessonType
import com.orirose.timetable.domain.LessonDetails
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.domain.details
import com.orirose.timetable.ui.common.label
import com.orirose.timetable.ui.common.letter
import com.orirose.timetable.ui.common.shortLabel
import java.time.DayOfWeek
import java.time.LocalTime

/** The pop-up that is open, and the lesson it is about. */
sealed interface LessonDialog {
    data class Reminder(val occurrence: Occurrence) : LessonDialog
    data class Edit(val occurrence: Occurrence) : LessonDialog
    data class Delete(val occurrence: Occurrence) : LessonDialog

    /** Adding a lesson, into the week of [viewed]. */
    data class Add(val viewed: LocalDate) : LessonDialog
}

private const val DEFAULT_REMINDER_MINUTES = 10
private val FieldShape = RoundedCornerShape(8.dp)

// The days the day view shows; a lesson moved to a Friday or Saturday would be out of sight.
private val Week = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
)

/**
 * Sets, changes or removes a lesson's reminder. A reminder that already repeats is changed for
 * this occurrence or for all of them, by the two headers; otherwise a tick box makes it repeat.
 */
@Composable
fun ReminderDialog(
    occurrence: Occurrence,
    onSave: (minutesBefore: Int, everyTime: Boolean) -> Unit,
    onRemove: (everyTime: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = occurrence.reminderMinutes
    val oneOff = occurrence.lesson.isOneOff
    val repeats = current != null && occurrence.reminderRepeats && !oneOff
    var everyTime by remember { mutableStateOf(false) }
    var hours by remember { mutableStateOf(((current ?: DEFAULT_REMINDER_MINUTES) / 60).toString()) }
    var minutes by remember { mutableStateOf(((current ?: DEFAULT_REMINDER_MINUTES) % 60).toString()) }
    val minutesPart = minutes.toIntOrNull() ?: 0
    val total = (hours.toIntOrNull() ?: 0) * 60 + minutesPart

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LessonSummary(occurrence)
                if (repeats) ScopeTabs(everyTime, onChange = { everyTime = it })
                Text(stringResource(R.string.reminder_how_long))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(hours, { hours = it }, stringResource(R.string.hours), Modifier.weight(1f))
                    NumberField(minutes, { minutes = it }, stringResource(R.string.minutes), Modifier.weight(1f))
                }
                if (!repeats && !oneOff) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = everyTime, role = Role.Checkbox, onValueChange = { everyTime = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = everyTime, onCheckedChange = null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.reminder_every_time))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(total, everyTime) }, enabled = minutesPart < 60) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onRemove(repeats && everyTime) }) {
                        Text(stringResource(R.string.reminder_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Asks before deleting, and whether it is this one occurrence or the lesson in every week. */
@Composable
fun DeleteDialog(occurrence: Occurrence, onDelete: (everyTime: Boolean) -> Unit, onDismiss: () -> Unit) {
    // A lesson that happens once has no other occurrences: deleting it removes it altogether.
    val oneOff = occurrence.lesson.isOneOff
    var everyTime by remember { mutableStateOf(oneOff) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LessonSummary(occurrence)
                if (!oneOff) ScopeTabs(everyTime, onChange = { everyTime = it })
                if (!oneOff) Text(
                    if (everyTime) {
                        stringResource(R.string.delete_all)
                    } else {
                        stringResource(R.string.delete_this, occurrence.date.shortLabel())
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(everyTime) }) {
                Text(stringResource(R.string.yes), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } },
    )
}

/**
 * Edits everything about a lesson, for this occurrence or for every week. A lesson that happens
 * once has no headers, as both would mean the same.
 *
 * @param weekly the lesson as it is every week, which is what "all" starts from
 */
@Composable
fun EditDialog(
    occurrence: Occurrence,
    weekly: LessonDetails,
    onSave: (LessonDetails, everyTime: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val oneOff = occurrence.lesson.isOneOff
    var everyTime by remember { mutableStateOf(false) }
    // The two headers edit different things, so each starts from its own values.
    var form by remember(everyTime) { mutableStateOf(LessonForm(if (everyTime) weekly else occurrence.details())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!oneOff) ScopeTabs(everyTime, onChange = { everyTime = it })
                LessonFields(form, onChange = { form = it }, nameHint = if (everyTime) stringResource(R.string.field_name_all_hint) else null)
            }
        },
        confirmButton = {
            TextButton(onClick = { form.toDetails()?.let { onSave(it, everyTime) } }, enabled = form.isValid) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Adds a lesson, once or every week. Name, day and times are required; the day starts as the one
 * being viewed. A weekly lesson runs from that week to "until", by default the semester's last such day.
 *
 * @param viewed the day on screen; the lesson goes into its week
 * @param defaultUntil where a weekly lesson on a given weekday stops unless another date is picked
 */
@Composable
fun AddDialog(
    viewed: LocalDate,
    defaultUntil: (DayOfWeek) -> LocalDate,
    onAdd: (details: LessonDetails, firstDate: LocalDate, until: LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var weekly by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(LessonForm(day = viewed.dayOfWeek.takeIf { it in Week } ?: DayOfWeek.SUNDAY)) }
    var pickedUntil by remember { mutableStateOf<LocalDate?>(null) }
    val firstDate = viewed.weekStart().plusDays(form.day.sundayOffset())
    val until = pickedUntil ?: defaultUntil(form.day)
    val context = LocalContext.current
    val details = form.toDetails()
    val valid = details != null && form.isValid && (!weekly || !until.isBefore(firstDate))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ScopeTabs(weekly, onChange = { weekly = it }, labels = R.string.add_once to R.string.add_weekly)
                LessonFields(form, onChange = { form = it }, nameHint = null)
                if (weekly) {
                    Labelled(stringResource(R.string.add_until)) {
                        ValueField(
                            value = if (pickedUntil == null) {
                                stringResource(R.string.add_until_term_end, until.shortLabel())
                            } else {
                                until.shortLabel()
                            },
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day -> pickedUntil = LocalDate.of(year, month + 1, day) },
                                    until.year,
                                    until.monthValue - 1,
                                    until.dayOfMonth,
                                ).apply { datePicker.minDate = firstDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                                    .show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (until.isBefore(firstDate)) {
                        Text(stringResource(R.string.add_until_too_early), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(stringResource(R.string.add_once_on, firstDate.shortLabel()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { details?.let { onAdd(it, firstDate, if (weekly) until else null) } }, enabled = valid) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The fields of a lesson, shared by editing and adding. */
@Composable
private fun LessonFields(form: LessonForm, onChange: (LessonForm) -> Unit, nameHint: String?) {
    val context = LocalContext.current

    fun pickTime(current: LocalTime?, onPicked: (LocalTime) -> Unit) {
        val shown = current ?: LocalTime.of(10, 0)
        TimePickerDialog(context, { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) }, shown.hour, shown.minute, true).show()
    }

    OutlinedTextField(
        value = form.name,
        onValueChange = { onChange(form.copy(name = it)) },
        label = { Text(stringResource(R.string.field_name)) },
        supportingText = nameHint?.let { hint -> { Text(hint) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Labelled(stringResource(R.string.field_type)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in listOf(LessonType.LECTURE, LessonType.RECITATION)) {
                Choice(type.label(), form.type == type, { onChange(form.copy(type = type)) }, Modifier.weight(1f))
            }
        }
    }
    Labelled(stringResource(R.string.field_day)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (day in Week) {
                Choice(day.letter, form.day == day, { onChange(form.copy(day = day)) }, Modifier.weight(1f))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeField(
            label = stringResource(R.string.field_start),
            time = form.start,
            onClick = { pickTime(form.start ?: form.end?.minusHours(1)) { onChange(form.copy(start = it)) } },
            modifier = Modifier.weight(1f),
        )
        TimeField(
            label = stringResource(R.string.field_end),
            time = form.end,
            onClick = { pickTime(form.end ?: form.start?.plusHours(1)) { onChange(form.copy(end = it)) } },
            modifier = Modifier.weight(1f),
        )
    }
    if (form.start != null && form.end != null && form.start >= form.end) {
        Text(
            text = stringResource(R.string.edit_time_order),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    OutlinedTextField(
        value = form.place,
        onValueChange = { onChange(form.copy(place = it)) },
        label = { Text(stringResource(R.string.field_place)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.teachers,
        onValueChange = { onChange(form.copy(teachers = it)) },
        label = { Text(stringResource(R.string.field_teacher)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * What the lesson fields hold while a pop-up is open. Times may still be unset when adding.
 * Several teachers are typed with commas between them.
 */
private data class LessonForm(
    val name: String = "",
    val type: LessonType = LessonType.LECTURE,
    val day: DayOfWeek,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val place: String = "",
    val teachers: String = "",
) {
    constructor(details: LessonDetails) : this(
        details.name, details.type, details.day, details.start, details.end, details.place,
        details.teachers.joinToString(", "),
    )

    val isValid: Boolean get() = name.isNotBlank() && start != null && end != null && start < end

    /** The lesson as entered, or `null` while something required is missing. */
    fun toDetails(): LessonDetails? {
        if (!isValid) return null
        return LessonDetails(
            name = name.trim(),
            type = type,
            day = day,
            start = start!!,
            end = end!!,
            place = place.trim(),
            teachers = teachers.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}

@Composable
private fun LessonSummary(occurrence: Occurrence) {
    Text(
        text = "${occurrence.course.name} · ${occurrence.lesson.type.label()} · ${occurrence.date.shortLabel()}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** The two headers: this occurrence, or all of them (or, when adding: once, or every week). */
@Composable
private fun ScopeTabs(
    everyTime: Boolean,
    onChange: (Boolean) -> Unit,
    labels: Pair<Int, Int> = R.string.scope_this to R.string.scope_all,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(3.dp)) {
        for ((label, value) in listOf(labels.first to false, labels.second to true)) {
            val selected = everyTime == value
            Text(
                text = stringResource(label),
                modifier = Modifier
                    .weight(1f)
                    .clip(FieldShape)
                    .background(if (selected) colors.surface else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onChange(value) })
                    .padding(vertical = 8.dp),
                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        content()
    }
}

/** One of a row of options, of which one is picked. */
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        modifier = modifier
            .clip(FieldShape)
            .background(if (selected) colors.primary else Color.Transparent)
            .border(1.dp, if (selected) colors.primary else colors.outline, FieldShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        color = if (selected) colors.onPrimary else colors.onSurface,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/** Shows a time, or a dash until one is chosen; tapping it opens the system's time picker. */
@Composable
private fun TimeField(label: String, time: LocalTime?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(FieldShape)
            .border(1.dp, colors.outline, FieldShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(time?.label() ?: "—", style = MaterialTheme.typography.titleMedium)
    }
}

/** A value that is changed by tapping it, as with a date picker. */
@Composable
private fun ValueField(value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = value,
        modifier = modifier
            .clip(FieldShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, FieldShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** A field for a number of up to two digits. */
@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
