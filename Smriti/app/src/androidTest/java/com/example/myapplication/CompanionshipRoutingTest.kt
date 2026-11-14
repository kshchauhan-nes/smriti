package com.example.myapplication

import com.example.myapplication.chat.*
import com.example.myapplication.data.ChatMessage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CompanionshipRoutingTest {
    private fun data() = JSONObject("""{"replyLanguage":"English","patient":{"name":"Test"},
      "likedTopics":["gardening"],"avoidedTopics":["politics"],"selectedInterests":"music",
      "family":[{"name":"Sad","relationship":"Lonely","notes":"Plays games and talks about medicines"}],
      "recentGames":[{"score":100}],"medicines":[{"name":"Test tablet"}]}""")

    @Test fun feelingsDoNotBecomeFamilyOrGameQueries() {
        for (q in listOf("I'm lonely", "I'm feeling lonely", "I'm sad", "I'm bored", "talk to me", "keep me company", "lonely")) {
            assertTrue(q, OfflineConversationSupport.companionship(q, emptyList()))
            assertNull(q, PatientRecordAnswers.answer(data(), q))
            assertNull(q, OfflineConversationSupport.topic(data(), q))
            val selected = PatientContextSelector.select(data(), q, emptyList())
            assertFalse(q, selected.has("family") || selected.has("recentGames") || selected.has("medicines"))
            assertTrue(q, selected.toString().contains("gardening"))
        }
        // A deliberate identity question still finds a custom saved relationship.
        assertTrue(PatientRecordAnswers.answer(data(), "Who is my Lonely?")!!.contains("Sad"))
    }

    @Test fun emotionFollowUpDoesNotReintroduceEarlierRecords() {
        val history = listOf(ChatMessage(sender="user", text="How are my games going?"),
            ChatMessage(sender="smriti", text="Your score was 100."),
            ChatMessage(sender="user", text="I'm sad"), ChatMessage(sender="smriti", text="I'm here to listen."))
        val selected = PatientContextSelector.select(data(), "Tell me more", history)
        assertFalse(selected.toString(), selected.has("recentGames") || selected.has("family"))
        assertNull(PatientRecordAnswers.answer(data(), "Tell me more", history))
        val topic = OfflineConversationSupport.topic(data(), "Suggest a topic")!!
        assertTrue(topic.contains("gardening")); assertFalse(topic.contains("politics"))
    }
}
