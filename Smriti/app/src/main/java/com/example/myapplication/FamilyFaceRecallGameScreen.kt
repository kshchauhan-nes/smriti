package com.example.myapplication

import com.example.myapplication.ui.UiText

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
import com.example.myapplication.data.FamilyMember
import com.example.myapplication.data.GameSessionRecord
import com.example.myapplication.data.SmritiRepository
import kotlinx.coroutines.launch

@Composable
fun FamilyFaceRecallGameScreen(
    repository: SmritiRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var familyList by remember { mutableStateOf<List<FamilyMember>>(emptyList()) }
    var currentMemberIndex by remember { mutableIntStateOf(0) }
    var randomizedMembers by remember { mutableStateOf<List<FamilyMember>>(emptyList()) }

    var gameState by remember { mutableStateOf("loading") } // "loading", "question", "feedback", "result"
    var selectedOption by remember { mutableStateOf<FamilyMember?>(null) }
    var isCorrectSelection by remember { mutableStateOf(false) }
    var correctAnswersCount by remember { mutableIntStateOf(0) }
    var startTime by remember { mutableLongStateOf(0L) }
    var totalTime by remember { mutableLongStateOf(0L) }
    var finalScore by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        familyList = repository.getAllFamilyMembers()
        if (familyList.isNotEmpty()) {
            randomizedMembers = familyList.filter { it.photoUri.isNotBlank() }.ifEmpty { familyList }.shuffled().take(4)
            currentMemberIndex = 0
            startTime = System.currentTimeMillis()
            gameState = "question"
        } else {
            gameState = "empty"
        }
    }

    val currentTarget = randomizedMembers.getOrNull(currentMemberIndex)

    // Generate 4 multiple-choice options (1 correct + 3 random distractors)
    val currentOptions = remember(currentMemberIndex, randomizedMembers) {
        if (currentTarget != null && familyList.isNotEmpty()) {
            val distractors = familyList.filter { it.id != currentTarget.id }.shuffled().take(3)
            (listOf(currentTarget) + distractors).shuffled()
        } else {
            emptyList()
        }
    }

    NarrationScreen(when (gameState) {
        "loading" -> "Loading family photographs."
        "empty" -> "No family profiles yet. Ask your caregiver to add photographs and details in the caregiver dashboard."
        "question" -> "Family face recall. Look at the photograph and choose the person. Choices: " + currentOptions.joinToString { it.name } + ". Tap a name to answer."
        "feedback" -> (if (isCorrectSelection) "Correct. " else "Let's remember this person. ") + "${currentTarget?.name.orEmpty()}, ${currentTarget?.relation.orEmpty()}. ${currentTarget?.notes.orEmpty()}. Continue to the next person."
        else -> "Family recall complete. $correctAnswersCount of ${randomizedMembers.size} correct. Score $finalScore percent. Play again or return to Games."
    }, assamese = when (gameState) {
        "loading" -> "পৰিয়ালৰ ছবি খোলা হৈছে।"
        "empty" -> "পৰিয়ালৰ ছবি নাই। যত্ন লওঁতাক ছবি আৰু তথ্য যোগ কৰিবলৈ কওক।"
        "question" -> "ছবিখন চাওক। এইজন কোন? " + currentOptions.joinToString("। ") { it.name }
        "feedback" -> (if (isCorrectSelection) "শুদ্ধ উত্তৰ। " else "এইজনক মনত ৰাখোঁ আহক। ") + "${currentTarget?.name.orEmpty()}। ${currentTarget?.relation.orEmpty()}। ${currentTarget?.notes.orEmpty()}।"
        else -> "খেল সম্পূৰ্ণ। ${randomizedMembers.size} টাৰ ভিতৰত $correctAnswersCount টা শুদ্ধ। নম্বৰ $finalScore শতাংশ।"
    })
    val background = Brush.verticalGradient(
        listOf(
            Color(0xFFFFFEFA),
            Color(0xFFFFEBEE),
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
            text = "Family Face Recall 👨‍👩‍👧",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        UiText(
            text = "Recognize your beloved family & friends",
            fontSize = 15.sp,
            color = Color(0xFF66758A)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (gameState == "loading") {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE57373))
            }
        } else if (gameState == "empty") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UiText(text = "👨‍👩‍👧", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    UiText(text = "No family members added yet.", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    UiText(
                        text = "Caregivers can add relatives in the Caregiver Dashboard.",
                        color = Color(0xFF66758A),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        }
        // -------------------------------------------------------------
        // PHASE 1: QUESTION PHASE
        // -------------------------------------------------------------
        else if (gameState == "question" && currentTarget != null) {
            UiText(
                text = "Person ${currentMemberIndex + 1} of ${randomizedMembers.size}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD32F2F)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Family Member Face Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(210.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        FamilyPhoto(currentTarget.photoUri, currentTarget.avatarEmoji, "Person to identify", Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    UiText(
                        text = "Do you remember who this is?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF233449),
                        textAlign = TextAlign.Center
                    )

                    UiText(
                        text = "Take your time, Aita 🌼",
                        fontSize = 14.sp,
                        color = Color(0xFF78909C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4 Options
            currentOptions.forEach { optMember ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable {
                            selectedOption = optMember
                            isCorrectSelection = (optMember.id == currentTarget.id)
                            if (isCorrectSelection) {
                                correctAnswersCount++
                            }
                            gameState = "feedback"
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UiText(text = if (currentTarget.photoUri.isBlank()) optMember.avatarEmoji else "👤", fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = optMember.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF233449)
                            )
                            Text(
                                text = optMember.relation,
                                fontSize = 14.sp,
                                color = Color(0xFF66758A)
                            )
                        }
                    }
                }
            }
        }
        // -------------------------------------------------------------
        // PHASE 2: IMMEDIATE REASSURING FEEDBACK
        // -------------------------------------------------------------
        else if (gameState == "feedback" && currentTarget != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrectSelection) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UiText(
                        text = if (isCorrectSelection) "🌟 Yes! Exactly right!" else "🌼 That's okay, Aita!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrectSelection) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        modifier = Modifier.size(150.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White
                    ) {
                        FamilyPhoto(currentTarget.photoUri, currentTarget.avatarEmoji, "Photo of ${currentTarget.name}", Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    UiText(
                        text = "${currentTarget.name} • ${currentTarget.relation}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF233449)
                    )

                    if (currentTarget.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        UiText(
                            text = "\"${currentTarget.notes}\"",
                            fontSize = 14.sp,
                            color = Color(0xFF455A64),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (currentMemberIndex + 1 < randomizedMembers.size) {
                        currentMemberIndex++
                        selectedOption = null
                        gameState = "question"
                    } else {
                        // Finished
                        totalTime = System.currentTimeMillis() - startTime
                        val totalPeople = randomizedMembers.size
                        finalScore = ((correctAnswersCount.toFloat() / totalPeople.toFloat()) * 100).toInt()

                        coroutineScope.launch {
                            repository.saveGameSession(
                                GameSessionRecord(
                                    gameType = "Family Face Recall",
                                    score = finalScore,
                                    level = 1,
                                    responseTimeMs = totalTime,
                                    correctAnswers = correctAnswersCount,
                                    totalObjects = totalPeople
                                )
                            )
                        }
                        gameState = "result"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6952C5))
            ) {
                UiText(
                    text = if (currentMemberIndex + 1 < randomizedMembers.size) "Next Person →" else "See Summary 🌟",
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
                        text = if (finalScore >= 75) "❤️ You know your family so well!" else "🌻 Beautiful connection with your loved ones",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF233449),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Surface(
                        modifier = Modifier.size(95.dp),
                        shape = CircleShape,
                        color = Color(0xFFFFEBEE)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            UiText(
                                text = "$finalScore%",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    UiText(
                        text = "Identified: $correctAnswersCount of ${randomizedMembers.size} relatives",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF37474F)
                    )

                    UiText(
                        text = String.format("Average response: %.1fs", (totalTime / 1000.0) / randomizedMembers.size.coerceAtLeast(1)),
                        fontSize = 13.sp,
                        color = Color(0xFF78909C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    randomizedMembers = familyList.filter { it.photoUri.isNotBlank() }.ifEmpty { familyList }.shuffled().take(4)
                    currentMemberIndex = 0
                    correctAnswersCount = 0
                    startTime = System.currentTimeMillis()
                    gameState = "question"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                UiText("Play Again 👨‍👩‍👧", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
