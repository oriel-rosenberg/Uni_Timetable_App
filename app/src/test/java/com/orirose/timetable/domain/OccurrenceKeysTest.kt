package com.orirose.timetable.domain

import com.orirose.timetable.date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceKeysTest {

    @Test
    fun `a tick saved under the lesson's own date moves to its week`() {
        // The first version of the app keyed Tuesday 13.10.2026 by that date; its week starts on Sunday 11.10.
        assertEquals("huji-5787-a|67577-2-09|2026-10-11", OccurrenceKeys.normalize("huji-5787-a|67577-2-09|2026-10-13"))
    }

    @Test
    fun `keys already in today's form are left alone`() {
        val regular = OccurrenceKeys.regular("t", "l", date(2026, 10, 11))
        val substituted = OccurrenceKeys.substituted("t", "l", date(2027, 6, 27), date(2027, 6, 27))

        assertEquals(regular, OccurrenceKeys.normalize(regular))
        assertEquals(substituted, OccurrenceKeys.normalize(substituted))
        assertEquals("not a key", OccurrenceKeys.normalize("not a key"))
    }

    @Test
    fun `a substituted occurrence remembers its date`() {
        val sunday = date(2027, 6, 27)
        assertEquals(sunday, OccurrenceKeys.substitutedDate(OccurrenceKeys.substituted("t", "l", sunday, sunday)))
        assertNull(OccurrenceKeys.substitutedDate(OccurrenceKeys.regular("t", "l", sunday)))
    }

    @Test
    fun `one lesson's keys cannot be mistaken for another's`() {
        val prefix = OccurrenceKeys.lessonPrefix("t", "67504-1-01")
        assertTrue(OccurrenceKeys.regular("t", "67504-1-01", date(2026, 10, 11)).startsWith(prefix))
        assertFalse(OccurrenceKeys.regular("t", "67504-1-01-sun", date(2026, 10, 11)).startsWith(prefix))
        assertFalse(OccurrenceKeys.isValidId("a|b"))
    }
}
