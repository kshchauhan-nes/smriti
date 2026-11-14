package com.example.myapplication.ai

import com.example.myapplication.data.*
import java.text.SimpleDateFormat
import java.util.*

class SmritiAIEngine(private val repository: SmritiRepository) {

    suspend fun generateResponse(userMessage: String): ChatMessage {
        val lower = userMessage.trim().lowercase(Locale.getDefault())

        // 1. Gather live contextual data
        val profile = repository.getPatientProfile()
        val patientName = if (profile.patientName.isNotBlank()) profile.patientName else "Aita"
        val location = if (profile.location.isNotBlank()) profile.location else "Jorhat, Assam"
        val familyMembers = repository.getAllFamilyMembers()
        val medicines = repository.getAllMedicines()
        val hydration = repository.getTodayHydration()
        val routines = repository.getTodayRoutines()
        val preferences = repository.getLearnedPreferences()

        // 2. Track learned keywords & preferences
        detectAndSavePreferences(lower)

        // 3. Current calendar context
        val calendar = Calendar.getInstance()
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Peaceful night"
        }

        // 4. Intent Classification & Contextual Reasoning

        // A. Family & Relatives Query (Contextual Caregiver Analysis)
        val matchingRelative = familyMembers.firstOrNull { member ->
            lower.contains(member.name.lowercase(Locale.getDefault())) ||
            lower.contains(member.relation.lowercase(Locale.getDefault()))
        }

        val responseText: String = when {
            // Specific Family Relative Mentioned
            matchingRelative != null -> {
                val member = matchingRelative
                val noteDetail = if (member.notes.isNotBlank()) " Caregiver note: \"${member.notes}\"." else ""
                
                // Specific intelligence: check if note mentions a specific day (like Sunday)
                val sundayNote = member.notes.contains("sunday", ignoreCase = true)
                val isSundayToday = dayOfWeek.equals("Sunday", ignoreCase = true)
                
                val dayContext = if (sundayNote && isSundayToday) {
                    " Since today is Sunday, did ${member.name} visit or call you today?"
                } else if (member.notes.contains("pitha", ignoreCase = true)) {
                    " Remember how much ${member.name} loves your sweet homemade pitha?"
                } else if (member.notes.contains("call", ignoreCase = true)) {
                    " Have you talked to ${member.name} recently?"
                } else {
                    " You share such a wonderful bond together 🌼"
                }

                "$patientName, that's your beloved ${member.relation}, ${member.name} ${member.avatarEmoji}!$dayContext$noteDetail"
            }

            // General Family Questions
            lower.contains("family") || lower.contains("relative") || lower.contains("children") || lower.contains("grandson") || lower.contains("daughter") -> {
                if (familyMembers.isEmpty()) {
                    "$patientName, your family loves you dearly. Your caregiver can add photos of your children and grandchildren in the Caregiver Dashboard."
                } else {
                    val namesList = familyMembers.joinToString(", ") { "${it.name} (${it.relation})" }
                    val randomRelative = familyMembers.random()
                    val anecdote = if (randomRelative.notes.isNotBlank()) " Remember: ${randomRelative.notes}." else ""
                    "$patientName, you have a beautiful family: $namesList. Your ${randomRelative.relation}, ${randomRelative.name} ${randomRelative.avatarEmoji}, is always thinking of you!$anecdote"
                }
            }

            // Medicine Questions
            lower.contains("medicine") || lower.contains("tablet") || lower.contains("pill") || lower.contains("dose") || lower.contains("med") -> {
                val takenMeds = medicines.filter { it.isTaken }
                val remainingMeds = medicines.filter { !it.isTaken }
                when {
                    medicines.isEmpty() -> "$patientName, no medicines are scheduled for today in your records. You are doing well!"
                    remainingMeds.isEmpty() -> "Wonderful news, $patientName! 🎉 You have taken all ${medicines.size} of your scheduled medicines for today. Great job keeping your mind and body healthy!"
                    else -> {
                        val nextMed = remainingMeds.first()
                        val takenCount = takenMeds.size
                        "$patientName, you have taken $takenCount of ${medicines.size} medicines today. You still have '${nextMed.name}' (${nextMed.dosage}) scheduled at ${nextMed.scheduledTime}. Would you like help with it?"
                    }
                }
            }

            // Hydration / Water Questions
            lower.contains("water") || lower.contains("drink") || lower.contains("hydration") || lower.contains("thirsty") -> {
                val drank = hydration.glassesDrunk
                val target = hydration.targetGlasses
                if (drank >= target) {
                    "Splendid, $patientName! 💧 You've already reached your daily goal with $drank glasses of water today. Excellent hydration!"
                } else {
                    "You've had $drank of your $target water glasses today, $patientName. Drinking a warm cup of water or Assam tea right now would feel very refreshing 🍵"
                }
            }

            // Daily Routine Questions
            lower.contains("routine") || lower.contains("task") || lower.contains("schedule") || lower.contains("today") || lower.contains("what should i do") -> {
                val pending = routines.filter { !it.isCompleted }
                val done = routines.filter { it.isCompleted }
                if (pending.isEmpty() && routines.isNotEmpty()) {
                    "$patientName, you've completed all your routine activities for today! It's a peaceful time to relax and listen to some music 🌤️"
                } else if (pending.isNotEmpty()) {
                    val nextTask = pending.first()
                    "$patientName, you have completed ${done.size} of ${routines.size} tasks today. Next on your daily schedule: ${nextTask.emoji} '${nextTask.title}' at ${nextTask.timeStr}."
                } else {
                    "$patientName, today is $dayOfWeek in $location. A gentle walk and a warm cup of tea make for a lovely day!"
                }
            }

            // Mood & Emotion: Sad / Lonely / Anxious / Confused
            lower.contains("sad") || lower.contains("lonely") || lower.contains("scared") || lower.contains("afraid") || lower.contains("anxious") || lower.contains("confused") || lower.contains("worried") -> {
                "It is completely okay to feel this way, $patientName 🌸. You are safe in your loving home in $location. I am right here with you, and your caregiver ${profile.caregiverName} is watching over you. Would you like to hear a peaceful story or talk about your garden?"
            }

            // Mood: Happy / Good / Great
            lower.contains("happy") || lower.contains("good") || lower.contains("great") || lower.contains("fine") || lower.contains("peaceful") || lower.contains("joy") -> {
                "That brings so much joy to my heart, $patientName! 😊 Keep that bright smile. When you are feeling happy, your mind stays calm and strong. What made you smile today?"
            }

            // Cultural & Regional (Assam / NER / Bihu / Tea / River)
            lower.contains("tea") || lower.contains("garden") || lower.contains("bihu") || lower.contains("assam") || lower.contains("song") || lower.contains("music") || lower.contains("pitha") -> {
                val topPref = preferences.maxByOrNull { it.value }?.key ?: "tea"
                when {
                    lower.contains("bihu") -> "Bihu is such a joyous celebration, $patientName! 🪕 The rhythmic sound of the Dhol, the red and white Gamusa, and the aroma of fresh coconut Pitha fill the home with happiness. Do you remember dancing to Bihu songs in your youth?"
                    lower.contains("tea") -> "Ah, the fresh aroma of Assam tea gardens 🍃! Warm morning tea with fresh air is one of the greatest pleasures of life in $location. How do you like your tea brewed, Aita?"
                    else -> "Life in the North-Eastern greenery has such a gentle, soothing rhythm, $patientName. The cool Brahmaputra breeze and the green hills bring peace to the soul."
                }
            }

            // Greetings & Namaskar
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaskar") || lower.contains("nomoskar") || lower.contains("hey") -> {
                "$timeGreeting, $patientName! 🌤️ It is wonderful to chat with you on this $dayOfWeek. How are you feeling today?"
            }

            // Farewell / Good Night
            lower.contains("bye") || lower.contains("good night") || lower.contains("sleep") || lower.contains("rest") -> {
                "Rest well, sweet $patientName 🌙. May your sleep be peaceful and restorative. I am always right here whenever you want to talk."
            }

            // Default Empathetic Dialogue (Learned Personalization)
            else -> {
                val favTopic = when {
                    (preferences["tea"] ?: 0) > 2 -> "Would you like to talk about your favorite tea or garden?"
                    (preferences["family"] ?: 0) > 2 -> "Shall we look at memories of your family?"
                    (preferences["bihu"] ?: 0) > 2 -> "Would you like to reminisce about Bihu festivals?"
                    else -> "We can talk about your loved ones, your daily routine, or peaceful stories of Assam."
                }
                "I hear you, $patientName 🌼. $favTopic I am always here to keep you company and listen to your thoughts."
            }
        }

        val category = when {
            lower.contains("medicine") || lower.contains("pill") -> "medicine"
            lower.contains("family") || matchingRelative != null -> "family"
            lower.contains("water") || lower.contains("drink") -> "hydration"
            lower.contains("sad") || lower.contains("happy") || lower.contains("worried") -> "mood"
            lower.contains("tea") || lower.contains("bihu") || lower.contains("assam") -> "cultural"
            else -> "general"
        }

        val responseMessage = ChatMessage(
            sender = "smriti",
            text = responseText,
            timestamp = System.currentTimeMillis(),
            topicCategory = category
        )

        // Save AI response to SQLite history
        repository.saveChatMessage(responseMessage)

        return responseMessage
    }

    private suspend fun detectAndSavePreferences(text: String) {
        val keywords = mapOf(
            "tea" to listOf("tea", "chai", "garden", "leaves"),
            "bihu" to listOf("bihu", "dhol", "dance", "song", "festival", "pitha"),
            "family" to listOf("rahul", "ananya", "deben", "meera", "priya", "children", "grandson", "daughter"),
            "nature" to listOf("river", "walk", "flowers", "birds", "trees", "weather"),
            "prayer" to listOf("prayer", "puja", "naam", "prasanga", "temple")
        )

        for ((category, words) in keywords) {
            if (words.any { text.contains(it) }) {
                repository.recordPreferenceMention(category)
            }
        }
    }
}
