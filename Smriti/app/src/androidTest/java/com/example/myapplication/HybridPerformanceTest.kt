package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.chat.*
import com.example.myapplication.cloud.CloudSession
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HybridPerformanceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun data() = JSONObject("""{"replyLanguage":"English","region":"Assam","patient":{"name":"Synthetic test"},"likedTopics":["gardening"],"selectedInterests":"music","avoidedTopics":["politics"],"family":[{"name":"Synthetic Relative","relationship":"custom friend","notes":"Enjoys painting"}],"medicines":[{"name":"Synthetic medicine","status":"marked_complete"}],"routines":[],"recentGames":[{"score":80}],"hydration":{"recordedGlasses":3,"targetGlasses":8}}""")
    @Test fun relevantCloudContextAndFastFacts() {
        val d=data()
        for (query in listOf("What is Bihu?", "What foods are famous in Assam?", "I'm feeling lonely")) {
            val selected=PatientContextSelector.selectForCloud(d,query,emptyList())
            assertFalse(selected.has("family")); assertFalse(selected.has("medicines")); assertFalse(selected.has("recentGames"))
            assertTrue(selected.has("likedTopics"))
        }
        val family=PatientContextSelector.selectForCloud(d,"Discuss Synthetic Relative",emptyList())
        assertEquals(1,family.getJSONArray("family").length()); assertFalse(family.has("recentGames"))
        assertTrue(PatientRecordAnswers.answer(d,"Do I like gardening?")!!.contains("gardening"))
        assertTrue(PatientRecordAnswers.answer(d,"Do I like Bihu?")!!.contains("not been recorded"))
        val start=android.os.SystemClock.elapsedRealtime()
        repeat(20) {
            assertNotNull(PatientRecordAnswers.answer(d,"How much water have I had?"))
            assertNotNull(PatientRecordAnswers.answer(d,"Who is my custom friend?"))
        }
        android.util.Log.i("GeminiValidation","Timing 40 deterministic answers ms=${android.os.SystemClock.elapsedRealtime()-start}")
    }
    @Test fun offlineModelLatencyAndStreaming() = runBlocking {
        val d=data()
        for ((index,q) in listOf("I'm feeling lonely.","Tell me about gardening.").withIndex()) {
            val start=android.os.SystemClock.elapsedRealtime()
            var first=0L
            var last=""
            val reply=HybridAi.local(context,d,emptyList(),q,onPartial={
                if(first==0L)first=android.os.SystemClock.elapsedRealtime()-start
                last=it
            })
            assertTrue(reply.isNotBlank()); assertTrue(last.isNotBlank())
            android.util.Log.i("GeminiValidation","Timing LFM case=$index firstMs=$first totalMs=${android.os.SystemClock.elapsedRealtime()-start} chars=${reply.length}")
        }
    }
}
