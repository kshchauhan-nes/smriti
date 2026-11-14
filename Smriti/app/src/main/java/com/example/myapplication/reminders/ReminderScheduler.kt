package com.example.myapplication.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.cloud.CloudSession
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.SmritiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ReminderScheduler {
    // Android locks sound and vibration settings after a channel is first created.
    // Versioning this ID upgrades earlier installs that received a silent channel.
    const val CHANNEL = "daily_care_reminders_v2"
    const val SCREEN = "reminder_screen"
    const val ALARM_ACTION = "com.example.myapplication.DAILY_REMINDERS"
    private val mutex = Mutex()

    data class Item(val key: String, val screen: String, val title: String,
                    val detail: String, val due: Long, val completed: Boolean)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Medicine & routine reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 180, 400)
                    setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())
                    description = "Sound and vibration reminders for today's medicines and activities that are still incomplete"
                }
            )
        }
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun exactAlarmsAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** All three stages use one next-deadline alarm, including stages crossing midnight. */
    @SuppressLint("ApplySharedPref") // Persist delivery markers on IO before the receiver finishes.
    suspend fun reconcile(context: Context) = withContext(Dispatchers.IO) { mutex.withLock {
        val app = context.applicationContext
        val localMode = CloudSession.isLocal(app)
        if (!localMode && CloudSession.configured(app) && CloudSession.auth.currentUser == null) { cancel(app); return@withLock }
        val patient = if (!localMode && CloudSession.configured(app)) CloudSession.auth.currentUser?.uid.orEmpty() else "legacy"
        createChannel(app)
        val now = System.currentTimeMillis()
        val today = ScheduleTime.dateKey(now)
        val yesterday = ScheduleTime.previousMidnight(now)
        val tomorrow = ScheduleTime.nextMidnight(now)
        val prefs = app.getSharedPreferences("staged_reminder_delivery_$patient", Context.MODE_PRIVATE)
        val manager = NotificationManagerCompat.from(app)
        // Remove notifications from the earlier one-stage version once on upgrade.
        val legacy = app.getSharedPreferences("daily_reminder_delivery", Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) {
            legacy.all.keys.filter { it != "date" }.forEach { manager.cancel(it, 1) }
            legacy.edit().clear().commit()
        }
        val oldest = ScheduleTime.dateKey(yesterday)
        prefs.all.keys.filter { it.substringBefore('|') < oldest }.forEach {
            manager.cancel(it.removeSuffix("#done"), 1)
            prefs.edit().remove(it).commit()
        }
        val repository = SmritiRepository(app)
        try {
            val medicines = repository.getAllMedicines()
            val routines = repository.getTodayRoutines()
            val days = listOf(yesterday, ScheduleTime.startOfDay(now), tomorrow)
            val items = days.flatMap { day ->
                val dayKey = ScheduleTime.dateKey(day)
                val medicineItems = medicines.mapNotNull { med ->
                    ScheduleTime.todayAt(med.scheduledTime, day)?.let { due ->
                        val key = "$dayKey|medicine_${med.id}"
                        val done = when {
                            dayKey == today -> med.isTaken
                            day < now -> prefs.getBoolean("$key#done", false) ||
                                (med.lastTakenTimestamp > 0 && ScheduleTime.dateKey(med.lastTakenTimestamp) == dayKey)
                            else -> false
                        }
                        Item(key, "medicine", med.name,
                            "Scheduled ${med.scheduledTime} • ${med.dosage}", due, done)
                    }
                }
                val dayRoutines = if (day < ScheduleTime.startOfDay(now))
                    repository.getRoutinesForDate(dayKey) else routines
                // Daily database rows have new IDs. Content identity + duplicate index remains stable
                // for tomorrow's advance reminder when those rows are copied at midnight.
                val duplicates = mutableMapOf<String, Int>()
                val routineItems = dayRoutines.mapNotNull { routine ->
                    ScheduleTime.todayAt(routine.timeStr, day)?.let { due ->
                        val identity = listOf(routine.title, routine.timeStr, routine.emoji)
                            .joinToString("") { "${it.length}:$it" }
                        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
                            .digest(identity.toByteArray(Charsets.UTF_8))
                            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                        val index = duplicates[fingerprint] ?: 0
                        duplicates[fingerprint] = index + 1
                        Item("$dayKey|routine_${fingerprint}_$index", "routine", routine.title,
                            "Scheduled ${routine.timeStr}", due, day < tomorrow && routine.isCompleted)
                    }
                }
                (medicineItems + routineItems).filter { item ->
                    day >= ScheduleTime.startOfDay(now) ||
                        item.due + ReminderTimeline.MISSED_DELAY_MS >= ScheduleTime.startOfDay(now)
                }
            }
            // Remove cards/markers for replaced routine identities or deleted schedules.
            val activeKeys = items.map { it.key }.toSet()
            prefs.all.keys.filter { it.substringBefore('#') !in activeKeys }.forEach { key ->
                manager.cancel(key.substringBefore('#'), 1)
                prefs.edit().remove(key).commit()
            }
            var next = tomorrow
            items.forEach { item ->
                if (CloudSession.isLocal(app) != localMode || (!localMode && CloudSession.configured(app) && CloudSession.auth.currentUser?.uid != patient)) return@forEach
                if (item.key.substringBefore('|') == today &&
                    prefs.getBoolean("${item.key}#done", false) != item.completed) {
                    prefs.edit().putBoolean("${item.key}#done", item.completed).commit()
                }
                val dueKey = "${item.key}#due"
                val contentKey = "${item.key}#content"
                val content = "${item.title.length}:${item.title}${item.detail}"
                if ((prefs.contains(dueKey) && prefs.getLong(dueKey, 0L) != item.due) ||
                    (prefs.contains(contentKey) && prefs.getString(contentKey, null) != content)) {
                    manager.cancel(item.key, 1)
                    prefs.edit().remove(item.key).commit()
                }
                prefs.edit().putLong(dueKey, item.due).putString(contentKey, content).commit()
                val plan = ReminderTimeline.plan(item.due, now, item.completed, prefs.getInt(item.key, -1))
                plan.nextAt?.let { next = minOf(next, it) }
                if (item.completed) manager.cancel(item.key, 1)
                val stage = plan.stage
                if (stage != null && notificationsAllowed(app)) {
                    val title = when (stage) {
                        ReminderTimeline.Stage.UPCOMING -> {
                            val minutes = ((item.due - now + 59_999L) / 60_000L).coerceAtLeast(1)
                            "In $minutes ${if (minutes == 1L) "minute" else "minutes"}: ${item.title}"
                        }
                        ReminderTimeline.Stage.DUE -> "It's time: ${item.title}"
                        ReminderTimeline.Stage.MISSED -> "Missed: ${item.title}"
                    }
                    val detail = item.detail + when (stage) {
                        ReminderTimeline.Stage.UPCOMING -> ". Your activity is coming up."
                        ReminderTimeline.Stage.DUE -> ". Please mark it when done."
                        ReminderTimeline.Stage.MISSED -> if (item.screen == "medicine")
                            ". Still not marked taken, 30 minutes after its scheduled time."
                            else ". Still not marked complete, 30 minutes after its scheduled time."
                    }
                    val intent = Intent(app, MainActivity::class.java)
                        .putExtra(SCREEN, item.screen)
                        .setAction("open_${item.screen}")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    val open = PendingIntent.getActivity(app, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification = NotificationCompat.Builder(app, CHANNEL)
                        .setSmallIcon(R.drawable.ic_reminder)
                        .setContentTitle(title)
                        .setContentText(detail)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        // Each stage must alert again, while replacing the previous stage's card.
                        .setOnlyAlertOnce(false)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                        .build()
                    try {
                        manager.notify(item.key, 1, notification)
                        prefs.edit().putInt(item.key, stage.ordinal).commit()
                    } catch (_: SecurityException) {
                        // Permission can be revoked between checking and posting.
                    }
                }
            }
            if (CloudSession.isLocal(app) == localMode && (localMode || !CloudSession.configured(app) || CloudSession.auth.currentUser?.uid == patient)) schedule(app, next) else cancel(app)
        } finally {
            repository.close()
        }
    } }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
        val pending = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java).setAction(ALARM_ACTION), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { context.getSystemService(AlarmManager::class.java).cancel(pending); pending.cancel() }
    }

    private fun schedule(context: Context, at: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ALARM_ACTION)
        val pending = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (exactAlarmsAllowed(context)) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            } catch (_: SecurityException) { /* Fall back if access changed. */ }
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
}

/** Rebuild after reboot, updates, clock/time-zone changes, or exact alarm access is granted. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                ReminderScheduler.ALARM_ACTION, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reconcile(context)
            } catch (e: Exception) {
                Log.e("SmritiReminders", "Unable to refresh reminders", e)
            } finally {
                pending.finish()
            }
        }
    }
}
