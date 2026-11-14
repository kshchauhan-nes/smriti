package com.example.myapplication

import com.example.myapplication.ui.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ai.SmritiAIEngine
import com.example.myapplication.data.ChatMessage
import com.example.myapplication.data.SmritiRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SmritiAssistantScreen(
    repository: SmritiRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val aiEngine = remember { SmritiAIEngine(repository) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun loadChat() {
        coroutineScope.launch {
            val history = repository.getChatHistory()
            messages = if (history.isEmpty()) {
                val welcome = ChatMessage(
                    sender = "smriti",
                    text = "Namaskar Aita! 🌸 I am Smriti, your caring companion. How are you feeling today? You can ask me about your family, medicines, routine, or talk about pleasant memories.",
                    topicCategory = "welcome"
                )
                repository.saveChatMessage(welcome)
                listOf(welcome)
            } else {
                history
            }
        }
    }

    LaunchedEffect(Unit) {
        loadChat()
    }

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isThinking) return
        val userMsg = ChatMessage(
            sender = "user",
            text = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        inputText = ""
        isThinking = true
        coroutineScope.launch {
            repository.saveChatMessage(userMsg)
            messages = messages + userMsg
            // Generate AI response
            val aiResponse = aiEngine.generateResponse(userMsg.text)
            messages = messages + aiResponse
            isThinking = false
        }
    }

    val suggestionChips = listOf(
        "Did I take my medicine? 💊",
        "Tell me about my family 👨‍👩‍👧",
        "What is my routine today? 📅",
        "I'm feeling peaceful 😊",
        "I feel a little lonely 🌸",
        "Talk about Assam tea gardens 🍵",
        "Tell me about Bihu celebrations 🪕"
    )

    val background = Brush.verticalGradient(
        listOf(
            Color(0xFFFFF8E7),
            Color(0xFFE8F5E9),
            Color(0xFFEDE7F6)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top App Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.9f),
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    UiText("← Back", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = Color(0xFFE8F5E9)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        UiText("🌸", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    UiText(
                        text = "Smriti AI Companion",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF263238)
                    )
                    UiText(
                        text = "● Offline AI • Always listening",
                        fontSize = 12.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }

        // Chat Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(message = msg)
            }

            if (isThinking) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UiText("🌸", fontSize = 16.sp)
                                UiText("Smriti is thinking…", fontSize = 14.sp, color = Color(0xFF607D8B))
                            }
                        }
                    }
                }
            }
        }

        // Quick Suggestion Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(suggestionChips) { chip ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.clickable { sendMessage(chip) }
                ) {
                    UiText(
                        text = chip,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF37474F)
                    )
                }
            }
        }

        // Input Field
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { UiText("Talk to Smriti…", fontSize = 15.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { sendMessage(inputText) },
                    enabled = inputText.isNotBlank() && !isThinking,
                    shape = CircleShape,
                    modifier = Modifier.size(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF78)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    UiText("↑", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == "user"
    val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = Color(0xFFE8F5E9)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UiText("🌸", fontSize = 18.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF5E35B1) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = message.text,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = if (isUser) Color.White else Color(0xFF263238)
                )
                Spacer(modifier = Modifier.height(4.dp))
                UiText(
                    text = timeFormatter.format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = if (isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF90A4AE),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
