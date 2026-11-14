package com.example.myapplication.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.myapplication.reminders.ScheduleTime

class SmritiDatabaseHelper(context: Context, databaseName: String = DATABASE_NAME) :
    SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {

    fun ensureDailyState(now: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        val today = ScheduleTime.dateKey(now)
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE $TABLE_MEDICINES SET $COL_MED_IS_TAKEN = 0 WHERE $COL_MED_IS_TAKEN != 0 " +
                    "AND ($COL_MED_LAST_TAKEN < ? OR $COL_MED_LAST_TAKEN >= ?)",
                arrayOf(ScheduleTime.startOfDay(now), ScheduleTime.nextMidnight(now))
            )
            val hasToday = db.rawQuery(
                "SELECT 1 FROM $TABLE_ROUTINE WHERE $COL_ROUTINE_DATE = ? LIMIT 1", arrayOf(today)
            ).use { it.moveToFirst() }
            if (!hasToday) {
                db.execSQL(
                    "INSERT INTO $TABLE_ROUTINE ($COL_ROUTINE_TITLE, $COL_ROUTINE_TIME, " +
                        "$COL_ROUTINE_EMOJI, $COL_ROUTINE_COMPLETED, $COL_ROUTINE_DATE) " +
                        "SELECT $COL_ROUTINE_TITLE, $COL_ROUTINE_TIME, $COL_ROUTINE_EMOJI, 0, ? " +
                        "FROM $TABLE_ROUTINE WHERE $COL_ROUTINE_DATE = " +
                        "(SELECT MAX($COL_ROUTINE_DATE) FROM $TABLE_ROUTINE) ORDER BY $COL_ROUTINE_ID",
                    arrayOf(today)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val DATABASE_NAME = "smriti_offline.db"
        const val DATABASE_VERSION = 4

        // Table: Game Sessions
        const val TABLE_GAME_SESSIONS = "game_sessions"
        const val COL_SESSION_ID = "id"
        const val COL_GAME_TYPE = "game_type"
        const val COL_SCORE = "score"
        const val COL_LEVEL = "level"
        const val COL_RESPONSE_TIME = "response_time_ms"
        const val COL_CORRECT_ANSWERS = "correct_answers"
        const val COL_TOTAL_OBJECTS = "total_objects"
        const val COL_SESSION_TIMESTAMP = "timestamp"

        // Table: Medicines
        const val TABLE_MEDICINES = "medicines"
        const val COL_MED_ID = "id"
        const val COL_MED_NAME = "name"
        const val COL_MED_DOSAGE = "dosage"
        const val COL_MED_TIME = "scheduled_time"
        const val COL_MED_INSTRUCTIONS = "instructions"
        const val COL_MED_IS_TAKEN = "is_taken"
        const val COL_MED_LAST_TAKEN = "last_taken_timestamp"

        // Table: Hydration Logs
        const val TABLE_HYDRATION = "hydration_logs"
        const val COL_HYD_DATE = "date_key"
        const val COL_HYD_GLASSES = "glasses_drunk"
        const val COL_HYD_TARGET = "target_glasses"
        const val COL_HYD_LAST_UPDATED = "last_updated"

        // Table: Routine Tasks
        const val TABLE_ROUTINE = "routine_tasks"
        const val COL_ROUTINE_ID = "id"
        const val COL_ROUTINE_TITLE = "title"
        const val COL_ROUTINE_TIME = "time_str"
        const val COL_ROUTINE_EMOJI = "emoji"
        const val COL_ROUTINE_COMPLETED = "is_completed"
        const val COL_ROUTINE_DATE = "date_key"

        // Table: Family Members
        const val TABLE_FAMILY = "family_members"
        const val COL_FAM_ID = "id"
        const val COL_FAM_NAME = "name"
        const val COL_FAM_RELATION = "relation"
        const val COL_FAM_EMOJI = "avatar_emoji"
        const val COL_FAM_PHOTO = "photo_uri"
        const val COL_FAM_NOTES = "notes"

        // Table: AI Chat Messages
        const val TABLE_CHAT = "ai_chat_messages"
        const val COL_CHAT_ID = "id"
        const val COL_CHAT_SENDER = "sender"
        const val COL_CHAT_TEXT = "text"
        const val COL_CHAT_TIME = "timestamp"
        const val COL_CHAT_TOPIC = "topic_category"

        // Table: Patient Preferences & Learned Facts
        const val TABLE_PREFERENCES = "patient_preferences"
        const val COL_PREF_ID = "id"
        const val COL_PREF_KEY = "topic_key"
        const val COL_PREF_LEVEL = "interest_level"
        const val COL_PREF_TIME = "last_mentioned"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSessionsTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_GAME_SESSIONS (
                $COL_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_GAME_TYPE TEXT NOT NULL,
                $COL_SCORE INTEGER NOT NULL,
                $COL_LEVEL INTEGER NOT NULL,
                $COL_RESPONSE_TIME INTEGER NOT NULL,
                $COL_CORRECT_ANSWERS INTEGER NOT NULL,
                $COL_TOTAL_OBJECTS INTEGER NOT NULL,
                $COL_SESSION_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()

        val createMedicinesTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_MEDICINES (
                $COL_MED_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MED_NAME TEXT NOT NULL,
                $COL_MED_DOSAGE TEXT NOT NULL,
                $COL_MED_TIME TEXT NOT NULL,
                $COL_MED_INSTRUCTIONS TEXT,
                $COL_MED_IS_TAKEN INTEGER DEFAULT 0,
                $COL_MED_LAST_TAKEN INTEGER DEFAULT 0
            )
        """.trimIndent()

        val createHydrationTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_HYDRATION (
                $COL_HYD_DATE TEXT PRIMARY KEY,
                $COL_HYD_GLASSES INTEGER NOT NULL,
                $COL_HYD_TARGET INTEGER NOT NULL,
                $COL_HYD_LAST_UPDATED INTEGER NOT NULL
            )
        """.trimIndent()

        val createRoutineTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_ROUTINE (
                $COL_ROUTINE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ROUTINE_TITLE TEXT NOT NULL,
                $COL_ROUTINE_TIME TEXT NOT NULL,
                $COL_ROUTINE_EMOJI TEXT NOT NULL,
                $COL_ROUTINE_COMPLETED INTEGER DEFAULT 0,
                $COL_ROUTINE_DATE TEXT NOT NULL
            )
        """.trimIndent()

        val createFamilyTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_FAMILY (
                $COL_FAM_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FAM_NAME TEXT NOT NULL,
                $COL_FAM_RELATION TEXT NOT NULL,
                $COL_FAM_EMOJI TEXT NOT NULL,
                $COL_FAM_PHOTO TEXT,
                $COL_FAM_NOTES TEXT
            )
        """.trimIndent()

        val createChatTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_CHAT (
                $COL_CHAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CHAT_SENDER TEXT NOT NULL,
                $COL_CHAT_TEXT TEXT NOT NULL,
                $COL_CHAT_TIME INTEGER NOT NULL,
                $COL_CHAT_TOPIC TEXT NOT NULL
            )
        """.trimIndent()

        val createPreferencesTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_PREFERENCES (
                $COL_PREF_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PREF_KEY TEXT UNIQUE NOT NULL,
                $COL_PREF_LEVEL INTEGER DEFAULT 1,
                $COL_PREF_TIME INTEGER NOT NULL
            )
        """.trimIndent()

        db.execSQL(createSessionsTable)
        db.execSQL(createMedicinesTable)
        db.execSQL(createHydrationTable)
        db.execSQL(createRoutineTable)
        db.execSQL(createFamilyTable)
        db.execSQL(createChatTable)
        db.execSQL(createPreferencesTable)

        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            val createChatTable = """
                CREATE TABLE IF NOT EXISTS $TABLE_CHAT (
                    $COL_CHAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_CHAT_SENDER TEXT NOT NULL,
                    $COL_CHAT_TEXT TEXT NOT NULL,
                    $COL_CHAT_TIME INTEGER NOT NULL,
                    $COL_CHAT_TOPIC TEXT NOT NULL
                )
            """.trimIndent()

            val createPreferencesTable = """
                CREATE TABLE IF NOT EXISTS $TABLE_PREFERENCES (
                    $COL_PREF_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_PREF_KEY TEXT UNIQUE NOT NULL,
                    $COL_PREF_LEVEL INTEGER DEFAULT 1,
                    $COL_PREF_TIME INTEGER NOT NULL
                )
            """.trimIndent()

            db.execSQL(createChatTable)
            db.execSQL(createPreferencesTable)
        }
    }

    private fun seedFamilyData(db: SQLiteDatabase) {
        val familyList = listOf(
            FamilyMember(name = "Rahul", relation = "Grandson (নাতি)", avatarEmoji = "👦", notes = "Studies in college, loves your homemade sweet coconut pitha"),
            FamilyMember(name = "Ananya", relation = "Daughter (জীয়াৰী)", avatarEmoji = "👩", notes = "Visits every Sunday and brings fresh fruit"),
            FamilyMember(name = "Priya", relation = "Caregiver / Nurse", avatarEmoji = "👩‍⚕️", notes = "Helps with daily morning routine & medicines"),
            FamilyMember(name = "Deben", relation = "Brother (ভাইটি)", avatarEmoji = "👨‍🦳", notes = "Lives nearby in Jorhat town and loves tea garden walks"),
            FamilyMember(name = "Meera", relation = "Granddaughter (নাতিনী)", avatarEmoji = "👧", notes = "Loves drawing flowers and listening to your Bihu folk tales")
        )

        for (member in familyList) {
            val values = ContentValues().apply {
                put(COL_FAM_NAME, member.name)
                put(COL_FAM_RELATION, member.relation)
                put(COL_FAM_EMOJI, member.avatarEmoji)
                put(COL_FAM_PHOTO, member.photoUri)
                put(COL_FAM_NOTES, member.notes)
            }
            db.insert(TABLE_FAMILY, null, values)
        }
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val today = ScheduleTime.dateKey(System.currentTimeMillis())

        // Seed default daily medicines
        val med1 = ContentValues().apply {
            put(COL_MED_NAME, "Donepezil (Memory Support)")
            put(COL_MED_DOSAGE, "5 mg • 1 Tablet")
            put(COL_MED_TIME, "08:30 AM")
            put(COL_MED_INSTRUCTIONS, "Take after breakfast with water")
            put(COL_MED_IS_TAKEN, 0)
            put(COL_MED_LAST_TAKEN, 0L)
        }
        val med2 = ContentValues().apply {
            put(COL_MED_NAME, "Multivitamin & Omega-3")
            put(COL_MED_DOSAGE, "1 Capsule")
            put(COL_MED_TIME, "01:00 PM")
            put(COL_MED_INSTRUCTIONS, "Take after lunch")
            put(COL_MED_IS_TAKEN, 0)
            put(COL_MED_LAST_TAKEN, 0L)
        }
        val med3 = ContentValues().apply {
            put(COL_MED_NAME, "Blood Pressure (Amlodipine)")
            put(COL_MED_DOSAGE, "5 mg • 1 Tablet")
            put(COL_MED_TIME, "08:00 PM")
            put(COL_MED_INSTRUCTIONS, "Take after dinner before sleeping")
            put(COL_MED_IS_TAKEN, 0)
            put(COL_MED_LAST_TAKEN, 0L)
        }
        db.insert(TABLE_MEDICINES, null, med1)
        db.insert(TABLE_MEDICINES, null, med2)
        db.insert(TABLE_MEDICINES, null, med3)

        // Seed default hydration for today
        val hyd = ContentValues().apply {
            put(COL_HYD_DATE, today)
            put(COL_HYD_GLASSES, 3)
            put(COL_HYD_TARGET, 8)
            put(COL_HYD_LAST_UPDATED, System.currentTimeMillis())
        }
        db.insert(TABLE_HYDRATION, null, hyd)

        // Seed default daily routine tasks
        val routines = listOf(
            Triple("Morning Tea & Fresh Air", "07:00 AM", "🍵"),
            Triple("Morning Prayer / Naam Prasanga", "08:00 AM", "🙏"),
            Triple("Breakfast & Morning Medicine", "08:30 AM", "🥣"),
            Triple("Memory Games Session", "10:30 AM", "🧠"),
            Triple("Lunch & Midday Rest", "01:00 PM", "🍛"),
            Triple("Afternoon Walk in Tea Garden", "04:30 PM", "🌿"),
            Triple("Family Chat & Music", "06:30 PM", "📻"),
            Triple("Dinner & Evening Medicine", "08:30 PM", "🌙")
        )

        for (item in routines) {
            val routineValues = ContentValues().apply {
                put(COL_ROUTINE_TITLE, item.first)
                put(COL_ROUTINE_TIME, item.second)
                put(COL_ROUTINE_EMOJI, item.third)
                put(COL_ROUTINE_COMPLETED, 0)
                put(COL_ROUTINE_DATE, today)
            }
            db.insert(TABLE_ROUTINE, null, routineValues)
        }

        seedFamilyData(db)

        // Initial welcome chat from Smriti
        val welcomeMsg = ContentValues().apply {
            put(COL_CHAT_SENDER, "smriti")
            put(COL_CHAT_TEXT, "Namaskar Aita! 🌸 I am Smriti, your friendly companion. How are you feeling today? We can chat about your family, daily routine, favorite memories, or anything on your heart.")
            put(COL_CHAT_TIME, System.currentTimeMillis())
            put(COL_CHAT_TOPIC, "welcome")
        }
        db.insert(TABLE_CHAT, null, welcomeMsg)
    }
}
