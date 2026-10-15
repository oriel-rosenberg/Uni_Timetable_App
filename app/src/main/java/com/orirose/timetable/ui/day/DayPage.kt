package com.orirose.timetable.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orirose.timetable.R
import com.orirose.timetable.domain.DayInfo
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.ui.theme.CourseAccents
import java.time.LocalTime

/**
 * One day: its grid of lessons, or a single line when there is nothing to show.
 *
 * @param now the current time when this page is today, else `null`
 * @param highlightKey a lesson on this day to scroll to and mark out, if any
 */
@Composable
fun DayPage(
    info: DayInfo,
    completed: Set<String>,
    now: LocalTime?,
    accents: CourseAccents,
    highlightKey: String?,
    onToggle: (key: String, done: Boolean) -> Unit,
    onAction: (Occurrence, LessonAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    if (info.lessons.isEmpty()) {
        Box(modifier.fillMaxSize().testTag(dayPageTag(info)), contentAlignment = Alignment.Center) {
            Text(
                text = info.label ?: stringResource(R.string.no_lessons),
                modifier = Modifier.padding(horizontal = 32.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Column(modifier.fillMaxSize().testTag(dayPageTag(info))) {
        info.label?.let { label ->
            Text(
                text = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
        Timeline(info.lessons, completed, now, accents, highlightKey, onToggle, onAction)
    }
}

internal fun dayPageTag(info: DayInfo): String = "day-${info.date}"
