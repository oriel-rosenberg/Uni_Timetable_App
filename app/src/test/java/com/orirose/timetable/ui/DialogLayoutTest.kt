package com.orirose.timetable.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.IdlingPolicies
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.orirose.timetable.bundledSchedule
import com.orirose.timetable.date
import com.orirose.timetable.domain.ScheduleCalculator
import com.orirose.timetable.ui.day.DayScreenContent
import com.orirose.timetable.ui.day.DayUiState
import com.orirose.timetable.ui.theme.TimetableTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * The pop-ups with text fields stay put while typing, at a phone's screen size. Sized the platform's
 * way, they were measured at two widths in turn on every change and jumped with each letter; under
 * Robolectric they never settled, so a regression shows here as a timeout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DialogLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun failFast() = IdlingPolicies.setMasterPolicyTimeout(5, TimeUnit.SECONDS)

    @After
    fun restore() = IdlingPolicies.setMasterPolicyTimeout(60, TimeUnit.SECONDS)

    @Before
    fun showTuesday() {
        compose.setContent {
            TimetableTheme {
                DayScreenContent(
                    DayUiState(ScheduleCalculator(bundledSchedule()), emptySet(), LocalDateTime.of(2026, 10, 13, 14, 20), pendingCount = 0),
                    RecordedActions(),
                    onOpenBacklog = {},
                )
            }
        }
    }

    private fun longPressLesson() =
        compose.onNode(hasText("יסודות תורת האינפורמציה") and hasAnyAncestor(hasTestTag("day-${date(2026, 10, 13)}")))
            .performTouchInput { longClick() }

    /** Types [steps] one after another into the field labelled [label]; the pop-up must not move or resize. */
    private fun typeAndHoldStill(label: String, steps: List<String>) {
        // The card itself; the window around it spans the screen.
        val dialog = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
        val before = dialog.fetchSemanticsNode().boundsInWindow
        // Narrower than the screen by the side margins, as wide as a phone allows.
        val screenWidth = RuntimeEnvironment.getApplication().resources.displayMetrics.widthPixels.toFloat()
        assertTrue("pop-up width ${before.width} of $screenWidth", before.width in screenWidth * 0.8f..screenWidth * 0.95f)

        for (text in steps) {
            compose.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(text)
            assertEquals("after typing \"$text\"", before, dialog.fetchSemanticsNode().boundsInWindow)
        }
    }

    private val growing = listOf("ז", "זו", "זומ", "זום", "זום ב", "זום בבית")

    @Test
    fun `the edit pop-up holds still`() {
        longPressLesson()
        compose.onNodeWithText("עריכה").performClick()
        typeAndHoldStill("מקום", growing)
    }

    @Test
    fun `the add pop-up holds still`() {
        compose.onNodeWithContentDescription("הוספת שיעור").performClick()
        typeAndHoldStill("שם הקורס", growing)
    }

    @Test
    fun `the reminder pop-up holds still`() {
        longPressLesson()
        compose.onNodeWithText("תזכורת…").performClick()
        typeAndHoldStill("דקות", listOf("", "4", "45", "5"))
    }
}
