package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.chat.*
import com.example.myapplication.data.ChatMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic snapshots only: never write demonstration people or care records into the app. */
class OfflineAcceptanceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun data() = JSONObject("""{
      "replyLanguage":"English","region":"Assam","localTime":"2026-09-13 Sunday 16:00",
      "patient":{"name":"Test user"},"likedTopics":["gardening"],"avoidedTopics":["politics"],
      "family":[{"name":"Riya","relationship":"niece","notes":"Enjoys drawing."},
        {"name":"Ananya","relationship":"Grand Daughter","notes":"Plans to visit Sunday."},
        {"name":"Amit","relationship":"son","notes":"Lives nearby."}],
      "medicines":[{"name":"Morning tablet","prescribedDosage":"one tablet","instructions":"With breakfast","time":"08:00 AM","status":"marked_complete"},
        {"name":"Evening tablet","time":"08:00 PM","status":"scheduled_later"}],
      "routines":[{"title":"Morning walk","time":"09:00 AM","status":"marked_complete"},
        {"title":"Water plants","time":"05:00 PM","status":"scheduled_later"}],
      "hydration":{"recordedGlasses":3,"targetGlasses":8},
      "reminders":{"leadMinutes":15,"uncheckedDelayMinutes":30,"notificationsAllowed":false,"exactAlarmsAllowed":true},
      "recentGames":[{"timestamp":3,"score":80,"level":2,"responseMs":2000},{"timestamp":2,"score":60,"level":1,"responseMs":3000}]
    }""")

    @Test fun directAcceptanceCasesUsePatientRecords() = runBlocking {
        assertFalse("Disable emulator network for offline acceptance", HybridAi.online(context))
        val expected = linkedMapOf(
            "Who is my granddaughter?" to "Ananya", "granddaughter" to "Ananya",
            "Tell me about Ananya" to "Plans to visit Sunday", "Who is my neice?" to "Riya",
            "what do you know about my son" to "Amit", "Who is my grandson?" to "not been added",
            "Who is my nephew?" to "not been added", "What medicines do I have today?" to "Morning tablet",
            "Tell me about Morning tablet" to "With breakfast", "What is left to do?" to "Water plants",
            "What have I completed?" to "Morning walk", "How much water have I had?" to "3 glasses",
            "How are my games going?" to "80%", "How fast were my game responses?" to "2500 milliseconds",
            "What reminders are enabled?" to "Notifications are currently blocked",
            "What time is it?" to "16:00", "What should I do about my medicine dose?" to "caregiver or clinician"
        )
        for ((query, fragment) in expected) {
            val reply = HybridAi.reply(context, "local-patient", data(), emptyList(), query)
            assertEquals(query, HybridAi.AiMode.PATIENT_RECORDS, reply.mode)
            assertTrue("$query: ${reply.text}", reply.text.contains(fragment))
            assertFalse(reply.text, reply.text.contains("INSUFFICIENT_DATA"))
        }
        val pending = PatientRecordAnswers.answer(data(), "What is left to do?")!!
        assertFalse(pending, pending.contains("Morning tablet") || pending.contains("Morning walk"))
        val familyGame = PatientRecordAnswers.answer(data(), "How are Riya's games going?")!!
        assertTrue(familyGame, familyGame.contains("belong to you"))
    }

    @Test fun cultureAndCompanionshipUseRealOfflineRouting() = runBlocking {
        assertFalse(HybridAi.online(context))
        val d = data()
        val bihu = HybridAi.reply(context,"local-patient",d,emptyList(),"What is Bihu?")
        android.util.Log.i("OfflineAcceptance", "Bihu ${bihu.mode}: ${bihu.text}")
        assertTrue(bihu.text, bihu.text.length > 40 && bihu.text.contains("Bihu", true))
        assertTrue(bihu.mode in setOf(HybridAi.AiMode.OFFLINE_LFM, HybridAi.AiMode.OFFLINE_REFERENCE))
        val history = listOf(ChatMessage(sender="user", text="What is Bihu?"),ChatMessage(sender="smriti",text=bihu.text))
        val more = HybridAi.reply(context,"local-patient",d,history,"Tell me more")
        android.util.Log.i("OfflineAcceptance", "More ${more.mode}: ${more.text}")
        assertTrue(more.text, more.text.length > 40)
        assertFalse(more.text, more.text == bihu.text)
        val lonely = HybridAi.reply(context,"local-patient",d,emptyList(),"I'm feeling lonely")
        android.util.Log.i("OfflineAcceptance", "Lonely ${lonely.mode}: ${lonely.text}")
        assertTrue(lonely.text, lonely.text.length > 30)
        assertFalse(lonely.text, lonely.text.contains("Bihu") || lonely.text.contains("I'm just a plant"))
        val topic = HybridAi.reply(context,"local-patient",d,emptyList(),"Suggest a topic")
        assertTrue(topic.text, topic.text.contains("gardening") && !topic.text.contains("politics"))
    }
}
