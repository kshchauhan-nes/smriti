package com.example.myapplication

import com.example.myapplication.chat.*
import com.example.myapplication.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class MultilingualChatTest {
    private val sunday = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 6, 15, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val facts = ChatFacts(PatientProfile(patientName = "", caregiverName = ""), emptyList(), emptyList(), emptyList())
    private val locales = listOf("as", "bn", "ne")

    @Test fun allConversationBranchesProduceLocalizedResponses() {
        val prompts = listOf("hello", "medicines", "tasks", "family", "sad", "happy", "music", "gardening", "food", "festivals", "memories", "Bihu", "suggest", "my interests", "thanks", "bye", "joke", "unknown question", "clear memory", "chest pain", "double dose", "I enjoy music", "I don't like music")
        for (language in locales) for (prompt in prompts) {
            val answer = OfflineChatEngine(language).reply(prompt, facts, ChatMemory(), sunday)
            assertFalse("$language / $prompt leaked English: $answer", Regex("[A-Za-z]").containsMatchIn(answer))
            assertTrue(answer.isNotBlank())
        }
    }

    @Test fun localizedSuggestionChipsReachTheirIntendedRules() {
        for (language in locales) {
            val words = ChatLanguage(language)
            val engine = OfflineChatEngine(language)
            val expected = mapOf("Family" to "family", "Today's tasks" to "routine", "Medicines" to "medicine", "I'm feeling lonely" to "mood", "Bihu" to "festivals")
            expected.forEach { (label, topic) ->
                val memory = ChatMemory()
                engine.reply(words.text(label), facts, memory, sunday)
                assertEquals("$language / $label", topic, memory.topic)
            }
        }
    }

    @Test fun nativePreferencesAndNegationSurviveChangingLanguages() {
        val phrases = listOf(Triple("as", "মই সংগীত ভাল পাওঁ", "মই সংগীত ভাল নাপাওঁ"), Triple("bn", "আমি গান পছন্দ করি", "আমি গান পছন্দ করি না"), Triple("ne", "मलाई संगीत मन पर्छ", "मलाई संगीत मन पर्दैन"))
        for ((language, positive, negative) in phrases) {
            val m = ChatMemory()
            OfflineChatEngine(language).reply(positive, facts, m, sunday)
            assertTrue("$language positive", "music" in m.likes)
            assertTrue(OfflineChatEngine("en").reply("my interests", facts, m, sunday).contains("music"))
            OfflineChatEngine(language).reply(negative, facts, m, sunday)
            assertFalse("$language negative", "music" in m.likes)
            assertTrue("music" in m.avoided)
        }
    }

    @Test fun visitQuestionKeepsNameAndHandlesNativeNoWithoutInventingVisit() {
        val family = facts.copy(family = listOf(FamilyMember(7, "Ananya", "granddaughter", notes = "Visits every Sunday")))
        for ((language, no) in listOf("as" to "নহয়", "bn" to "না", "ne" to "होइन")) {
            val m = ChatMemory()
            val engine = OfflineChatEngine(language)
            val opening = engine.opening(family, m, sunday)
            assertTrue(opening.contains("Ananya")); assertTrue(opening.contains("?"))
            assertFalse(opening.contains("visits"))
            engine.reply(no, family, m, sunday)
            assertEquals("", m.pendingVisit)
        }
    }

    @Test fun medicalDataIsPreservedAndUncheckedIsNotReportedAsMissed() {
        val f = facts.copy(medicines = listOf(MedicineItem(name = "Example 25", dosage = "1 tablet", scheduledTime = "09:00 AM")))
        for (language in locales) {
            val words = ChatLanguage(language)
            val result = OfflineChatEngine(language).reply(words.text("Medicines"), f, ChatMemory(), sunday)
            assertTrue(result.contains("Example 25 — 09:00 AM, 1 tablet"))
            assertTrue(result.contains(words.text("not marked taken today")))
            assertTrue(result.contains(words.text("medicine_note")))
        }
    }

    @Test fun namedFamilyNotesAreNeverMachineTranslated() {
        val note = "Favourite recipe: rice & tea 50%"
        val family = facts.copy(family = listOf(FamilyMember(8, "Ananya", "granddaughter", notes = note)))
        for (language in locales) {
            val response = OfflineChatEngine(language).reply("Ananya", family, ChatMemory(), sunday)
            assertTrue(response.contains(note))
        }
    }
}
