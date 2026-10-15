package com.orirose.timetable.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.data.model.Schedule
import com.orirose.timetable.date
import com.orirose.timetable.domain.ScheduleCalculator
import com.orirose.timetable.domain.ScheduleEdits
import com.orirose.timetable.ui.day.DayScreenContent
import com.orirose.timetable.ui.day.DayUiState
import com.orirose.timetable.ui.theme.TimetableTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.LocalDateTime

/**
 * Drives a lesson's long-press menu and its three pop-ups on the real day screen.
 *
 * This runs at Robolectric's default screen size on purpose: at any other size a Compose pop-up
 * that holds a text field is laid out over and over there and the test never gets a turn.
 */
@RunWith(RobolectricTestRunner::class)
class LessonMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private val schedule = bundledSchedule()
    private val actions = RecordedActions()

    private val tuesday = date(2026, 10, 13)
    private val recitation = "huji-5787-a|67561-2-01|2026-10-11" // Tuesday 15:00
    private val recitationName = "יסודות תורת האינפורמציה"

    @Before
    fun allowNotifications() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openMenu(schedule: Schedule = this.schedule) {
        val now = LocalDateTime.of(2026, 10, 13, 14, 20)
        compose.setContent {
            TimetableTheme {
                val calculator = ScheduleCalculator(schedule)
                DayScreenContent(DayUiState(calculator, emptySet(), now, pendingCount = 0), actions, onOpenBacklog = {})
            }
        }
        compose.onNode(hasText(recitationName) and hasAnyAncestor(hasTestTag("day-$tuesday")))
            .performTouchInput { longClick() }
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun type(current: String, replacement: String) =
        compose.onNode(hasSetTextAction() and hasText(current)).performTextReplacement(replacement)

    @Test
    fun `the menu offers a reminder, editing and deleting`() {
        openMenu()
        compose.onNodeWithText("תזכורת…").assertIsDisplayed()
        compose.onNodeWithText("עריכה").assertIsDisplayed()
        compose.onNodeWithText("מחיקת השיעור").assertIsDisplayed()
    }

    @Test
    fun `a reminder defaults to ten minutes before this occurrence only`() {
        openMenu()
        tap("תזכורת…")
        tap("אישור")

        assertEquals(listOf("remind $recitation 10 false"), actions.calls)
    }

    @Test
    fun `a reminder can be set in hours and minutes for every occurrence`() {
        openMenu()
        tap("תזכורת…")
        type(current = "0", replacement = "1")
        type(current = "10", replacement = "30")
        tap("להזכיר לפני כל מופע של השיעור הזה")
        tap("אישור")

        assertEquals(listOf("remind $recitation 90 true"), actions.calls)
    }

    @Test
    fun `a repeating reminder is changed or removed for this occurrence or for all`() {
        val occurrence = ScheduleCalculator(schedule).day(tuesday).lessons.first { it.key == recitation }
        openMenu(ScheduleEdits.setReminder(schedule, occurrence, minutesBefore = 90, everyTime = true))

        tap("שינוי או מחיקת תזכורת")
        type(current = "30", replacement = "45") // an hour and a half shows as 1 and 30
        tap("אישור")
        assertEquals(listOf("remind $recitation 105 false"), actions.calls)

        actions.calls.clear()
        compose.onNode(hasText(recitationName) and hasAnyAncestor(hasTestTag("day-$tuesday")))
            .performTouchInput { longClick() }
        tap("שינוי או מחיקת תזכורת")
        tap("כל המופעים")
        tap("מחיקת התזכורת")
        assertEquals(listOf("unremind $recitation true"), actions.calls)
    }

    @Test
    fun `deleting asks first and takes only this occurrence unless told otherwise`() {
        openMenu()
        tap("מחיקת השיעור")
        tap("כן")
        assertEquals(listOf("delete $recitation false"), actions.calls)

        actions.calls.clear()
        compose.onNode(hasText(recitationName) and hasAnyAncestor(hasTestTag("day-$tuesday")))
            .performTouchInput { longClick() }
        tap("מחיקת השיעור")
        tap("כל המופעים")
        tap("כן")
        assertEquals(listOf("delete $recitation true"), actions.calls)
    }

    @Test
    fun `saying no deletes nothing`() {
        openMenu()
        tap("מחיקת השיעור")
        tap("לא")
        assertEquals(emptyList<String>(), actions.calls)
    }

    @Test
    fun `editing changes this occurrence, or the lesson in every week`() {
        openMenu()
        tap("עריכה")
        type(current = "הנדסה - אודיטוריום הנדסה", replacement = "זום")
        compose.onNodeWithText("ה").performScrollTo().performClick() // Thursday
        tap("אישור")
        assertEquals(listOf("edit $recitation $recitationName THURSDAY זום false"), actions.calls)

        actions.calls.clear()
        compose.onNode(hasText(recitationName) and hasAnyAncestor(hasTestTag("day-$tuesday")))
            .performTouchInput { longClick() }
        tap("עריכה")
        tap("כל המופעים")
        type(current = recitationName, replacement = "תורת האינפורמציה")
        tap("אישור")
        assertEquals(listOf("edit $recitation תורת האינפורמציה TUESDAY הנדסה - אודיטוריום הנדסה true"), actions.calls)
    }
}
