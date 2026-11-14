package com.example.myapplication.reminders

/** Three independently delivered stages; reopening late emits only the current stage. */
object ReminderTimeline {
    const val LEAD_MS = 15 * 60_000L
    const val MISSED_DELAY_MS = 30 * 60_000L
    enum class Stage { UPCOMING, DUE, MISSED }
    data class Plan(val stage: Stage?, val nextAt: Long?)

    fun plan(due: Long, now: Long, completed: Boolean, deliveredStage: Int = -1): Plan {
        if (completed) return Plan(null, null)
        val times = listOf(due - LEAD_MS, due, due + MISSED_DELAY_MS)
        val current = times.indexOfLast { it <= now }
        return Plan(
            Stage.entries.getOrNull(current)?.takeIf { current > deliveredStage },
            times.firstOrNull { it > now }
        )
    }
}
