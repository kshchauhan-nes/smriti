package com.example.myapplication

import com.example.myapplication.ui.UiText

import com.example.myapplication.speech.*
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.FamilyMember
import com.example.myapplication.data.GameSessionRecord
import com.example.myapplication.data.HydrationData
import com.example.myapplication.data.MedicineItem
import com.example.myapplication.data.PatientProfile
import com.example.myapplication.data.RoutineItem
import com.example.myapplication.reminders.missedCareItems
import com.example.myapplication.data.SmritiRepository
import kotlinx.coroutines.launch
import com.example.myapplication.reminders.DailyRefreshEffect
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CaregiverDashboard(
    repository: SmritiRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<GameSessionRecord>>(emptyList()) }
    var medicines by remember { mutableStateOf<List<MedicineItem>>(emptyList()) }
    var routines by remember { mutableStateOf<List<RoutineItem>>(emptyList()) }
    var checkedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var careLoaded by remember { mutableStateOf(false) }
    var hydration by remember { mutableStateOf(HydrationData("", 0, 8)) }
    var familyMembers by remember { mutableStateOf<List<FamilyMember>>(emptyList()) }
    var showAddRelativeDialog by remember { mutableStateOf(false) }
    var editingRelative by remember { mutableStateOf<FamilyMember?>(null) }

    var profile by remember { mutableStateOf(PatientProfile("", "", "", "", "")) }

    fun refreshAll() {
        coroutineScope.launch {
            profile = repository.getPatientProfile()
            sessions = repository.getAllGameSessions()
            medicines = repository.getAllMedicines()
            routines = repository.getTodayRoutines()
            checkedAt = System.currentTimeMillis()
            careLoaded = true
            hydration = repository.getTodayHydration()
            familyMembers = repository.getAllFamilyMembers()
        }
    }

    DailyRefreshEffect(refreshEveryMs = 30_000L) {
        refreshAll()
    }

    val averageScore = if (sessions.isEmpty()) 0 else sessions.map { it.score }.average().toInt()
    val averageResponse = if (sessions.isEmpty()) 0.0 else sessions.map { it.responseTimeMs / 1000.0 }.average()
    val highestScore = sessions.maxOfOrNull { it.score } ?: 0
    val medicinesTaken = medicines.count { it.isTaken }
    val totalMedicines = medicines.size
    val missed = missedCareItems(medicines, routines, checkedAt)

    NarrationScreen("Caregiver dashboard for ${profile.patientName}. Caregiver: ${profile.caregiverName}. " +
        "Contact: ${profile.caregiverPhone}. Location: ${profile.location}. " +
        "${sessions.size} game sessions. Average score $averageScore percent. Highest score $highestScore percent. " +
        "$medicinesTaken of $totalMedicines medicines marked taken. " +
        "${routines.count { it.isCompleted }} of ${routines.size} routines complete. " +
        "${hydration.glassesDrunk} of ${hydration.targetGlasses} glasses recorded. " +
        "Overdue and still unchecked: " + missed.joinToString { "${it.title} scheduled ${it.scheduledTime}" }.ifEmpty { "None" } +
        ". An unchecked medicine is not proof a dose was missed.\nFamily profiles: " +
        familyMembers.joinToString("\n") { "${it.name}, ${it.relation}. ${it.notes}" } +
        "\nUse Add relative to add a name, relationship, notes and a photograph. Tap an existing relative to edit their details.", assamese = "যত্ন লওঁতাৰ পৃষ্ঠা। ${profile.patientName}। ${sessions.size} বাৰ খেল খেলা হৈছে। গড় নম্বৰ $averageScore শতাংশ। $totalMedicines টা ঔষধৰ ভিতৰত $medicinesTaken টা খোৱা বুলি চিহ্নিত। সময় পাৰ হোৱা কিন্তু চিহ্নিত নকৰা কাম। " + missed.joinToString("। ") { "${it.title}। সময় ${it.scheduledTime}" }.ifEmpty { "নাই" } + "। পৰিয়ালৰ তথ্য। " + familyMembers.joinToString("। ") { "${it.name}। ${it.relation}। ${it.notes}" })
    val background = Brush.verticalGradient(
        listOf(
            Color(0xFFF8FAFD),
            Color(0xFFFAFCFC),
            Color(0xFFFFFEFA)
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
            UiText("← Patient View", fontSize = 17.sp)
        }

        Spacer(modifier = Modifier.height(6.dp))

        UiText(
            text = "Caregiver Dashboard",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        UiText(
            text = "Patient monitoring & health overview",
            fontSize = 15.sp,
            color = Color(0xFF66758A)
        )

        Spacer(modifier = Modifier.height(20.dp))

        CaregiverScheduleEditor(repository, medicines, routines, ::refreshAll)
        Spacer(Modifier.height(18.dp))
        com.example.myapplication.ui.ProgressOverview(sessions)
        Spacer(modifier = Modifier.height(18.dp))

        OutlinedButton(onClick = { editingRelative = null; showAddRelativeDialog = true }, modifier = Modifier.fillMaxWidth()) {
            UiText("+ Add family photo & details")
        }
        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = if (missed.isEmpty()) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UiText("Missed medicines & activities", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF233449))
                UiText("Today • Still unchecked 30 minutes after the scheduled time",
                    fontSize = 13.sp, color = Color(0xFF66758A))
                if (!careLoaded) {
                    UiText("Loading today's checklist…", fontSize = 14.sp)
                } else if (missed.isEmpty()) {
                    UiText("No missed medicines or activities today.", fontSize = 15.sp, color = Color(0xFF2E7D32))
                } else {
                    UiText("${missed.count { it.medicine }} medicines • ${missed.count { !it.medicine }} activities",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB71C1C))
                    missed.forEach { item ->
                        Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                UiText(if (item.medicine) "💊" else "📅", fontSize = 24.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF233449))
                                    UiText("${item.scheduledTime} • ${item.overdueMinutes} min overdue", fontSize = 13.sp, color = Color(0xFFB71C1C))
                                    UiText(if (item.medicine) "Not marked taken" else "Not marked complete", fontSize = 12.sp, color = Color(0xFF66758A))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Patient Status & Emergency SOS Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            UiText(text = "👵", fontSize = 34.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.patientName,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        UiText(
                            text = "${profile.location} • Offline Mode",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:${profile.caregiverPhone}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    UiText(
                        text = "📞 Emergency SOS Contact (${profile.caregiverName})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Daily Adherence Overview
        UiText(
            text = "Today's Adherence",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                emoji = "💊",
                value = "$medicinesTaken / $totalMedicines",
                label = "Meds Taken",
                background = Color(0xFFFFE0E0)
            )

            DashboardStatCard(
                modifier = Modifier.weight(1f),
                emoji = "💧",
                value = "${hydration.glassesDrunk} / ${hydration.targetGlasses}",
                label = "Water Glasses",
                background = Color(0xFFDDEEFF)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Cognitive Performance Section
        UiText(
            text = "Cognitive Performance",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                emoji = "🧠",
                value = "${sessions.size}",
                label = "Sessions Played",
                background = Color(0xFFDFF3FF)
            )

            DashboardStatCard(
                modifier = Modifier.weight(1f),
                emoji = "⭐",
                value = "$averageScore%",
                label = "Avg Score",
                background = Color(0xFFE3F5E5)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardStatCard(
                modifier = Modifier.weight(1f),
                emoji = "🏆",
                value = "$highestScore%",
                label = "Best Score",
                background = Color(0xFFFFEBC8)
            )

            DashboardStatCard(
                modifier = Modifier.weight(1f),
                emoji = "⏱️",
                value = if (sessions.isEmpty()) "--" else String.format("%.1fs", averageResponse),
                label = "Avg Speed",
                background = Color(0xFFE9E2FF)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Family Recognition Management Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                UiText(
                    text = "Family Photos & Profiles 👨‍👩‍👧",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF233449)
                )
                UiText(
                    text = "${familyMembers.size} relatives configured for games",
                    fontSize = 13.sp,
                    color = Color(0xFF66758A)
                )
            }

            Button(
                onClick = { editingRelative = null; showAddRelativeDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                UiText("+ Add Photo", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        familyMembers.forEach { member ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = Color(0xFFFFEBEE)
                    ) {
                        FamilyPhoto(member.photoUri, member.avatarEmoji, "Photo of ${member.name}", Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = member.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = member.relation, fontSize = 13.sp, color = Color(0xFF66758A))
                        if (member.notes.isNotEmpty()) {
                            Text(text = member.notes, fontSize = 11.sp, color = Color(0xFF90A4AE))
                        }
                    }
                    TextButton(onClick = { editingRelative = member; showAddRelativeDialog = true }) { UiText("Edit") }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.deleteFamilyMember(member.id)
                                refreshAll()
                            }
                        }
                    ) {
                        UiText("🗑️", fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        UiText(
            text = "Recent Game History (Offline SQLite)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (sessions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UiText(text = "🌱", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    UiText(text = "No sessions recorded yet", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    UiText(text = "Play any memory game to see offline performance metrics.", color = Color(0xFF66758A), fontSize = 13.sp)
                }
            }
        } else {
            sessions.take(6).forEachIndexed { index, session ->
                SessionRecordCard(session = session, number = sessions.size - index)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        UiText(
            text = "• All data is safely stored offline on this device.\n• Intended for cognitive engagement & caregiver tracking, not clinical diagnosis.",
            fontSize = 12.sp,
            color = Color(0xFF78909C)
        )

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (showAddRelativeDialog) {
        FamilyProfileDialog(
            member = editingRelative,
            repository = repository,
            onDismiss = { showAddRelativeDialog = false },
            onSaved = { refreshAll(); showAddRelativeDialog = false }
        )
    }
}

@Composable
fun DashboardStatCard(
    modifier: Modifier,
    emoji: String,
    value: String,
    label: String,
    background: Color
) {
    Card(
        modifier = modifier.height(125.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            UiText(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            UiText(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            UiText(text = label, fontSize = 13.sp, color = Color(0xFF66758A))
        }
    }
}

@Composable
fun SessionRecordCard(
    session: GameSessionRecord,
    number: Int
) {
    val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFE8F5E9)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UiText(text = "🧠", fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                UiText(
                    text = "${session.gameType} #$number",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                UiText(
                    text = "Lvl ${session.level} • ${formatter.format(Date(session.timestamp))}",
                    fontSize = 12.sp,
                    color = Color(0xFF78909C)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                UiText(
                    text = "${session.score}%",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (session.score >= 80) Color(0xFF2E7D32) else Color(0xFF455A64)
                )
                UiText(
                    text = String.format("%.1fs", session.responseTimeMs / 1000.0),
                    fontSize = 12.sp,
                    color = Color(0xFF78909C)
                )
            }
        }
    }
}

