package com.orirose.timetable.ui.day

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.orirose.timetable.domain.MinuteRange

/**
 * Zoom and scroll of one day's grid, in pixels. Zoom is simply the height of one hour; both are
 * changed together here so that zooming can keep the point under the fingers still.
 *
 * @param day the hours the grid covers
 * @param window the stretch that should fill the screen to begin with
 * @param viewport the visible height
 */
@Stable
internal class TimelineState(
    val day: MinuteRange,
    window: MinuteRange,
    private val viewport: Float,
    private val topPadding: Float,
    private val bottomPadding: Float,
    hourHeightLimit: Float,
) {
    private val dayHours = day.length / 60f
    private val usable = (viewport - topPadding - bottomPadding).coerceAtLeast(1f)

    /** Zoomed all the way out, the whole day fits on screen. */
    private val minHourHeight = usable / dayHours
    private val maxHourHeight = maxOf(hourHeightLimit, minHourHeight)

    var hourHeight by mutableFloatStateOf((usable / (window.length / 60f)).coerceIn(minHourHeight, maxHourHeight))
        private set

    var scroll by mutableFloatStateOf(0f)
        private set

    private val maxScroll: Float
        get() = (topPadding + dayHours * hourHeight + bottomPadding - viewport).coerceAtLeast(0f)

    init {
        scroll = ((window.start - day.start) / 60f * hourHeight).coerceIn(0f, maxScroll)
    }

    /** Distance from the top of the (unscrolled) content to a time of day. */
    fun yOf(minuteOfDay: Int): Float = topPadding + (minuteOfDay - day.start) / 60f * hourHeight

    /** Returns how far it actually scrolled. */
    fun scrollBy(delta: Float): Float {
        val before = scroll
        scroll = (before + delta).coerceIn(0f, maxScroll)
        return scroll - before
    }

    /** Scrolls just enough for the whole of [range] to be on screen, if it isn't already. */
    fun reveal(range: MinuteRange) {
        val top = yOf(range.start) - 2 * topPadding
        val bottom = yOf(range.end) + bottomPadding - viewport
        scroll = when {
            scroll > top -> top
            scroll < bottom -> minOf(bottom, top)
            else -> scroll
        }.coerceIn(0f, maxScroll)
    }

    /** Zooms by [factor], keeping the time at [focusY] (measured from the top of the viewport) in place. */
    fun zoomBy(factor: Float, focusY: Float) {
        val focusHours = (scroll + focusY - topPadding) / hourHeight
        hourHeight = (hourHeight * factor).coerceIn(minHourHeight, maxHourHeight)
        scroll = (topPadding + focusHours * hourHeight - focusY).coerceIn(0f, maxScroll)
    }
}
