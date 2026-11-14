package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.chat.*
import com.example.myapplication.data.ChatMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineConversationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun data() = JSONObject("""{"replyLanguage":"English","region":"Assam","family":[{"name":"Ananya","relationship":"granddaughter","notes":"Visits every Sunday. Likes painting."}],"medicines":[{"name":"Morning tablet","time":"08:00 AM","status":"marked_complete"},{"name":"Evening tablet","time":"08:00 PM","status":"scheduled_later"}],"routines":[{"title":"Breakfast","time":"08:30 AM","status":"marked_complete"},{"title":"Garden walk","time":"05:00 PM","status":"scheduled_later"},{"title":"Water plants","time":"09:00 AM","status":"overdue_unchecked"}],"hydration":{"recordedGlasses":3,"targetGlasses":8}}""")

    @Test fun companionshipIsACompleteResponseWithoutIrrelevantBihu() = runBlocking {
        val d = data().put("likedTopics", org.json.JSONArray().put("gardening"))
        val start = System.currentTimeMillis()
        val answer = HybridAi.local(context, d, emptyList(), "I'm feeling lonely")
        android.util.Log.i("OfflineConversation", "Companionship ${System.currentTimeMillis()-start}ms: $answer")
        assertTrue(answer, answer.length > 30)
        assertFalse(answer, answer.trim().endsWith(" is"))
        assertFalse(answer, answer.contains("Bihu", true))
        val followUp = HybridAi.local(context, d, listOf(ChatMessage(sender="user",text="I'm feeling lonely"), ChatMessage(sender="smriti",text=answer)), "Tell me more")
        android.util.Log.i("OfflineConversation", "Companionship follow-up: $followUp")
        assertTrue(followUp, followUp.length > 30)
    }

    @Test fun bihuAndFoodFollowUpUseOfflineReferences() = runBlocking {
        assertFalse("Run this test with emulator Wi-Fi and mobile data disabled", HybridAi.online(context))
        val snapshot = data()
        val first = HybridAi.local(context, snapshot, emptyList(), "tell me abt bihu")
        android.util.Log.i("OfflineConversation", "Bihu: $first")
        assertTrue(first, first.contains("Assam", true))
        assertTrue(first, first.length > 25)
        val history = listOf(ChatMessage(sender="user", text="tell me abt bihu"), ChatMessage(sender="smriti", text=first))
        val second = HybridAi.local(context, snapshot, history, "What food is enjoyed then?")
        android.util.Log.i("OfflineConversation", "Food follow-up: $second")
        assertTrue(second, second.contains("pitha", true))
        val story = HybridAi.local(context, snapshot, history, "folklore")
        android.util.Log.i("OfflineConversation", "Folklore: $story")
        assertTrue(story, story.contains("Tejimola", true))
        assertTrue(story, story.length > 80)
        assertTrue(story, story.contains("her father was travelling home by boat"))
        assertTrue(story, story.contains("It became a bird"))
        assertFalse(story, story.contains("bird became a bird"))
    }

    @Test fun familyPronounFollowUpUsesNotes() = runBlocking {
        val history = listOf(ChatMessage(sender="user",text="Who is Ananya?"),ChatMessage(sender="smriti",text="Ananya is your granddaughter."))
        val answer = HybridAi.local(context, data(), history, "When does she visit?")
        android.util.Log.i("OfflineConversation", "Family follow-up: $answer")
        assertTrue(answer, answer.contains("Sunday",true))
    }

    @Test fun completedPendingUpcomingAndOverdueSummariesStayAccurate() {
        val d = data()
        val family = PatientRecordAnswers.answer(d,"Ananya")!!
        assertTrue(family,family.contains("granddaughter")); assertTrue(family,family.contains("Sunday")); assertTrue(family,family.contains("painting"))
        val done = PatientRecordAnswers.answer(d,"Which tasks are completed?")!!
        assertTrue(done,done.contains("Breakfast")); assertFalse(done,done.contains("Garden walk"))
        val pending = PatientRecordAnswers.answer(d,"Which tasks are not done?")!!
        assertTrue(pending,pending.contains("Garden walk")); assertTrue(pending,pending.contains("Water plants")); assertFalse(pending,pending.contains("Breakfast"))
        val upcoming = PatientRecordAnswers.answer(d,"What tasks and meds are upcoming?")!!
        assertTrue(upcoming,upcoming.contains("Evening tablet")); assertTrue(upcoming,upcoming.contains("Garden walk")); assertFalse(upcoming,upcoming.contains("Water plants"))
        val missed = PatientRecordAnswers.answer(d,"Which tasks are missed?")!!
        assertTrue(missed,missed.contains("Water plants")); assertTrue(missed,missed.contains("not marked complete"))
        val meds = PatientRecordAnswers.answer(d,"Which meds are taken?")!!
        assertTrue(meds,meds.contains("Morning tablet")); assertFalse(meds,meds.contains("Evening tablet"))
        d.getJSONArray("routines").getJSONObject(1).put("status","marked_complete")
        assertTrue(PatientRecordAnswers.answer(d,"What tasks have I done?")!!.contains("Garden walk"))
        for (selected in listOf(PatientContextSelector.select(d,"medicines routines hydration",emptyList()),PatientContextSelector.selectForCloud(d,"medicines routines hydration",emptyList()))) {
            val insights=selected.getJSONObject("computedInsights")
            assertEquals(1,insights.getJSONObject("medicines").getInt("markedTaken"))
            assertEquals(2,insights.getJSONObject("routines").getInt("completed"))
            assertEquals(3,insights.getJSONObject("hydration").getInt("loggedGlasses"))
        }
    }
}
