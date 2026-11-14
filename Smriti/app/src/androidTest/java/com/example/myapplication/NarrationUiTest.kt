package com.example.myapplication

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myapplication.speech.*
import com.example.myapplication.ui.LanguageHost
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NarrationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun speakerTogglesAndPageChangeStopsSpeech() {
        val text = mutableStateOf("Remember these objects. " .repeat(80))
        var narrator: ScreenNarrator? = null
        compose.setContent {
            LanguageHost {
                val current = LocalNarrator.current
                SideEffect { narrator = current }
                NarrationScreen(text.value)
                Text("Narration test screen")
            }
        }
        compose.waitUntil(60000) { narrator?.ready == true }
        compose.onNodeWithContentDescription("Read this screen aloud").performClick()
        compose.onNodeWithContentDescription("Stop narration").assertExists()
        compose.runOnIdle { assertTrue("Expected Kokoro engine, got ${narrator!!.voiceEngine}", narrator!!.voiceEngine == "com.k2fsa.sherpa.onnx.tts.engine") }
        compose.runOnIdle { text.value = "New screen. " .repeat(80) }
        compose.onNodeWithContentDescription("Read this screen aloud").assertExists()
        compose.runOnIdle { assertFalse(narrator!!.speaking); assertNull(narrator!!.message) }
        compose.onNodeWithContentDescription("Read this screen aloud").performClick()
        compose.onNodeWithContentDescription("Stop narration").performClick()
        compose.onNodeWithContentDescription("Read this screen aloud").assertExists()
    }
    @Test fun localizedContextKeepsPermissionLauncherOwner() {
        compose.setContent {
            LanguageHost {
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
                Text("Permission launcher registered")
            }
        }
        compose.onNodeWithText("Permission launcher registered").assertExists()
    }

    @Test fun assameseUiSelectsOfflineAssameseNarration() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("interface_language", 0)
        val old = prefs.getString("code", "en")
        prefs.edit().putString("code", "as").commit()
        var narrator: ScreenNarrator? = null
        try {
            compose.setContent {
                LanguageHost {
                    val current = LocalNarrator.current
                    SideEffect { narrator = current }
                    NarrationScreen("Welcome to Smriti.", assamese = "স্মৃতিলৈ স্বাগতম। আজি আপোনাৰ কেনে লাগিছে? ".repeat(20))
                }
            }
            compose.waitUntil(60000) { narrator?.ready == true }
            compose.runOnIdle { assertEquals("as", narrator!!.page!!.language); assertTrue(narrator!!.page!!.text.startsWith("স্মৃতি")) }
            compose.onNodeWithContentDescription("Read this screen aloud").performClick()
            compose.runOnIdle {
                assertEquals("com.reecedunn.espeak", narrator!!.voiceEngine)
                assertTrue(narrator!!.speaking)
                assertNull(narrator!!.message)
            }
            compose.onNodeWithContentDescription("Stop narration").performClick()
        } finally { prefs.edit().putString("code", old).commit() }
    }

}
