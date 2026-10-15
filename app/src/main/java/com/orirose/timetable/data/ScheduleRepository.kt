package com.orirose.timetable.data

import android.content.Context
import android.net.Uri
import com.orirose.timetable.data.model.Schedule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Holds the active schedule: the one bundled with the app until the user imports another or
 * changes something, and from then on the copy kept in the app's own storage.
 */
class ScheduleRepository(
    private val context: Context,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val importedFile = File(context.filesDir, FILE_NAME)
    private val saving = Mutex()

    private val _schedule = MutableStateFlow<Schedule?>(null)

    /** `null` only for the instant before the stored schedule has been read. */
    val schedule: StateFlow<Schedule?> = _schedule.asStateFlow()

    init {
        scope.launch { _schedule.value = withContext(io) { loadStored() } }
    }

    /** Reads the text of a file the user picked, without applying it. */
    @Throws(IOException::class)
    suspend fun readText(uri: Uri): String = withContext(io) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        stream.use { input ->
            val bytes = input.readNBytesCompat(MAX_FILE_BYTES + 1)
            if (bytes.size > MAX_FILE_BYTES) throw IOException("File is too large to be a schedule")
            bytes.toString(Charsets.UTF_8).removePrefix("﻿")
        }
    }

    /** Makes [text] the active schedule and keeps it for future launches. */
    @Throws(ScheduleFormatException::class, IOException::class)
    suspend fun replace(text: String) {
        val parsed = ScheduleParser.parse(text)
        _schedule.value = parsed
        save()
    }

    /** Applies a change the user made to the active schedule and keeps it. */
    suspend fun update(transform: (Schedule) -> Schedule) {
        val current = _schedule.value ?: return
        val updated = transform(current)
        if (updated == current) return
        _schedule.value = updated
        save()
    }

    /** Writes the schedule as it is now; when changes come in quick succession the last one wins. */
    private suspend fun save() = withContext(io) {
        saving.withLock {
            val text = ScheduleParser.write(_schedule.value ?: return@withLock)
            val temp = File(importedFile.parentFile, "$FILE_NAME.tmp")
            temp.writeText(text)
            if (!temp.renameTo(importedFile)) {
                temp.copyTo(importedFile, overwrite = true)
                temp.delete()
            }
        }
    }

    private fun loadStored(): Schedule {
        if (importedFile.exists()) {
            try {
                return ScheduleParser.parse(importedFile.readText())
            } catch (_: ScheduleFormatException) {
                // Written by a different app version; fall back to the bundled schedule.
            } catch (_: IOException) {
            }
        }
        return ScheduleParser.parse(context.assets.open(SEED_ASSET).bufferedReader().use { it.readText() })
    }

    private companion object {
        const val FILE_NAME = "schedule.json"
        const val SEED_ASSET = "schedule.json"
        const val MAX_FILE_BYTES = 1_000_000
    }
}

/** `InputStream.readNBytes` only exists from Android 13. */
private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read == -1) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
