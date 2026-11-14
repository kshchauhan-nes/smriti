package com.example.myapplication.cloud

import com.example.myapplication.ui.UiText

import com.example.myapplication.speech.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.*
import com.example.myapplication.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

@Composable
fun PatientAccountGate(content: @Composable (SmritiRepository) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { CloudSession.initialize(context) }
    val uid by CloudSession.uid.collectAsState()
    val ready by CloudSession.ready.collectAsState()
    val error by CloudSession.error.collectAsState()
    val status by CloudSession.status.collectAsState()
    var offlineMode by remember { mutableStateOf(CloudSession.isLocal(context)) }

    if (!CloudSession.configured(context) || offlineMode) {
        val repository = remember { SmritiRepository(context, forceLocal = true) }
        DisposableEffect(repository) { onDispose { repository.close() } }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                UiText("Local patient • saved on this device", modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                if (CloudSession.configured(context)) TextButton(onClick = { CloudSession.setLocal(context, false); offlineMode = false }) { UiText(stringResource(R.string.lang_11)) }
            }
            Box(Modifier.weight(1f)) { content(repository) }
        }
    } else if (uid == null) {
        SignInScreen(onContinueOffline = { CloudSession.setLocal(context, true); offlineMode = true })
    } else if (!ready) {
        NarrationScreen("Opening your patient account. " + (error ?: status) + ". If the connection fails, retry or return to sign in.", assamese = "আপোনাৰ একাউণ্ট খোলা হৈছে। সংযোগ নহ’লে পুনৰ চেষ্টা কৰক অথবা প্ৰৱেশ পৃষ্ঠালৈ ঘূৰি যাওক।")
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UiText("Opening your patient account", fontSize = 25.sp)
            UiText(error ?: status)
            if (error == null) CircularProgressIndicator()
            else Button(onClick = { CloudSession.retry() }) { UiText("Retry connection") }
            TextButton(onClick = { CloudSession.auth.signOut() }) { UiText("Return to sign in") }
        }
    } else key(uid) {
        val repository = remember(uid) { SmritiRepository(context) }
        var settings by remember { mutableStateOf(false) }
        BackHandler(enabled = settings) { settings = false }
        DisposableEffect(repository) { onDispose { repository.close() } }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                UiText(status, modifier = Modifier.weight(1f).padding(top = 12.dp), fontSize = 12.sp)
                TextButton(onClick = { settings = !settings }) { UiText(if (settings) stringResource(R.string.lang_13) else stringResource(R.string.lang_12)) }
            }
            if (error != null) UiText(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 14.dp))
            Box(Modifier.weight(1f)) {
                if (settings) PatientSettings(repository) else content(repository)
            }
        }
    }
}

@Composable
private fun SignInScreen(onContinueOffline: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    NarrationScreen(if (register) "Create a patient account. Enter the patient's name, email address, password and password confirmation. Passwords must match and have at least eight characters. Tap Create patient account. Or return to Sign in."
        else "Welcome to Smriti. Enter your email address and password, then tap Sign in. For a new patient choose Create account. Forgot password sends a reset link to the entered email address. Continue offline opens the local patient on this device.", assamese = if (register) "ৰোগীৰ নতুন একাউণ্ট খুলিবলৈ নাম, ইমেইল আৰু পাছৱৰ্ড লিখক। পাছৱৰ্ড দুবাৰ একে হ’ব লাগিব আৰু কমেও আঠটা আখৰ থাকিব লাগিব।" else "স্মৃতিলৈ স্বাগতম। ইমেইল আৰু পাছৱৰ্ড লিখি প্ৰৱেশ কৰক। নতুন একাউণ্টো খুলিব পাৰে। ইণ্টাৰনেট অবিহনে চলাবলৈ অফলাইন বাছক।")
    BackHandler(enabled = register && !busy) { register = false; message = null }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFFEFA), Color(0xFFFAFCFC), Color(0xFFF5FAFC)))).statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(12.dp))
        Surface(shape = CircleShape, color = Color(0xFFDFF3E2), modifier = Modifier.size(76.dp).align(Alignment.CenterHorizontally)) {
            Box(contentAlignment = Alignment.Center) { UiText("🌼", fontSize = 40.sp) }
        }
        UiText("Smriti", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6952C5), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        UiText("A little care. A familiar connection.", fontSize = 16.sp, color = Color(0xFF587366), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
          Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        UiText(if (register) "Let's get to know you" else stringResource(R.string.lang_14), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color(0xFF263238))
        UiText(if (register) "Create one account for your patient. Their care and memories will stay together." else "Sign in to bring your care, routines and family memories with you.", fontSize = 15.sp, color = Color(0xFF607D8B))
        if (register) OutlinedTextField(name, { name = it }, label = { UiText(stringResource(R.string.lang_15)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6952C5), unfocusedBorderColor = Color(0xFFD9E3DC), focusedLabelColor = Color(0xFF6952C5)), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { UiText(stringResource(R.string.lang_17)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6952C5), unfocusedBorderColor = Color(0xFFD9E3DC), focusedLabelColor = Color(0xFF6952C5)), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { UiText(stringResource(R.string.lang_18)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6952C5), unfocusedBorderColor = Color(0xFFD9E3DC), focusedLabelColor = Color(0xFF6952C5)), modifier = Modifier.fillMaxWidth())
        if (register) OutlinedTextField(confirmation, { confirmation = it }, label = { UiText(stringResource(R.string.lang_19)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6952C5), unfocusedBorderColor = Color(0xFFD9E3DC), focusedLabelColor = Color(0xFF6952C5)), modifier = Modifier.fillMaxWidth())
        message?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6952C5)), onClick = {
            busy = true; message = null
            scope.launch {
                try {
                    require(email.trim().isNotEmpty() && password.isNotEmpty()) { "Enter your email and password" }
                    if (register) {
                        require(name.isNotBlank()) { "Enter the patient's name" }
                        require(password.length >= 8 && password == confirmation) { "Use at least 8 characters and matching passwords" }
                        CloudSession.register(email.trim(), password, name.trim())
                    } else withTimeout(35_000) { CloudSession.auth.signInWithEmailAndPassword(email.trim(), password).result() }
                } catch (e: CancellationException) { if (CloudSession.auth.currentUser == null) message = "Connection timed out. Please try again." }
                catch (e: Exception) { message = e.localizedMessage ?: "Could not sign in" }
                finally { busy = false }
            }
        }) { UiText(if (busy) stringResource(R.string.lang_23) else if (register) stringResource(R.string.lang_20) else stringResource(R.string.lang_11), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        TextButton(enabled = !busy, onClick = { register = !register; message = null }) { UiText(if (register) "Already registered? Sign in" else "New patient? Create account") }
        TextButton(enabled = !busy && email.isNotBlank(), onClick = {
            scope.launch {
                try { withTimeout(25_000) { CloudSession.auth.sendPasswordResetEmail(email.trim()).result() }; message = "If this account exists, a password reset email has been sent." }
                catch (e: Exception) { message = e.localizedMessage }
            }
        }) { UiText(stringResource(R.string.lang_21)) }
        
          }
        }
        UiText("Or stay on this device", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color(0xFF607D8B), fontSize = 14.sp)
        
        OutlinedButton(
            enabled = !busy,
            onClick = onContinueOffline,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6952C5))
        ) {
            UiText(stringResource(R.string.lang_22))
        }

        UiText("Your everyday care works offline. Sign in when you want to sync across devices.", fontSize = 13.sp, color = Color(0xFF607D8B), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PatientSettings(repository: SmritiRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var profile by remember { mutableStateOf(PatientProfile("", "", "", "", "English")) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var routineTitle by remember { mutableStateOf("") }
    var routineTime by remember { mutableStateOf("") }
    var confirmImport by remember { mutableStateOf(false) }
    LaunchedEffect(repository) { profile = repository.getPatientProfile(); loaded = true }
    NarrationScreen("Patient and account settings. Patient: ${profile.patientName}. Caregiver: ${profile.caregiverName}. " +
        "Phone: ${profile.caregiverPhone}. Location: ${profile.location}. Edit details and tap Save patient details. " +
        "Add a daily routine using an activity and time. Sync now uploads queued changes when connected. " +
        "Import local patient copies this device's standalone records into this account after confirmation. " +
        "Log out switches patient accounts. Connect before signing out if changes are waiting to sync.", assamese = "ৰোগী আৰু একাউণ্টৰ ছেটিংছ। ${profile.patientName}। যত্ন লওঁতা ${profile.caregiverName}। ফোন ${profile.caregiverPhone}। ঠিকনা ${profile.location}। তথ্য সলনি কৰি সংৰক্ষণ কৰক। দৈনন্দিন কাম আৰু সময় যোগ কৰিব পাৰে। একাউণ্ট সলনি কৰিবলৈ লগ আউট কৰক।")
    fun runOperation(block: suspend () -> String) {
        busy = true; message = null
        scope.launch {
            try { message = block() }
            catch (e: Exception) { message = if (e is CancellationException) "Connect to the internet to finish syncing before signing out. Your data is still saved on this device." else e.localizedMessage }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiText("Patient & account settings", fontSize = 26.sp)
        UiText(CloudSession.auth.currentUser?.email.orEmpty())
        UiText("Patient ID: ${CloudSession.uid.value}", fontSize = 12.sp)
        OutlinedTextField(profile.patientName, { profile = profile.copy(patientName = it) }, label = { UiText(stringResource(R.string.lang_16)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(profile.caregiverName, { profile = profile.copy(caregiverName = it) }, label = { UiText(stringResource(R.string.lang_24)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(profile.caregiverPhone, { profile = profile.copy(caregiverPhone = it) }, label = { UiText(stringResource(R.string.lang_25)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(profile.location, { profile = profile.copy(location = it) }, label = { UiText(stringResource(R.string.lang_26)) }, modifier = Modifier.fillMaxWidth())
        Button(enabled = loaded && !busy, onClick = { runOperation { repository.savePatientProfile(profile); "Profile saved on this device" } }) { UiText(stringResource(R.string.lang_27)) }
        HorizontalDivider()
        UiText(stringResource(R.string.lang_28), fontSize = 20.sp)
        OutlinedTextField(routineTitle, { routineTitle = it }, label = { UiText(stringResource(R.string.lang_29)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(routineTime, { routineTime = it }, label = { UiText("Time, e.g. 08:00 AM") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy, onClick = { runOperation {
            repository.saveRoutine(RoutineItem(title = routineTitle, timeStr = routineTime, emoji = "📋"))
            ReminderScheduler.reconcile(context); routineTitle = ""; routineTime = ""; "Daily routine saved"
        } }) { UiText(stringResource(R.string.lang_30)) }
        HorizontalDivider()
        message?.let { UiText(it, color = if (it.contains("saved", ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, onClick = { runOperation { CloudSession.syncNow(); "Patient data is fully synced" } }) { UiText(stringResource(R.string.lang_31)) }
        OutlinedButton(enabled = loaded && !busy, onClick = { confirmImport = true }) { UiText("Import this device's local patient") }
        UiText("Signed-in care records and photos sync automatically. Offline chatbot history stays on this device. Connect before signing out if changes are waiting to sync.", fontSize = 13.sp)
        OutlinedButton(enabled = !busy, onClick = { runOperation { CloudSession.logout(); "Signed out" } }) { UiText("Log out / switch patient account") }
    }
    if (confirmImport) AlertDialog(onDismissRequest = { confirmImport = false }, title = { NarratedTitle("Import into ${profile.patientName}?", "Only import if the local patient is ${profile.patientName}. Import adds medicines, routines, hydration, family photos and game history to this account. Choose Import for this patient, or Cancel.") },
        text = { UiText("Only import if this device's local patient is the same person. This adds their current medicines, routines, hydration, family details/photos and game history to this account. Review any example medicines before using them.") },
        dismissButton = { TextButton(onClick = { confirmImport = false }) { UiText(stringResource(R.string.lang_9)) } },
        confirmButton = { TextButton(onClick = {
            confirmImport = false
            runOperation {
                val account = requireNotNull(CloudSession.uid.value)
                val prefs = context.getSharedPreferences("legacy_import", 0)
                check(!prefs.getBoolean(account, false)) { "Local records were already imported into this account." }
                val old = LegacySmritiRepository(context)
                try {
                    old.getAllMedicines().forEach { repository.addMedicine(it) }
                    old.getTodayRoutines().forEach { r -> repository.saveRoutine(r); if (r.isCompleted) repository.toggleRoutineCompleted(r.id, true) }
                    repository.saveHydration(old.getTodayHydration())
                    old.getAllGameSessions().forEach { repository.saveGameSession(it) }
                    old.getAllFamilyMembers().forEach { repository.saveFamilyProfile(it) }
                    prefs.edit().putBoolean(account, true).commit()
                    ReminderScheduler.reconcile(context)
                    "Local records imported. Changes will sync when connected."
                } finally { old.close() }
            }
        }) { UiText("Import for this patient") } })
}
