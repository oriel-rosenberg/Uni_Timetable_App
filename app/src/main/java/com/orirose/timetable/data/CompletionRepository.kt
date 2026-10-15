package com.orirose.timetable.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.orirose.timetable.domain.OccurrenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Remembers which lesson occurrences have been ticked, by [com.orirose.timetable.domain.Occurrence.key]. */
class CompletionRepository(private val store: DataStore<Preferences>) {

    val completed: Flow<Set<String>> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.completedKeys() }

    suspend fun setCompleted(key: String, done: Boolean) {
        store.edit { preferences ->
            val current = preferences.completedKeys()
            preferences[COMPLETED] = if (done) current + key else current - key
        }
    }

    /** Forgets every tick whose key starts with [prefix]: those of a lesson that has been deleted. */
    suspend fun forget(prefix: String) {
        store.edit { preferences ->
            preferences[COMPLETED] = preferences.completedKeys().filterNotTo(HashSet()) { it.startsWith(prefix) }
        }
    }

    /** Ticks saved by the first version of the app are brought to today's key form as they are read. */
    private fun Preferences.completedKeys(): Set<String> =
        this[COMPLETED].orEmpty().mapTo(HashSet(), OccurrenceKeys::normalize)

    private companion object {
        val COMPLETED = stringSetPreferencesKey("completed")
    }
}
