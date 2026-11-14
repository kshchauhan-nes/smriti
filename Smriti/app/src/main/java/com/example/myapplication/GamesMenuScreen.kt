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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GamesMenuScreen(
    onBack: () -> Unit,
    onSelectPictureGame: () -> Unit,
    onSelectStoryGame: () -> Unit,
    onSelectFamilyGame: () -> Unit
) {
    NarrationScreen("Memory games. Choose Picture Recall to remember objects, Story Recall to answer questions about a short story, or Family Face Recall to identify familiar people. Tap Back to return home.", assamese = "স্মৃতিৰ খেল। বস্তু মনত ৰাখিবলৈ ছবিৰ খেল বাছক। কাহিনী পঢ়ি প্ৰশ্নৰ উত্তৰ দিবলৈ কাহিনীৰ খেল বাছক। চিনাকি মানুহ চিনিবলৈ পৰিয়ালৰ ছবিৰ খেল বাছক। আগৰ পৃষ্ঠালৈ যাবলৈ পিছলৈ টিপক।")
    val background = Brush.verticalGradient(
        listOf(
            Color(0xFFFFFEFA),
            Color(0xFFE8F5E9),
            Color(0xFFF5FAFC)
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
            UiText("← Back Home", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(6.dp))

        UiText(
            text = "Memory Games",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF233449)
        )

        UiText(
            text = "Choose an exercise for your mind today",
            fontSize = 16.sp,
            color = Color(0xFF66758A)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Game 1: Picture Recall
        GameSelectionCard(
            title = "Picture Recall",
            subtitle = "Look at regional objects & recall them",
            badge = "Object Memory",
            emoji = "🧠",
            cardColor = Color(0xFF6952C5),
            onClick = onSelectPictureGame
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Game 2: Story & Memory
        GameSelectionCard(
            title = "Story & Memory",
            subtitle = "Read a short story & answer gentle questions",
            badge = "Episodic Recall",
            emoji = "📖",
            cardColor = Color(0xFF6952C5),
            onClick = onSelectStoryGame
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Game 3: Family Face Recall
        GameSelectionCard(
            title = "Family Face Recall",
            subtitle = "Recognize beloved children, relatives & friends",
            badge = "Reminiscence",
            emoji = "👨‍👩‍👧",
            cardColor = Color(0xFF8870CA),
            onClick = onSelectFamilyGame
        )

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun GameSelectionCard(
    title: String,
    subtitle: String,
    badge: String,
    emoji: String,
    cardColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UiText(text = emoji, fontSize = 32.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.25f)
                ) {
                    UiText(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                UiText(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                UiText(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            UiText(
                text = "→",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
