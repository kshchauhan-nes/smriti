package com.example.myapplication

import com.example.myapplication.data.MedicineItem
import com.example.myapplication.data.RoutineItem
import com.example.myapplication.reminders.ScheduleTime
import com.example.myapplication.reminders.missedCareItems
import org.junit.Assert.*
import org.junit.Test

class MissedCareItemsTest {
    @Test fun caregiverListUsesThirtyMinuteBoundaryAndExcludesCompletedAndUpcomingItems() {
        val now = ScheduleTime.todayAt("10:30", System.currentTimeMillis())!!
        val medicines = listOf(
            MedicineItem(name = "Missed medicine", dosage = "", scheduledTime = "10:00"),
            MedicineItem(name = "Taken", dosage = "", scheduledTime = "09:00", isTaken = true),
            MedicineItem(name = "Not yet missed", dosage = "", scheduledTime = "10:01"),
            MedicineItem(name = "Later", dosage = "", scheduledTime = "12:00")
        )
        val routines = listOf(
            RoutineItem(title = "Missed walk", timeStr = "09:30", emoji = ""),
            RoutineItem(title = "Completed", timeStr = "09:00", emoji = "", isCompleted = true)
        )
        val missed = missedCareItems(medicines, routines, now)
        assertEquals(listOf("Missed walk", "Missed medicine"), missed.map { it.title })
        assertEquals(listOf(60L, 30L), missed.map { it.overdueMinutes })
        assertEquals(1, missed.count { it.medicine })
    }
}
