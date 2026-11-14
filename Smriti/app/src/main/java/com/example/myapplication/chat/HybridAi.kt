package com.example.myapplication.chat

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.myapplication.BuildConfig as AppBuildConfig
import com.example.myapplication.cloud.CloudSession
import com.example.myapplication.cloud.result
import com.example.myapplication.data.ChatMessage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Model weights are shared; conversations and their KV state are never shared between patients. */
object HybridAi {
    // Signed-in users with connectivity try Gemini; failures fall back to local inference.
    private const val onlineEnabled = true
    private val mutex = Mutex()
    private var engineHandle = 0L
    const val modelUrl = "https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/resolve/main/LFM2.5-350M-Q4_K_M.gguf"
    const val modelBytes = 229312224L
    const val modelSha256 = "7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4"
    fun modelFile(context: Context) = File(context.noBackupFilesDir, "ai/lfm2.5-350m-q4.gguf")
    fun installed(context: Context) = modelFile(context).length() == modelBytes

    suspend fun download(context: Context, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val destination = modelFile(context)
            if (installed(context)) return@withLock
            destination.parentFile!!.mkdirs()
            val temporary = File(destination.parentFile, "model.download")
            val connection = URL(modelUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000; connection.readTimeout = 60_000
            try {
                check(connection.responseCode == 200) { "Model download unavailable. Try again when connected." }
                connection.inputStream.use { input -> temporary.outputStream().use { output ->
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(128 * 1024); var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        output.write(buffer, 0, count); digest.update(buffer, 0, count); total += count
                        check(total <= modelBytes) { "Unexpected model download." }
                        ModelSetup.received(total)
                        progress((total * 100 / modelBytes).toInt())
                    }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == modelSha256) { "Model verification failed. Please retry." }
                } }
                check(temporary.length() == modelBytes) { "Incomplete model download. Please retry." }
                check(temporary.renameTo(destination)) { "Could not save the offline model." }
            } finally { connection.disconnect(); temporary.delete() }
        }
    }

    enum class AiMode {
        PATIENT_RECORDS,
        ONLINE_GEMINI,
        OFFLINE_LFM,
        OFFLINE_REFERENCE,
        OFFLINE_SUPPORT,
        OFFLINE_UNAVAILABLE
    }

    data class Reply(
        val text: String,
        val mode: AiMode,
        val notice: String? = null
    )
    fun online(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    fun currentMode(
        context: Context,
        patientKey: String
    ): AiMode {
        val user = CloudSession.auth.currentUser

        return when {
            onlineEnabled && AppBuildConfig.AI_ENDPOINT.isNotBlank() &&
            (user?.uid == patientKey || (patientKey == "local-patient" && CloudSession.isLocal(context))) &&
            online(context) -> AiMode.ONLINE_GEMINI

            installed(context) -> AiMode.OFFLINE_LFM

            else -> AiMode.OFFLINE_UNAVAILABLE
        }
    }

    suspend fun reply(
        context: Context,
        patientKey: String,
        snapshot: JSONObject,
        history: List<com.example.myapplication.data.ChatMessage>,
        input: String,
        purpose: String = "chat",
        onlineEndpoint: String = AppBuildConfig.AI_ENDPOINT,
        onPartial: (String) -> Unit = {}
    ): Reply {

        var onlineFailure: String? = null

        if (purpose == "chat") {
            PatientRecordAnswers.answer(snapshot, input, history)?.let {
                return Reply(
                    it,
                    AiMode.PATIENT_RECORDS
                )
            }
        }

// ONLINE / OFFLINE ROUTING STARTS HERE
        val user = CloudSession.auth.currentUser

        // ONLINE MODE GEMINI
        if(
            onlineEnabled && onlineEndpoint.isNotBlank() &&
            (user?.uid == patientKey || (patientKey == "local-patient" && CloudSession.isLocal(context))) &&
            online(context)
        ){
            try {
                val signedInForPatient = user?.uid == patientKey
                val token = if (signedInForPatient) withTimeout(8_000L) { user!!.getIdToken(false).result().token } ?: error("Sign in again") else null
                val cloudReply: String =
                    withContext(Dispatchers.IO) {
                        val connection =
                            URL(onlineEndpoint)
                                .openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.doOutput = true

                        connection.connectTimeout = 5000
                        connection.readTimeout = if (purpose == "chat") 16_000 else 35_000

                        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                        else {
                            val preferences = context.getSharedPreferences("ai_guest", 0)
                            val id = preferences.getString("id", null) ?: java.util.UUID.randomUUID().toString().also {
                                preferences.edit().putString("id", it).apply()
                            }
                            connection.setRequestProperty("X-Smriti-Guest-ID", id)
                        }

                        connection.setRequestProperty(
                            "Content-Type",
                            "application/json"
                        )

                        try {
                            val cloudContext = if (purpose != "chat") snapshot else
                                PatientContextSelector.selectForCloud(
                                    snapshot = snapshot,
                                    input = input,
                                    history = history
                                )
                            val body =
                                JSONObject()
                                    .put("message", input)
                                    .put("purpose", purpose)
                                    .put("context",
                                        cloudContext)
                                    .put(
                                        "history",
                                        JSONArray(
                                                    history
                                                        .takeLast(6)
                                                        .map{

                                                            JSONObject()
                                                                .put(
                                                                    "role",
                                                                    if(it.sender == "user")
                                                                                "user"
                                                                            else
                                                                                "assistant"
                                                                )
                                                                .put(
                                                                    "content",
                                                                    it.text.take(700)
                                                                )
                                                        }
                                        )
                                    )
                            connection.outputStream.use {
                                it.write(
                                    body.toString()
                                        .toByteArray((Charsets.UTF_8))
                                )
                            }

                            check(
                                connection.responseCode == 200
                            ) {
                                "Online AI unavailable"
                            }
                            val response = JSONObject(
                                connection.inputStream
                                    .bufferedReader()
                                    .use {
                                        it.readText()
                                    }
                            )
                            check(response.optString("provider") == "gemini") { "Unexpected online provider" }
                            response.getString("reply").trim().also { check(it.isNotBlank()) { "Empty online response" } }
                        } finally {
                            connection.disconnect()
                        }
                    }

                coroutineContext.ensureActive()

                check(
                    if (signedInForPatient) CloudSession.auth.currentUser?.uid == patientKey
                    else CloudSession.isLocal(context)
                ) {
                    "Patient changed"
                }

                return Reply(
                    text = cloudReply,
                    mode = AiMode.ONLINE_GEMINI
                )
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                coroutineContext.ensureActive()
                onlineFailure = "Gemini sign-in timed out; using offline AI."
            } catch (
                e: kotlinx.coroutines.CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                onlineFailure = "Gemini unavailable; using offline AI."
            }

        }

        if (
            purpose == "chat" &&
            snapshot.optString("replyLanguage").lowercase() in listOf("en", "english")
        ) {
            val question = input
                .trim()
                .lowercase()
                .trimEnd('?', '.', '!')

            // GREETING
            if (
                question in setOf(
                    "hi",
                    "hello",
                    "hey"
                )
            ) {
                return Reply(
                    "Hi! How can I help?",
                    AiMode.PATIENT_RECORDS, onlineFailure
                )
            }

            OfflineConversationSupport.topic(snapshot, input)?.let {
                return Reply(it, AiMode.PATIENT_RECORDS, onlineFailure)
            }
        }

        if (purpose == "chat") OfflineKnowledge.story(context, snapshot, history, input)?.let {
            return Reply(it, AiMode.OFFLINE_REFERENCE, onlineFailure)
        }
        val culturalReference = if (purpose == "chat") OfflineKnowledge.culturalAnswer(snapshot, history, input) else null
        if (culturalReference != null) return Reply(culturalReference, AiMode.OFFLINE_REFERENCE, onlineFailure)
        // LFM 350M is not a creative-writing model. The story screen retains its saved regional tale.
        check(purpose != "story") { "Online story AI unavailable. Use the saved regional story." }

        //OFFlINE MODE LFM

        if (installed(context)) {
            val result =
                local(
                    context = context,
                    snapshot = snapshot,
                    history = history,
                    input = input,
                    purpose = purpose,
                    useReferences = false,
                    onPartial = if (OfflineConversationSupport.companionship(input, history)) ({ _: String -> }) else onPartial
                )
            OfflineConversationSupport.repair(snapshot, input, history, result)?.let {
                return Reply(it, AiMode.OFFLINE_SUPPORT, onlineFailure)
            }
            return Reply(
                text = result,
                mode = AiMode.OFFLINE_LFM,
                notice = onlineFailure
            )
        }
        return Reply(
            text =
                "The Offline AI model has not downloaded yet. " +
                "Connect to the internet once and download the offline model to use Smriti without Internet.",
            mode = AiMode.OFFLINE_UNAVAILABLE
        )
    }

    suspend fun local(context: Context, snapshot: JSONObject,
        history: List<com.example.myapplication.data.ChatMessage>, input: String, purpose: String = "chat", useReferences: Boolean = true, onPartial: (String) -> Unit = {}): String =
        withContext(Dispatchers.IO) { mutex.withLock {
            if (purpose == "chat") OfflineKnowledge.story(context, snapshot, history, input)?.let { return@withLock it }
            if (purpose == "chat" && useReferences) OfflineKnowledge.culturalAnswer(snapshot, history, input)?.let { return@withLock it }
            check(installed(context)) { "Download the offline AI model to use AI without internet." }
            if (engineHandle == 0L) engineHandle = LfmRuntime.load(modelFile(context).absolutePath.toByteArray(Charsets.UTF_8))
            val runtime = engineHandle
            val storyRequest = purpose == "chat" && OfflineKnowledge.isStory(input)
            val reference = if (purpose == "chat") OfflineKnowledge.notes(context, snapshot, history, input) else ""
            val culturalQuestion = reference.isNotBlank() && !Regex("\\b(my|our|we|did i|was i)\\b", RegexOption.IGNORE_CASE).containsMatchIn(input)
            val instruction = if (culturalQuestion) {
                "You are Smriti, a friendly companion. Answer in ${snapshot.optString("replyLanguage", "English")}. " +
                    if (storyRequest) "Tell the supplied folk tale excerpt in three or four simple sentences, including its title. Keep its events unchanged. Do not just repeat the title." else
                        "Explain the user's cultural question using the reference notes in TWO informative short sentences. Give an explanation, not just the topic name. Do not discuss unrelated patient tasks."
            } else if (purpose == "chat")
                "You are Smriti. Answer in ${snapshot.optString("replyLanguage", "English")} in two short, warm sentences. " +
                "Use saved facts only for personal details; never invent memories, diagnose, or change medicines. " +
                "Respect avoided topics. Address the user as you; avoid repeated greetings." +
                if (OfflineConversationSupport.companionship(input, history)) " Offer company and a liked topic." else ""
            else AiPatientContext.instructions + "\nFollow the requested output format exactly."
            val compact = if (culturalQuestion) JSONObject().put("replyLanguage", snapshot.optString("replyLanguage"))
                .put("region", snapshot.optString("region")) else if (purpose != "chat") JSONObject(snapshot.toString()) else PatientContextSelector.select(snapshot, input, history)
            val normalizedInput = if (input.trim().lowercase() in setOf("folklore", "story", "a story", "folk tale", "tell me a story"))
                "Tell me the folk tale in the reference notes in three short sentences. Describe what happens in the tale."
                else input.replace(Regex("\\babt\\b", RegexOption.IGNORE_CASE), "about")
            val system = instruction + "\nSaved facts about the person you are speaking to:\n$compact" +
                if (reference.isNotBlank()) "\nReference notes:\n$reference" else ""
            val content = normalizedInput.take(1000)
            val sentenceLimit = if (storyRequest) 4 else 2
            val characterLimit = if (storyRequest) 700 else 420
            val maxTokens = if (storyRequest || purpose != "chat") 220 else 96
            val output = ByteArrayOutputStream()
            var latestVisible = ""
            try {

                withTimeout(
                    if (purpose == "story")
                        120_000L
                    else
                        25_000L
                ) {

                    LfmRuntime.begin(
                        runtime,
                        LfmRuntime.prompt(
                            system,
                            content, history
                        ).toByteArray(Charsets.UTF_8),

                        Runtime.getRuntime()
                            .availableProcessors()
                            .coerceIn(1, 4),

                        maxTokens
                    )

                    while (true) {

                        coroutineContext.ensureActive()

                        if (LfmRuntime.prefill(runtime)) {
                            break
                        }
                    }

                    for (index in 0 until maxTokens) {

                        coroutineContext.ensureActive()

                        val piece =
                            LfmRuntime.next(runtime)
                                ?: break

                        output.write(piece)

                        val visible =
                            BriefReply.visible(
                                output.toString("UTF-8")
                            )
                                .trimEnd('\uFFFD')
                                .trim()

                        if (visible.isNotBlank()) {

                            latestVisible =
                                if (purpose == "chat")
                                    BriefReply.concise(visible, sentenceLimit, characterLimit)
                                else
                                    visible

                            onPartial(latestVisible)
                        }

                        if (
                            purpose == "chat" &&
                            (
                                    latestVisible.length < visible.length ||
                                            visible.length >= characterLimit
                                    )
                        ) {
                            break
                        }
                    }
                }

            }catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // An outer cancellation must propagate instead of becoming a model error.
                coroutineContext.ensureActive()
                if (output.size() == 0) throw IllegalStateException("Offline AI took too long. Please try a shorter question.", e)
            } finally { LfmRuntime.reset(runtime) }
            coroutineContext.ensureActive()
            val raw =
                output.toString("UTF-8")
                    .trimEnd('\uFFFD')
                    .trim()

            val finalAnswer =
                if (purpose == "chat")
                    BriefReply.concise(raw, sentenceLimit, characterLimit)
                else
                    BriefReply.visible(raw)

            val answer =
                if (finalAnswer.isNotBlank()) {
                    finalAnswer
                } else {
                    latestVisible
                }

            check(answer.isNotBlank()) {
                "The local model returned no answer. Please try a shorter question."
            }

            answer
        } }
}
