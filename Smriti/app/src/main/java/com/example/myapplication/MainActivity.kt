package com.example.myapplication

import com.example.myapplication.ui.UiText

import com.example.myapplication.speech.*
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.chat.OfflineChatScreen
import com.example.myapplication.cloud.PatientAccountGate
import com.example.myapplication.data.PatientProfile
import com.example.myapplication.data.HydrationData
import com.example.myapplication.data.MedicineItem
import com.example.myapplication.data.RoutineItem
import com.example.myapplication.data.SmritiRepository
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import com.example.myapplication.reminders.DailyRefreshEffect
import com.example.myapplication.reminders.ReminderScheduler

class
MainActivity : ComponentActivity() {

    private val screenHistory = mutableStateListOf("home")
    private val currentScreen: String get() = screenHistory.last()
    private var activePatient: String? = null
    private var pendingReminder: String? = null

    private fun navigate(screen: String) {
        if (currentScreen != screen) screenHistory.add(screen)
    }

    private fun goBack() {
        if (screenHistory.size > 1) screenHistory.removeAt(screenHistory.lastIndex)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("screen_history", ArrayList(screenHistory))
        outState.putString("navigation_patient", activePatient)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openReminder(intent)
    }

    private fun openReminder(intent: Intent) {
        intent.getStringExtra(ReminderScheduler.SCREEN)?.let {
            if (it == "medicine" || it == "routine") {
                pendingReminder = if (activePatient == null) it else null
                navigate(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList("screen_history")?.takeIf { it.isNotEmpty() }?.let {
            screenHistory.clear()
            screenHistory.addAll(it)
            activePatient = savedInstanceState.getString("navigation_patient")
        }
        if (savedInstanceState == null) openReminder(intent)
        ReminderScheduler.createChannel(this)

        setContent {
            MyApplicationTheme {
              com.example.myapplication.ui.LanguageHost {
              com.example.myapplication.chat.ModelDownloadHost {
              PatientAccountGate { repository ->
                DisposableEffect(repository.patientKey) {
                    if (activePatient != repository.patientKey) {
                        screenHistory.clear()
                        screenHistory.add("home")
                        activePatient = repository.patientKey
                    }
                    pendingReminder?.let { navigate(it); pendingReminder = null }
                    onDispose {}
                }
                BackHandler(enabled = screenHistory.size > 1) { goBack() }
                val coroutineScope = rememberCoroutineScope()
                var showCaregiverPinDialog by remember { mutableStateOf(false) }

                DailyRefreshEffect { ReminderScheduler.reconcile(this@MainActivity) }

                when (currentScreen) {
                    "home" -> com.example.myapplication.ui.PersonalisationGate(repository.patientKey) { SmritiHomeScreen(
                        repository = repository,
                        onMemoryGamesClick = { navigate("games_menu") },
                        onMemoriesClick = { navigate("game_family") },
                        onCaregiverClick = { showCaregiverPinDialog = true },
                        onMedicineClick = { navigate("medicine") },
                        onHydrationClick = { navigate("hydration") },
                        onRoutineClick = { navigate("routine") },
                        onChatClick = { navigate("chat") }
                    ) }

                    "chat" -> OfflineChatScreen(repository, onBack = { goBack() }, onMedicines = { navigate("medicine") }, onRoutines = { navigate("routine") })

                    "games_menu" -> GamesMenuScreen(
                        onBack = { goBack() },
                        onSelectPictureGame = { navigate("game_picture") },
                        onSelectStoryGame = { navigate("game_story") },
                        onSelectFamilyGame = { navigate("game_family") }
                    )

                    "game_picture" -> MemoryGameScreen(
                        repository = repository,
                        onBack = { goBack() },
                        onSessionCompleted = { session ->
                            coroutineScope.launch {
                                repository.saveGameSession(session)
                            }
                        }
                    )

                    "game_story" -> StoryRecallGameScreen(
                        repository = repository,
                        onBack = { goBack() }
                    )

                    "game_family" -> FamilyFaceRecallGameScreen(
                        repository = repository,
                        onBack = { goBack() }
                    )

                    "caregiver" -> CaregiverDashboard(
                        repository = repository,
                        onBack = { goBack() }
                    )

                    "medicine" -> MedicineScreen(
                        repository = repository,
                        onBack = { goBack() }
                    )

                    "hydration" -> HydrationScreen(
                        repository = repository,
                        onBack = { goBack() }
                    )

                    "routine" -> RoutineScreen(
                        repository = repository,
                        onBack = { goBack() }
                    )
                }

                if (showCaregiverPinDialog) {
                    CaregiverPinDialog(
                        onDismiss = { showCaregiverPinDialog = false },
                        onSuccess = {
                            showCaregiverPinDialog = false
                            navigate("caregiver")
                        }
                    )
                }
              }
              }
              }
            }
        }
    }

    @Composable
    fun SmritiHomeScreen(
        repository: SmritiRepository,
        onMemoryGamesClick: () -> Unit,
        onMemoriesClick: () -> Unit,
        onCaregiverClick: () -> Unit,
        onMedicineClick: () -> Unit,
        onHydrationClick: () -> Unit,
        onRoutineClick: () -> Unit,
        onChatClick: () -> Unit
    ) {
        var profile by remember { mutableStateOf(PatientProfile("", "", "", "", "")) }
        var medicines by remember { mutableStateOf<List<MedicineItem>>(emptyList()) }
        var hydration by remember { mutableStateOf(HydrationData("", 0, 8)) }
        var routines by remember { mutableStateOf<List<RoutineItem>>(emptyList()) }

        DailyRefreshEffect {
            profile = repository.getPatientProfile()
            medicines = repository.getAllMedicines()
            hydration = repository.getTodayHydration()
            routines = repository.getTodayRoutines()
        }

        NarrationScreen("Welcome to Smriti, ${profile.patientName}. " +
            "Your day: ${medicines.count { it.isTaken }} of ${medicines.size} medicines marked taken. " +
            "${hydration.glassesDrunk} of ${hydration.targetGlasses} glasses of water recorded. " +
            "${routines.count { it.isCompleted }} of ${routines.size} routine activities complete. " +
            "Choose Memory Games, Medicine, Hydration, My Memories, My Routine, or Talk to Smriti. " +
            "Caregiver View requires the caregiver PIN. The Language button changes the interface language.", assamese = "স্মৃতিলৈ স্বাগতম। ${profile.patientName}। আজি ${medicines.size} টা ঔষধৰ ভিতৰত ${medicines.count { it.isTaken }} টা খোৱা বুলি চিহ্নিত কৰা হৈছে। ${hydration.targetGlasses} গিলাচ পানীৰ ভিতৰত ${hydration.glassesDrunk} গিলাচ খোৱা হৈছে। ${routines.size} টা কামৰ ভিতৰত ${routines.count { it.isCompleted }} টা সম্পূৰ্ণ হৈছে। স্মৃতিৰ খেল, ঔষধ, পানী, পৰিয়ালৰ স্মৃতি অথবা দৈনন্দিন কাম বাছক। মোৰ সৈতে কথা পাতিবলৈ স্মৃতি বাছক।")
        val remainingMeds = medicines.count { !it.isTaken }
        val completedRoutines = routines.count { it.isCompleted }

        com.example.myapplication.ui.PersonalisedHome(
            repository.patientKey, profile.patientName,
            if (medicines.isEmpty()) stringResource(R.string.language_check_meds) else stringResource(R.string.language_remaining, remainingMeds),
            stringResource(R.string.language_glasses, hydration.glassesDrunk, hydration.targetGlasses),
            stringResource(R.string.language_completed, completedRoutines, routines.size),
            onMemoryGamesClick, onMemoriesClick, onMedicineClick, onHydrationClick, onRoutineClick, onChatClick, onCaregiverClick
        )
    }

    @Composable
    fun CaregiverPinDialog(
        onDismiss: () -> Unit,
        onSuccess: () -> Unit
    ) {
        var pin by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                NarratedTitle(stringResource(R.string.lang_47), "Enter the four digit caregiver PIN, then tap Unlock Dashboard. Cancel returns to the patient screen. The PIN you enter will not be read aloud.")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    UiText(
                        text = "Please enter the 4-digit caregiver PIN to access monitoring, emergency contacts, and relative profiles.",
                        fontSize = 14.sp,
                        color = Color(0xFF607D8B)
                    )

                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                pin = it
                                errorMessage = null
                            }
                        },
                        label = { UiText("4-Digit PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMessage?.let {
                        UiText(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pin == "1234") {
                            onSuccess()
                        } else {
                            errorMessage = "Incorrect PIN. Caregiver access only."
                        }
                    },
                    enabled = pin.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    UiText(stringResource(R.string.lang_48))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    UiText(stringResource(R.string.lang_9))
                }
            }
        )
    }

    @Composable
    fun MemoryGameCard(
        onClick: () -> Unit
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF78)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 6.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    UiText(
                        text = "🧠",
                        fontSize = 36.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    UiText(
                        text = stringResource(R.string.lang_7),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    UiText(
                        text = stringResource(R.string.lang_44),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.22f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        UiText(
                            text = "→",
                            fontSize = 28.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SmallActionCard(
        modifier: Modifier = Modifier,
        emoji: String,
        title: String,
        subtitle: String,
        background: Color,
        onClick: () -> Unit = {}
    ) {
        Card(
            onClick = onClick,
            modifier = modifier.height(145.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = background
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 3.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                UiText(
                    text = emoji,
                    fontSize = 30.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                UiText(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF263238)
                )

                Spacer(modifier = Modifier.height(2.dp))

                UiText(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF607D8B)
                )
            }
        }
    }

    @Composable
    fun ChatCompanionCard(onClick: () -> Unit) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF37474F)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        UiText(
                            text = "🎙️",
                            fontSize = 26.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    UiText(
                        text = stringResource(R.string.lang_8),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    UiText(
                        text = stringResource(R.string.lang_45),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    @Composable
    fun CaregiverButton(
        onClick: () -> Unit
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            UiText(
                text = stringResource(R.string.lang_46),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
