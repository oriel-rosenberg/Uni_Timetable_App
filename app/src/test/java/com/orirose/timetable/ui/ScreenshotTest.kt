package com.orirose.timetable.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.orirose.timetable.R
import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.date
import com.orirose.timetable.domain.Occurrence
import com.orirose.timetable.domain.ScheduleCalculator
import com.orirose.timetable.domain.ScheduleEdits
import com.orirose.timetable.domain.lessonDetails
import com.orirose.timetable.ui.backlog.BacklogScreenContent
import com.orirose.timetable.ui.backlog.BacklogUiState
import com.orirose.timetable.ui.backlog.PendingItem
import com.orirose.timetable.ui.day.AddDialog
import com.orirose.timetable.ui.day.DayScreenContent
import com.orirose.timetable.ui.day.DayUiState
import com.orirose.timetable.ui.day.EditDialog
import com.orirose.timetable.ui.day.LessonLink
import com.orirose.timetable.ui.day.ReminderDialog
import com.orirose.timetable.ui.theme.TimetableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Renders the real screens with the bundled schedule at phone size, on the PC, and saves them as
 * PNGs to look at. Also drives the day view's gestures, which nothing else exercises without a phone.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val schedule = bundledSchedule()
    private val outputDir = File(System.getProperty("timetable.screenshotDir") ?: "build/screenshots")
    private val actions = RecordedActions()

    private val tuesday = date(2026, 10, 13)
    private val tuesdayAfternoon = LocalDateTime.of(2026, 10, 13, 14, 20)
    private val mlRecitation = "huji-5787-a|67577-2-09|2026-10-11" // Tuesday 12:00
    private val infoRecitation = "huji-5787-a|67561-2-01|2026-10-11" // Tuesday 15:00
    private val infoRecitationName = "יסודות תורת האינפורמציה"

    /** The schedule with a reminder an hour and a half before every information-theory recitation. */
    private val scheduleWithReminder = ScheduleEdits.setReminder(schedule, schedule.infoRecitation(), minutesBefore = 90, everyTime = true)

    private fun Schedule.infoRecitation(): Occurrence =
        ScheduleCalculator(this).day(tuesday).lessons.first { it.key == infoRecitation }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { TimetableTheme(fontFamily = hostHebrewFont(), content = content) }
    }

    /** Just the app's own window. */
    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(File(outputDir, "$name.png").path)
    }

    /** The whole screen, with any menu or pop-up on top. */
    private fun captureScreen(name: String) {
        captureScreenRoboImage(File(outputDir, "$name.png").path)
    }

    @Composable
    private fun Day(
        shown: LocalDate = tuesday,
        completed: Set<String> = setOf(mlRecitation),
        schedule: Schedule = this.schedule,
        link: LessonLink? = null,
    ) {
        val calculator = ScheduleCalculator(schedule)
        DayScreenContent(
            state = DayUiState(calculator, completed, tuesdayAfternoon, calculator.pending(tuesdayAfternoon, completed).size),
            actions = actions,
            onOpenBacklog = {},
            initialDate = shown,
            link = link,
        )
    }

    /** Draws a pop-up over the day screen. */
    private fun capturePopUp(name: String, schedule: Schedule = this.schedule, popUp: @Composable () -> Unit) {
        show {
            Day(schedule = schedule)
            popUp()
        }
        captureScreen(name)
    }

    private fun showBacklog(now: LocalDateTime) = show {
        val items = ScheduleCalculator(schedule).pending(now, setOf(mlRecitation))
            .map { PendingItem(it, ChronoUnit.DAYS.between(it.date, now.toLocalDate()).toInt()) }
        BacklogScreenContent(BacklogUiState(schedule, items), onToggle = { _, _ -> }, onBack = {}, onReplaceSchedule = {})
    }

    private fun longPressInfoRecitation() {
        compose.onNode(hasText(infoRecitationName) and hasAnyAncestor(hasTestTag("day-$tuesday")))
            .performTouchInput { longClick() }
    }

    @Test
    fun day_today() {
        show { Day() }
        capture("day_today")
    }

    @Test
    @Config(qualifiers = "+night")
    fun day_today_dark() {
        show { Day() }
        capture("day_today_dark")
    }

    @Test
    fun day_long_lectures() {
        show { Day(shown = date(2026, 10, 12)) }
        capture("day_monday")
    }

    @Test
    fun day_with_overlap() {
        show { Day(shown = date(2026, 10, 14)) }
        capture("day_wednesday_overlap")
    }

    @Test
    fun day_off() {
        show { Day(shown = date(2026, 10, 27)) }
        capture("day_off")
    }

    /** Each text shown on a day's page, and how much of it shows before any "…". */
    private fun textsOn(date: LocalDate): List<Pair<String, Int>> = compose
        .onAllNodes(
            hasAnyAncestor(hasTestTag("day-$date")) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        )
        .fetchSemanticsNodes()
        // A block lays out several versions of its details and shows one; the others stay unplaced.
        .filter { it.layoutInfo.isPlaced }
        .mapNotNull { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.firstOrNull()?.let { it.layoutInput.text.text to it.getLineEnd(it.lineCount - 1, visibleEnd = true) }
        }

    @Test
    @Config(fontScale = 1.3f)
    fun overlapping_lessons_with_large_text_still_show_their_whole_time() {
        val wednesday = date(2026, 10, 14)
        show { Day(shown = wednesday) }
        capture("day_wednesday_large_text")

        val times = textsOn(wednesday).filter { (text, _) -> '–' in text }
        assertEquals(3, times.size)
        times.forEach { (text, visible) -> assertTrue("time cut off in: $text", visible >= text.indexOf('⁩')) }
    }

    @Test
    @Config(fontScale = 1.3f)
    fun zooming_in_gives_overlapping_lessons_room_for_everything() {
        val wednesday = date(2026, 10, 14)
        show { Day(shown = wednesday) }
        compose.onNodeWithTag("day-$wednesday").performTouchInput {
            pinch(
                start0 = center + Offset(0f, -40f), end0 = center + Offset(0f, -700f),
                start1 = center + Offset(0f, 40f), end1 = center + Offset(0f, 700f),
            )
        }
        capture("day_wednesday_zoomed_in")

        val texts = textsOn(wednesday)
        assertTrue(texts.isNotEmpty())
        texts.forEach { (text, visible) -> assertEquals("cut off: $text", text.length, visible) }
    }

    @Test
    fun pinching_zooms_out_to_the_whole_day() {
        show { Day(shown = date(2026, 10, 14)) }
        compose.onNodeWithTag("day-2026-10-14").performTouchInput {
            pinch(
                start0 = center + Offset(0f, -500f), end0 = center + Offset(0f, -60f),
                start1 = center + Offset(0f, 500f), end1 = center + Offset(0f, 60f),
            )
        }
        capture("day_zoomed_out")
    }

    @Test
    fun swiping_moves_to_the_next_day() {
        show { Day() }
        compose.onNodeWithTag("day-2026-10-13").performTouchInput { swipeRight() } // right-to-left: tomorrow is to the left
        compose.onNodeWithTag("day-2026-10-14").assertIsDisplayed()
        compose.onNodeWithText("היום").assertIsDisplayed()
        capture("day_after_swipe")
    }

    @Test
    fun after_thursday_comes_sunday_of_the_next_week() {
        show { Day(shown = date(2026, 10, 15)) }
        capture("week_boundary")
        compose.onNodeWithTag("day-2026-10-15").performTouchInput { swipeRight() }
        compose.onNodeWithTag("day-2026-10-18").assertIsDisplayed()
    }

    @Test
    fun ticking_a_lesson_reports_it() {
        show { Day(completed = emptySet()) }

        compose.onAllNodes(hasContentDescription("סימון כהושלם") and hasAnyAncestor(hasTestTag("day-$tuesday")))
            .onFirst()
            .performClick()

        assertEquals(listOf("tick $mlRecitation true"), actions.calls)
    }

    @Test
    fun a_lesson_with_a_reminder_is_marked_and_a_tapped_reminder_leads_to_it() {
        // Arriving the way a tapped reminder does: the lesson is scrolled to and outlined.
        show { Day(schedule = scheduleWithReminder, link = LessonLink(tuesday, infoRecitation)) }
        compose.onNode(hasContentDescription("יש תזכורת") and hasAnyAncestor(hasTestTag("day-$tuesday"))).assertIsDisplayed()
        capture("day_with_reminder")
    }

    @Test
    fun lesson_menu() {
        show { Day() }
        longPressInfoRecitation()
        compose.onNodeWithText("תזכורת…").assertIsDisplayed()
        captureScreen("lesson_menu")
    }

    @Test
    fun delete_dialog() {
        show { Day() }
        longPressInfoRecitation()
        compose.onNodeWithText("מחיקת השיעור").performClick()
        compose.onNodeWithText("כן").assertIsDisplayed()
        captureScreen("delete_dialog")
    }

    @Test
    fun reminder_dialog() = capturePopUp("reminder_dialog") {
        ReminderDialog(schedule.infoRecitation(), onSave = { _, _ -> }, onRemove = {}, onDismiss = {})
    }

    @Test
    fun reminder_change_dialog() = capturePopUp("reminder_change_dialog", scheduleWithReminder) {
        ReminderDialog(scheduleWithReminder.infoRecitation(), onSave = { _, _ -> }, onRemove = {}, onDismiss = {})
    }

    @Test
    fun edit_dialog() = capturePopUp("edit_dialog") {
        EditDialog(
            occurrence = schedule.infoRecitation(),
            weekly = schedule.lessonDetails("67561-2-01")!!,
            onSave = { _, _ -> },
            onDismiss = {},
        )
    }

    @Test
    fun add_dialog() = capturePopUp("add_dialog") {
        AddDialog(
            viewed = tuesday,
            defaultUntil = { day -> ScheduleEdits.defaultUntil(schedule.term, day) },
            onAdd = { _, _, _ -> },
            onDismiss = {},
        )
    }

    @Test
    fun backlog() {
        showBacklog(LocalDateTime.of(2026, 10, 21, 10, 0))
        capture("backlog")
    }

    @Test
    @Config(qualifiers = "+night")
    fun backlog_dark() {
        showBacklog(LocalDateTime.of(2026, 10, 21, 10, 0))
        capture("backlog_dark")
    }

    @Test
    fun launcher_icon() {
        show {
            Row(
                modifier = Modifier.background(Color(0xFFE9EAEE)).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LauncherIcon(128.dp, CircleShape)
                LauncherIcon(128.dp, RoundedCornerShape(percent = 28))
                LauncherIcon(48.dp, CircleShape) // the size it has on a home screen
            }
        }
        capture("launcher_icon")
    }

    /** The adaptive icon as a launcher draws it: the middle two thirds of the layers, cut to the launcher's shape. */
    @Composable
    private fun LauncherIcon(size: Dp, shape: Shape) {
        Box(
            modifier = Modifier.size(size).clip(shape).background(colorResource(R.color.launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(size * 1.5f),
            )
        }
    }
}

/** Robolectric's built-in fonts have no Hebrew letters, so borrow a system font from the PC running the tests. */
private fun hostHebrewFont(): FontFamily {
    val fonts = File("C:/Windows/Fonts")
    val regular = File(fonts, "segoeui.ttf")
    if (!regular.exists()) return FontFamily.Default
    return FontFamily(
        Font(regular, FontWeight.Normal),
        Font(File(fonts, "seguisb.ttf"), FontWeight.SemiBold),
        Font(File(fonts, "segoeuib.ttf"), FontWeight.Bold),
    )
}
