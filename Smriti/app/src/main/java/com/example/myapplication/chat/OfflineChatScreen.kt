package com.example.myapplication.chat

import com.example.myapplication.ui.UiText

import com.example.myapplication.speech.*
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import com.example.myapplication.ui.SmritiPurple
import com.example.myapplication.ui.SmritiInk
import com.example.myapplication.ui.SmritiPaper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OfflineChatScreen(repository: SmritiRepository, onBack: () -> Unit, onMedicines: () -> Unit, onRoutines: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(repository) { OfflineChatStore(context, repository.patientKey) }
    val language = androidx.compose.ui.platform.LocalConfiguration.current.let {
        if (android.os.Build.VERSION.SDK_INT >= 24) it.locales[0].language else @Suppress("DEPRECATION") it.locale.language
    }
    val engine = remember(language) { OfflineChatEngine(language) }
    val words = remember(language) { ChatLanguage(language) }
    var previousLanguage by remember(store) { mutableStateOf<String?>(null) }
    var memory by remember(store) { mutableStateOf(ChatMemory()) }
    var messages by remember(store) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var partialReply by remember { mutableStateOf("") }
    var requestSequence by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var aiInstalled by remember { mutableStateOf(HybridAi.installed(context)) }
    val modelSetup by ModelSetup.state.collectAsState()
    LaunchedEffect(modelSetup.phase) { aiInstalled = HybridAi.installed(context) }
    var aiMode by remember(repository) { mutableStateOf(aiModeLabel(HybridAi.currentMode(context, repository.patientKey))) }
    fun modeText(): String = when(language) {
        "as" -> "কথোপকথনৰ ব্যৱস্থা: $aiMode"
        "bn" -> "কথোপকথনের ব্যবস্থা: $aiMode"
        "ne" -> "कुराकानी प्रणाली: $aiMode"
        else -> "Conversation mode: $aiMode"
    }
    var showMemory by remember { mutableStateOf(false) }
    var memoryVersion by remember { mutableIntStateOf(0) }
    val scroll = rememberLazyListState()
    suspend fun facts() = ChatFacts(repository.getPatientProfile(), repository.getAllFamilyMembers(), repository.getAllMedicines(), repository.getTodayRoutines())
    LaunchedEffect(store, language) {
        busy = true
        try {
            memory = withContext(Dispatchers.IO) { store.loadMemory() }
            messages = withContext(Dispatchers.IO) { store.loadHistory() }
            if ((previousLanguage != null && previousLanguage != language) || messages.isEmpty() || System.currentTimeMillis() - messages.last().timestamp > 4 * 60 * 60 * 1000) {
                messages = (messages + ChatMessage(sender = "smriti", text = engine.opening(facts(), memory, System.currentTimeMillis()))).takeLast(120)
                withContext(Dispatchers.IO) { store.save(memory, messages) }
            }
        } catch (e: Exception) { error = "Couldn't load chat: ${e.localizedMessage}" }
        finally { previousLanguage = language; busy = false }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) scroll.animateScrollToItem(messages.lastIndex) }
    fun send(text: String) {
        if (busy || text.isBlank()) return
        busy = true; input = ""; error = null; partialReply = ""
        val thisRequest = ++requestSequence
        val historyBeforeSend = messages
        messages = (messages + ChatMessage(sender = "user", text = text.take(1000))).takeLast(120)
        scope.launch {
            try {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { store.save(memory, messages) }
                val safetyOrPreference = Regex("double dose|extra dose|overdose|stop medicine|change dose|chest pain|can.t breathe|kill myself|forget everything|clear memory", RegexOption.IGNORE_CASE).containsMatchIn(text)
                val answer = if (safetyOrPreference || (!aiInstalled && com.example.myapplication.BuildConfig.AI_ENDPOINT.isBlank())) {
                    aiMode = "Offline"; engine.reply(text.take(1000), facts(), memory, System.currentTimeMillis())
                } else {
                    engine.learnPreferences(text.take(1000), memory)
                    val snapshot = AiPatientContext.snapshot(context, repository, memory, language)
                    aiMode = "Thinking…"
                    val generated = HybridAi.reply(context, repository.patientKey, snapshot, historyBeforeSend, text,
                        onPartial = { value -> scope.launch { if (busy && requestSequence == thisRequest) { partialReply = value; aiMode = "LFM · offline AI" } } })
                    aiMode = aiModeLabel(generated.mode); error = generated.notice; generated.text
                }
                messages = (messages + ChatMessage(sender = "smriti", text = answer)).takeLast(120)
                withContext(Dispatchers.IO) { store.save(memory, messages) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { input = text; error = "Couldn't finish this message: ${e.localizedMessage}" }
            finally {
                busy = false
                if (partialReply.isNotBlank() && messages.lastOrNull()?.sender == "user") {
                    messages = (messages + ChatMessage(sender = "smriti", text = partialReply)).takeLast(120)
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { store.save(memory, messages) }
                }
                partialReply = ""
            }
        }
    }
    NarrationScreen(messages.takeLast(6).joinToString("\n") { it.text }.ifBlank { words.text("How are you feeling today?") }, language)
    Column(Modifier.fillMaxSize().background(SmritiPaper).navigationBarsPadding().imePadding().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { UiText(stringResource(R.string.lang_32), color = SmritiPurple) }
            UiText("Smriti", modifier = Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.Bold, color = SmritiInk)
            TextButton(enabled = !busy, onClick = { showMemory = true }) { UiText(stringResource(R.string.lang_33), color = SmritiPurple) }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFE3DBF6)), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EEFF))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { UiText("♡", fontSize = 33.sp, color = SmritiPurple) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UiText(stringResource(R.string.lang_34), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = SmritiInk)
                    UiText(stringResource(R.string.lang_35), fontSize = 13.sp, color = Color(0xFF66758A))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE6F3EA)) {
                        UiText(stringResource(R.string.lang_36), fontSize = 11.sp, color = Color(0xFF416C50), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        Text(modeText(), color = SmritiPurple, fontSize = 12.sp)
        LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(messages) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.sender == "user") Arrangement.End else Arrangement.Start) {
                    Surface(shape = if (message.sender == "user") RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp) else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp), border = BorderStroke(1.dp, if (message.sender == "user") Color(0xFFE1D7F7) else Color(0xFFE1E7EC)), color = if (message.sender == "user") Color(0xFFF0EBFF) else Color.White, modifier = Modifier.widthIn(max = 330.dp)) {
                        Column(Modifier.padding(17.dp)) {
                            UiText(if (message.sender == "user") stringResource(R.string.lang_42) else "Smriti", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SmritiPurple)
                            Spacer(Modifier.height(5.dp))
                            Text(message.text, fontSize = 17.sp, lineHeight = 25.sp, color = Color(0xFF233449))
                        }
                    }
                }
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (partialReply.isNotBlank()) Text(partialReply, modifier = Modifier.padding(12.dp), color = SmritiInk)
        }
        error?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Family", "Today's tasks", "Medicines", "I'm feeling lonely", "Suggest a topic", "Bihu", "My interests").forEach { label ->
                SuggestionChip(enabled = !busy, onClick = { send(words.text(label)) }, shape = RoundedCornerShape(18.dp), colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFF3EEFF), labelColor = SmritiPurple), label = { UiText(words.text(label)) })
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE1E7EC))) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it.take(1000) }, placeholder = { UiText(stringResource(R.string.lang_37)) }, modifier = Modifier.weight(1f), maxLines = 3, enabled = !busy, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6952C5), unfocusedBorderColor = Color.Transparent, focusedContainerColor = Color(0xFFFAF8FF), unfocusedContainerColor = Color(0xFFFAF8FF)))
            Button(enabled = !busy && input.isNotBlank(), onClick = { send(input) }, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6952C5))) { UiText(stringResource(R.string.lang_38), fontWeight = FontWeight.Bold) }
        }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onMedicines) { UiText(stringResource(R.string.lang_39), color = Color(0xFF468D79)) }
            TextButton(onClick = onRoutines) { UiText(stringResource(R.string.lang_40), color = SmritiPurple) }
        }
    }
    if (showMemory) key(memoryVersion) {
        AlertDialog(onDismissRequest = { showMemory = false }, title = { NarratedTitle(stringResource(R.string.lang_41), "Conversation memory. Preferences are stored only on this device. Liked topics: ${memory.likes.joinToString()}. Avoided topics: ${memory.avoided.joinToString()}. Tap a preference to remove it. Clear conversation and memory removes saved messages and preferences. Done returns to chat.") },
            text = { Column {
                UiText("Saved only on this device for this patient. Preferences come from explicit likes and dislikes. The latest 120 messages are kept.")
                (memory.likes.map { it to "Enjoys" } + memory.avoided.map { it to "Avoid" }).forEach { (topic, label) ->
                    TextButton(onClick = {
                        memory.likes.remove(topic); memory.avoided.remove(topic); memoryVersion++
                        scope.launch { withContext(Dispatchers.IO) { store.save(memory, messages) } }
                    }) { UiText("$label: $topic • Remove") }
                }
                if (memory.likes.isEmpty() && memory.avoided.isEmpty()) UiText("No preferences saved. Try: I enjoy music.")
            } },
            confirmButton = { TextButton(onClick = { showMemory = false }) { UiText(stringResource(R.string.lang_10)) } },
            dismissButton = { TextButton(onClick = {
                scope.launch {
                    try { withContext(Dispatchers.IO) { store.clear() }; memory = ChatMemory(); messages = emptyList(); showMemory = false }
                    catch (e: Exception) { error = e.localizedMessage }
                }
            }) { UiText("Clear conversation & memory") } })
    }
}

private fun aiModeLabel(mode: HybridAi.AiMode): String = when(mode) { HybridAi.AiMode.PATIENT_RECORDS -> "Patient records · offline"; HybridAi.AiMode.ONLINE_GEMINI -> "Gemini · online AI"; HybridAi.AiMode.OFFLINE_LFM -> "LFM · offline AI"; HybridAi.AiMode.OFFLINE_REFERENCE -> "Saved reference · offline"; HybridAi.AiMode.OFFLINE_SUPPORT -> "Support · offline"; HybridAi.AiMode.OFFLINE_UNAVAILABLE -> "Model unavailable" }
