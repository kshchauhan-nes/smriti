package com.example.myapplication.data

data class GameSessionRecord(
    val id: Long = 0,
    val gameType: String = "Picture Recall",
    val score: Int,
    val level: Int,
    val responseTimeMs: Long,
    val correctAnswers: Int,
    val totalObjects: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class MedicineItem(
    val id: Long = 0,
    val name: String,
    val dosage: String,
    val scheduledTime: String,
    val instructions: String = "",
    val isTaken: Boolean = false,
    val lastTakenTimestamp: Long = 0L
)

data class HydrationData(
    val dateKey: String,
    val glassesDrunk: Int,
    val targetGlasses: Int = 8,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class RoutineItem(
    val id: Long = 0,
    val title: String,
    val timeStr: String,
    val emoji: String,
    val isCompleted: Boolean = false,
    val dateKey: String = ""
)

data class FamilyMember(
    val id: Long = 0,
    val name: String,
    val relation: String,
    val avatarEmoji: String = "👤",
    val photoUri: String = "",
    val notes: String = ""
)

data class PatientProfile(
    val patientName: String = "Aita",
    val caregiverName: String = "Priya Sharma",
    val caregiverPhone: String = "9876543210",
    val location: String = "Jorhat, Assam (NER)",
    val preferredLanguage: String = "Assamese / English"
)

data class ChatMessage(
    val id: Long = 0,
    val sender: String, // "user" or "smriti"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val topicCategory: String = "general"
)

data class PatientPreference(
    val id: Long = 0,
    val topicKey: String,
    val interestLevel: Int = 1,
    val lastMentioned: Long = System.currentTimeMillis()
)
