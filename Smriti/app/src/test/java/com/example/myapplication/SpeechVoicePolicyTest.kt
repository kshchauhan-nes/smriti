package com.example.myapplication

import com.example.myapplication.speech.SpeechVoicePolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SpeechVoicePolicyTest {
    @Test fun englishVoiceChoiceIsStableAndPrefersAmericanSlt() {
        val rh = "com.github.olga_yakovleva.rhvoice.android"
        assertTrue(SpeechVoicePolicy.rank(rh, Locale.US, "SLT") > SpeechVoicePolicy.rank("com.reecedunn.espeak", Locale.US, "en-us"))
        assertTrue(SpeechVoicePolicy.rank(rh, Locale.US, "SLT") > SpeechVoicePolicy.rank(rh, Locale.UK, "Scottish"))
        assertTrue(SpeechVoicePolicy.rank("espeak", Locale.US, "en-us") > SpeechVoicePolicy.rank("espeak", Locale.US, "en-us+croak"))
    }
    @Test fun actualScriptDeterminesVoiceInsteadOfMisreadingOldChatHistory() {
        assertEquals("en", SpeechVoicePolicy.languageFor("Your medicines are ready.", "as"))
        assertEquals("ne", SpeechVoicePolicy.languageFor("नमस्कार", "en"))
        assertEquals("as", SpeechVoicePolicy.languageFor("নমস্কাৰ", "bn"))
        assertEquals("bn", SpeechVoicePolicy.languageFor("নমস্কার", "en"))
    }
    @Test fun isoThreeLetterVoicesAreRecognized() {
        assertTrue(SpeechVoicePolicy.sameLanguage(Locale("eng"), "en"))
        assertFalse(SpeechVoicePolicy.sameLanguage(Locale.FRENCH, "en"))
    }
}
