package com.example.myapplication

import com.example.myapplication.ui.UiText

import com.example.myapplication.speech.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.RoutineItem
import com.example.myapplication.data.SmritiRepository
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.reminders.ReminderSettingsCard
import com.example.myapplication.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import com.example.myapplication.reminders.DailyRefreshEffect

@Composable
fun RoutineScreen(
    repository: SmritiRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var routines by remember { mutableStateOf<List<RoutineItem>>(emptyList()) }

    fun refresh() {
        coroutineScope.launch {
            routines = repository.getTodayRoutines()
        }
    }

    DailyRefreshEffect {
        refresh()
    }

    NarrationScreen("Daily routine. " +
        if (routines.isEmpty()) "No daily activities listed yet. Your caregiver can add them."
        else routines.joinToString("\n") { "${it.title}, at ${it.timeStr}. " + if (it.isCompleted) "Completed." else "Not marked complete." } +
            "\nTap an activity to update its completion. Each new day has its own checklist. Reminders arrive fifteen minutes before, at the scheduled time, and thirty minutes later if still unchecked.", assamese = "দৈনন্দিন কাম। " + (if (routines.isEmpty()) "এতিয়াও কোনো কাম যোগ কৰা হোৱা নাই।" else routines.joinToString("\n") { "${it.title}। সময় ${it.timeStr}। " + if (it.isCompleted) "সম্পূৰ্ণ হৈছে।" else "সম্পূৰ্ণ বুলি চিহ্নিত কৰা হোৱা নাই।" }) + " কাম সম্পূৰ্ণ হ’লে টিপি চিহ্নিত কৰক। প্ৰতিদিনে নতুন তালিকা আৰম্ভ হয়।")
    val completedCount = routines.count { it.isCompleted }
    val totalCount = routines.size

    val background = Brush.verticalGradient(
        listOf(
            Color(0xFFEDE7F6),
            Color(0xFFFFFEFA),
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
            UiText("← Back", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(6.dp))

        UiText(
            text = "Daily Routine 📅",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        UiText(
            text = "$completedCount of $totalCount activities completed today",
            fontSize = 15.sp,
            color = Color(0xFF66758A)
        )

        Spacer(modifier = Modifier.height(20.dp))

        ReminderSettingsCard()

        // Routine Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF6952C5)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UiText(text = "🌤️", fontSize = 36.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    UiText(
                        text = "Peaceful Daily Rhythm",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    UiText(
                        text = "Structured routines reduce anxiety in dementia care",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        UiText(
            text = "Today's Schedule",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        Spacer(modifier = Modifier.height(14.dp))

        routines.forEach { routine ->
            RoutineCard(
                routine = routine,
                onToggle = { isChecked ->
                    coroutineScope.launch {
                        repository.toggleRoutineCompleted(routine.id, isChecked)
                        ReminderScheduler.reconcile(context)
                        refresh()
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun RoutineCard(
    routine: RoutineItem,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (routine.isCompleted) Color(0xFFF1F8E9) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (routine.isCompleted) 1.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (routine.isCompleted) Color(0xFFDCEDC8) else Color(0xFFEDE7F6)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UiText(text = routine.emoji, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (routine.isCompleted) Color(0xFF33691E) else Color(0xFF233449)
                )
                Text(
                    text = routine.timeStr,
                    fontSize = 14.sp,
                    color = Color(0xFF66758A)
                )
            }

            Checkbox(
                checked = routine.isCompleted,
                onCheckedChange = { onToggle(it) },
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
            )
        }
    }
}
