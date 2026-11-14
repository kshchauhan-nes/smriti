package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.myapplication.speech.NarrationText
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OfflineNarrationTest {
    @Test fun embeddedVoicesSynthesizeAudioWithoutInternet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val init = CountDownLatch(1)
        var status = -1
        val tts = TextToSpeech(context, { status = it; init.countDown() }, "com.reecedunn.espeak")
        try {
            assertTrue(init.await(20, TimeUnit.SECONDS)); assertEquals(TextToSpeech.SUCCESS, status)
            val samples = mapOf("en" to "Welcome to Smriti. Your daily routine is ready.", "as" to "নমস্কাৰ। আজি আপোনাৰ কেনে লাগিছে?", "bn" to "নমস্কার। আজ আপনার কেমন লাগছে?", "ne" to "नमस्कार। आज तपाईंलाई कस्तो छ?")
            for ((language, text) in samples) {
                val voice = tts.voices.firstOrNull { it.locale.language == language && !it.isNetworkConnectionRequired }
                assertNotNull("No offline voice for $language: ${tts.voices.map { it.name + ":" + it.locale.language + ":" + it.isNetworkConnectionRequired }}", voice)
                assertEquals(TextToSpeech.SUCCESS, tts.setVoice(voice))
                val done = CountDownLatch(1)
                var error = false
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { done.countDown() }
                    @Deprecated("Android callback") override fun onError(id: String?) { error = true; done.countDown() }
                })
                val file = File(context.cacheDir, "narration-test-$language.wav")
                assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile(text, null, file, language))
                assertTrue("Synthesis timed out: $language", done.await(20, TimeUnit.SECONDS))
                assertFalse("Synthesis error: $language", error)
                assertTrue("No generated audio: $language", file.length() > 100)
                file.delete()
            }
        } finally { tts.shutdown() }
    }
    @Test fun clearerEnglishVoiceWorksOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val init = CountDownLatch(1)
        val tts = TextToSpeech(context, { init.countDown() }, "com.github.olga_yakovleva.rhvoice.android")
        try {
            assertTrue(init.await(20, TimeUnit.SECONDS))
            val voice = tts.voices.firstOrNull { it.name.contains("slt", true) && !it.isNetworkConnectionRequired }
            assertNotNull("SLT voice must be installed", voice)
            assertEquals(TextToSpeech.SUCCESS, tts.setVoice(voice))
            val done = CountDownLatch(1)
            var error = false
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { done.countDown() }
                @Deprecated("Android callback") override fun onError(id: String?) { error = true; done.countDown() }
            })
            val file = File(context.cacheDir, "english-voice-check.wav")
            assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile("Welcome to Smriti. Your medicine is scheduled for nine A M. Tap the speaker again to stop narration.", null, file, "slt"))
            assertTrue(done.await(20, TimeUnit.SECONDS)); assertFalse(error); assertTrue(file.length() > 100)
            file.delete()
        } finally { tts.shutdown() }
    }

}
