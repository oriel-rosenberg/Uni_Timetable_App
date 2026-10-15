package com.orirose.timetable.ui.day

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.orirose.timetable.R
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.ui.common.TickBox
import com.orirose.timetable.ui.common.label
import com.orirose.timetable.ui.common.timeRange
import com.orirose.timetable.ui.theme.Accent

private val BlockShape = RoundedCornerShape(10.dp)
private val CornerGap = 4.dp
private val EdgeGap = 6.dp

/**
 * A lesson on the day grid, at whatever size the grid gives it.
 *
 * @param highlighted briefly true when the lesson is the one a reminder led to
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LessonBlock(
    occurrence: Occurrence,
    done: Boolean,
    accent: Accent,
    highlighted: Boolean,
    onToggle: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha by animateFloatAsState(if (done) 0.45f else 1f, label = "lessonAlpha")
    val highlight by animateFloatAsState(if (highlighted) 1f else 0f, tween(durationMillis = 400), label = "lessonHighlight")

    Row(
        modifier
            .clip(BlockShape)
            .background(accent.container)
            .border(2.dp, accent.strong.copy(alpha = highlight), BlockShape)
            .combinedClickable(onLongClick = onLongPress, onClick = {}),
    ) {
        Box(Modifier.fillMaxHeight().width(4.dp).alpha(contentAlpha).background(accent.strong))
        LessonText(
            occurrence = occurrence,
            textAlpha = contentAlpha,
            corner = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (occurrence.reminderMinutes != null) {
                        Icon(
                            painter = painterResource(R.drawable.ic_alarm),
                            contentDescription = stringResource(R.string.has_reminder),
                            tint = accent.strong,
                            modifier = Modifier.size(16.dp).alpha(contentAlpha),
                        )
                    }
                    TickBox(checked = done, onCheckedChange = onToggle, accent = accent.strong)
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 10.dp, top = 5.dp, bottom = 5.dp),
        )
    }
}

/**
 * The course name, then time and type, place and teacher, with [corner] (the tick box) in the top
 * corner. Only the text beside the corner is narrowed by it; whatever starts below it gets the
 * block's whole width, which matters in the half-width blocks of overlapping lessons.
 *
 * Nothing is cut short while there is room: the name and each detail wrap onto as many lines as
 * they need. When the block is too short for that, the details run together on one paragraph, and
 * only if even that won't fit are the name and then the details cut, with "…".
 */
@Composable
private fun LessonText(
    occurrence: Occurrence,
    textAlpha: Float,
    corner: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lesson = occurrence.lesson
    val detailColor = MaterialTheme.colorScheme.onSurfaceVariant
    val detailStyle = MaterialTheme.typography.bodySmall
    val time = timeRange(lesson.start!!, lesson.end!!)
    val type = lesson.type.label()
    // Time and type share a line when it fits; when it doesn't they go on two, whole, with no dot left hanging.
    val timeAndType = "$time · $type"
    val rest = listOfNotNull(lesson.place, lesson.teachers.joinToString(", ").ifEmpty { null })
    val textModifier = Modifier.alpha(textAlpha)

    Layout(
        contents = listOf(
            corner,
            {
                Text(
                    text = occurrence.course.name,
                    modifier = textModifier,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            { Text(timeAndType, textModifier, color = detailColor, style = detailStyle, overflow = TextOverflow.Ellipsis) },
            {
                Text(time, textModifier, color = detailColor, style = detailStyle, overflow = TextOverflow.Ellipsis)
                Text(type, textModifier, color = detailColor, style = detailStyle, overflow = TextOverflow.Ellipsis)
            },
            {
                rest.forEach { Text(it, textModifier, color = detailColor, style = detailStyle, overflow = TextOverflow.Ellipsis) }
            },
            {
                Text(
                    (listOf(timeAndType) + rest).joinToString(" · "),
                    textModifier,
                    color = detailColor,
                    style = detailStyle,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        ),
        modifier = modifier,
    ) { measurables, constraints ->
        val cornerMeasurables = measurables[0]
        val titleMeasurables = measurables[1]
        val together = measurables[2].single()
        val apart = measurables[3]
        val restMeasurables = measurables[4]
        val summaryMeasurables = measurables[5]
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val cornerPlaceable = cornerMeasurables.single().measure(Constraints())
        val besideCorner = (width - cornerPlaceable.width - CornerGap.roundToPx()).coerceAtLeast(0)

        /**
         * How wide text starting at [y] may be: narrowed while it is level with the corner, and
         * below it kept off the block's rounded edge, which the corner itself already does above.
         */
        fun widthAt(y: Int) = if (y < cornerPlaceable.height) besideCorner else width - EdgeGap.roundToPx()

        val title = titleMeasurables.single()
        val summary = summaryMeasurables.single()
        val fullTitleHeight = title.maxIntrinsicHeight(besideCorner)
        val placed = ArrayList<Pair<Placeable, Int>>() // with their y

        fun place(measurable: Measurable, y: Int, maxHeight: Int): Int {
            val placeable = measurable.measure(Constraints(maxWidth = widthAt(y), maxHeight = maxHeight.coerceAtLeast(0)))
            placed += placeable to y
            return y + placeable.height
        }

        // 1. Everything, each detail on its own line, if it all fits.
        val lineMeasurables = if (together.maxIntrinsicWidth(Constraints.Infinity) <= widthAt(fullTitleHeight)) {
            listOf(together) + restMeasurables
        } else {
            apart + restMeasurables
        }
        var y = fullTitleHeight
        val lineHeights = lineMeasurables.map { line -> line.maxIntrinsicHeight(widthAt(y)).also { y += it } }
        if (y <= height) {
            var top = place(title, 0, fullTitleHeight)
            lineMeasurables.forEachIndexed { index, line -> top = place(line, top, lineHeights[index]) }
        } else {
            val summaryLine = summary.minIntrinsicHeight(Constraints.Infinity)
            val titleLine = title.minIntrinsicHeight(Constraints.Infinity)
            when {
                // 2. The whole name, and the details as one paragraph.
                fullTitleHeight + summary.maxIntrinsicHeight(widthAt(fullTitleHeight)) <= height -> {
                    val top = place(title, 0, fullTitleHeight)
                    place(summary, top, height - top)
                }
                // 3. The name cut to leave at least a line for the details, which take what is left.
                height - summaryLine >= titleLine -> {
                    val top = place(title, 0, height - summaryLine)
                    place(summary, top, height - top)
                }
                // 4. A sliver of a block: the name alone.
                else -> place(title, 0, height)
            }
        }

        layout(width, height) {
            cornerPlaceable.placeRelative(width - cornerPlaceable.width, 0)
            placed.forEach { (placeable, top) -> placeable.placeRelative(0, top) }
        }
    }
}
