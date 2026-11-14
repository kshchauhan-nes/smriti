package com.example.myapplication.reminders

import com.example.myapplication.ui.UiText

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private fun openExactAlarmSettings(context: android.content.Context) {
    val exact = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")
    )
    val fallback = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    val intent = when {
        exact.resolveActivity(context.packageManager) != null -> exact
        fallback.resolveActivity(context.packageManager) != null -> fallback
        else -> null
    }
    if (intent == null) {
        android.widget.Toast.makeText(context, "Precise reminder settings are unavailable on this device.", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "Could not open reminder settings. Please try from your phone settings.", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ReminderSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(ReminderScheduler.notificationsAllowed(context)) }
    var exact by remember { mutableStateOf(ReminderScheduler.exactAlarmsAllowed(context)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = ReminderScheduler.notificationsAllowed(context)
        scope.launch { ReminderScheduler.reconcile(context) }
    }
    DailyRefreshEffect {
        allowed = ReminderScheduler.notificationsAllowed(context)
        exact = ReminderScheduler.exactAlarmsAllowed(context)
    }
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            UiText("Daily reminders", style = MaterialTheme.typography.titleMedium)
            UiText(
                when {
                    !allowed -> "Allow notifications to be reminded about unfinished medicines and activities."
                    !exact -> "Notifications are on. Allow precise reminders to receive them at the scheduled time; otherwise Android may delay them."
                    else -> "Reminders arrive 15 minutes before, at the scheduled time, and 30 minutes after if still unchecked (missed). Checklists reset every day."
                }, style = MaterialTheme.typography.bodyMedium
            )
            if (!allowed) {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context,
                            Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        request.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")))
                    }
                }) { UiText("Allow notifications") }
                // Also available after Android stops displaying a repeatedly denied permission prompt.
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")))
                }) { UiText("Notification settings") }
            }
            if (!exact && Build.VERSION.SDK_INT >= 31) {
                TextButton(onClick = {
                    openExactAlarmSettings(context)
                }) { UiText("Allow precise reminders") }
            }
        }
    }
}
