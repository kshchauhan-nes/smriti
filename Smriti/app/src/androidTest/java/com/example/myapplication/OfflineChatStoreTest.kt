package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.chat.*
import com.example.myapplication.data.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OfflineChatStoreTest {
    @Test fun memorySurvivesReopenIsPrivateAndCanBeDeleted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val account = "test-${UUID.randomUUID()}"
        val first = OfflineChatStore(context, account)
        val other = OfflineChatStore(context, "$account-other")
        try {
            val memory = ChatMemory(likes = linkedSetOf("music"), asked = linkedSetOf("7:2026-09-06"))
            first.save(memory, listOf(ChatMessage(sender = "user", text = "I enjoy music")))
            val reopened = OfflineChatStore(context, account)
            assertTrue("music" in reopened.loadMemory().likes)
            assertTrue("7:2026-09-06" in reopened.loadMemory().asked)
            assertEquals("I enjoy music", reopened.loadHistory().single().text)
            assertTrue(other.loadHistory().isEmpty()); assertTrue(other.loadMemory().likes.isEmpty())
            reopened.clear()
            assertTrue(first.loadHistory().isEmpty()); assertTrue(first.loadMemory().likes.isEmpty())
        } finally { first.clear(); other.clear() }
    }
}
