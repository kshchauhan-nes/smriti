package com.example.myapplication.chat

import android.content.Context
import com.example.myapplication.data.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Local-only storage, separated by the repository's patient identity. */
class OfflineChatStore(context: Context, patientKey: String) {
    private val key = MessageDigest.getInstance("SHA-256").digest(patientKey.toByteArray()).joinToString("") { "%02x".format(it) }
    private val prefs = context.applicationContext.getSharedPreferences("offline_conversation_$key", Context.MODE_PRIVATE)
    fun loadMemory(): ChatMemory {
        val json = runCatching { JSONObject(prefs.getString("memory", "{}")!!) }.getOrDefault(JSONObject())
        fun set(name: String): MutableSet<String> = linkedSetOf<String>().apply {
            val array = json.optJSONArray(name) ?: JSONArray()
            for (i in 0 until array.length()) add(array.getString(i))
        }
        return ChatMemory(set("likes"), set("avoided"), set("asked"), json.optString("pendingVisit"), json.optString("pendingName"), json.optString("topic"), json.optInt("turn"))
    }
    fun loadHistory(): List<ChatMessage> = runCatching {
        val array = JSONArray(prefs.getString("messages", "[]"))
        (0 until array.length()).map { index -> val m = array.getJSONObject(index)
            ChatMessage(sender = m.getString("sender"), text = m.getString("text"), timestamp = m.getLong("time")) }
    }.getOrDefault(emptyList())
    fun save(memory: ChatMemory, messages: List<ChatMessage>) {
        val json = JSONObject().put("likes", JSONArray(memory.likes.toList())).put("avoided", JSONArray(memory.avoided.toList()))
            .put("asked", JSONArray(memory.asked.toList())).put("pendingVisit", memory.pendingVisit).put("pendingName", memory.pendingName)
            .put("topic", memory.topic).put("turn", memory.turn)
        val history = JSONArray()
        messages.takeLast(120).forEach { history.put(JSONObject().put("sender", it.sender).put("text", it.text).put("time", it.timestamp)) }
        check(prefs.edit().putString("memory", json.toString()).putString("messages", history.toString()).commit()) { "Could not save conversation on this device" }
    }
    fun clear() { check(prefs.edit().clear().commit()) { "Could not clear conversation" } }
}
