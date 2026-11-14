package com.example.myapplication.chat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object ModelSetup {
    data class Status(val phase: String = "checking", val bytes: Long = 0, val error: String? = null)
    private val mutable = MutableStateFlow(Status())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    fun received(bytes: Long) { mutable.value = Status("downloading", bytes) }
    @Synchronized fun start(context: Context) {
        if (job?.isActive == true || mutable.value.phase == "ready") return
        val app = context.applicationContext
        job = scope.launch {
            try {
                if (!HybridAi.installed(app)) {
                    while (!HybridAi.online(app)) {
                        mutable.value = Status("waiting")
                        delay(3000)
                    }
                    mutable.value = Status("downloading")
                    HybridAi.download(app) {}
                }
                mutable.value = Status("verifying", HybridAi.modelBytes)
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                HybridAi.modelFile(app).inputStream().use { stream ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                }
                if (digest.digest().joinToString("") { "%02x".format(it) } != HybridAi.modelSha256) {
                    HybridAi.modelFile(app).delete()
                    error("Model file verification failed. Retry will download a fresh copy.")
                }
                mutable.value = Status("starting", HybridAi.modelBytes)
                val answer = HybridAi.local(app, JSONObject().put("replyLanguage", "English"), emptyList(), "Say hello in one word.")
                check(answer.isNotBlank()) { "Model did not respond" }
                // Retire only the obsolete weights after the new model responds successfully.
                java.io.File(app.noBackupFilesDir, "ai/qwen3.litertlm").delete()
                mutable.value = Status("ready", HybridAi.modelBytes)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = Status("error", mutable.value.bytes, e.localizedMessage) }
        }
    }
}

@Composable
fun ModelDownloadHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val status by ModelSetup.state.collectAsState()
    LaunchedEffect(Unit) { ModelSetup.start(context) }
    val language = context.resources.configuration.locales[0].language
    fun label(en: String, assamese: String, bengali: String, nepali: String) = when(language) {
        "as" -> assamese; "bn" -> bengali; "ne" -> nepali; else -> en
    }
    Column(Modifier.fillMaxSize()) {
        if (status.phase != "ready") {
            Column(Modifier.fillMaxWidth().background(Color(0xFFF0EBFF)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                val text = when(status.phase) {
                    "downloading" -> label("Downloading offline AI", "অফলাইন এআই ডাউনলোড হৈছে", "অফলাইন এআই ডাউনলোড হচ্ছে", "अफलाइन एआई डाउनलोड हुँदैछ") +
                        " · ${status.bytes * 100 / HybridAi.modelBytes}% · ${status.bytes / 1_000_000} / ${HybridAi.modelBytes / 1_000_000} MB"
                    "waiting" -> label("Offline AI · waiting for internet (${HybridAi.modelBytes / 1_000_000} MB)", "অফলাইন এআই · ইণ্টাৰনেটৰ অপেক্ষাত (${HybridAi.modelBytes / 1_000_000} MB)", "অফলাইন এআই · ইন্টারনেটের অপেক্ষায় (${HybridAi.modelBytes / 1_000_000} MB)", "अफलाइन एआई · इन्टरनेटको प्रतीक्षा (${HybridAi.modelBytes / 1_000_000} MB)")
                    "verifying" -> label("Verifying offline AI…", "অফলাইন এআই পৰীক্ষা কৰা হৈছে…", "অফলাইন এআই যাচাই হচ্ছে…", "अफलाइन एआई जाँच हुँदैछ…")
                    "starting" -> label("Starting offline AI…", "অফলাইন এআই আৰম্ভ হৈছে…", "অফলাইন এআই চালু হচ্ছে…", "अफलाइन एआई सुरु हुँदैछ…")
                    "error" -> label("Offline AI setup failed. Tap Retry.", "অফলাইন এআই সাজু নহ’ল। পুনৰ চেষ্টা কৰক।", "অফলাইন এআই প্রস্তুত হয়নি। আবার চেষ্টা করুন।", "अफलाइन एआई तयार भएन। फेरि प्रयास गर्नुहोस्।")
                    else -> label("Checking offline AI…", "অফলাইন এআই পৰীক্ষা হৈছে…", "অফলাইন এআই পরীক্ষা হচ্ছে…", "अफलाइन एआई जाँच हुँदैछ…")
                }
                Text(text, fontSize = 13.sp, color = Color(0xFF5943AB))
                Spacer(Modifier.height(5.dp))
                when(status.phase) {
                    "downloading" -> LinearProgressIndicator(progress = { status.bytes.toFloat() / HybridAi.modelBytes }, modifier = Modifier.fillMaxWidth())
                    "starting", "verifying", "checking" -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    "error" -> TextButton(onClick = { ModelSetup.start(context) }) { Text(label("Retry", "পুনৰ চেষ্টা", "আবার চেষ্টা", "फेरि प्रयास")) }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
