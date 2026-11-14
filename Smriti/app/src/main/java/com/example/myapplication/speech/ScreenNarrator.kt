package com.example.myapplication.speech

import com.example.myapplication.ui.UiText

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.UUID

data class NarrationPage(val text: String, val language: String = "en")

/** Accept only installed embedded voices. Never permits a network synthesis voice. */
class ScreenNarrator(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val engines = linkedMapOf<String, TextToSpeech>()
    private val initialized = mutableSetOf<String>()
    private var closed = false
    private var generation = 0
    private var currentUtterance = ""
    private data class Request(val engine: TextToSpeech, val voice: android.speech.tts.Voice, val text: String)
    private var queue = emptyList<Request>()
    private var position = 0
    var ready by mutableStateOf(false); private set
    var speaking by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var voiceName by mutableStateOf(""); private set
    var voiceEngine by mutableStateOf(""); private set
    private val pages = linkedMapOf<String, NarrationPage>()
    var page by mutableStateOf<NarrationPage?>(null); private set

    init {
        val intent = android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val packages = app.packageManager.queryIntentServices(intent, 0).map { it.serviceInfo.packageName }.distinct()
        if (packages.isEmpty()) message = "Install an offline speech engine and voice on this device, then reopen Smriti."
        var pending = packages.size
        packages.forEach { name ->
            engines[name] = TextToSpeech(app, { status -> main.post {
                if (!closed) {
                    pending--
                    if (status == TextToSpeech.SUCCESS) {
                        initialized.add(name)
                        engines[name]?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(id: String?) {}
                            override fun onDone(id: String?) { main.post {
                                if (speaking && id == currentUtterance) { position++; speakNext() }
                            } }
                            @Deprecated("Android callback") override fun onError(id: String?) { main.post {
                                if (speaking && id == currentUtterance) {
                                    stop(); message = "The installed voice could not finish speaking. Please try again."
                                }
                            } }
                        })
                    }
                    // Wait for all engines so a fast fallback cannot win over the preferred voice.
                    ready = pending == 0 && initialized.isNotEmpty()
                    if (pending == 0 && initialized.isEmpty()) message = "The offline speech engines could not start. Reopen Smriti after checking the installed voices."
                }
            } }, name)
        }
    }

    fun update(key: String, next: NarrationPage) {
        pages[key] = next
        val active = pages.values.lastOrNull()
        if (active != page) { stop(); page = active }
    }
    fun remove(key: String) {
        pages.remove(key)
        val next = pages.values.lastOrNull()
        if (page != next) { stop(); page = next }
    }
    fun stop() {
        generation++; speaking = false; currentUtterance = ""; queue = emptyList()
        initialized.forEach { engines[it]?.stop() }
    }
    fun close() { closed = true; stop(); engines.values.forEach { it.shutdown() }; engines.clear() }

    fun toggle() {
        if (speaking) { stop(); return }
        val current = page ?: return
        if (!ready) { message = "The offline voices are starting. Please wait a moment and try again."; return }
        val chunks = NarrationText.chunks(current.text, limit = 300)
        val requests = mutableListOf<Request>()
        for (chunk in chunks) {
            val language = SpeechVoicePolicy.languageFor(chunk, current.language)
            val candidates = initialized.flatMap { name ->
                val tts = engines.getValue(name)
                tts.voices.orEmpty().filter { !it.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() &&
                    SpeechVoicePolicy.sameLanguage(it.locale, language)
                }.map { voice -> Triple(tts, voice, name) }
            }
            val selected = candidates.sortedWith(compareByDescending<Triple<TextToSpeech, android.speech.tts.Voice, String>> {
                SpeechVoicePolicy.rank(it.third, it.second.locale, it.second.name)
            }.thenBy { it.second.name }).firstOrNull()
            if (selected == null) {
                message = "No installed offline voice can read this language. No online voice was used."
                return
            }
            if (requests.isEmpty()) voiceEngine = selected.third
            requests.add(Request(selected.first, selected.second, chunk))
        }
        if (requests.isEmpty()) return
        stop(); message = null; queue = requests; position = 0; speaking = true
        voiceName = requests.first().voice.name
        speakNext()
    }

    private fun speakNext() {
        if (!speaking) return
        if (position >= queue.size) { speaking = false; currentUtterance = ""; queue = emptyList(); return }
        val request = queue[position]
        currentUtterance = "$generation:$position"
        request.engine.setSpeechRate(0.95f)
        request.engine.setPitch(1.0f)
        request.engine.setAudioAttributes(android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
        val params = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.85f) }
        if (request.engine.setVoice(request.voice) == TextToSpeech.ERROR ||
            request.engine.speak(request.text, TextToSpeech.QUEUE_FLUSH, params, currentUtterance) == TextToSpeech.ERROR) {
            stop(); message = "The offline voice could not read this screen. Please check its installed voice data."
        }
    }
}

val LocalNarrator = staticCompositionLocalOf<ScreenNarrator?> { null }

@Composable
fun NarrationScreen(text: String, language: String = "", assamese: String? = null) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val selectedLanguage = language.ifBlank { if (android.os.Build.VERSION.SDK_INT >= 24) configuration.locales[0].language else @Suppress("DEPRECATION") configuration.locale.language }
    val narrator = LocalNarrator.current
    val token = remember { UUID.randomUUID().toString() }
    SideEffect { narrator?.update(token, NarrationPage(if (selectedLanguage == "as") assamese ?: text else text, selectedLanguage)) }
    DisposableEffect(narrator, token) { onDispose { narrator?.remove(token) } }
}

@Composable
fun NarrationButton() {
    val narrator = LocalNarrator.current ?: return
    val label = if (narrator.speaking) "Stop narration" else "Read this screen aloud"
    IconButton(onClick = { narrator.toggle() }, enabled = narrator.page != null,
        modifier = Modifier.semantics { contentDescription = label }) {
        UiText(if (narrator.speaking) "■" else "🔊", fontSize = 23.sp)
    }
}

@Composable
fun NarratedTitle(title: String, summary: String) {
    NarrationScreen(summary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        UiText(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        NarrationButton()
    }
}
