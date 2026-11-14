package com.example.myapplication.chat

import com.example.myapplication.data.*
import java.text.SimpleDateFormat
import java.util.*

data class ChatFacts(val profile: PatientProfile, val family: List<FamilyMember>, val medicines: List<MedicineItem>, val routines: List<RoutineItem>)
data class ChatMemory(
    val likes: MutableSet<String> = linkedSetOf(),
    val avoided: MutableSet<String> = linkedSetOf(),
    val asked: MutableSet<String> = linkedSetOf(),
    var pendingVisit: String = "",
    var pendingName: String = "",
    var topic: String = "",
    var turn: Int = 0
)

/** Deterministic, local conversation rules. Never changes a patient's care checklist. */
class OfflineChatEngine(language: String = "en") {
    private val words = ChatLanguage(language)
    private val locale = Locale.forLanguageTag(language)
    private fun t(key: String, vararg args: Any) = words.text(key, *args)
    private val topics = linkedMapOf(
        "music" to listOf("music", "song", "songs", "singing"),
        "gardening" to listOf("gardening", "garden", "flowers", "plants"),
        "food" to listOf("food", "cooking", "recipe", "tea", "pitha"),
        "festivals" to listOf("festival", "festivals", "bihu", "diwali", "eid", "christmas", "puja", "culture", "cultural"),
        "memories" to listOf("memories", "childhood", "school", "hometown"),
        "family" to listOf("family", "granddaughter", "grandson", "daughter", "son")
    )
    private fun has(text: String, words: List<String>) = words.any { Regex("(?<![\\p{L}\\p{M}])${Regex.escape(it)}(?![\\p{L}\\p{M}])").containsMatchIn(text) }
    private fun date(now: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

    fun opening(facts: ChatFacts, memory: ChatMemory, now: Long): String {
        val question = visitQuestion(facts, memory, now)
        return t("hello", facts.profile.patientName.ifBlank { t("friend") }) + (question ?: t("How are you feeling today?"))
    }

    fun learnPreferences(input: String, memory: ChatMemory): String? {
        val text = ChatInput.normalize(input)
        val mentionedTopic = topics.entries.firstOrNull { has(text, it.value) }?.key
        val negative = has(text, listOf("don't like", "do not like", "dislike", "hate", "don't enjoy", "stop talking about", "avoid"))
        val positive = has(text, listOf("i like", "i love", "i enjoy", "my favourite", "my favorite"))
        if (mentionedTopic != null && (negative || positive)) {
            if (negative) { memory.likes.remove(mentionedTopic); memory.avoided.add(mentionedTopic) }
            else { memory.avoided.remove(mentionedTopic); memory.likes.add(mentionedTopic) }
            memory.topic = if (negative) "" else mentionedTopic
            return if (negative) t("avoid", t(mentionedTopic))
            else t("like", t(mentionedTopic), topicQuestion(mentionedTopic, memory.turn))
        }
        return null
    }

    fun reply(input: String, facts: ChatFacts, memory: ChatMemory, now: Long): String {
        val text = ChatInput.normalize(input)
        memory.turn++
        if (has(text, listOf("suicide", "kill myself", "hurt myself", "can't breathe", "chest pain"))) {
            memory.pendingVisit = ""
            return t("Please contact local emergency help now and tell someone nearby or your caregiver. I cannot call for help from this chat.")
        }
        if (has(text, listOf("double dose", "extra dose", "overdose", "stop medicine", "change dose")))
            return t("I can't advise a medicine dose change. Please ask your caregiver, pharmacist or clinician before changing or repeating a dose.")
        if (has(text, listOf("forget everything", "clear memory"))) {
            memory.likes.clear(); memory.avoided.clear(); memory.asked.clear(); memory.pendingVisit = ""; memory.topic = ""
            return t("I've cleared your conversation preferences and follow-up memory. Use Memory → Clear conversation to remove the messages too.")
        }
        learnPreferences(input, memory)?.let { return it }
        val mentionedTopic = topics.entries.firstOrNull { has(text, it.value) }?.key
        if (has(text, listOf("sad", "lonely", "upset", "worried", "anxious", "unhappy", "not happy"))) {
            memory.pendingVisit = ""; memory.topic = "mood"
            return t("sad", facts.profile.caregiverName.ifBlank { t("your caregiver") })
        }
        if (has(text, listOf("happy", "good mood", "excited"))) {
            memory.pendingVisit = ""; memory.topic = "mood"
            return t("What has made today feel good?")
        }
        if (memory.pendingVisit.isNotBlank()) {
            if (has(text, listOf("yes", "visited", "came", "did visit")) && !has(text, listOf("not", "didn't", "never", "hasn't"))) {
                val name = memory.pendingName; memory.pendingVisit = ""; memory.topic = "family"
                return t("visited", name)
            }
            if (has(text, listOf("no", "not yet", "didn't", "hasn't", "don't know", "not sure", "skip", "don't ask"))) {
                memory.pendingVisit = ""
                return t("That's okay. We can leave that question for now. What would you like to talk about?")
            }
            // An unrelated request ends the yes/no question so a later 'yes' cannot answer it accidentally.
            memory.pendingVisit = ""
        }
        if (has(text, listOf("medicine", "medicines", "meds", "tablet", "tablets", "dose")) ||
            facts.medicines.any { it.name.isNotBlank() && text.contains(it.name.lowercase(Locale.ROOT)) }) {
            memory.topic = "medicine"
            if (facts.medicines.isEmpty()) return t("No medicines are listed in this patient's dashboard. Your caregiver can add the prescribed schedule.")
            val selected = facts.medicines.filter { text.contains(it.name.lowercase(Locale.ROOT)) }.ifEmpty { facts.medicines }
            return selected.joinToString("\n") { "${it.name} — ${it.scheduledTime}, ${it.dosage}: ${if (it.isTaken) t("marked taken today") else t("not marked taken today")}." } +
                t("medicine_note")
        }
        if (has(text, listOf("routine", "routines", "task", "tasks", "schedule", "activities", "what should i do"))) {
            memory.topic = "routine"
            return if (facts.routines.isEmpty()) t("No daily activities are listed yet. Your caregiver can add them.")
            else facts.routines.joinToString("\n") { "${it.timeStr} — ${it.title}: ${if (it.isCompleted) t("completed") else t("not marked complete")}." } + t("routine_note")
        }
        val member = facts.family.firstOrNull { it.name.isNotBlank() && has(text, listOf(it.name.lowercase(Locale.ROOT))) }
        if (member != null) {
            memory.topic = "family"
            return t("member", member.name, member.relation) +
                (if (member.notes.isBlank()) "" else t("note", member.notes.take(700))) +
                "\n" + (visitQuestion(facts.copy(family = listOf(member)), memory, now) ?: t("share", member.name))
        }
        if (has(text, listOf("family", "visit", "visits", "who is visiting"))) {
            memory.topic = "family"
            return visitQuestion(facts, memory, now) ?: if (facts.family.isEmpty()) t("Your caregiver hasn't added family details yet. Who would you like to talk about?")
            else t("Your dashboard lists ") + facts.family.joinToString { "${it.name} (${it.relation})" } + t(". Who would you like to talk about?")
        }
        if (has(text, listOf("what do you remember", "my interests", "what do i like")))
            return t("interests", memory.likes.joinToString { t(it) }.ifEmpty { t("none saved yet") }, memory.avoided.joinToString { t(it) }.ifEmpty { t("none") })
        if (has(text, listOf("time", "date", "what day")))
            return t("time", SimpleDateFormat("EEEE, d MMMM, h:mm a", locale).format(Date(now)))
        if (has(text, listOf("hello", "hi", "namaste", "namaskar", "good morning"))) return opening(facts, memory, now)
        if (has(text, listOf("thank you", "thanks"))) return t("You're welcome. Would you like to chat some more or take a break?")
        if (has(text, listOf("bye", "goodnight", "good night"))) return t("Take care. You can come back whenever you would like to chat.")
        if (has(text, listOf("joke", "funny"))) return if (memory.turn % 2 == 0) t("Why did the gardener bring a ladder? To reach new heights with the plants!") else t("What kind of room has no doors? A mushroom!")
        if (has(text, listOf("bihu"))) { memory.topic = "festivals"; return t("Bihu is associated with Assam's seasonal celebrations, including music, dance and shared food. What Bihu tradition do you enjoy, if you celebrate it?") }
        if (mentionedTopic != null) { memory.topic = mentionedTopic; return topicQuestion(mentionedTopic, memory.turn) }
        if (has(text, listOf("suggest", "bored", "talk to me", "something else"))) {
            visitQuestion(facts, memory, now)?.let { return it }
            val choices = memory.likes.filterNot { it in memory.avoided }.ifEmpty { topics.keys.filterNot { it in memory.avoided } }
            if (choices.isEmpty()) return t("What would you like to talk about?")
            memory.topic = choices[memory.turn % choices.size]
            return topicQuestion(memory.topic, memory.turn)
        }
        if (memory.topic in topics.keys) return t("sharing", topicQuestion(memory.topic, memory.turn))
        if (memory.topic == "mood") return t("Thank you for telling me. Would you prefer a quiet chat, a favourite memory, or a break?")
        return t("I'm a simple offline chatbot, so I may not understand every question. Try asking about your family, today's tasks, medicines, mood, music or festivals.")
    }

    private fun topicQuestion(topic: String, turn: Int): String {
        val questions = when (topic) {
            "music" -> listOf(t("Which song do you enjoy listening to?"), t("Does a favourite song remind you of someone?"), t("Do you prefer listening to music or singing along?"))
            "gardening" -> listOf(t("Which flower or plant do you enjoy most?"), t("Have you grown anything you were proud of?"), t("Do you enjoy sitting among plants?"))
            "food" -> listOf(t("What is a favourite dish of yours?"), t("Is there a family recipe you enjoy?"), t("Who do you enjoy sharing a cup of tea with?"))
            "festivals" -> listOf(t("Which festival or tradition would you like to talk about?"), t("What do you enjoy most about that celebration?"), t("Is there a song, food or gathering you associate with it?"))
            "family" -> listOf(t("What do you enjoy doing with your family?"), t("Is there a family memory you'd like to share?"), t("Who would you enjoy spending time with?"))
            else -> listOf(t("Is there a place you remember fondly?"), t("What did you enjoy doing when you were younger?"), t("Would you like to share a favourite memory?"))
        }
        return questions[turn % questions.size]
    }

    fun visitQuestion(facts: ChatFacts, memory: ChatMemory, now: Long): String? {
        if ("family" in memory.avoided) return null
        val days = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        for (member in facts.family) {
            val notes = member.notes.lowercase(Locale.ROOT)
            if (!has(notes, listOf("visit", "visits", "comes", "come")) || has(notes, listOf("no longer", "does not", "doesn't", "never", "used to", "cancelled", "canceled"))) continue
            val match = Regex("\\b(?:every|each|on)\\s+(sunday|monday|tuesday|wednesday|thursday|friday|saturday)s?\\b").find(notes) ?: continue
            val day = days.indexOf(match.groupValues[1]) + 1
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            val elapsed = (calendar.get(Calendar.DAY_OF_WEEK) - day + 7) % 7
            // Ask on the scheduled day or the following day, never about an arbitrary stale week.
            if (elapsed > 1) continue
            calendar.add(Calendar.DAY_OF_YEAR, -elapsed)
            val key = "${member.id}:${date(calendar.timeInMillis)}"
            if (key in memory.asked) continue
            memory.asked.add(key)
            if (memory.asked.size > 200) memory.asked.remove(memory.asked.first())
            memory.pendingVisit = key; memory.pendingName = member.name
            return t(if (elapsed == 0) "visit_today" else "visit_yesterday", member.name,
                t(match.groupValues[1].replaceFirstChar { it.uppercase() }), member.name)
        }
        return null
    }
}
