package com.example.myapplication

import com.example.myapplication.ui.UiText

import com.example.myapplication.speech.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.HydrationData
import com.example.myapplication.data.SmritiRepository
import kotlinx.coroutines.launch
import com.example.myapplication.reminders.DailyRefreshEffect

@Composable
fun HydrationScreen(
    repository: SmritiRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var hydration by remember { mutableStateOf(HydrationData("", 0, 8)) }

    fun refresh() {
        coroutineScope.launch {
            hydration = repository.getTodayHydration()
        }
    }

    DailyRefreshEffect {
        refresh()
    }

    NarrationScreen("Hydration tracker. ${hydration.glassesDrunk} of ${hydration.targetGlasses} glasses recorded today. " +
        "Tap plus one glass after drinking, or minus one glass to correct an entry.", assamese = "পানী খোৱাৰ হিচাপ। আজি ${hydration.targetGlasses} গিলাচৰ ভিতৰত ${hydration.glassesDrunk} গিলাচ পানী খোৱা হৈছে। পানী খোৱাৰ পিছত যোগ চিহ্নত টিপক। ভুল শুধৰাবলৈ বিয়োগ চিহ্নত টিপক।")
    val background = Brush.verticalGradient(
        listOf(
            Color(0xFFF5FAFC),
            Color(0xFFE0F7FA),
            Color(0xFFF1F8E9)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                UiText("← Back", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        UiText(
            text = "Hydration Tracker 💧",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449),
            modifier = Modifier.fillMaxWidth()
        )

        UiText(
            text = "Gentle water reminders for brain health",
            fontSize = 15.sp,
            color = Color(0xFF66758A),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Big Hydration Dial Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(90.dp),
                    shape = CircleShape,
                    color = Color(0xFFE1F5FE)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        UiText(text = "💧", fontSize = 48.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                UiText(
                    text = "${hydration.glassesDrunk} / ${hydration.targetGlasses}",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0288D1)
                )

                UiText(
                    text = "Glasses Drunk Today",
                    fontSize = 16.sp,
                    color = Color(0xFF546E7A)
                )

                Spacer(modifier = Modifier.height(20.dp))

                val progress = (hydration.glassesDrunk.toFloat() / hydration.targetGlasses.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    color = Color(0xFF03A9F4),
                    trackColor = Color(0xFFE1F5FE),
                )

                Spacer(modifier = Modifier.height(14.dp))

                UiText(
                    text = if (hydration.glassesDrunk >= hydration.targetGlasses)
                        "🌟 Daily Goal Achieved! Wonderful hydration!"
                    else
                        "Keep a glass of water nearby and take small sips.",
                    fontSize = 14.sp,
                    color = Color(0xFF455A64),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Increment / Decrement Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        hydration = repository.decrementHydration()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCFD8DC))
            ) {
                UiText(
                    text = "- 1 Glass",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF37474F)
                )
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        hydration = repository.incrementHydration()
                    }
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
            ) {
                UiText(
                    text = "+ 1 Glass 💧",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Regional Care Note Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UiText(text = "🍃", fontSize = 32.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    UiText(
                        text = "NER Climate Care Tip",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    UiText(
                        text = "Warm Assam tea and boiled water support good digestion and cognitive alertness in elderly individuals.",
                        fontSize = 13.sp,
                        color = Color(0xFF37474F)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
