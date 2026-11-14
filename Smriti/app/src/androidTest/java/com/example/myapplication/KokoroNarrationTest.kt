package com.example.myapplication

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KokoroNarrationTest {
    @Test fun generatesEnglishSampleOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { it.serviceInfo.packageName }.first { it == "com.k2fsa.sherpa.onnx.tts.engine" }
        val init = CountDownLatch(1)
        var status = -1
        val tts = TextToSpeech(context, { status = it; init.countDown() }, name)
        try {
            assertTrue("Engine startup timed out", init.await(60, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, status)
            val voice = tts.voices.filter { !it.isNetworkConnectionRequired && (it.locale.language == "en" || it.locale.language == "eng") }
                .sortedBy { it.name }.firstOrNull()
            assertNotNull("No offline English voice", voice)
            assertEquals(TextToSpeech.SUCCESS, tts.setVoice(voice))
            tts.setSpeechRate(0.95f)
            val done = CountDownLatch(1)
            var error = false
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { done.countDown() }
                @Deprecated("Android callback") override fun onError(id: String?) { error = true; done.countDown() }
            })
            val file = File(context.filesDir, "kokoro-preview.wav")
            assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile("Welcome to Smriti. How are you feeling today? Your daily routine is ready. Tap the speaker again whenever you want me to stop.", null, file, "kokoro"))
            assertTrue("Synthesis timed out", done.await(90, TimeUnit.SECONDS))
            assertFalse("Synthesis failed", error)
            assertTrue("No audio generated", file.length() > 1000)
        } finally { tts.shutdown() }
    }
}
