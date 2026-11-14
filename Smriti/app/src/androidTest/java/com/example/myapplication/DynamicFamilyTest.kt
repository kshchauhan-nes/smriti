package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.chat.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicFamilyTest {
    private fun snapshot() = JSONObject().put("replyLanguage", "English").put("family", JSONArray()
        .put(JSONObject().put("name", "Mira Das").put("relationship", "Grand Daughter").put("notes", "Plans to visit on Tuesday."))
        .put(JSONObject().put("name", "Ritu").put("relationship", "grand-daughter").put("notes", "Enjoys singing."))
        .put(JSONObject().put("name", "Jo Lee").put("relationship", "Childhood chess-partner").put("notes", "Met at school.")))

    @Test fun arbitraryRelationshipsNormalizeAndReturnEveryMatch() {
        val data = snapshot()
        for (query in listOf("granddaughter", "Grand-Daughter!", "my GRAND DAUGHTER", "Who is my grand.daughter?", "Tell me about my granddaughter")) {
            val answer = PatientRecordAnswers.answer(data, query)!!
            assertTrue(answer, answer.contains("Mira Das") && answer.contains("Ritu"))
            assertTrue(answer, answer.contains("Plans to visit on Tuesday."))
            assertFalse(answer, answer.contains("games"))
        }
        for (query in listOf("childhood chess partner", "Who is my childhood-chess-partner?", "Tell me about my childhood chess partner")) {
            assertTrue(query, PatientRecordAnswers.answer(data, query)!!.contains("Jo Lee"))
        }
    }

    @Test fun namesMissingRecordsAndPatientSwitchRemainGrounded() {
        val data = snapshot()
        for (query in listOf("Who is Jo Lee?", "Tell me about Jo Lee", "What do you know about Jo Lee?", "jo lee")) {
            val answer = PatientRecordAnswers.answer(data, query)!!
            assertTrue(answer, answer.contains("Childhood chess-partner") && answer.contains("Met at school."))
        }
        assertTrue(PatientRecordAnswers.answer(data, "Who is my neighbour?")!!.contains("not been added"))
        assertTrue(PatientRecordAnswers.answer(data, "Who is Unrecorded Person?")!!.contains("not been added"))
        data.put("family", JSONArray())
        assertNull(PatientRecordAnswers.answer(data, "granddaughter"))
        assertNull(PatientRecordAnswers.answer(data, "childhood chess-partner"))
        assertTrue(PatientRecordAnswers.answer(data, "Who is Jo Lee?")!!.contains("not been added"))
    }

    @Test fun contextAndSuggestionsDoNotMixUnrelatedData() {
        val data = snapshot().put("recentGames", JSONArray().put(JSONObject().put("score", 100)))
            .put("likedTopics", JSONArray().put("gardening").put("music"))
            .put("avoidedTopics", JSONArray().put("gardening"))
        val selected = PatientContextSelector.select(data, "Tell me more about my grand-daughter", emptyList())
        assertFalse(selected.toString(), selected.has("recentGames"))
        assertFalse(selected.toString(), selected.toString().contains("averageScore"))
        val topic = OfflineConversationSupport.topic(data, "Suggest a topic")!!
        assertTrue(topic, topic.contains("music")); assertFalse(topic, topic.contains("gardening"))
    }

    @Test fun incompleteOrInvalidCompanionshipHasAnHonestFallback() {
        val data = snapshot().put("likedTopics", JSONArray().put("gardening"))
        for (answer in listOf("Loneliness is", "Patient feels lonely.", "I'm just a plant, but I grow in your heart.")) {
            val repaired = OfflineConversationSupport.repair(data, "I'm lonely", emptyList(), answer)!!
            assertTrue(repaired, repaired.contains("gardening"))
            assertFalse(repaired, repaired.contains("I'm just a plant"))
        }
        assertNull(OfflineConversationSupport.repair(data, "I'm lonely", emptyList(), "I'm here to listen. Would you like to tell me about your day?"))
    }

    @Test fun ordinaryPhrasesAreNotMisclassifiedAsRelatives() {
        val data = snapshot().put("likedTopics", JSONArray().put("gardening"))
        for (query in listOf("my interests", "what are my hobbies", "tell me about my interests")) {
            val answer = PatientRecordAnswers.answer(data, query)!!
            assertTrue(answer, answer.contains("gardening"))
            assertFalse(answer, answer.contains("family records"))
        }
        for (query in listOf("music", "my mood", "my favourite season", "gardening makes me happy", "I enjoy talking to Jo Lee")) {
            assertNull(query, PatientRecordAnswers.answer(data, query))
        }
    }

    @Test fun gameHydrationAndDoseAnswersUseRecords() {
        val data = snapshot().put("hydration", JSONObject().put("recordedGlasses", 2).put("targetGlasses", 7))
            .put("recentGames", JSONArray()
                .put(JSONObject().put("timestamp", 1).put("score", 40).put("level", 1))
                .put(JSONObject().put("timestamp", 3).put("score", 80).put("level", 2))
                .put(JSONObject().put("timestamp", 2).put("score", 60).put("level", 1)))
        val hydration = PatientRecordAnswers.answer(data, "How much water have I had?")!!
        assertTrue(hydration, hydration.contains("2 glasses") && hydration.contains("7 glasses"))
        val game = PatientRecordAnswers.answer(data, "How are my games going?")!!
        assertTrue(game, game.contains("latest recorded score is 80%"))
        assertTrue(game, game.contains("average recorded score is 60%"))
        assertFalse(game, game.contains("Mira") || game.contains("INSUFFICIENT_DATA"))
        val dose = PatientRecordAnswers.answer(data, "What should I do about my medicine dose?")!!
        assertTrue(dose, dose.contains("caregiver or clinician"))
    }
}
