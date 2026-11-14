package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.cloud.CloudSession
import com.example.myapplication.cloud.PatientRepository
import com.example.myapplication.reminders.ScheduleTime

/** Screens share one facade; falls back to 100% offline SQLite when cloud is not active. */
class SmritiRepository(context: Context, forceLocal: Boolean = false) {
    private val cloud = if (!forceLocal && !CloudSession.isLocal(context) && CloudSession.configured(context)) CloudSession.auth.currentUser?.uid?.let { PatientRepository(context.applicationContext, it) } else null
    val patientKey: String = cloud?.uid ?: "local-patient"
    private val legacy by lazy { LegacySmritiRepository(context) }
    fun close() { if (cloud == null) legacy.close() }
    suspend fun getPatientProfile() = cloud?.profile() ?: legacy.getPatientProfile()
    suspend fun savePatientProfile(p: PatientProfile) { if (cloud != null) cloud.saveProfile(p) else legacy.savePatientProfile(p) }
    suspend fun saveGameSession(s: GameSessionRecord) = cloud?.saveGame(s) ?: legacy.saveGameSession(s)
    suspend fun getAllGameSessions() = cloud?.games() ?: legacy.getAllGameSessions()
    suspend fun getAllMedicines() = cloud?.medicines() ?: legacy.getAllMedicines()
    suspend fun toggleMedicineTaken(id: Long, taken: Boolean) { if (cloud != null) cloud.toggleMedicine(id, taken) else legacy.toggleMedicineTaken(id, taken) }
    suspend fun addMedicine(m: MedicineItem) = cloud?.addMedicine(m) ?: legacy.addMedicine(m)
    suspend fun updateMedicine(m: MedicineItem) {
        require(m.id > 0 && m.name.isNotBlank() && ScheduleTime.parse(m.scheduledTime) != null)
        if (cloud != null) cloud.updateMedicine(m) else legacy.updateMedicine(m)
    }
    suspend fun updateRoutine(r: RoutineItem) {
        require(r.id > 0 && r.title.isNotBlank() && ScheduleTime.parse(r.timeStr) != null)
        if (cloud != null) cloud.saveRoutine(r) else legacy.updateRoutine(r)
    }
    suspend fun getTodayHydration() = cloud?.hydration() ?: legacy.getTodayHydration()
    suspend fun saveHydration(h: HydrationData) { if (cloud != null) cloud.saveHydration(h) else legacy.saveHydration(h) }
    suspend fun incrementHydration(): HydrationData = changeHydration(1)
    suspend fun decrementHydration(): HydrationData = changeHydration(-1)
    private suspend fun changeHydration(delta: Int): HydrationData {
        val h = getTodayHydration()
        val updated = h.copy(glassesDrunk = (h.glassesDrunk + delta).coerceIn(0,16), lastUpdated = System.currentTimeMillis())
        saveHydration(updated); return updated
    }
    suspend fun getTodayRoutines() = getRoutinesForDate(ScheduleTime.dateKey(System.currentTimeMillis()))
    suspend fun getRoutinesForDate(date: String) = cloud?.routines(date) ?: legacy.getRoutinesForDate(date)
    suspend fun toggleRoutineCompleted(id: Long, done: Boolean) { if (cloud != null) cloud.toggleRoutine(id, done) else legacy.toggleRoutineCompleted(id, done) }
    suspend fun saveRoutine(r: RoutineItem) = if (cloud != null) cloud.saveRoutine(r) else legacy.saveRoutine(r)
    suspend fun getAllFamilyMembers() = cloud?.family() ?: legacy.getAllFamilyMembers()
    suspend fun addFamilyMember(m: FamilyMember) = cloud?.saveFamily(m) ?: legacy.addFamilyMember(m)
    suspend fun saveFamilyProfile(m: FamilyMember) = cloud?.saveFamily(m) ?: legacy.saveFamilyProfile(m)
    suspend fun deleteFamilyMember(id: Long) { if (cloud != null) cloud.deleteFamily(id) else legacy.deleteFamilyMember(id) }

    // AI Chat & Preferences
    suspend fun getChatHistory() = legacy.getChatHistory()
    suspend fun saveChatMessage(msg: ChatMessage) = legacy.saveChatMessage(msg)
    suspend fun getLearnedPreferences() = legacy.getLearnedPreferences()
    suspend fun recordPreferenceMention(topicKey: String) = legacy.recordPreferenceMention(topicKey)
}
