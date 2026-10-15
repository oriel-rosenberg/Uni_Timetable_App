package com.orirose.timetable.ui.day

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.orirose.timetable.ui.common.shortLabel
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

private const val VISIBLE_DAYS = 5

// Chips kept just out of sight on each side, so a swipe never slides in an empty gap.
private const val SPARE_DAYS = 2

// The space, with a line in it, that sets one week's days apart from the next week's.
private val WeekGap = 22.dp

/**
 * The row of days above the grid: the selected day in the middle with two days either side.
 * It follows [pagerState] continuously, so it slides together with the page being swiped.
 * Weeks are set apart by a gap with a line, so moving into another week is easy to notice.
 */
@Composable
fun DayStrip(
    pagerState: PagerState,
    today: LocalDate,
    isQuiet: (LocalDate) -> Boolean,
    onSelect: (page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = pagerState.currentPage
    val reach = VISIBLE_DAYS / 2 + SPARE_DAYS
    val firstPage = current - reach
    val lineColor = MaterialTheme.colorScheme.outline

    Layout(
        modifier = modifier.fillMaxWidth().height(52.dp).clipToBounds(),
        content = {
            for (page in firstPage..current + reach) {
                key(page) {
                    val date = dateOfPage(page)
                    DayChip(
                        label = date.shortLabel(),
                        selected = page == current,
                        today = date == today,
                        quiet = isQuiet(date),
                        onClick = { onSelect(page) },
                        // The first day of a week carries the line that separates it from the week before.
                        modifier = if (page % DAYS_PER_WEEK != 0) {
                            Modifier
                        } else {
                            Modifier.drawBehind {
                                val x = if (layoutDirection == LayoutDirection.Rtl) size.width + WeekGap.toPx() / 2 else -WeekGap.toPx() / 2
                                drawLine(lineColor, Offset(x, size.height * 0.28f), Offset(x, size.height * 0.72f), strokeWidth = 1.5.dp.toPx())
                            }
                        },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val gap = WeekGap.toPx()
        // Five days always span one week gap, so leave room for it rather than cut off the outer days.
        val chipWidth = ((constraints.maxWidth - gap) / VISIBLE_DAYS).toInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(chipWidth, constraints.maxHeight)) }

        // How far along the strip a (possibly in-between) page position is, week gaps included.
        fun distance(position: Float): Float {
            val week = floor(position / DAYS_PER_WEEK)
            val intoGap = (position - week * DAYS_PER_WEEK - (DAYS_PER_WEEK - 1)).coerceAtLeast(0f)
            return position * chipWidth + (week + intoGap) * gap
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            // Read here, during placement, so swiping only moves the chips and recomposes nothing.
            // Centre the five days on show, week gap included, rather than just the selected one;
            // otherwise the gap pushes the outermost day on its side off the screen.
            val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val windowStart = distance(position - VISIBLE_DAYS / 2)
            val windowEnd = distance(position + VISIBLE_DAYS / 2) + chipWidth
            val margin = (constraints.maxWidth - (windowEnd - windowStart)) / 2
            placeables.forEachIndexed { index, placeable ->
                val x = margin + distance((firstPage + index).toFloat()) - windowStart
                placeable.placeRelative(x = x.roundToInt(), y = 0)
            }
        }
    }
}

@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    today: Boolean,
    quiet: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary else Color.Transparent, label = "chipBackground")
    val textColor = when {
        selected -> colors.onPrimary
        today -> colors.primary
        quiet -> colors.onSurfaceVariant.copy(alpha = 0.6f)
        else -> colors.onSurface
    }
    Box(
        modifier = modifier.clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .clip(CircleShape)
                .background(background)
                .padding(horizontal = 7.dp, vertical = 5.dp),
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}
