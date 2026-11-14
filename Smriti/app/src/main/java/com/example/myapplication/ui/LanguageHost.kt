package com.example.myapplication.ui

import com.example.myapplication.ui.UiText

import android.content.res.Configuration
import com.example.myapplication.speech.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import java.util.Locale

/** Device UI preference; never changes patient-entered content or authentication. */
@Composable
fun LanguageHost(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val activityResultOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    val narrator = remember { ScreenNarrator(base) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(narrator) { onDispose { narrator.close() } }
    DisposableEffect(lifecycleOwner, narrator) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) narrator.stop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val deviceConfiguration = LocalConfiguration.current
    val preferences = remember { base.applicationContext.getSharedPreferences("interface_language", 0) }
    val languages = remember { listOf("en" to "English", "as" to "অসমীয়া · Assamese", "bn" to "বাংলা · Bengali", "ne" to "नेपाली · Nepali") }
    var selected by remember { mutableStateOf(preferences.getString("code", "en").let { code -> languages.firstOrNull { it.first == code }?.first ?: "en" }) }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "code") selected = preferences.getString("code", "en") ?: "en"
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var choosing by remember { mutableStateOf(false) }
    val configuration = remember(selected, deviceConfiguration) {
        Configuration(deviceConfiguration).apply { setLocale(Locale.forLanguageTag(selected)) }
    }
    val translatedContext = remember(base, configuration) { base.createConfigurationContext(configuration) }
    CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides requireNotNull(activityResultOwner), LocalNarrator provides narrator, LocalContext provides translatedContext, LocalConfiguration provides configuration) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { narrator.stop(); choosing = true }) {
                    UiText("${stringResource(R.string.lang_0)} · ${languages.first { it.first == selected }.second.substringBefore(" ·")}")
                }
                NarrationButton()
            }
            Box(Modifier.weight(1f)) { content() }
        }
        narrator.message?.let { detail ->
            AlertDialog(onDismissRequest = { narrator.message = null }, title = { UiText("Offline narration") },
                text = { UiText(detail) }, confirmButton = { TextButton(onClick = { narrator.message = null }) { UiText("OK") } })
        }
        if (choosing) AlertDialog(
            onDismissRequest = { choosing = false },
            title = { UiText("${stringResource(R.string.lang_0)}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UiText(stringResource(R.string.lang_50))
                    languages.forEach { (code, label) ->
                        TextButton(onClick = {
                            preferences.edit().putString("code", code).apply()
                            selected = code
                            choosing = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            UiText(if (code == selected) "✓ $label" else label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { UiText(stringResource(R.string.lang_10)) } }
        )
    }
}
