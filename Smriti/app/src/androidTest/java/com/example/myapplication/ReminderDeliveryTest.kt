package com.example.myapplication

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.MedicineItem
import com.example.myapplication.data.SmritiDatabaseHelper
import com.example.myapplication.data.SmritiRepository
import com.example.myapplication.reminders.ReminderScheduler
import com.example.myapplication.reminders.ScheduleTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ReminderDeliveryTest {
    @Test fun stagesAlertOnceAndCompletionClearsTheNotification() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(ReminderScheduler.notificationsAllowed(context))
        val now = System.currentTimeMillis()
        // Keep the synthetic schedule on this local day; midnight boundaries have pure tests.
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        assumeTrue(hour in 1..22)
        val repository = SmritiRepository(context)
        val helper = SmritiDatabaseHelper(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        fun time(at: Long) = SimpleDateFormat("HH:mm", Locale.US).format(Date(at))
        val id = repository.addMedicine(MedicineItem(name = "Reminder verification", dosage = "Test only",
            scheduledTime = time(now + 10 * 60_000L)))
        assertTrue(id > 0)
        val tag = "${ScheduleTime.dateKey(now)}|medicine_$id"
        fun notification() = manager.activeNotifications.firstOrNull { it.tag == tag }
        fun changeTime(at: Long) {
            helper.writableDatabase.execSQL("UPDATE medicines SET scheduled_time = ? WHERE id = ?",
                arrayOf(time(at), id))
        }
        try {
            ReminderScheduler.reconcile(context)
            assertTrue(notification()!!.notification.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("In "))
            val firstPost = notification()!!.postTime
            ReminderScheduler.reconcile(context)
            assertEquals(firstPost, notification()!!.postTime)
            changeTime(now)
            ReminderScheduler.reconcile(context)
            assertTrue(notification()!!.notification.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("It's time:"))
            changeTime(now - 31 * 60_000L)
            ReminderScheduler.reconcile(context)
            assertTrue(notification()!!.notification.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("Missed:"))
            repository.toggleMedicineTaken(id, true)
            assertTrue(repository.getAllMedicines().first { it.id == id }.isTaken)
            ReminderScheduler.reconcile(context)
            // NotificationManager cancellation is processed asynchronously by Android.
            val deadline = android.os.SystemClock.elapsedRealtime() + 2_000L
            while (notification() != null && android.os.SystemClock.elapsedRealtime() < deadline) {
                kotlinx.coroutines.delay(25L)
            }
            assertNull(notification())
        } finally {
            helper.writableDatabase.delete("medicines", "id = ?", arrayOf(id.toString()))
            manager.cancel(tag, 1)
            context.getSharedPreferences("staged_reminder_delivery", Context.MODE_PRIVATE)
                .edit().remove(tag).remove("$tag#done").commit()
            helper.close()
            repository.close()
            ReminderScheduler.reconcile(context)
        }
    }
}
