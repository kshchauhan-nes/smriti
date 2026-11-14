package com.example.myapplication.chat

import android.content.Context
import com.example.myapplication.StoryItem
import com.example.myapplication.StoryQuestion
import com.example.myapplication.data.SmritiRepository
import org.json.JSONArray
import org.json.JSONObject

object AiGames {
    data class RegionalStory(val item: StoryItem, val source: String, val sourceUrl: String)
    fun source(context: Context, patientKey: String): RegionalStory {
        val region = context.getSharedPreferences("personalisation_$patientKey",0).getString("region","Assam")
        val rows = JSONArray(context.assets.open("ai/folklore.json").bufferedReader().use { it.readText() })
        val row = (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.getString("region") == region } ?: rows.getJSONObject(0)
        return RegionalStory(StoryItem(row.getString("title"), "📖", listOf(row.getString("story")), questions(row.getJSONArray("questions"))), row.getString("source"), row.getString("sourceUrl"))
    }
    private fun questions(rows: JSONArray): List<StoryQuestion> = (0 until rows.length()).map { i ->
        val q=rows.getJSONObject(i); val options=q.getJSONArray("options")
        StoryQuestion(q.getString("question"),(0 until options.length()).map { options.getString(it) },q.getInt("correctIndex"))
    }
    suspend fun story(context: Context, repository: SmritiRepository, source: RegionalStory): Pair<StoryItem,String> {
        val language = repository.getPatientProfile().preferredLanguage.ifBlank { "en" }
        val recent = repository.getAllGameSessions().filter { it.gameType.contains("Story",true) }.sortedByDescending { it.timestamp }.take(3)
        val count = if (recent.isNotEmpty() && recent.map { it.score }.average() < 60) 2 else 3
        val data = JSONObject().put("replyLanguage",language).put("sourceTitle",source.item.title)
            .put("verifiedSourceSummary",source.item.storyParagraphs.joinToString(" ")).put("attribution",source.source)
        val prompt = "Retell only this supplied traditional tale excerpt in 40-70 words in replyLanguage. No invented facts. " +
            "Create $count easy recall questions. Return JSON only: {\"title\":\"...\",\"story\":\"...\",\"questions\":[{\"question\":\"...\",\"options\":[\"...\",\"...\",\"...\"],\"correctIndex\":0}]}. " +
            "Every correct answer must appear verbatim in the story. Use different answer positions."
        val reply=HybridAi.reply(context,repository.patientKey,data,emptyList(),prompt,"story")
        val result=JSONObject(reply.text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val text=result.getString("story"); val title=result.getString("title"); val questions=questions(result.getJSONArray("questions"))
        require(text.length in 80..1600 && title.length in 1..150 && questions.size == count)
        require(questions.all { q -> q.questionText.length in 5..250 && q.options.size == 3 && q.options.distinct().size == 3 && q.correctIndex in 0..2 && q.options.all { it.length in 1..150 } && text.contains(q.options[q.correctIndex],ignoreCase=true) })
        return StoryItem(title,"📖",listOf(text),questions) to reply.mode.name
    }
    suspend fun extraSeconds(context: Context, repository: SmritiRepository, level: Int, score: Int, responseMs: Long): Int {
        val data=JSONObject().put("replyLanguage","English").put("level",level).put("scorePercent",score).put("responseMs",responseMs)
        val reply=HybridAi.reply(context,repository.patientKey,data,emptyList(),
            "Choose extra viewing time for the next memory-game round. Poor recall needs more time. Never change the object count. Return only JSON {\"extraSeconds\":3}. Allowed values: 0, 3, 6.","difficulty")
        val extra=JSONObject(reply.text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).getInt("extraSeconds")
        require(extra in listOf(0,3,6))
        return if (score < 60) extra.coerceAtLeast(3) else extra
    }
}
