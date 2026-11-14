package com.example.myapplication

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.cloud.CloudSession
import com.example.myapplication.data.*
import com.example.myapplication.reminders.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CareScheduleEditTest {
    @Test fun editsPersistWithoutDuplicatesAndReplaceAlerts() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Local emulator required",CloudSession.isLocal(context))
        assumeTrue("Notifications must be allowed",ReminderScheduler.notificationsAllowed(context))
        val repo=SmritiRepository(context)
        val helper=SmritiDatabaseHelper(context)
        val manager=context.getSystemService(NotificationManager::class.java)
        val now=System.currentTimeMillis()
        fun time(delta:Long)=SimpleDateFormat("HH:mm",Locale.US).format(Date(now+delta))
        assumeTrue(ScheduleTime.dateKey(now+60*60_000L)==ScheduleTime.dateKey(now))
        val medicineId=repo.addMedicine(MedicineItem(name="Schedule edit test medicine",dosage="Test only",scheduledTime=time(10*60_000L)))
        val routineId=repo.saveRoutine(RoutineItem(title="Schedule edit test routine",timeStr=time(10*60_000L),emoji="✓"))
        val tag="${ScheduleTime.dateKey(now)}|medicine_$medicineId"
        suspend fun noAlert(oldTag:String) {
            repeat(40) { if(manager.activeNotifications.none { it.tag==oldTag })return; delay(50) }
            fail("Old notification remained")
        }
        try {
            val medCount=repo.getAllMedicines().size
            val routineCount=repo.getTodayRoutines().size
            ReminderScheduler.reconcile(context)
            assertTrue(manager.activeNotifications.any { it.tag==tag })
            val routineTag=manager.activeNotifications.first { it.notification.extras.getString("android.title").orEmpty().contains("Schedule edit test routine") }.tag
            var med=repo.getAllMedicines().first { it.id==medicineId }
            repo.updateMedicine(med.copy(name="Edited test medicine",scheduledTime=time(60*60_000L),dosage="Saved test dosage",instructions="Saved test instructions"))
            var routine=repo.getTodayRoutines().first { it.id==routineId }
            repo.updateRoutine(routine.copy(title="Edited test routine",timeStr=time(60*60_000L)))
            ReminderScheduler.reconcile(context)
            noAlert(tag); noAlert(routineTag)
            assertEquals(medCount,repo.getAllMedicines().size)
            assertEquals(routineCount,repo.getTodayRoutines().size)
            med=repo.getAllMedicines().first { it.id==medicineId }
            assertEquals("Saved test dosage",med.dosage)
            assertEquals("Saved test instructions",med.instructions)
            // Moving back into the advance window must alert, even if the previous schedule alerted already.
            repo.updateMedicine(med.copy(scheduledTime=time(10*60_000L)))
            ReminderScheduler.reconcile(context)
            assertTrue(manager.activeNotifications.any { it.tag==tag })
            repo.toggleMedicineTaken(medicineId,true)
            repo.toggleRoutineCompleted(routineId,true)
            med=repo.getAllMedicines().first { it.id==medicineId }
            routine=repo.getTodayRoutines().first { it.id==routineId }
            repo.updateMedicine(med.copy(name="Completed edited medicine",isTaken=false,lastTakenTimestamp=0))
            repo.updateRoutine(routine.copy(title="Completed edited routine",isCompleted=false))
            val saved=repo.getAllMedicines().first { it.id==medicineId }
            assertTrue(saved.isTaken); assertEquals(med.lastTakenTimestamp,saved.lastTakenTimestamp)
            assertTrue(repo.getTodayRoutines().first { it.id==routineId }.isCompleted)
            ReminderScheduler.reconcile(context)
            noAlert(tag)
            assertNotNull(manager.getNotificationChannel(ReminderScheduler.CHANNEL).sound)
        } finally {
            helper.writableDatabase.delete(SmritiDatabaseHelper.TABLE_MEDICINES,"id = ?",arrayOf(medicineId.toString()))
            helper.writableDatabase.delete(SmritiDatabaseHelper.TABLE_ROUTINE,"id = ?",arrayOf(routineId.toString()))
            helper.close(); repo.close(); ReminderScheduler.reconcile(context)
        }
    }
}
