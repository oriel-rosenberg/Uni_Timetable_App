package com.orirose.timetable.ui.day

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.orirose.timetable.data.model.minuteOfDay
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.domain.TimelineLayout
import com.orirose.timetable.ui.theme.CourseAccents
import java.time.LocalTime
import kotlin.math.roundToInt

private val GutterWidth = 52.dp // the column of hour labels
private val EdgePadding = 12.dp
private val TopPadding = 14.dp
private val BottomPadding = 156.dp // room for the corner buttons to float over
private val BlockGap = 3.dp
private val MaxHourHeight = 280.dp
private val NowColor = Color(0xFFE5484D)

/**
 * A day as an hour grid with its lessons as blocks. Drag to scroll, pinch to zoom; it opens zoomed
 * so that the busy part of the day fills the screen. A long press on a lesson opens its menu.
 *
 * @param highlightKey a lesson to scroll to and mark out for a moment
 */
@Composable
fun Timeline(
    lessons: List<Occurrence>,
    completed: Set<String>,
    now: LocalTime?,
    accents: CourseAccents,
    highlightKey: String?,
    onToggle: (key: String, done: Boolean) -> Unit,
    onAction: (Occurrence, LessonAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placed = remember(lessons) { TimelineLayout.place(lessons) }
    val day = remember(lessons) { TimelineLayout.dayRange(lessons) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewport = constraints.maxHeight.toFloat()
        val state = remember(lessons, viewport, density) {
            with(density) {
                TimelineState(
                    day = day,
                    window = TimelineLayout.defaultWindow(lessons, day),
                    viewport = viewport,
                    topPadding = TopPadding.toPx(),
                    bottomPadding = BottomPadding.toPx(),
                    hourHeightLimit = MaxHourHeight.toPx(),
                )
            }
        }
        // Dragging down gives a positive delta, which means scrolling back.
        val scrollableState = rememberScrollableState { delta -> -state.scrollBy(-delta) }

        LaunchedEffect(highlightKey, state) {
            placed.firstOrNull { it.occurrence.key == highlightKey }?.let { state.reveal(it.range) }
        }

        val gridColor = MaterialTheme.colorScheme.outlineVariant
        val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        val textMeasurer = rememberTextMeasurer()
        val hourLabels = remember(day, labelStyle, textMeasurer) {
            (day.start / 60..day.end / 60).map { hour -> textMeasurer.measure("$hour:00", labelStyle) }
        }

        Layout(
            content = {
                placed.forEach { item ->
                    val occurrence = item.occurrence
                    key(occurrence.key) {
                        // One box per lesson: the grid below places exactly one item for each.
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }
                            LessonBlock(
                                occurrence = occurrence,
                                done = occurrence.key in completed,
                                accent = accents.of(occurrence.course.id),
                                highlighted = occurrence.key == highlightKey,
                                onToggle = { done -> onToggle(occurrence.key, done) },
                                onLongPress = { menuOpen = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                            LessonMenu(
                                expanded = menuOpen,
                                hasReminder = occurrence.reminderMinutes != null,
                                onAction = { action ->
                                    menuOpen = false
                                    onAction(occurrence, action)
                                },
                                onDismiss = { menuOpen = false },
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(state) { detectPinch(state::zoomBy) }
                .scrollable(scrollableState, Orientation.Vertical)
                .drawWithContent {
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    val gutter = GutterWidth.toPx()
                    // The hour labels sit at the start edge: on the right in Hebrew.
                    val gridLeft = if (rtl) 0f else gutter
                    val gridRight = if (rtl) size.width - gutter else size.width
                    val gutterLeft = if (rtl) gridRight else 0f
                    val scroll = state.scroll

                    hourLabels.forEachIndexed { index, label ->
                        val y = state.yOf(day.start + index * 60) - scroll
                        if (y > -label.size.height && y < size.height + label.size.height) {
                            drawLine(gridColor, Offset(gridLeft, y), Offset(gridRight, y), strokeWidth = 1.dp.toPx())
                            drawText(
                                textLayoutResult = label,
                                topLeft = Offset(gutterLeft + (gutter - label.size.width) / 2f, y - label.size.height / 2f),
                            )
                        }
                    }

                    drawContent()

                    val nowMinute = now?.minuteOfDay()
                    if (nowMinute != null && nowMinute in day.start..day.end) {
                        val y = state.yOf(nowMinute) - scroll
                        drawLine(NowColor, Offset(gridLeft, y), Offset(gridRight, y), strokeWidth = 1.5.dp.toPx())
                        drawCircle(NowColor, radius = 4.dp.toPx(), center = Offset(if (rtl) gridRight else gridLeft, y))
                    }
                },
        ) { measurables, constraints ->
            val gap = BlockGap.roundToPx()
            val gutter = GutterWidth.roundToPx()
            val blocksWidth = constraints.maxWidth - gutter - EdgePadding.roundToPx()
            val hourHeight = state.hourHeight

            val placeables = measurables.mapIndexed { index, measurable ->
                val item = placed[index]
                measurable.measure(
                    Constraints.fixed(
                        width = (blocksWidth / item.columnCount - gap).coerceAtLeast(0),
                        height = ((item.range.length / 60f * hourHeight).roundToInt() - gap).coerceAtLeast(0),
                    ),
                )
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                // Read here, during placement, so scrolling doesn't re-measure the blocks.
                val scroll = state.scroll
                placeables.forEachIndexed { index, placeable ->
                    val item = placed[index]
                    placeable.placeRelative(
                        x = gutter + item.column * (blocksWidth / item.columnCount),
                        y = (state.yOf(item.range.start) - scroll).roundToInt(),
                    )
                }
            }
        }
    }
}
