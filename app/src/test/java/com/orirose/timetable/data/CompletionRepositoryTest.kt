package com.orirose.timetable.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.orirose.timetable.domain.OccurrenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionRepositoryTest {

    /** Stands in for the on-disk store, which on Windows cannot replace its own file. */
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun `deleting a lesson's ticks leaves every other lesson's`() = runTest {
        val repository = CompletionRepository(MemoryStore())
        repository.setCompleted("t|my-1-1|2026-10-18", done = true)
        repository.setCompleted("t|my-1-10|2026-10-18", done = true)
        repository.setCompleted("t|67504-1-01-sun|2026-10-11", done = true)

        repository.forget(OccurrenceKeys.lessonPrefixOf("t|my-1-1|2026-10-18"))

        assertEquals(setOf("t|my-1-10|2026-10-18", "t|67504-1-01-sun|2026-10-11"), repository.completed.first())
    }
}
