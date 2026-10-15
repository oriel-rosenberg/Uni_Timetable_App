package com.orirose.timetable.ui

import android.app.TimePickerDialog
import android.content.DialogInterface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.domain.ScheduleCalculator
import com.orirose.timetable.ui.day.DayScreenContent
import com.orirose.timetable.ui.day.DayUiState
import com.orirose.timetable.ui.theme.TimetableTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDateTime

/** The "+" button and its pop-up, at the default screen size for the reason given in LessonMenuTest. */
@RunWith(RobolectricTestRunner::class)
class AddLessonDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordedActions()

    @Before
    fun showTuesday() {
        val now = LocalDateTime.of(2026, 10, 13, 14, 20)
        compose.setContent {
            TimetableTheme {
                DayScreenContent(DayUiState(ScheduleCalculator(bundledSchedule()), emptySet(), now, 0), actions, onOpenBacklog = {})
            }
        }
        compose.onNodeWithContentDescription("הוספת שיעור").performClick()
    }

    /** Taps a time field and answers the system time picker that it opens. */
    private fun pickTime(field: String, hour: Int, minute: Int) {
        compose.onNodeWithText(field).performScrollTo().performClick()
        compose.waitForIdle()
        val picker = ShadowDialog.getLatestDialog() as TimePickerDialog
        picker.updateTime(hour, minute)
        picker.onClick(picker, DialogInterface.BUTTON_POSITIVE)
        compose.waitForIdle()
    }

    private fun fillIn() {
        compose.onNode(hasSetTextAction() and hasText("שם הקורס")).performTextReplacement("סדנת כתיבה")
        pickTime("התחלה", 17, 0)
        pickTime("סיום", 18, 30)
    }

    @Test
    fun `nothing can be added until name and times are set`() {
        compose.onNodeWithText("אישור").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("שם הקורס")).performTextReplacement("סדנת כתיבה")
        compose.onNodeWithText("אישור").assertIsNotEnabled()
        pickTime("התחלה", 17, 0)
        pickTime("סיום", 18, 30)
        compose.onNodeWithText("אישור").assertIsEnabled()
    }

    @Test
    fun `a single occurrence goes on the day being viewed`() {
        fillIn()
        compose.onNodeWithText("פעם אחת, ב־ג 13.10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("אישור").performClick()
        assertEquals(listOf("add סדנת כתיבה 17:00-18:30 2026-10-13 until null"), actions.calls)
    }

    @Test
    fun `a weekly lesson runs until the end of the semester unless told otherwise`() {
        compose.onNodeWithText("קבוע").performClick()
        fillIn()
        compose.onNodeWithText("סוף הסמסטר (ג 12.1)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("אישור").performClick()
        assertEquals(listOf("add סדנת כתיבה 17:00-18:30 2026-10-13 until 2027-01-12"), actions.calls)
    }

    @Test
    fun `choosing another day moves the lesson and its default end`() {
        compose.onNodeWithText("קבוע").performClick()
        fillIn()
        compose.onNodeWithText("ה").performScrollTo().performClick()
        compose.onNodeWithText("סוף הסמסטר (ה 14.1)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("אישור").performClick()
        assertEquals(listOf("add סדנת כתיבה 17:00-18:30 2026-10-15 until 2027-01-14"), actions.calls)
    }
}
