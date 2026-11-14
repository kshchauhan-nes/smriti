package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.SmritiDatabaseHelper
import com.example.myapplication.reminders.ScheduleTime
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyRolloverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "daily_rollover_test.db"
    private lateinit var helper: SmritiDatabaseHelper
    private val now get() = System.currentTimeMillis()

    @Before fun setup() {
        context.deleteDatabase(databaseName)
        helper = SmritiDatabaseHelper(context, databaseName)
        helper.writableDatabase
    }

    @After fun cleanup() {
        helper.close()
        context.deleteDatabase(databaseName)
    }

    private fun count(sql: String): Int = helper.readableDatabase.rawQuery(sql, null).use {
        it.moveToFirst(); it.getInt(0)
    }

    @Test fun routinesReappearUncheckedWithoutErasingHistoryOrDuplicatingOnReread() {
        val db = helper.writableDatabase
        db.execSQL("UPDATE routine_tasks SET date_key = '2000-01-01', is_completed = 1")
        helper.ensureDailyState(now)
        helper.ensureDailyState(now)
        assertEquals(8, count("SELECT COUNT(*) FROM routine_tasks WHERE date_key = '${ScheduleTime.dateKey(now)}' AND is_completed = 0"))
        assertEquals(8, count("SELECT COUNT(*) FROM routine_tasks WHERE date_key = '2000-01-01' AND is_completed = 1"))
        assertEquals(16, count("SELECT COUNT(*) FROM routine_tasks"))
    }

    @Test fun todayChecksSurviveRefresh() {
        helper.writableDatabase.execSQL("UPDATE routine_tasks SET is_completed = 1")
        helper.ensureDailyState(now)
        assertEquals(8, count("SELECT COUNT(*) FROM routine_tasks WHERE is_completed = 1"))
        assertEquals(8, count("SELECT COUNT(*) FROM routine_tasks"))
    }

    @Test fun medicinesResetOnlyWhenTakenOnAnotherDay() {
        val today = now
        helper.writableDatabase.execSQL("UPDATE medicines SET is_taken = 1, last_taken_timestamp = ? WHERE id = 1", arrayOf(today))
        helper.writableDatabase.execSQL("UPDATE medicines SET is_taken = 1, last_taken_timestamp = ? WHERE id = 2", arrayOf(ScheduleTime.startOfDay(today) - 1))
        helper.ensureDailyState(today)
        assertEquals(1, count("SELECT is_taken FROM medicines WHERE id = 1"))
        assertEquals(0, count("SELECT is_taken FROM medicines WHERE id = 2"))
        helper.ensureDailyState(ScheduleTime.nextMidnight(today))
        assertEquals(0, count("SELECT COUNT(*) FROM medicines WHERE is_taken = 1"))
    }

    @Test fun missedDaysAndCustomScheduleAreHandled() {
        val db = helper.writableDatabase
        db.execSQL("UPDATE routine_tasks SET date_key = '2000-01-01', title = 'Custom activity', time_str = '09:45 AM'")
        helper.ensureDailyState(now)
        assertEquals(8, count("SELECT COUNT(*) FROM routine_tasks WHERE date_key = '${ScheduleTime.dateKey(now)}' AND title = 'Custom activity' AND time_str = '09:45 AM'"))
    }
}
