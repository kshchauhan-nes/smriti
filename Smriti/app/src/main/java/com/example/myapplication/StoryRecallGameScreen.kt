package com.example.myapplication

import com.example.myapplication.ui.UiText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.example.myapplication.chat.AiGames

import com.example.myapplication.speech.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.GameSessionRecord
import com.example.myapplication.data.SmritiRepository
import kotlinx.coroutines.launch

data class StoryQuestion(
    val questionText: String,
    val options: List<String>,
    val correctIndex: Int
)

data class StoryItem(
    val title: String,
    val emoji: String,
    val storyParagraphs: List<String>,
    val questions: List<StoryQuestion>
)

@Composable
fun StoryRecallGameScreen(
    repository: SmritiRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val regional = remember(repository.patientKey) { AiGames.source(context, repository.patientKey) }
    var currentStory by remember(regional) { mutableStateOf(regional.item) }
    var generating by remember { mutableStateOf(false) }
    var aiStatus by remember { mutableStateOf("") }

    var gameState by remember { mutableStateOf("read") } // "read", "quiz", "result"
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }
    var quizStartTime by remember { mutableLongStateOf(0L) }
    var totalResponseTime by remember { mutableLongStateOf(0L) }
    var calculatedScore by remember { mutableIntStateOf(0) }

    NarrationScreen(when (gameState) {
        "read" -> "Story recall. ${currentStory.title}.\n" + currentStory.storyParagraphs.joinToString("\n") + "\nWhen ready, start the quiz."
        "quiz" -> currentStory.questions[currentQuestionIndex].let { q ->
            "Question ${currentQuestionIndex + 1} of ${currentStory.questions.size}. ${q.questionText}. " +
                q.options.mapIndexed { index, option -> "Option ${index + 1}: $option" + if (selectedOptionIndex == index) ", selected" else "" }.joinToString(". ") +
                ". Select an answer and continue."
        }
        else -> "Story recall complete. $correctCount of ${currentStory.questions.size} correct. Score $calculatedScore percent. Play another story or return to Games."
    }, assamese = when (gameState) {
 "read" -> "কাহিনীৰ স্মৃতিৰ খেল। " + currentStory.title + "।\n" + currentStory.storyParagraphs.joinToString("\n") + "\nসাজু হ’লে প্ৰশ্ন আৰম্ভ কৰক।"
 "quiz" -> currentStory.questions[currentQuestionIndex].let { q -> "প্ৰশ্ন ${currentQuestionIndex + 1}।\n${q.questionText}\n" + q.options.mapIndexed { index, option -> "${index + 1}। $option" }.joinToString("\n") + "\nউত্তৰ বাছি আগবাঢ়ক।" }
 else -> "খেল সম্পূৰ্ণ। ${currentStory.questions.size} টাৰ ভিতৰত $correctCount টা শুদ্ধ। নম্বৰ $calculatedScore শতাংশ।"
})
    val background = Brush.verticalGradient(
        listOf(
            Color(0xFFFFFEFA),
            Color(0xFFEDE7F6),
            Color(0xFFE8F5E9)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onBack) {
            UiText("← Back to Games", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(6.dp))

        UiText(
            text = "Story & Memory 📖",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        UiText(
            text = "Listen, remember events, and recall gently",
            fontSize = 15.sp,
            color = Color(0xFF66758A)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // -------------------------------------------------------------
        // PHASE 1: READING / STORY PHASE
        // -------------------------------------------------------------
        if (gameState == "read") {
            TextButton(onClick = { uriHandler.openUri(regional.sourceUrl) }) { Text(regional.source, fontSize = 12.sp) }
            Button(enabled = !generating, onClick = {
                generating = true; aiStatus = ""
                coroutineScope.launch {
                    try {
                        val generated = AiGames.story(context, repository, regional)
                        currentStory = generated.first
                        aiStatus = when(context.resources.configuration.locales[0].language) {
                            "as" -> "এআইয়ে সৰল কৰা কাহিনী"
                            "bn" -> "এআই দিয়ে সরল করা গল্প"
                            "ne" -> "एआईले सरल बनाएको कथा"
                            else -> "AI adaptation of the cited tale excerpt"
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { aiStatus = when(context.resources.configuration.locales[0].language) {
                        "as" -> "এতিয়া এআই উপলব্ধ নহয়। তলৰ কাহিনীটো খেলিব পাৰে।"
                        "bn" -> "এখন এআই উপলব্ধ নয়। নিচের গল্পটি খেলতে পারেন।"
                        "ne" -> "अहिले एआई उपलब्ध छैन। तलको कथा खेल्न सक्नुहुन्छ।"
                        else -> "AI unavailable. You can play the saved story below."
                    } }
                    finally { generating = false }
                }
            }) { Text(when(context.resources.configuration.locales[0].language) {
                "as" -> "এআইৰ সৈতে কাহিনী সৰল কৰক"
                "bn" -> "এআই দিয়ে গল্প সহজ করুন"
                "ne" -> "एआईसँग कथा सरल बनाउनुहोस्"
                else -> "Adapt this story with AI"
            }) }
            if (generating) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (aiStatus.isNotBlank()) Text(aiStatus, fontSize = 13.sp)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(54.dp),
                            shape = CircleShape,
                            color = Color(0xFFFFF3E0)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                UiText(text = currentStory.emoji, fontSize = 28.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        UiText(
                            text = currentStory.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF233449)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    currentStory.storyParagraphs.forEach { para ->
                        UiText(
                            text = para,
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                            color = Color(0xFF37474F),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                enabled = !generating,
                onClick = {
                    quizStartTime = System.currentTimeMillis()
                    currentQuestionIndex = 0
                    correctCount = 0
                    selectedOptionIndex = null
                    gameState = "quiz"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6952C5))
            ) {
                UiText(
                    text = "I'm Ready for Questions →",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // -------------------------------------------------------------
        // PHASE 2: QUIZ / QUESTIONS PHASE
        // -------------------------------------------------------------
        else if (gameState == "quiz") {
            val q = currentStory.questions[currentQuestionIndex]

            UiText(
                text = "Question ${currentQuestionIndex + 1} of ${currentStory.questions.size}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6952C5)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                UiText(
                    text = q.questionText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF233449),
                    modifier = Modifier.padding(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Shuffled or indexed options
            q.options.forEachIndexed { optIdx, optText ->
                val isSelected = selectedOptionIndex == optIdx
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { selectedOptionIndex = optIdx },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFD1C4E9) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF6952C5) else Color(0xFFEDE7F6)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                UiText(
                                    text = ('A'.code + optIdx).toChar().toString(),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF6952C5)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        UiText(
                            text = optText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF233449)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (selectedOptionIndex == q.correctIndex) {
                        correctCount++
                    }

                    if (currentQuestionIndex + 1 < currentStory.questions.size) {
                        currentQuestionIndex++
                        selectedOptionIndex = null
                    } else {
                        // Finished all questions
                        totalResponseTime = System.currentTimeMillis() - quizStartTime
                        val totalQ = currentStory.questions.size
                        calculatedScore = ((correctCount.toFloat() / totalQ.toFloat()) * 100).toInt()

                        coroutineScope.launch {
                            repository.saveGameSession(
                                GameSessionRecord(
                                    gameType = "Story Recall",
                                    score = calculatedScore,
                                    level = 1,
                                    responseTimeMs = totalResponseTime,
                                    correctAnswers = correctCount,
                                    totalObjects = totalQ
                                )
                            )
                        }
                        gameState = "result"
                    }
                },
                enabled = selectedOptionIndex != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6952C5))
            ) {
                UiText(
                    text = if (currentQuestionIndex + 1 < currentStory.questions.size) "Next Question →" else "See Results 🌟",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // -------------------------------------------------------------
        // PHASE 3: RESULT PHASE
        // -------------------------------------------------------------
        else if (gameState == "result") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UiText(
                        text = if (calculatedScore >= 70) "🎉 Wonderful Recall!" else "😊 Good Effort, Aita!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF233449),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    UiText(
                        text = "Story: ${currentStory.title}",
                        fontSize = 15.sp,
                        color = Color(0xFF66758A)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        modifier = Modifier.size(100.dp),
                        shape = CircleShape,
                        color = if (calculatedScore >= 70) Color(0xFFE8F5E9) else Color(0xFFEDE7F6)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            UiText(
                                text = "$calculatedScore%",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (calculatedScore >= 70) Color(0xFF2E7D32) else Color(0xFF6952C5)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    UiText(
                        text = "Correct Answers: $correctCount / ${currentStory.questions.size}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF37474F)
                    )

                    UiText(
                        text = String.format("Total Recall Time: %.1fs", totalResponseTime / 1000.0),
                        fontSize = 14.sp,
                        color = Color(0xFF78909C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    currentStory = regional.item
                    aiStatus = ""
                    gameState = "read"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6952C5))
            ) {
                UiText("Play Another Story 📖", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                UiText("Back to Games Menu", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
