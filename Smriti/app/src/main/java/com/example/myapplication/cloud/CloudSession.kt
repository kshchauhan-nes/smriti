package com.example.myapplication.cloud

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.myapplication.reminders.ReminderScheduler

suspend fun <T> Task<T>.result(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Request failed"))
        }
    }
}

/** One account owns one private subtree. Firestore persists its cache and pending writes on disk. */
object CloudSession {
    val uid = MutableStateFlow<String?>(null)
    val ready = MutableStateFlow(false)
    val status = MutableStateFlow("Sign in to sync")
    val error = MutableStateFlow<String?>(null)
    val revision = MutableStateFlow(0L)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false
    private var creatingAccount = false
    private lateinit var app: Context
    private val listeners = mutableListOf<ListenerRegistration>()
    private val pending = mutableMapOf<String, Boolean>()
    private val cached = mutableMapOf<String, Boolean>()
    private var loading: Job? = null
    val collections = listOf("medicines", "medicineDays", "routines", "routineDays", "hydration", "games", "family", "photos")
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED).build()).build()
        }
    }
    fun isLocal(context: Context) = context.applicationContext.getSharedPreferences("account_mode", 0).getBoolean("local", false)
    fun setLocal(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences("account_mode", 0).edit().putBoolean("local", enabled).commit()
        ReminderScheduler.cancel(context)
    }
    fun configured(context: Context): Boolean = FirebaseApp.initializeApp(context.applicationContext) != null

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        app = context.applicationContext
        if (!configured(app)) { error.value = "Firebase setup is not installed yet."; return }
        initialized = true
        auth.addAuthStateListener { if (!creatingAccount) activate(it.currentUser?.uid) }
    }

    private fun activate(account: String?) {
        if (uid.value == account && (ready.value || loading?.isActive == true)) return
        loading?.cancel()
        listeners.forEach { it.remove() }; listeners.clear()
        synchronized(pending) { pending.clear(); cached.clear() }
        ready.value = false
        uid.value = account
        error.value = null
        ReminderScheduler.cancel(app)
        if (account == null) { status.value = "Sign in to sync"; return }
        status.value = "Loading this patient's data…"
        loading = scope.launch {
            try {
                val root = db.collection("patients").document(account)
                val prefs = app.getSharedPreferences("patient_cache", Context.MODE_PRIVATE)
                val cachedProfile = runCatching { root.get(Source.CACHE).result().exists() }.getOrDefault(false)
                if (!prefs.getBoolean(account, false) || !cachedProfile) {
                    // A new device must finish the first download before any edits are possible.
                    withTimeout(45_000) {
                        root.get(Source.SERVER).result()
                        collections.forEach { root.collection(it).get(Source.SERVER).result() }
                    }
                    prefs.edit().putBoolean(account, true).commit()
                }
                if (uid.value != account) return@launch
                listeners += root.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                    if (uid.value == account) update("profile", snapshot?.metadata, failure)
                }
                collections.forEach { name ->
                    listeners += root.collection(name).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                        if (uid.value == account) update(name, snapshot?.metadata, failure)
                    }
                }
                ready.value = true
                SyncWorker.enqueue(app)
                ReminderScheduler.reconcile(app)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.localizedMessage ?: "Connect to the internet to finish loading this patient." }
        }
    }

    private fun update(name: String, metadata: SnapshotMetadata?, failure: Exception?) {
        if (failure != null) { error.value = "Sync failed: ${failure.localizedMessage}"; return }
        synchronized(pending) {
            pending[name] = metadata?.hasPendingWrites() == true
            cached[name] = metadata?.isFromCache == true
            status.value = when {
                pending.values.any { it } -> "Saved on device • waiting to sync"
                cached.values.any { it } -> "Offline data available • checking connection"
                else -> "Synced to your patient account"
            }
        }
        revision.value += 1
    }

    fun retry() {
        loading?.cancel(); loading = null; ready.value = false
        activate(auth.currentUser?.uid)
    }
    suspend fun register(email: String, password: String, patientName: String) {
        creatingAccount = true
        try {
            withTimeout(35_000) {
                val user = auth.createUserWithEmailAndPassword(email, password).result().user ?: error("Account creation failed")
                db.collection("patients").document(user.uid).set(mapOf("patientName" to patientName,
                    "caregiverName" to "", "caregiverPhone" to "", "location" to "", "preferredLanguage" to "English")).result()
            }
        } finally {
            creatingAccount = false
            activate(auth.currentUser?.uid)
        }
    }
    fun watch(task: Task<Void>) {
        status.value = "Saved on device • waiting to sync"
        task.addOnFailureListener { error.value = "Could not sync a change: ${it.localizedMessage}" }
        if (::app.isInitialized) SyncWorker.enqueue(app)
    }
    suspend fun syncNow() {
        withTimeout(25_000) { db.waitForPendingWrites().result() }
        error.value = null
    }
    suspend fun logout() {
        // Do not strand offline mutations under a different authentication identity.
        syncNow()
        ReminderScheduler.cancel(app)
        auth.signOut()
    }
}
