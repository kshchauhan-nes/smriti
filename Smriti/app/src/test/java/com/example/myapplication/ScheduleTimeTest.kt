package com.example.myapplication

import com.example.myapplication.reminders.ScheduleTime
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ScheduleTimeTest {
    @Test fun acceptsTwelveAndTwentyFourHourSchedules() {
        assertEquals(0 to 0, ScheduleTime.parse("12:00 AM"))
        assertEquals(12 to 0, ScheduleTime.parse("12:00 PM"))
        assertEquals(21 to 5, ScheduleTime.parse(" 9:05 pm "))
        assertEquals(23 to 59, ScheduleTime.parse("23:59"))
        assertEquals(8 to 30, ScheduleTime.parse("08:30 AM"))
    }

    @Test fun rejectsInvalidSchedulesInsteadOfSilentlySkippingReminders() {
        listOf("", "25:00", "12:60", "00:30 AM", "13:00 PM", "9:5", "tomorrow", "08:00 AM junk")
            .forEach { assertNull(it, ScheduleTime.parse(it)) }
    }

    private fun local(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test fun midnightCrossesMonthAndYear() {
        val now = local(2026, 12, 31, 23, 59)
        assertEquals(local(2027, 1, 1), ScheduleTime.nextMidnight(now))
        assertEquals("2027-01-01", ScheduleTime.dateKey(ScheduleTime.nextMidnight(now)))
    }

    @Test fun scheduleUsesCurrentLocalDayAndClearsSeconds() {
        val now = local(2026, 9, 7, 15, 42) + 12345
        assertEquals(local(2026, 9, 7, 8, 30), ScheduleTime.todayAt("08:30 AM", now))
        assertEquals(local(2026, 9, 7), ScheduleTime.startOfDay(now))
    }

    @Test fun midnightUsesCalendarDaysAcrossDaylightSaving() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val spring = local(2026, 3, 8)
            val autumn = local(2026, 11, 1)
            assertEquals(23 * 3600000L, ScheduleTime.nextMidnight(spring) - spring)
            assertEquals(25 * 3600000L, ScheduleTime.nextMidnight(autumn) - autumn)
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun dateKeysStayStableAcrossLanguageChanges() {
        val original = Locale.getDefault()
        try {
            val now = local(2026, 9, 7)
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("2026-09-07", ScheduleTime.dateKey(now))
        } finally { Locale.setDefault(original) }
    }
}
