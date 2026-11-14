package com.example.myapplication

import com.example.myapplication.reminders.ReminderTimeline
import com.example.myapplication.reminders.ReminderTimeline.Stage
import org.junit.Assert.*
import org.junit.Test

class ReminderTimelineTest {
    private val due = 10 * 60 * 60_000L

    @Test fun advanceReminderStartsExactlyFifteenMinutesBefore() {
        val before = due - 15 * 60_000L
        assertNull(ReminderTimeline.plan(due, before - 1, false).stage)
        assertEquals(before, ReminderTimeline.plan(due, before - 1, false).nextAt)
        assertEquals(Stage.UPCOMING, ReminderTimeline.plan(due, before, false).stage)
    }

    @Test fun secondReminderStartsAtTheScheduledTime() {
        assertNull(ReminderTimeline.plan(due, due - 1, false, Stage.UPCOMING.ordinal).stage)
        assertEquals(Stage.DUE, ReminderTimeline.plan(due, due, false, Stage.UPCOMING.ordinal).stage)
        assertEquals(due + 30 * 60_000L, ReminderTimeline.plan(due, due, false).nextAt)
    }

    @Test fun missedWaitsThirtyMinutes() {
        val missed = due + 30 * 60_000L
        assertNull(ReminderTimeline.plan(due, missed - 1, false, Stage.DUE.ordinal).stage)
        assertEquals(Stage.MISSED, ReminderTimeline.plan(due, missed, false, Stage.DUE.ordinal).stage)
        assertNull(ReminderTimeline.plan(due, missed, false).nextAt)
    }

    @Test fun completionSuppressesEveryRemainingStage() {
        listOf(due - 15 * 60_000L, due, due + 30 * 60_000L).forEach {
            assertEquals(ReminderTimeline.Plan(null, null), ReminderTimeline.plan(due, it, true))
        }
    }

    @Test fun refreshAndClockChangesDoNotDuplicateDeliveredStages() {
        assertNull(ReminderTimeline.plan(due, due - 10 * 60_000L, false, 0).stage)
        assertNull(ReminderTimeline.plan(due, due + 5 * 60_000L, false, 1).stage)
        assertNull(ReminderTimeline.plan(due, due + 60 * 60_000L, false, 2).stage)
        assertNull(ReminderTimeline.plan(due, due - 10 * 60_000L, false, 2).stage)
    }

    @Test fun lateReopenOnlyShowsTheCurrentStage() {
        assertEquals(Stage.MISSED, ReminderTimeline.plan(due, due + 60 * 60_000L, false).stage)
    }

    @Test fun reminderWindowsCanCrossMidnight() {
        val midnight = 24 * 60 * 60_000L
        val earlyActivity = midnight + 5 * 60_000L
        assertEquals(Stage.UPCOMING, ReminderTimeline.plan(earlyActivity, midnight - 10 * 60_000L, false).stage)
        val lateActivity = midnight - 5 * 60_000L
        assertEquals(Stage.MISSED, ReminderTimeline.plan(lateActivity, midnight + 25 * 60_000L, false).stage)
    }
}
