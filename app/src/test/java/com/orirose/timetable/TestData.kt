package com.orirose.timetable

import com.orirose.timetable.data.ScheduleParser
import com.orirose.timetable.data.model.Schedule
import java.io.File
import java.time.LocalDate

/** The schedule that ships in the app. Unit tests run with the module directory as working directory. */
fun bundledSchedule(): Schedule = ScheduleParser.parse(File("src/main/assets/schedule.json").readText())

fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)
