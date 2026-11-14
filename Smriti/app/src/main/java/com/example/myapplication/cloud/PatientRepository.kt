package com.example.myapplication.cloud

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.myapplication.FamilyPhotoStore
import com.example.myapplication.data.*
import com.example.myapplication.reminders.ScheduleTime
import com.google.firebase.firestore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.security.MessageDigest

/** Cache reads are local; SDK writes enter its durable queue and synchronize on reconnection. */
class PatientRepository(private val context: Context, val uid: String) {
    private val db get() = CloudSession.db
    private val root get() = db.collection("patients").document(uid)
    private fun guard() = check(CloudSession.auth.currentUser?.uid == uid) { "The patient account changed. Reopen this screen." }
    private fun collection(name: String) = root.collection(name)
    private fun today() = ScheduleTime.dateKey(System.currentTimeMillis())
    private fun id() = (SecureRandom().nextLong() and Long.MAX_VALUE).coerceAtLeast(1)
    private suspend fun docs(name: String): List<DocumentSnapshot> {
        guard(); return collection(name).get(Source.CACHE).result().documents
    }
    private suspend fun put(name: String, key: String, data: Map<String, Any>) {
        guard(); CloudSession.watch(collection(name).document(key).set(data))
        // SDK queue ordering: complete after the preceding mutation has reached the local cache.
        collection(name).document(key).get(Source.CACHE).result()
    }
    private fun DocumentSnapshot.s(key: String) = getString(key).orEmpty()
    private fun DocumentSnapshot.n(key: String) = getLong(key) ?: 0L
    private fun DocumentSnapshot.b(key: String) = getBoolean(key) == true

    suspend fun profile(): PatientProfile {
        guard()
        val d = root.get(Source.CACHE).result()
        return PatientProfile(d.s("patientName"), d.s("caregiverName"), d.s("caregiverPhone"), d.s("location"), d.s("preferredLanguage"))
    }
    suspend fun saveProfile(p: PatientProfile) {
        guard(); require(p.patientName.isNotBlank()) { "Enter the patient's name" }
        CloudSession.watch(root.set(mapOf("patientName" to p.patientName.trim(), "caregiverName" to p.caregiverName.trim(),
            "caregiverPhone" to p.caregiverPhone.trim(), "location" to p.location.trim(), "preferredLanguage" to p.preferredLanguage.trim()), SetOptions.merge()))
        root.get(Source.CACHE).result()
    }
    suspend fun medicines(): List<MedicineItem> {
        val days = docs("medicineDays")
        return docs("medicines").map { d ->
            val day = days.firstOrNull { it.id == "${d.id}_${today()}" }
            val latest = days.filter { it.n("medicineId") == d.id.toLong() && it.b("taken") }.maxOfOrNull { it.n("timestamp") } ?: 0L
            MedicineItem(d.id.toLong(), d.s("name"), d.s("dosage"), d.s("scheduledTime"), d.s("instructions"), day?.b("taken") == true, latest)
        }.sortedBy { ScheduleTime.parse(it.scheduledTime)?.toString() }
    }
    suspend fun addMedicine(m: MedicineItem): Long {
        require(m.name.isNotBlank() && ScheduleTime.parse(m.scheduledTime) != null) { "Enter a name and valid time" }
        val key = if (m.id > 0) m.id else id()
        put("medicines", "$key", mapOf("name" to m.name, "dosage" to m.dosage, "scheduledTime" to m.scheduledTime, "instructions" to m.instructions))
        if (m.isTaken) toggleMedicine(key, true)
        return key
    }
    suspend fun updateMedicine(m: MedicineItem) {
        // Edit the schedule only; never rewrite the daily taken record or its timestamp.
        put("medicines", "${m.id}", mapOf("name" to m.name.trim(), "dosage" to m.dosage.trim(),
            "scheduledTime" to m.scheduledTime, "instructions" to m.instructions.trim()))
    }
    suspend fun toggleMedicine(key: Long, taken: Boolean) = put("medicineDays", "${key}_${today()}",
        mapOf("medicineId" to key, "dateKey" to today(), "taken" to taken, "timestamp" to System.currentTimeMillis()))
    suspend fun routines(date: String): List<RoutineItem> {
        val days = docs("routineDays").associateBy { it.id }
        return docs("routines").map { d -> RoutineItem(d.id.toLong(), d.s("title"), d.s("timeStr"), d.s("emoji"),
            days["${d.id}_$date"]?.b("done") == true, date) }.sortedBy { it.id }
    }
    suspend fun saveRoutine(r: RoutineItem): Long {
        require(r.title.isNotBlank() && ScheduleTime.parse(r.timeStr) != null) { "Enter a title and valid time" }
        val key = if (r.id > 0) r.id else id()
        put("routines", "$key", mapOf("title" to r.title, "timeStr" to r.timeStr, "emoji" to r.emoji))
        return key
    }
    suspend fun toggleRoutine(key: Long, done: Boolean) = put("routineDays", "${key}_${today()}",
        mapOf("routineId" to key, "dateKey" to today(), "done" to done, "timestamp" to System.currentTimeMillis()))
    suspend fun hydration(): HydrationData {
        val d = docs("hydration").firstOrNull { it.id == today() }
        return if (d == null) HydrationData(today(), 0) else HydrationData(today(), d.n("glassesDrunk").toInt(), d.n("targetGlasses").toInt(), d.n("lastUpdated"))
    }
    suspend fun saveHydration(h: HydrationData) = put("hydration", h.dateKey, mapOf("glassesDrunk" to h.glassesDrunk,
        "targetGlasses" to h.targetGlasses, "lastUpdated" to h.lastUpdated))
    suspend fun games() = docs("games").map { d -> GameSessionRecord(d.id.toLong(), d.s("gameType"), d.n("score").toInt(), d.n("level").toInt(),
        d.n("responseTimeMs"), d.n("correctAnswers").toInt(), d.n("totalObjects").toInt(), d.n("timestamp")) }.sortedByDescending { it.timestamp }
    suspend fun saveGame(s: GameSessionRecord): Long {
        val key = if (s.id > 0) s.id else id()
        put("games", "$key", mapOf("gameType" to s.gameType, "score" to s.score, "level" to s.level,
            "responseTimeMs" to s.responseTimeMs, "correctAnswers" to s.correctAnswers, "totalObjects" to s.totalObjects, "timestamp" to s.timestamp))
        return key
    }
    suspend fun family(): List<FamilyMember> = withContext(Dispatchers.IO) {
        val photos = docs("photos").associateBy { it.id }
        docs("family").map { d ->
            val bytes = photos[d.id]?.getBlob("image")?.toBytes()
            var uri = ""
            if (bytes != null) {
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val dir = File(context.filesDir, "patients/$uid/photos").apply { mkdirs() }
                val file = File(dir, "${d.id}_$hash.jpg")
                if (!file.exists()) file.writeBytes(bytes)
                uri = Uri.fromFile(file).toString()
            }
            FamilyMember(d.id.toLong(), d.s("name"), d.s("relation"), d.s("avatarEmoji"), uri, d.s("notes"))
        }.sortedBy { it.name }
    }
    suspend fun saveFamily(m: FamilyMember): Long = withContext(Dispatchers.IO) {
        guard()
        require(m.name.isNotBlank() && m.relation.isNotBlank()) { "Enter name and relationship" }
        if (m.photoUri.isBlank()) {
            val key = if (m.id > 0) m.id else id()
            put("family", "$key", mapOf("name" to m.name.trim(), "relation" to m.relation.trim(), "notes" to m.notes.trim(), "avatarEmoji" to m.avatarEmoji))
            return@withContext key
        }
        require(m.notes.isNotBlank()) { "Enter information about the photo" }
        val bitmap = FamilyPhotoStore.read(context, m.photoUri, 768)
        val bytes = try {
            var quality = 85
            var encoded: ByteArray
            do { encoded = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it); it.toByteArray() }; quality -= 15 }
            while (encoded.size > 500_000 && quality >= 25)
            require(encoded.size <= 500_000) { "This photo is too large. Choose a smaller image." }
            encoded
        } finally { bitmap.recycle() }
        val key = if (m.id > 0) m.id else id()
        guard()
        val batch = db.batch()
        batch.set(collection("family").document("$key"), mapOf("name" to m.name.trim(), "relation" to m.relation.trim(), "notes" to m.notes.trim(), "avatarEmoji" to m.avatarEmoji))
        batch.set(collection("photos").document("$key"), mapOf("image" to Blob.fromBytes(bytes), "contentType" to "image/jpeg"))
        CloudSession.watch(batch.commit())
        collection("photos").document("$key").get(Source.CACHE).result()
        key
    }
    suspend fun deleteFamily(key: Long) {
        guard()
        CloudSession.watch(db.batch().delete(collection("family").document("$key")).delete(collection("photos").document("$key")).commit())
        collection("family").get(Source.CACHE).result()
        withContext(Dispatchers.IO) { File(context.filesDir, "patients/$uid/photos").listFiles()?.filter { it.name.startsWith("${key}_") }?.forEach { it.delete() } }
    }
}
