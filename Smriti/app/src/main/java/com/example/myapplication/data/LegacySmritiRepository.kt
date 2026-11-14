package com.example.myapplication.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.myapplication.reminders.ScheduleTime
import com.example.myapplication.FamilyPhotoStore

class LegacySmritiRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dbHelper = SmritiDatabaseHelper(context.applicationContext)

    fun close() = dbHelper.close()

    private fun getTodayDateKey(): String {
        return ScheduleTime.dateKey(System.currentTimeMillis())
    }

    // ==========================================
    // PATIENT PROFILE
    // ==========================================

    suspend fun getPatientProfile(): PatientProfile = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences("patient_profile_local", Context.MODE_PRIVATE)
        PatientProfile(
            patientName = prefs.getString("patient_name", "Aita") ?: "Aita",
            caregiverName = prefs.getString("caregiver_name", "Priya Sharma") ?: "Priya Sharma",
            caregiverPhone = prefs.getString("caregiver_phone", "9876543210") ?: "9876543210",
            location = prefs.getString("location", "Jorhat, Assam (NER)") ?: "Jorhat, Assam (NER)",
            preferredLanguage = prefs.getString("language", "Assamese / English") ?: "Assamese / English"
        )
    }

    suspend fun savePatientProfile(profile: PatientProfile) = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences("patient_profile_local", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("patient_name", profile.patientName)
            .putString("caregiver_name", profile.caregiverName)
            .putString("caregiver_phone", profile.caregiverPhone)
            .putString("location", profile.location)
            .putString("language", profile.preferredLanguage)
            .apply()
    }

    // ==========================================
    // GAME SESSIONS
    // ==========================================

    suspend fun saveGameSession(session: GameSessionRecord): Long = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_GAME_TYPE, session.gameType)
                put(SmritiDatabaseHelper.COL_SCORE, session.score)
                put(SmritiDatabaseHelper.COL_LEVEL, session.level)
                put(SmritiDatabaseHelper.COL_RESPONSE_TIME, session.responseTimeMs)
                put(SmritiDatabaseHelper.COL_CORRECT_ANSWERS, session.correctAnswers)
                put(SmritiDatabaseHelper.COL_TOTAL_OBJECTS, session.totalObjects)
                put(SmritiDatabaseHelper.COL_SESSION_TIMESTAMP, session.timestamp)
            }
            db.insert(SmritiDatabaseHelper.TABLE_GAME_SESSIONS, null, values)
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    suspend fun getAllGameSessions(): List<GameSessionRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<GameSessionRecord>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                SmritiDatabaseHelper.TABLE_GAME_SESSIONS,
                null,
                null,
                null,
                null,
                null,
                "${SmritiDatabaseHelper.COL_SESSION_TIMESTAMP} DESC"
            )
            cursor.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_SESSION_ID))
                        val gameType = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_GAME_TYPE))
                        val score = it.getInt(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_SCORE))
                        val level = it.getInt(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_LEVEL))
                        val responseTime = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_RESPONSE_TIME))
                        val correct = it.getInt(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_CORRECT_ANSWERS))
                        val total = it.getInt(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_TOTAL_OBJECTS))
                        val timestamp = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_SESSION_TIMESTAMP))

                        list.add(
                            GameSessionRecord(
                                id = id,
                                gameType = gameType,
                                score = score,
                                level = level,
                                responseTimeMs = responseTime,
                                correctAnswers = correct,
                                totalObjects = total,
                                timestamp = timestamp
                            )
                        )
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    // ==========================================
    // MEDICINES
    // ==========================================

    suspend fun getAllMedicines(): List<MedicineItem> = withContext(Dispatchers.IO) {
        dbHelper.ensureDailyState()
        val list = mutableListOf<MedicineItem>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                SmritiDatabaseHelper.TABLE_MEDICINES,
                null,
                null,
                null,
                null,
                null,
                "${SmritiDatabaseHelper.COL_MED_ID} ASC"
            )
            cursor.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_MED_ID))
                        val name = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_MED_NAME))
                        val dosage = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_MED_DOSAGE))
                        val time = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_MED_TIME))
                        val instructions = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_MED_INSTRUCTIONS)) ?: ""
                        val isTaken = it.getInt(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_MED_IS_TAKEN)) == 1
                        val lastTaken = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_MED_LAST_TAKEN))

                        list.add(
                            MedicineItem(
                                id = id,
                                name = name,
                                dosage = dosage,
                                scheduledTime = time,
                                instructions = instructions,
                                isTaken = isTaken,
                                lastTakenTimestamp = lastTaken
                            )
                        )
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun toggleMedicineTaken(medicineId: Long, isTaken: Boolean) = withContext(Dispatchers.IO) {
        dbHelper.ensureDailyState()
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_MED_IS_TAKEN, if (isTaken) 1 else 0)
                put(SmritiDatabaseHelper.COL_MED_LAST_TAKEN, if (isTaken) System.currentTimeMillis() else 0L)
            }
            db.update(
                SmritiDatabaseHelper.TABLE_MEDICINES,
                values,
                "${SmritiDatabaseHelper.COL_MED_ID} = ?",
                arrayOf(medicineId.toString())
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateMedicine(m: MedicineItem) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(SmritiDatabaseHelper.COL_MED_NAME, m.name.trim())
            put(SmritiDatabaseHelper.COL_MED_DOSAGE, m.dosage.trim())
            put(SmritiDatabaseHelper.COL_MED_TIME, m.scheduledTime)
            put(SmritiDatabaseHelper.COL_MED_INSTRUCTIONS, m.instructions.trim())
        }
        check(dbHelper.writableDatabase.update(SmritiDatabaseHelper.TABLE_MEDICINES, values,
            "${SmritiDatabaseHelper.COL_MED_ID} = ?", arrayOf(m.id.toString())) == 1) { "Medicine no longer exists. Reopen the editor." }
    }
    suspend fun updateRoutine(r: RoutineItem) = withContext(Dispatchers.IO) {
        require(r.dateKey == getTodayDateKey()) { "The day has changed. Reopen today's routine to edit it." }
        val values = ContentValues().apply {
            put(SmritiDatabaseHelper.COL_ROUTINE_TITLE, r.title.trim())
            put(SmritiDatabaseHelper.COL_ROUTINE_TIME, r.timeStr)
            put(SmritiDatabaseHelper.COL_ROUTINE_EMOJI, r.emoji)
        }
        check(dbHelper.writableDatabase.update(SmritiDatabaseHelper.TABLE_ROUTINE, values,
            "${SmritiDatabaseHelper.COL_ROUTINE_ID} = ?", arrayOf(r.id.toString())) == 1) { "Routine no longer exists. Reopen the editor." }
    }

    suspend fun addMedicine(medicine: MedicineItem): Long = withContext(Dispatchers.IO) {
        require(ScheduleTime.parse(medicine.scheduledTime) != null) { "Enter a valid scheduled time" }
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_MED_NAME, medicine.name)
                put(SmritiDatabaseHelper.COL_MED_DOSAGE, medicine.dosage)
                put(SmritiDatabaseHelper.COL_MED_TIME, medicine.scheduledTime)
                put(SmritiDatabaseHelper.COL_MED_INSTRUCTIONS, medicine.instructions)
                put(SmritiDatabaseHelper.COL_MED_IS_TAKEN, if (medicine.isTaken) 1 else 0)
                put(SmritiDatabaseHelper.COL_MED_LAST_TAKEN, medicine.lastTakenTimestamp)
            }
            db.insert(SmritiDatabaseHelper.TABLE_MEDICINES, null, values)
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    // ==========================================
    // HYDRATION
    // ==========================================

    suspend fun getTodayHydration(): HydrationData = withContext(Dispatchers.IO) {
        val todayKey = getTodayDateKey()
        var existingData: HydrationData? = null
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                SmritiDatabaseHelper.TABLE_HYDRATION,
                null,
                "${SmritiDatabaseHelper.COL_HYD_DATE} = ?",
                arrayOf(todayKey),
                null,
                null,
                null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val glasses = it.getInt(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_HYD_GLASSES))
                    val target = it.getInt(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_HYD_TARGET))
                    val lastUpdated = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_HYD_LAST_UPDATED))
                    existingData = HydrationData(todayKey, glasses, target, lastUpdated)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (existingData != null) {
            existingData!!
        } else {
            val newHydration = HydrationData(todayKey, 0, 8, System.currentTimeMillis())
            saveHydration(newHydration)
            newHydration
        }
    }

    suspend fun saveHydration(hydration: HydrationData) = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_HYD_DATE, hydration.dateKey)
                put(SmritiDatabaseHelper.COL_HYD_GLASSES, hydration.glassesDrunk)
                put(SmritiDatabaseHelper.COL_HYD_TARGET, hydration.targetGlasses)
                put(SmritiDatabaseHelper.COL_HYD_LAST_UPDATED, hydration.lastUpdated)
            }
            db.insertWithOnConflict(
                SmritiDatabaseHelper.TABLE_HYDRATION,
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun incrementHydration(): HydrationData = withContext(Dispatchers.IO) {
        val current = getTodayHydration()
        val updated = current.copy(
            glassesDrunk = (current.glassesDrunk + 1).coerceAtMost(16),
            lastUpdated = System.currentTimeMillis()
        )
        saveHydration(updated)
        updated
    }

    suspend fun decrementHydration(): HydrationData = withContext(Dispatchers.IO) {
        val current = getTodayHydration()
        val updated = current.copy(
            glassesDrunk = (current.glassesDrunk - 1).coerceAtLeast(0),
            lastUpdated = System.currentTimeMillis()
        )
        saveHydration(updated)
        updated
    }

    // ==========================================
    // ROUTINE TASKS
    // ==========================================

    suspend fun getTodayRoutines(): List<RoutineItem> = getRoutinesForDate(getTodayDateKey())

    suspend fun getRoutinesForDate(dateKey: String): List<RoutineItem> = withContext(Dispatchers.IO) {
        dbHelper.ensureDailyState()
        val todayKey = dateKey
        val list = mutableListOf<RoutineItem>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                SmritiDatabaseHelper.TABLE_ROUTINE,
                null,
                "${SmritiDatabaseHelper.COL_ROUTINE_DATE} = ?",
                arrayOf(todayKey),
                null,
                null,
                "${SmritiDatabaseHelper.COL_ROUTINE_ID} ASC"
            )
            cursor.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_ROUTINE_ID))
                        val title = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_ROUTINE_TITLE))
                        val time = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_ROUTINE_TIME))
                        val emoji = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_ROUTINE_EMOJI))
                        val isDone = it.getInt(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_ROUTINE_COMPLETED)) == 1

                        list.add(RoutineItem(id, title, time, emoji, isDone, todayKey))
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun toggleRoutineCompleted(routineId: Long, isDone: Boolean) = withContext(Dispatchers.IO) {
        dbHelper.ensureDailyState()
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_ROUTINE_COMPLETED, if (isDone) 1 else 0)
            }
            db.update(
                SmritiDatabaseHelper.TABLE_ROUTINE,
                values,
                "${SmritiDatabaseHelper.COL_ROUTINE_ID} = ? AND ${SmritiDatabaseHelper.COL_ROUTINE_DATE} = ?",
                arrayOf(routineId.toString(), getTodayDateKey())
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveRoutine(routine: RoutineItem): Long = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_ROUTINE_TITLE, routine.title.trim())
                put(SmritiDatabaseHelper.COL_ROUTINE_TIME, routine.timeStr.trim())
                put(SmritiDatabaseHelper.COL_ROUTINE_EMOJI, routine.emoji)
                put(SmritiDatabaseHelper.COL_ROUTINE_COMPLETED, if (routine.isCompleted) 1 else 0)
                put(SmritiDatabaseHelper.COL_ROUTINE_DATE, if (routine.dateKey.isNotBlank()) routine.dateKey else getTodayDateKey())
            }
            db.insert(SmritiDatabaseHelper.TABLE_ROUTINE, null, values)
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    // ==========================================
    // FAMILY MEMBERS & RELATIVES
    // ==========================================

    suspend fun getAllFamilyMembers(): List<FamilyMember> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FamilyMember>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                SmritiDatabaseHelper.TABLE_FAMILY,
                null,
                null,
                null,
                null,
                null,
                "${SmritiDatabaseHelper.COL_FAM_ID} ASC"
            )
            cursor.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_FAM_ID))
                        val name = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_FAM_NAME))
                        val relation = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_FAM_RELATION))
                        val emoji = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_FAM_EMOJI)) ?: "👤"
                        val photoUri = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_FAM_PHOTO)) ?: ""
                        val notes = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_FAM_NOTES)) ?: ""

                        list.add(FamilyMember(id, name, relation, emoji, photoUri, notes))
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun addFamilyMember(member: FamilyMember): Long = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_FAM_NAME, member.name)
                put(SmritiDatabaseHelper.COL_FAM_RELATION, member.relation)
                put(SmritiDatabaseHelper.COL_FAM_EMOJI, member.avatarEmoji)
                put(SmritiDatabaseHelper.COL_FAM_PHOTO, member.photoUri)
                put(SmritiDatabaseHelper.COL_FAM_NOTES, member.notes)
            }
            db.insert(SmritiDatabaseHelper.TABLE_FAMILY, null, values)
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    suspend fun saveFamilyProfile(member: FamilyMember): Long = withContext(Dispatchers.IO) {
        require(member.name.isNotBlank() && member.relation.isNotBlank() && member.notes.isNotBlank()) {
            "Enter the person's name, relationship, and information about the photo"
        }
        var importedPhoto = false
        val photo = if (member.photoUri.isNotBlank()) {
            val existing = getAllFamilyMembers().firstOrNull { it.id == member.id }
            val reused = existing?.photoUri == member.photoUri && FamilyPhotoStore.isOwned(appContext, member.photoUri)
            if (reused) member.photoUri else FamilyPhotoStore.importPhoto(appContext, member.photoUri).also { importedPhoto = true }
        } else ""

        try {
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_FAM_NAME, member.name.trim())
                put(SmritiDatabaseHelper.COL_FAM_RELATION, member.relation.trim())
                put(SmritiDatabaseHelper.COL_FAM_EMOJI, member.avatarEmoji)
                put(SmritiDatabaseHelper.COL_FAM_PHOTO, photo)
                put(SmritiDatabaseHelper.COL_FAM_NOTES, member.notes.trim())
            }
            val db = dbHelper.writableDatabase
            val id = if (member.id == 0L) db.insertOrThrow(SmritiDatabaseHelper.TABLE_FAMILY, null, values)
                else {
                    check(db.update(SmritiDatabaseHelper.TABLE_FAMILY, values, "id = ?", arrayOf(member.id.toString())) == 1) {
                        "This relative no longer exists"
                    }
                    member.id
                }
            id
        } catch (e: Exception) {
            if (importedPhoto && photo.isNotBlank()) FamilyPhotoStore.deleteOwned(appContext, photo)
            throw e
        }
    }

    suspend fun deleteFamilyMember(memberId: Long) = withContext(Dispatchers.IO) {
        try {
            val existing = getAllFamilyMembers().firstOrNull { it.id == memberId }
            val db = dbHelper.writableDatabase
            db.delete(
                SmritiDatabaseHelper.TABLE_FAMILY,
                "${SmritiDatabaseHelper.COL_FAM_ID} = ?",
                arrayOf(memberId.toString())
            )
            existing?.let { FamilyPhotoStore.deleteOwned(appContext, it.photoUri) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================
    // AI CHAT MESSAGES & CONVERSATION MEMORY
    // ==========================================

    suspend fun getChatHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ChatMessage>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                SmritiDatabaseHelper.TABLE_CHAT,
                null,
                null,
                null,
                null,
                null,
                "${SmritiDatabaseHelper.COL_CHAT_TIME} ASC"
            )
            cursor.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_CHAT_ID))
                        val sender = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_CHAT_SENDER))
                        val text = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_CHAT_TEXT))
                        val time = it.getLong(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_CHAT_TIME))
                        val topic = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_CHAT_TOPIC))

                        list.add(ChatMessage(id, sender, text, time, topic))
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun saveChatMessage(msg: ChatMessage): Long = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_CHAT_SENDER, msg.sender)
                put(SmritiDatabaseHelper.COL_CHAT_TEXT, msg.text)
                put(SmritiDatabaseHelper.COL_CHAT_TIME, msg.timestamp)
                put(SmritiDatabaseHelper.COL_CHAT_TOPIC, msg.topicCategory)
            }
            db.insert(SmritiDatabaseHelper.TABLE_CHAT, null, values)
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    suspend fun getLearnedPreferences(): Map<String, Int> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, Int>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                SmritiDatabaseHelper.TABLE_PREFERENCES,
                null,
                null,
                null,
                null,
                null,
                "${SmritiDatabaseHelper.COL_PREF_LEVEL} DESC"
            )
            cursor.use {
                if (it.moveToFirst()) {
                    do {
                        val key = it.getString(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_PREF_KEY))
                        val level = it.getInt(it.getColumnIndexOrThrow(SmritiDatabaseHelper.COL_PREF_LEVEL))
                        map[key] = level
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        map
    }

    suspend fun recordPreferenceMention(topicKey: String) = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val currentMap = getLearnedPreferences()
            val currentLevel = currentMap[topicKey] ?: 0
            val values = ContentValues().apply {
                put(SmritiDatabaseHelper.COL_PREF_KEY, topicKey)
                put(SmritiDatabaseHelper.COL_PREF_LEVEL, currentLevel + 1)
                put(SmritiDatabaseHelper.COL_PREF_TIME, System.currentTimeMillis())
            }
            db.insertWithOnConflict(
                SmritiDatabaseHelper.TABLE_PREFERENCES,
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
