package com.example.myapplication

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.*
import com.example.myapplication.reminders.*
import com.example.myapplication.ui.UiText
import kotlinx.coroutines.launch

private fun openReminderChannelSettings(context: android.content.Context) {
    ReminderScheduler.createChannel(context)
    val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderScheduler.CHANNEL)
    val appSettings = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.parse("package:${context.packageName}")
    )
    val intent = when {
        channelIntent.resolveActivity(context.packageManager) != null -> channelIntent
        appSettings.resolveActivity(context.packageManager) != null -> appSettings
        else -> null
    }
    if (intent == null) {
        android.widget.Toast.makeText(context, "Sound settings are unavailable on this device.", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "Could not open sound settings. Please use your phone settings.", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun CaregiverScheduleEditor(repository: SmritiRepository, medicines: List<MedicineItem>,
    routines: List<RoutineItem>, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var medicine by remember { mutableStateOf<MedicineItem?>(null) }
    var routine by remember { mutableStateOf<RoutineItem?>(null) }
    var editor by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            UiText("Daily care schedule", style = MaterialTheme.typography.titleLarge)
            UiText("Edit medicines and routines here. Saved times repeat daily; today's checkmarks are kept.")
            UiText("Medicines", style = MaterialTheme.typography.titleMedium)
            medicines.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        UiText(item.name)
                        UiText("${item.scheduledTime} • ${item.dosage}")
                        UiText(if (item.isTaken) "Marked taken today" else "Not marked taken today")
                    }
                    TextButton(onClick = { medicine = item; routine = null; error = null; editor = "medicine" }) { UiText("Edit") }
                }
            }
            OutlinedButton(onClick = { medicine = null; routine = null; error = null; editor = "medicine" }) { UiText("Add medicine") }
            HorizontalDivider()
            UiText("Daily routines", style = MaterialTheme.typography.titleMedium)
            routines.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        UiText("${item.emoji} ${item.title}")
                        UiText(item.timeStr)
                        UiText(if (item.isCompleted) "Completed today" else "Not marked complete today")
                    }
                    TextButton(onClick = { routine = item; medicine = null; error = null; editor = "routine" }) { UiText("Edit") }
                }
            }
            OutlinedButton(onClick = { routine = null; medicine = null; error = null; editor = "routine" }) { UiText("Add routine") }
            ReminderSettingsCard()
            TextButton(onClick = { openReminderChannelSettings(context) }) { UiText("Reminder sound & vibration") }
            TextButton(onClick = {
                ReminderScheduler.createChannel(context)
                if (ReminderScheduler.notificationsAllowed(context)) {
                    val open = android.app.PendingIntent.getActivity(context, 903, Intent(context, MainActivity::class.java),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                    val notification = androidx.core.app.NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
                        .setSmallIcon(R.drawable.ic_reminder).setContentTitle("Smriti reminder test")
                        .setContentText("This is a sound and vibration test. Your care schedule is unchanged.")
                        .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(10000)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_SOUND or androidx.core.app.NotificationCompat.DEFAULT_VIBRATE).build()
                    try { androidx.core.app.NotificationManagerCompat.from(context).notify("care-preview", 903, notification) }
                    catch (_: SecurityException) { android.widget.Toast.makeText(context, "Allow notifications first", android.widget.Toast.LENGTH_SHORT).show() }
                } else android.widget.Toast.makeText(context, "Allow notifications first", android.widget.Toast.LENGTH_SHORT).show()
            }) { UiText("Test reminder sound") }
            UiText("Sound and vibration follow your phone's notification volume, channel settings and Do Not Disturb mode.", style = MaterialTheme.typography.bodySmall)
        }
    }
    editor?.let { kind ->
        key(kind, medicine?.id, routine?.id) {
            var title by remember { mutableStateOf(medicine?.name ?: routine?.title ?: "") }
            var time by remember { mutableStateOf(medicine?.scheduledTime ?: routine?.timeStr ?: "09:00") }
            var dosage by remember { mutableStateOf(medicine?.dosage ?: "") }
            var notes by remember { mutableStateOf(medicine?.instructions ?: "") }
            AlertDialog(onDismissRequest = { if (!saving) editor = null },
                title = { UiText("${if (medicine != null || routine != null) "Edit" else "Add"} $kind") },
                text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it.take(150) }, label = { UiText(if (kind == "medicine") "Medicine name" else "Routine title") }, enabled = !saving)
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it.take(8) },
                        label = { UiText("Daily time (HH:MM, for example 09:30 or 6:30 PM)") },
                        supportingText = {
                            UiText(if (ScheduleTime.parse(time) == null) "Enter a valid daily time." else "This time repeats every day.")
                        },
                        isError = time.isNotBlank() && ScheduleTime.parse(time) == null,
                        enabled = !saving
                    )
                    if (kind == "medicine") {
                        OutlinedTextField(dosage, { dosage = it.take(150) }, label = { UiText("Prescribed dosage") }, enabled = !saving)
                        OutlinedTextField(notes, { notes = it.take(700) }, label = { UiText("Instructions") }, enabled = !saving)
                        UiText("Enter the prescribed schedule. Editing does not mark a dose as taken.")
                    }
                    error?.let { UiText(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { TextButton(enabled = !saving && title.isNotBlank() &&
                    ScheduleTime.parse(time) != null && (kind != "medicine" || dosage.isNotBlank()), onClick = {
                    saving = true; error = null
                    scope.launch {
                        try {
                            if (kind == "medicine") {
                                val updated = (medicine ?: MedicineItem(name = "", dosage = "", scheduledTime = time)).copy(name = title.trim(), dosage = dosage.trim(), scheduledTime = time, instructions = notes.trim())
                                if (updated.id > 0) repository.updateMedicine(updated) else check(repository.addMedicine(updated) > 0) { "Could not save medicine" }
                            } else {
                                val updated = (routine ?: RoutineItem(title = "", timeStr = time, emoji = "✓")).copy(title = title.trim(), timeStr = time)
                                if (updated.id > 0) repository.updateRoutine(updated) else check(repository.saveRoutine(updated) > 0) { "Could not save routine" }
                            }
                            ReminderScheduler.reconcile(context)
                            onSaved(); editor = null
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { error = "Could not finish saving. Reopen the item to check its saved details and retry."; onSaved() }
                        finally { saving = false }
                    }
                }) { UiText(if (saving) "Saving…" else "Save schedule") } },
                dismissButton = { TextButton(enabled = !saving, onClick = { editor = null }) { UiText("Cancel") } })
        }
    }
}
