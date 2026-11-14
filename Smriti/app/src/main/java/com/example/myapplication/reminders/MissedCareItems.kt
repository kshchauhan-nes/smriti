package com.example.myapplication.reminders

import com.example.myapplication.data.MedicineItem
import com.example.myapplication.data.RoutineItem

data class MissedCareItem(val title: String, val scheduledTime: String, val medicine: Boolean,
                          val due: Long, val overdueMinutes: Long)

fun missedCareItems(medicines: List<MedicineItem>, routines: List<RoutineItem>, now: Long): List<MissedCareItem> {
    fun item(title: String, time: String, medicine: Boolean, completed: Boolean): MissedCareItem? {
        val due = ScheduleTime.todayAt(time, now) ?: return null
        if (ReminderTimeline.plan(due, now, completed).stage != ReminderTimeline.Stage.MISSED) return null
        return MissedCareItem(title, time, medicine, due, (now - due) / 60_000L)
    }
    return (medicines.mapNotNull { item(it.name, it.scheduledTime, true, it.isTaken) } +
        routines.mapNotNull { item(it.title, it.timeStr, false, it.isCompleted) }).sortedBy { it.due }
}
