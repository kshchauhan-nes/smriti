package com.example.myapplication.reminders

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Local wall-clock schedules: recomputed each day, including daylight-saving changes. */
object ScheduleTime {
    fun dateKey(now: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

    fun parse(value: String): Pair<Int, Int>? {
        val match = Regex("^(\\d{1,2}):(\\d{2})(?:\\s*(AM|PM))?$", RegexOption.IGNORE_CASE)
            .matchEntire(value.trim()) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val period = match.groupValues[3].uppercase(Locale.US)
        if (minute !in 0..59) return null
        if (period.isEmpty()) {
            if (hour !in 0..23) return null
        } else {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (period == "PM") 12 else 0
        }
        return hour to minute
    }

    fun todayAt(value: String, now: Long): Long? {
        val (hour, minute) = parse(value) ?: return null
        return Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun nextMidnight(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(now)
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    fun previousMidnight(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(now)
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis
}
