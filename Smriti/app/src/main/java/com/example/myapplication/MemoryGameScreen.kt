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
import com.example.myapplication.data.GameSessionRecord
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.chat.AiGames
import com.example.myapplication.data.SmritiRepository

@Composable
fun MemoryGameScreen(
    onBack: () -> Unit,
    onSessionCompleted: (GameSessionRecord) -> Unit,
    repository: SmritiRepository? = null
) {

    // -----------------------------
    // GAME STATE
    // -----------------------------

    var difficulty by remember {
        mutableIntStateOf(1)
    }

    var nextDifficulty by remember { mutableIntStateOf(1) }

    var gameState by remember {
        mutableStateOf("show")
    }

    var score by remember {
        mutableIntStateOf(0)
    }

    var questionStartTime by remember {
        mutableLongStateOf(0L)
    }

    var responseTime by remember {
        mutableLongStateOf(0L)
    }

    var lastCompletedLevel by remember {
        mutableIntStateOf(1)
    }

    var correctAnswers by remember {
        mutableIntStateOf(0)
    }

    var incorrectAnswers by remember {
        mutableIntStateOf(0)
    }

    var sessionNumber by remember {
        mutableIntStateOf(1)
    }

    // -----------------------------
    // OBJECT LIBRARY
    // -----------------------------

    val allObjects = listOf(
        "🍵" to "Tea",
        "🌾" to "Rice",
        "🧺" to "Basket",
        "🌺" to "Flower",
        "🥭" to "Mango",
        "🐦" to "Bird",
        "☂️" to "Umbrella",
        "🍎" to "Apple",
        "🌻" to "Sunflower",
        "🐟" to "Fish"
    )

    // -----------------------------
    // DIFFICULTY SETTINGS
    // -----------------------------

    val objectCount = when (difficulty) {

        1 -> 3

        2 -> 4

        else -> 5
    }

    var extraViewingSeconds by remember { mutableIntStateOf(0) }
    val aiContext = LocalContext.current
    LaunchedEffect(gameState) {
        if (gameState == "result" && repository != null) {
            extraViewingSeconds = if (score < 60) 3 else 0
            try { extraViewingSeconds = AiGames.extraSeconds(aiContext, repository, difficulty, score, responseTime) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Keep the performance-based viewing time. */ }
        }
    }
    val displayTime = (when (difficulty) {

        1 -> 7000L

        2 -> 5500L

        else -> 4000L
    }) + extraViewingSeconds * 1000L

    // -----------------------------
    // CURRENT ROUND OBJECTS
    // -----------------------------

    val shownObjects = remember(difficulty, sessionNumber) {

        allObjects
            .shuffled()
            .take(objectCount)
    }

    val distractors = remember(difficulty, sessionNumber) {

        allObjects
            .filter { it !in shownObjects }
            .shuffled()
            .take(2)
    }

    val answerOptions = remember(difficulty, sessionNumber) {

        (shownObjects + distractors)
            .shuffled()
    }

    var userSelections by remember(difficulty, sessionNumber) {

        mutableStateOf(
            emptySet<Pair<String, String>>()
        )
    }

    // -----------------------------
    // Narration follows the visible phase and never discloses hidden objects during recall.
    val narrationSpeaking = LocalNarrator.current?.speaking == true
    NarrationScreen(when (gameState) {
        "show" -> "Picture recall. Remember these $objectCount objects: " + shownObjects.joinToString { it.second } + ". The timer pauses while narration plays."
        "question" -> "Which objects did you see? Select the objects you remember, then tap Check Answer. Choices: " +
            answerOptions.joinToString { it.second + if (it in userSelections) ", selected" else "" }
        else -> "Round complete. $correctAnswers out of $objectCount remembered. $incorrectAnswers extra selections. Score $score percent. Tap Play Next Round to continue."
    }, assamese = when (gameState) {
        "show" -> "ছবিৰ স্মৃতিৰ খেল। এই $objectCount টা বস্তু মনত ৰাখক। " + shownObjects.joinToString("। ") { AssameseObjects.name(it.second) } + "। মাত শুনি থাকোঁতে সময় গণনা ৰৈ থাকিব।"
        "question" -> "কোনবোৰ বস্তু দেখিছিল? মনত থকা বস্তু বাছক। " + answerOptions.joinToString("। ") { AssameseObjects.name(it.second) + if (it in userSelections) " বাছনি কৰা হৈছে" else "" }
        else -> "এই পৰ্ব সম্পূৰ্ণ। $objectCount টাৰ ভিতৰত $correctAnswers টা শুদ্ধ। $incorrectAnswers টা অতিৰিক্ত বাছনি। নম্বৰ $score শতাংশ। পৰৱৰ্তী পৰ্ব খেলিবলৈ টিপক।"
    })

    // MEMORY DISPLAY TIMER
    // -----------------------------

    LaunchedEffect(
        gameState,
        sessionNumber,
        narrationSpeaking
    ) {

        if (gameState == "show" && !narrationSpeaking) {

            delay(displayTime)

            questionStartTime =
                System.currentTimeMillis()

            gameState = "question"
        }
    }

    // -----------------------------
    // BACKGROUND
    // -----------------------------

    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFFEFA),
            Color(0xFFE8F5E9),
            Color(0xFFF5FAFC)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(
                rememberScrollState()
            )
            .padding(22.dp)
    ) {

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        // -----------------------------
        // BACK BUTTON
        // -----------------------------

        TextButton(
            onClick = onBack
        ) {

            UiText(
                text = "← Back",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        // -----------------------------
        // HEADER
        // -----------------------------

        UiText(
            text = "Picture Recall",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            DifficultyBadge(
                difficulty = difficulty
            )

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            UiText(
                text = "Adaptive Memory Training",
                fontSize = 15.sp,
                color = Color(0xFF66758A)
            )
        }

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        // -----------------------------
        // SHOW OBJECTS
        // -----------------------------

        if (gameState == "show") {

            UiText(
                text = "Remember these objects",
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF233449)
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            UiText(
                text =
                    "Take your time and look carefully 🌼",
                fontSize = 16.sp,
                color = Color(0xFF66758A)
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            shownObjects
                .chunked(2)
                .forEach { rowObjects ->

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                16.dp
                            )
                    ) {

                        rowObjects.forEach { item ->

                            ObjectCard(
                                emoji = item.first,
                                name = item.second,
                                modifier =
                                    Modifier.weight(1f)
                            )
                        }

                        if (rowObjects.size == 1) {

                            Spacer(
                                modifier =
                                    Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(16.dp)
                    )
                }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = Color(0xFF6952C5)
            )
        }

        // -----------------------------
        // QUESTION
        // -----------------------------

        else if (gameState == "question") {

            UiText(
                text =
                    "Which objects did you see?",
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF233449)
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            UiText(
                text =
                    "Select the $objectCount objects you saw.",
                fontSize = 16.sp,
                color = Color(0xFF66758A)
            )

            Spacer(
                modifier = Modifier.height(22.dp)
            )

            answerOptions
                .chunked(2)
                .forEach { rowObjects ->

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                14.dp
                            )
                    ) {

                        rowObjects.forEach { item ->

                            val selected =
                                item in userSelections

                            AnswerCard(
                                emoji = item.first,
                                name = item.second,
                                selected = selected,
                                modifier =
                                    Modifier.weight(1f)
                            ) {

                                userSelections =
                                    if (selected) {

                                        userSelections -
                                                item

                                    } else {

                                        userSelections +
                                                item
                                    }
                            }
                        }

                        if (rowObjects.size == 1) {

                            Spacer(
                                modifier =
                                    Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )
                }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            UiText(
                text =
                    "${userSelections.size} selected",
                fontSize = 15.sp,
                color = Color(0xFF66758A)
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Button(
                onClick = {

                    // -----------------------------
                    // RESPONSE TIME
                    // -----------------------------

                    responseTime =
                        System.currentTimeMillis() -
                                questionStartTime

                    lastCompletedLevel =
                        difficulty

                    // -----------------------------
                    // ANSWER CALCULATION
                    // -----------------------------

                    correctAnswers =
                        userSelections.count {
                            it in shownObjects
                        }

                    incorrectAnswers =
                        userSelections.count {
                            it !in shownObjects
                        }

                    // Base accuracy

                    val accuracy =
                        correctAnswers.toFloat() /
                                shownObjects.size

                    // Incorrect answers receive
                    // a small penalty.

                    val incorrectPenalty =
                        incorrectAnswers * 10

                    score =
                        (
                                accuracy * 100
                                ).toInt() -
                                incorrectPenalty

                    score =
                        score.coerceIn(
                            0,
                            100
                        )

                    // -----------------------------
                    // ADAPTIVE ENGINE
                    // -----------------------------



                    // Keep this round's objects and denominator stable on the result screen.
                    nextDifficulty = nextPictureRecallLevel(difficulty, correctAnswers, shownObjects.size, incorrectAnswers)

                    onSessionCompleted(
                        GameSessionRecord(
                            score = score,
                            level = lastCompletedLevel,
                            responseTimeMs = responseTime,
                            correctAnswers = correctAnswers,
                            totalObjects = shownObjects.size
                        )
                    )

                    gameState = "result"
                },
                enabled =
                    userSelections.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape =
                    RoundedCornerShape(20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF6952C5)
                    )
            ) {

                UiText(
                    text = "Check Answer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // -----------------------------
        // RESULT SCREEN
        // -----------------------------

        else if (gameState == "result") {

            ResultSection(
                score = score,
                correct = correctAnswers,
                total = shownObjects.size,
                incorrect = incorrectAnswers,
                responseTime = responseTime,
                completedLevel =
                    lastCompletedLevel,
                nextLevel = nextDifficulty
            )

            Spacer(
                modifier = Modifier.height(26.dp)
            )

            Button(
                onClick = {

                    userSelections =
                        emptySet()

                    difficulty = nextDifficulty
                    sessionNumber++

                    gameState = "show"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape =
                    RoundedCornerShape(20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF6952C5)
                    )
            ) {

                UiText(
                    text = "Play Next Round",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape =
                    RoundedCornerShape(20.dp)
            ) {

                UiText(
                    text = "Return Home",
                    fontSize = 17.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(30.dp)
        )
    }
}


// =====================================================
// RESULT
// =====================================================

@Composable
fun ResultSection(
    score: Int,
    correct: Int,
    total: Int,
    incorrect: Int,
    responseTime: Long,
    completedLevel: Int,
    nextLevel: Int
) {

    val message =
        when {

            score >= 80 ->
                "Wonderful! 🌟"

            score >= 50 ->
                "Good effort 😊"

            else ->
                "Let's try another one 🌼"
        }

    val description =
        when {

            score >= 80 ->
                "You remembered these very well."

            score >= 50 ->
                "You're doing nicely. Keep going."

            else ->
                "No worries. You can practise with the same number of objects."
        }

    UiText(
        text = message,
        fontSize = 25.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF233449)
    )

    Spacer(
        modifier = Modifier.height(8.dp)
    )

    UiText(
        text = description,
        fontSize = 17.sp,
        color = Color(0xFF66758A)
    )

    Spacer(
        modifier = Modifier.height(24.dp)
    )

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 5.dp
            )
    ) {

        Column(
            modifier =
                Modifier.padding(22.dp)
        ) {

            UiText(
                text = "Round Summary",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            ResultRow(
                emoji = "⭐",
                label = "Score",
                value = "$score%"
            )

            ResultRow(
                emoji = "✅",
                label = "Remembered",
                value = "$correct / $total"
            )

            ResultRow(
                emoji = "🔹",
                label = "Extra selections",
                value = "$incorrect"
            )

            ResultRow(
                emoji = "⏱️",
                label = "Response time",
                value =
                    String.format(
                        "%.1f sec",
                        responseTime / 1000.0
                    )
            )

            ResultRow(
                emoji = "🧠",
                label = "Completed level",
                value = "Level $completedLevel"
            )
        }
    }

    Spacer(
        modifier = Modifier.height(20.dp)
    )

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(0xFFE8F5E9)
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            UiText(
                text = "✨",
                fontSize = 32.sp
            )

            Spacer(
                modifier =
                    Modifier.width(14.dp)
            )

            Column {

                UiText(
                    text =
                        "Adaptive Difficulty",
                    fontSize = 18.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                UiText(
                    text =
                        if (
                            nextLevel >
                            completedLevel
                        ) {

                            "All correct! Next round: ${nextLevel + 2} objects."

                        } else if (
                            nextLevel <
                            completedLevel
                        ) {

                            "Making the next round easier."

                        } else {

                            "Next round: ${nextLevel + 2} objects. Get all correct with no extra selections to advance."
                        },
                    fontSize = 15.sp,
                    color =
                        Color(0xFF546E7A)
                )
            }
        }
    }
}


// =====================================================
// RESULT ROW
// =====================================================

@Composable
fun ResultRow(
    emoji: String,
    label: String,
    value: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 7.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        UiText(
            text = emoji,
            fontSize = 22.sp
        )

        Spacer(
            modifier =
                Modifier.width(12.dp)
        )

        UiText(
            text = label,
            modifier =
                Modifier.weight(1f),
            fontSize = 16.sp,
            color =
                Color(0xFF546E7A)
        )

        UiText(
            text = value,
            fontSize = 17.sp,
            fontWeight =
                FontWeight.Bold,
            color =
                Color(0xFF233449)
        )
    }
}


// =====================================================
// DIFFICULTY BADGE
// =====================================================

@Composable
fun DifficultyBadge(
    difficulty: Int
) {

    val background =
        when (difficulty) {

            1 ->
                Color(0xFFDFF5E3)

            2 ->
                Color(0xFFFFE9B8)

            else ->
                Color(0xFFFFD7D7)
        }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = background
    ) {

        UiText(
            text = "Level $difficulty",
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 7.dp
            ),
            fontWeight =
                FontWeight.Bold,
            fontSize = 14.sp,
            color =
                Color(0xFF37474F)
        )
    }
}


// =====================================================
// OBJECT CARD
// =====================================================

@Composable
fun ObjectCard(
    emoji: String,
    name: String,
    modifier: Modifier
) {

    Card(
        modifier =
            modifier.height(140.dp),
        shape =
            RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 5.dp
            )
    ) {

        Column(
            modifier =
                Modifier.fillMaxSize(),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            UiText(
                text = emoji,
                fontSize = 48.sp
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            UiText(
                text = name,
                fontSize = 18.sp,
                fontWeight =
                    FontWeight.Medium,
                textAlign =
                    TextAlign.Center
            )
        }
    }
}


// =====================================================
// ANSWER CARD
// =====================================================

@Composable
fun AnswerCard(
    emoji: String,
    name: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {

    Card(
        modifier = modifier
            .height(125.dp)
            .clickable {
                onClick()
            },
        shape =
            RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {

                        Color(0xFFB9F6CA)

                    } else {

                        Color.White
                    }
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    if (selected)
                        8.dp
                    else
                        4.dp
            )
    ) {

        Column(
            modifier =
                Modifier.fillMaxSize(),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            UiText(
                text = emoji,
                fontSize = 40.sp
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            UiText(
                text = name,
                fontSize = 16.sp,
                fontWeight =
                    FontWeight.Medium
            )

            if (selected) {

                Spacer(
                    modifier =
                        Modifier.height(3.dp)
                )

                UiText(
                    text = "✓ Selected",
                    fontSize = 12.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        Color(0xFF2E7D32)
                )
            }
        }
    }
}