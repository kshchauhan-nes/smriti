package com.example.myapplication.cloud

import android.content.Context
import androidx.work.*
import com.example.myapplication.reminders.ReminderScheduler
import kotlinx.coroutines.withTimeout

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!CloudSession.configured(applicationContext)) return Result.success()
        if (CloudSession.auth.currentUser == null) return Result.success()
        return try {
            withTimeout(90_000) { CloudSession.db.waitForPendingWrites().result() }
            ReminderScheduler.reconcile(applicationContext)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("patient_sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
