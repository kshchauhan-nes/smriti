package com.example.myapplication.chat

import com.example.myapplication.data.ChatMessage
import org.json.JSONObject

/** One preference-aware topic selector, shared by suggestions and incomplete-reply recovery. */
object OfflineConversationSupport {
    private fun emotionalRequest(input: String): Boolean {
        val q = input.replace('’', '\'').lowercase().trim().trimEnd('?', '.', '!')
        return q in setOf("lonely", "sad", "bored", "upset", "talk to me", "keep me company") ||
            Regex("^(?:(?:i'm|i am|im)(?: feeling)?|i feel|feeling) (?:so |very |a bit )?(?:lonely|alone|sad|bored|upset|down)\\b").containsMatchIn(q)
    }

    fun companionship(input: String, history: List<ChatMessage>): Boolean =
        emotionalRequest(input) || (input.trim().trimEnd('?', '.', '!').lowercase() in setOf("tell me more", "more", "go on") &&
            history.lastOrNull { it.sender == "user" }?.let { emotionalRequest(it.text) } == true)

    fun repair(snapshot: JSONObject, input: String, history: List<ChatMessage>, answer: String): String? {
        if (snapshot.optString("replyLanguage", "English").lowercase() !in setOf("en", "english") || !companionship(input, history)) return null
        val q = answer.replace('’', '\'').lowercase().trim()
        val claimsIdentity = Regex("\\bi(?:'m| am) (?:just )?an? ").containsMatchIn(q) &&
            !Regex("\\bi(?:'m| am) (?:an? )?(?:chatbot|assistant|ai|virtual assistant)\\b").containsMatchIn(q)
        val phrases = q.split(Regex("[^a-z]+" )).filter { it.isNotBlank() }.windowed(3).map { it.joinToString(" ") }
        val repeatedPhrase = phrases.groupingBy { it }.eachCount().any { it.value > 1 }
        val repeats = history.lastOrNull { it.sender != "user" }?.text?.trim()?.equals(answer.trim(), true) == true
        if (answer.length >= 30 && !q.startsWith("patient ") && !claimsIdentity && !repeats && !repeatedPhrase &&
            !Regex("\\b(is|are|the|and|or|to)$").containsMatchIn(q)) return null
        return "I'm here with you. ${suggestion(snapshot).substringBefore(".")}."
    }

    fun topic(snapshot: JSONObject, input: String): String? {
        val q = input.lowercase().trim().trimEnd('?', '.', '!')
        if (!(q.contains("suggest") && (q.contains("topic") || q.contains("talk"))) &&
            q !in setOf("what should we talk about", "what can we talk about", "suggest a topic", "something to talk about")) return null
        return suggestion(snapshot)
    }

    fun suggestion(snapshot: JSONObject): String {
        fun strings(key: String): List<String> {
            val array = snapshot.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotBlank() }
        }
        val avoided = strings("avoidedTopics").map { it.lowercase() }
        val candidates = strings("likedTopics") + snapshot.optString("selectedInterests").split(',', ';', '|') +
            listOf(snapshot.optString("region").takeIf { it.isNotBlank() }?.let { "culture in $it" }.orEmpty(), "music", "food", "a happy memory")
        val topic = candidates.map { it.trim() }.firstOrNull { candidate ->
            candidate.isNotBlank() && avoided.none { candidate.lowercase().contains(it) || it.contains(candidate.lowercase()) }
        } ?: return "I'm here to listen. What would you like to talk about?"
        return "We could talk about $topic. What do you enjoy about it?"
    }

}
