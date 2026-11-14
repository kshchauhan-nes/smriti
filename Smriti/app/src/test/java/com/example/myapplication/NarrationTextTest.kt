package com.example.myapplication

import com.example.myapplication.speech.NarrationText
import org.junit.Assert.*
import org.junit.Test

class NarrationTextTest {
    @Test fun longNarrationNeverDropsTextOrExceedsEngineLimit() {
        val original = (1..1800).joinToString(" ") { "medicine$it" }
        val parts = NarrationText.chunks(original)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 2800 })
        assertEquals(original, parts.joinToString(" "))
    }
    @Test fun nativeScriptsAndDosesArePreservedButDecorativeEmojiAreRemoved() {
        val parts = NarrationText.chunks("💊 ঔষধ 25 mg\nऔषधि 09:00 AM")
        assertEquals(listOf("ঔষধ 25 mg", "औषधि 09:00 AM"), parts)
        assertTrue(NarrationText.chunks("  ").isEmpty())
    }
}
