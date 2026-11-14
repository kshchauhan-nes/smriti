package com.example.myapplication.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.example.myapplication.cloud.CloudSession

/** Reload on resume and local date changes, even when a checklist stays open overnight. */
@Composable
fun DailyRefreshEffect(refreshEveryMs: Long? = null, refresh: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentRefresh by rememberUpdatedState(refresh)
    LaunchedEffect(owner, refreshEveryMs) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastDate = ""
            var lastRevision = -1L
            while (true) {
                val now = System.currentTimeMillis()
                val date = ScheduleTime.dateKey(now)
                if (date != lastDate || refreshEveryMs != null || lastRevision != CloudSession.revision.value) {
                    currentRefresh()
                    lastDate = date
                    lastRevision = CloudSession.revision.value
                }
                // The short bound also detects manual date/time-zone changes while foregrounded.
                delay((ScheduleTime.nextMidnight(now) - now).coerceIn(100L, minOf(refreshEveryMs ?: 30_000L, 2_000L)))
            }
        }
    }
}
