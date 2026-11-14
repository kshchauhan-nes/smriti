package com.example.myapplication.chat

import android.content.Context
import com.example.myapplication.data.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

/** Small sourced reference notes supplement model knowledge; never substitute for patient records. */
object OfflineKnowledge {
    fun culturalAnswer(snapshot: JSONObject, history: List<ChatMessage>, input: String): String? {
        if (snapshot.optString("replyLanguage", "English").lowercase() !in listOf("en", "english")) return null
        val q = input.lowercase()
        if (Regex("\\b(my|our|we|i|feel|love|like|remember)\\b").containsMatchIn(q) || isStory(input)) return null
        val recent = history.takeLast(4).joinToString(" ") { it.text.lowercase() }
        val named = Regex("\\b(bihu|rongali|bohag|bhogali|magh|kongali|kati)\\b").containsMatchIn(q)
        val followUp = "bihu" in recent && Regex("\\b(food|eat|dance|music|when|month|celebrat|more)\\w*\\b").containsMatchIn(q)
        if (!named && !followUp) return null
        return when {
            followUp && q.trim().trimEnd('?', '.', '!') in setOf("tell me more", "more", "go on") -> "Rongali Bihu in April includes music and dancing. Bhogali Bihu in January is associated with harvest feasts, while Kongali Bihu in October includes lamps and prayers for crops."
            Regex("\\b(food|eat|pitha)\\w*\\b").containsMatchIn(q) -> "Pitha is a traditional festive food enjoyed during Bihu, especially the feasting of Bhogali Bihu."
            Regex("\\b(dance|music)\\b").containsMatchIn(q) -> "Rongali Bihu is celebrated with lively music and Bihu dancing in Assam."
            Regex("\\b(rongali|bohag)\\b").containsMatchIn(q) -> "Rongali, also called Bohag Bihu, marks the Assamese New Year in April. Music and dancing are part of its celebrations."
            Regex("\\b(bhogali|magh)\\b").containsMatchIn(q) -> "Bhogali, also called Magh Bihu, celebrates the end of the harvest in January. Families enjoy feasts and traditional foods such as pitha."
            Regex("\\b(kongali|kati)\\b").containsMatchIn(q) -> "Kongali, also called Kati Bihu, is observed in October. People light lamps and pray for a good harvest."
            else -> "Bihu refers to three seasonal festivals in Assam. Rongali in April marks the New Year, Bhogali in January celebrates the harvest, and Kongali in October includes lamps and prayers for crops."
        }
    }
    fun isStory(input: String) = Regex("^(?:folklore|story|a story|folk tale)[.!?]?$|\\b(?:tell|share|read|another)\\b.*\\b(?:folklore|folk tale|story|tale)\\b", RegexOption.IGNORE_CASE).containsMatchIn(input.trim())
    fun story(context: Context, snapshot: JSONObject, history: List<ChatMessage>, input: String): String? {
        if (!isStory(input)) return null
        val region = if ((input + history.takeLast(4).joinToString(" ") { it.text }).contains("bihu", true)) "Assam" else snapshot.optString("region", "Assam")
        val rows = JSONArray(context.assets.open("ai/folklore.json").bufferedReader().use { it.readText() })
        val row = (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.optString("region").equals(region, true) } ?: return null
        return "${row.optString("title")}: ${row.optString("story") }"
    }
    fun notes(context: Context, snapshot: JSONObject, history: List<ChatMessage>, input: String): String {
        val question = input.lowercase()
        val recent = history.takeLast(4).joinToString(" ") { it.text.lowercase() }
        val bihu = "bihu" in question || ("bihu" in recent && (isStory(input) || Regex("\\b(food|dance|festival|when|celebrat|more)\\w*\\b").containsMatchIn(question)))
        if (isStory(input)) {
            val region = if (bihu) "Assam" else snapshot.optString("region", "Assam")
            val rows = JSONArray(context.assets.open("ai/folklore.json").bufferedReader().use { it.readText() })
            val row = (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.optString("region").equals(region, true) }
            if (row != null) return "Traditional story title: ${row.optString("title")}. Story events: ${row.optString("story")} This is a folk tale, not a patient's memory."
        }
        val shortGeneral = Regex("^(?:what is|tell me (?:about|abt)|explain) bihu[?.!]*$", RegexOption.IGNORE_CASE).matches(input.trim())
        if (bihu && shortGeneral) return "Bihu is a group of three seasonal festivals in Assam, observed at different times of the year. Celebrations include music, dancing and harvest feasts. Source: Assam Tourism."
        return if (bihu) "Bihu is a group of Assamese seasonal festivals. Rongali/Bohag Bihu in April marks the Assamese New Year and is associated with music and dancing. Bhogali/Magh Bihu in January celebrates the harvest with feasting. Kongali/Kati Bihu in October includes lighting lamps and prayers for crops. Pitha is a traditional festive food. Source: Assam Tourism, https://assamtourism.gov.in/bihu-festival.php. These are general cultural facts, not proof of the patient's personal experiences." else ""
    }
}
