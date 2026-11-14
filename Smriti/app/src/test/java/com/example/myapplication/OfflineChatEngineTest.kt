package com.example.myapplication

import com.example.myapplication.chat.*
import com.example.myapplication.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class OfflineChatEngineTest {
    private val engine = OfflineChatEngine()
    private val family = FamilyMember(7, "Ananya", "granddaughter", notes = "Visits every Sunday")
    private val facts = ChatFacts(PatientProfile(), listOf(family), emptyList(), emptyList())
    private fun day(date: Int) = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, date, 15, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun sundayVisitIsAQuestionNotAnAssumption() {
        val reply = engine.opening(facts, ChatMemory(), day(6))
        assertTrue(reply.contains("Has Ananya visited today yet?"))
    }
    @Test fun mondayAsksAboutYesterdayAndTuesdayDoesNotAsk() {
        assertTrue(engine.opening(facts, ChatMemory(), day(7)).contains("Did Ananya visit yesterday?"))
        assertFalse(engine.opening(facts, ChatMemory(), day(8)).contains("visit"))
    }
    @Test fun noRepeatedQuestionWithinWeekButNextWeekWorks() {
        val m = ChatMemory()
        assertNotNull(engine.visitQuestion(facts, m, day(6)))
        assertNull(engine.visitQuestion(facts, m, day(7)))
        assertNotNull(engine.visitQuestion(facts, m, day(13)))
    }
    @Test fun canceledVisitsAreNotSuggested() {
        assertNull(engine.visitQuestion(facts.copy(family = listOf(family.copy(notes = "No longer visits every Sunday"))), ChatMemory(), day(6)))
    }
    @Test fun noVisitAndUnknownAreHandledWithoutInventingVisit() {
        for (answer in listOf("no", "not yet", "not sure", "she hasn't visited")) {
            val m = ChatMemory(); engine.opening(facts, m, day(6))
            assertTrue(engine.reply(answer, facts, m, day(6)).contains("leave that question"))
            assertEquals("", m.pendingVisit)
        }
    }
    @Test fun unrelatedReplyClearsPendingVisit() {
        val m = ChatMemory(); engine.opening(facts, m, day(6))
        engine.reply("show my tasks", facts, m, day(6))
        assertFalse(engine.reply("yes", facts, m, day(6)).contains("visit"))
    }
    @Test fun preferencesRespectNegationAndArePatientSpecific() {
        val one = ChatMemory(); val two = ChatMemory()
        engine.reply("I enjoy music", facts, one, day(6))
        assertTrue("music" in one.likes); assertTrue(two.likes.isEmpty())
        engine.reply("I don't like music", facts, one, day(6))
        assertFalse("music" in one.likes); assertTrue("music" in one.avoided)
    }
    @Test fun mentionsAloneDoNotBecomePreferences() {
        val m = ChatMemory(); engine.reply("my daughter plays music", facts, m, day(6))
        assertTrue(m.likes.isEmpty())
    }
    @Test fun medicineStatusIsNotAClaimOfMissedDose() {
        val f = facts.copy(medicines = listOf(MedicineItem(name = "Example", dosage = "as prescribed", scheduledTime = "08:00 AM")))
        val answer = engine.reply("medicines", f, ChatMemory(), day(6))
        assertTrue(answer.contains("not marked taken")); assertTrue(answer.contains("doesn't prove"))
    }
    @Test fun noSubstringMedicineMatchInOrdinaryWords() {
        val answer = engine.reply("sometimes", facts, ChatMemory(), day(6))
        assertFalse(answer.startsWith("It is"))
    }
    @Test fun explicitDoseChangeDoesNotReceiveRoutineConversation() {
        assertTrue(engine.reply("Should I double dose?", facts, ChatMemory(), day(6)).contains("can't advise"))
    }
}
