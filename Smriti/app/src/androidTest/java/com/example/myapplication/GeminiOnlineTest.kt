package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.chat.*
import com.example.myapplication.cloud.CloudSession
import com.example.myapplication.data.ChatMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GeminiOnlineTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val patientKey get() = if (CloudSession.isLocal(context)) "local-patient" else CloudSession.auth.currentUser?.uid ?: "local-patient"
    private fun data() = JSONObject("""{"replyLanguage":"English","region":"Assam","likedTopics":["gardening"],"avoidedTopics":["politics"],"selectedInterests":"music","patient":{"name":"Synthetic test patient"},"family":[{"name":"Test Relative","relationship":"friend","notes":"Enjoys drawing."}],"medicines":[{"name":"Test Medicine","time":"08:00 AM","status":"marked_complete"}],"routines":[{"title":"Garden walk","time":"05:00 PM","status":"scheduled_later"}],"hydration":{"recordedGlasses":3,"targetGlasses":8},"recentGames":[{"score":80,"level":2}]}""")

    @Test fun liveBihuUsesGemini() = runBlocking {
        assertTrue("Internet must be validated", HybridAi.online(context))
        val answer = HybridAi.reply(context, patientKey, data(), emptyList(), "What is Bihu?")
        android.util.Log.i("GeminiValidation", "Bihu mode=${answer.mode}, characters=${answer.text.length}, fallback=${answer.notice != null}")
        assertEquals("Expected Gemini; fallback=${answer.notice != null}", HybridAi.AiMode.ONLINE_GEMINI, answer.mode)
        assertTrue("Coherent Bihu answer", answer.text.length > 40 && answer.text.contains("Bihu", true))
    }

    @Test fun onlineAcceptanceAndNetworkTransitions() = runBlocking<Unit> {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = automation.executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).use { it.bufferedReader().readText().trim() }
        }
        suspend fun network(enabled: Boolean) {
            shell("svc wifi ${if(enabled) "enable" else "disable"}")
            shell("svc data ${if(enabled) "enable" else "disable"}")
            repeat(30) { if (HybridAi.online(context) == enabled) return; delay(1000) }
            fail("Network did not change to requested state")
        }
        val wifi = shell("settings get global wifi_on")
        val mobile = shell("settings get global mobile_data")
        try {
            network(true)
            val d = data()
            val history = mutableListOf<ChatMessage>()
            val prompts = listOf("What is Bihu?", "Tell me more.", "What foods are associated with it?",
                "Tell me about Assamese traditions.", "Tell me a folklore story.", "Suggest a topic.")
            for ((index, prompt) in prompts.withIndex()) {
                // Respect the deployed six-requests-per-minute quota.
                if(index > 0) delay(11_000)
                var partialCount = 0
                val started = android.os.SystemClock.elapsedRealtime()
                val reply = HybridAi.reply(context,patientKey,d,history,prompt,onPartial={ partialCount++ })
                android.util.Log.i("GeminiValidation", "Timing online case=$index ms=${android.os.SystemClock.elapsedRealtime()-started}")
                assertEquals("Online case $index; fallback=${reply.notice != null}", HybridAi.AiMode.ONLINE_GEMINI, reply.mode)
                assertEquals("Offline generation must not run after Gemini success",0,partialCount)
                assertTrue("Online case $index was empty/too short",reply.text.length>30)
                if(index==2) assertTrue("Food follow-up",Regex("pitha|laru|rice|food",RegexOption.IGNORE_CASE).containsMatchIn(reply.text))
                if(index==4) assertTrue("Verified regional folklore",reply.text.contains("Tejimola",true))
                if(index==5) assertTrue("Personalized suggestion",Regex("garden|music",RegexOption.IGNORE_CASE).containsMatchIn(reply.text))
                history += ChatMessage(sender="user",text=prompt)
                history += ChatMessage(sender="smriti",text=reply.text)
                android.util.Log.i("GeminiValidation","Online case $index passed; mode=${reply.mode}")
            }
            for (q in listOf("Who is Test Relative?","What medicines do I have today?","What tasks are left?","How much water have I had?","How are my games going?")) {
                assertEquals(HybridAi.AiMode.PATIENT_RECORDS,HybridAi.reply(context,patientKey,d,history,q).mode)
            }
            val failure = HybridAi.reply(context,patientKey,d,history,"Hello",onlineEndpoint="https://127.0.0.1:1")
            assertNotEquals(HybridAi.AiMode.ONLINE_GEMINI,failure.mode)
            assertNotNull(failure.notice)
            assertTrue(failure.text.isNotBlank())
            val store = OfflineChatStore(context,"online-fallback-regression")
            val persisted = history + ChatMessage(sender="user",text="Hello") + ChatMessage(sender="smriti",text=failure.text)
            store.save(ChatMemory(),persisted)
            assertEquals(persisted.map { it.text },store.loadHistory().map { it.text })
            store.clear()
            network(false)
            val offlineStarted = android.os.SystemClock.elapsedRealtime()
            val offline = HybridAi.reply(context,patientKey,d,emptyList(),"What is Bihu?")
            android.util.Log.i("GeminiValidation", "Timing offline reference ms=${android.os.SystemClock.elapsedRealtime()-offlineStarted}")
            assertNotEquals(HybridAi.AiMode.ONLINE_GEMINI,offline.mode)
            assertNull("No online failure notice when no network",offline.notice)
            assertTrue(offline.text.contains("Bihu",true))
            for ((index, prompt) in listOf("I'm feeling lonely.", "Suggest a topic.").withIndex()) {
                val started = android.os.SystemClock.elapsedRealtime()
                val answer = HybridAi.reply(context,patientKey,d,emptyList(),prompt)
                assertNotEquals(HybridAi.AiMode.ONLINE_GEMINI,answer.mode)
                assertNull(answer.notice)
                assertTrue(answer.text.length > 25)
                assertFalse("No unrelated culture in companionship",answer.text.contains("Bihu",true))
                if(index==1) assertTrue(answer.text.contains("garden",true))
                android.util.Log.i("GeminiValidation","Timing offline routed case=$index ms=${android.os.SystemClock.elapsedRealtime()-started} mode=${answer.mode}")
            }
            network(true)
            val resumed = HybridAi.reply(context,patientKey,d,emptyList(),"Hello")
            assertEquals("Online recovers without app restart",HybridAi.AiMode.ONLINE_GEMINI,resumed.mode)
            android.util.Log.i("GeminiValidation","Failure fallback, persistence, OFF/ON transitions passed")
        } finally {
            shell("svc wifi ${if(wifi=="1") "enable" else "disable"}")
            shell("svc data ${if(mobile=="1") "enable" else "disable"}")
        }
    }
}
