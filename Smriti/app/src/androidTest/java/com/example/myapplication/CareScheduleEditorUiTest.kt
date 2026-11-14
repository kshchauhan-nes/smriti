package com.example.myapplication

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.*
import com.example.myapplication.reminders.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CareScheduleEditorUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun editMedicineWithInAppTimeField() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=SmritiRepository(context,forceLocal=true)
        val helper=SmritiDatabaseHelper(context)
        val id=runBlocking { repo.addMedicine(MedicineItem(name="UI schedule test",dosage="Test only",scheduledTime="09:00")) }
        val med=runBlocking { repo.getAllMedicines().first { it.id==id } }
        var saved=false
        try {
            compose.setContent { MaterialTheme { com.example.myapplication.ui.LanguageHost { CaregiverScheduleEditor(repo,listOf(med),emptyList(),{saved=true}) } } }
            compose.onNodeWithText("Edit").performClick()
            compose.onNodeWithText("Medicine name").performTextReplacement("UI edited medicine")
            compose.onNodeWithText("Daily time (HH:MM, for example 09:30 or 6:30 PM)").performTextReplacement("10:35")
            compose.onNodeWithText("Save schedule").performClick()
            compose.waitUntil(15000) { saved }
            val updated=runBlocking { repo.getAllMedicines().first { it.id==id } }
            assertEquals("UI edited medicine",updated.name)
            assertEquals("10:35",updated.scheduledTime)
        } finally {
            helper.writableDatabase.delete(SmritiDatabaseHelper.TABLE_MEDICINES,"id = ?",arrayOf(id.toString()))
            helper.close(); repo.close(); runBlocking { ReminderScheduler.reconcile(context) }
        }
    }
}
