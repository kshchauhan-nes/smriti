package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.chat.HybridAi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAiSmokeTest {
    @Test fun consecutivePatientsDoNotShareModelState() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (name in listOf("Ananya", "Meera")) {
            val data = JSONObject().put("replyLanguage", "English").put("family",
                org.json.JSONArray().put(JSONObject().put("name", name).put("relationship", "granddaughter").put("notes", "Visits every Sunday")))
            val started = android.os.SystemClock.elapsedRealtime()
            val answer = HybridAi.local(context, data, emptyList(), "Name the relative who visits on Sunday.")
            android.util.Log.i("LfmIntegration", "Patient record reply in ${android.os.SystemClock.elapsedRealtime()-started} ms: $answer")
            assertTrue("Wrong patient record: $answer", answer.contains(name, true))
            assertFalse("Previous patient leaked: $answer", answer.contains(if (name == "Ananya") "Meera" else "Ananya", true))
        }
    }

    @Test fun cancellationAllowsTheNextMessage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = JSONObject().put("replyLanguage", "English")
        kotlinx.coroutines.withTimeoutOrNull(1) {
            HybridAi.local(context, data, emptyList(), "Tell me about gardening.")
        }
        val answer = HybridAi.local(context, data, emptyList(), "Say hello in one word.")
        assertTrue("No response after cancellation", answer.isNotBlank())
    }

    @Test fun localModelUsesSuppliedFamilyFact() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Install verified model before this integration test", HybridAi.installed(context))
        val snapshot = JSONObject("""{"replyLanguage":"English","patient":{"name":"Test Patient"},"family":[{"name":"Ananya","relationship":"granddaughter","notes":"Visits on Sundays"}]}""")
        val answer = HybridAi.local(context, snapshot, emptyList(), "What is my granddaughter's name? Answer with her name only.")
        assertTrue("Expected supplied name; received: $answer", answer.contains("Ananya", ignoreCase=true))
    }
    @Test fun localModelProducesStructuredDifficulty() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val answer = HybridAi.local(context, JSONObject().put("scorePercent", 20), emptyList(),
            "Return only this JSON: {\"extraSeconds\":3}", "difficulty")
        assertEquals(answer, 3, JSONObject(answer).getInt("extraSeconds"))
    }
    @Test fun medicinesAndRoutinesUseFreshRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = JSONObject("""{"replyLanguage":"English","medicines":[{"name":"TestMed","time":"09:00 AM","status":"marked_complete"}],"routines":[{"title":"Garden walk","time":"05:00 PM","status":"scheduled_later"}]}""")
        val routed = HybridAi.reply(context, "local-patient", data, emptyList(), "What is the recorded name of my medicine? Give its name only.")
        assertEquals(HybridAi.AiMode.PATIENT_RECORDS, routed.mode)
        val medicine = routed.text
        assertTrue("Medicine record missing: $medicine", medicine.contains("TestMed",true))
        val routine = HybridAi.local(context, data, listOf(com.example.myapplication.data.ChatMessage(sender="user",text="What is my medicine?"), com.example.myapplication.data.ChatMessage(sender="smriti",text=medicine)), "Now what activity is scheduled for 5 PM? Name the activity only.")
        assertTrue("Routine record missing: $routine", routine.contains("walk",true))
        assertNotEquals(medicine, routine)
    }
}
