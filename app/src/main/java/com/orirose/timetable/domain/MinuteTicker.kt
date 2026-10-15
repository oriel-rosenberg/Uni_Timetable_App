package com.orirose.timetable.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Emits the current time straight away and again at the start of every minute. */
fun minuteTicker(clock: Clock): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now(clock)
        emit(now.truncatedTo(ChronoUnit.MINUTES))
        val intoMinute = now.second * 1000L + now.nano / 1_000_000L
        delay(60_000L - intoMinute + 50L)
    }
}
