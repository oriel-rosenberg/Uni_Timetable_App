package com.orirose.timetable.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.orirose.timetable.data.model.Schedule

/** A course's colour: [strong] for its edge and tick, [container] for the block behind its text. */
@Immutable
data class Accent(val strong: Color, val container: Color)

/** Gives every course in a schedule its own colour, the same one everywhere in the app. */
@Immutable
class CourseAccents internal constructor(
    private val byCourse: Map<String, Accent>,
    private val fallback: Accent,
) {
    fun of(courseId: String): Accent = byCourse[courseId] ?: fallback
}

private val LightHues = listOf(
    Color(0xFF3B6FE0), Color(0xFF0E9F8E), Color(0xFF8B5CF6), Color(0xFFD9822B),
    Color(0xFFE0457B), Color(0xFF4C9A2A), Color(0xFF0891B2), Color(0xFFB4533C),
)

private val DarkHues = listOf(
    Color(0xFF7FA6FF), Color(0xFF4FD1C1), Color(0xFFB79CFF), Color(0xFFF2B36B),
    Color(0xFFFF8FB3), Color(0xFF8FD16A), Color(0xFF5CCFE6), Color(0xFFE59A84),
)

@Composable
fun rememberCourseAccents(schedule: Schedule): CourseAccents {
    val surface = MaterialTheme.colorScheme.surface
    return remember(schedule.courses, surface) {
        val dark = surface.luminance() < 0.5f
        val hues = if (dark) DarkHues else LightHues
        fun accent(hue: Color) = Accent(hue, hue.copy(alpha = if (dark) 0.22f else 0.12f).compositeOver(surface))
        CourseAccents(
            byCourse = schedule.courses.mapIndexed { index, course -> course.id to accent(hues[index % hues.size]) }.toMap(),
            fallback = accent(hues.first()),
        )
    }
}
