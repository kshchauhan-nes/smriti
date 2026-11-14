package com.example.myapplication

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class CompleteLocalizationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun everyCatalogEntryHasAllThreeTranslations() {
        for (code in listOf("as", "bn", "ne")) {
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(code)) })
            for ((source,id) in UiCatalog.labels) {
                val translated = localized.getString(id)
                assertTrue("Empty $code: $source", translated.isNotBlank())
                assertNotEquals("English fallback in $code: $source", source, translated)
                val placeholders = Regex("""\{\d+\}""")
                assertEquals("Placeholders changed in $code: $source", placeholders.findAll(source).map{it.value}.toSet(), placeholders.findAll(translated).map{it.value}.toSet())
            }
            assertFalse(UiCatalog.translate(localized, "3 of 5 taken today").contains("taken"))
            assertEquals("Patient-entered unchanged", "Patient-entered unchanged", UiCatalog.translate(localized,"Patient-entered unchanged"))
        }
    }

    @Test fun switchingLanguageUpdatesVisibleScreenAndKeepsPatientText() {
        val prefs=context.getSharedPreferences("interface_language",0)
        val before=prefs.getString("code","en")
        prefs.edit().putString("code","en").commit()
        try {
            compose.setContent {
                LanguageHost {
                    Column {
                        UiText("3 of 5 taken today")
                        Text("Tea") // patient-supplied name must remain exactly as entered
                        GamesMenuScreen({}, {}, {}, {})
                    }
                }
            }
            compose.onNodeWithText("Picture Recall").assertExists()
            for ((code,title) in listOf("as" to "ছবিৰ স্মৃতি", "bn" to "ছবির স্মৃতি", "ne" to "तस्बिर सम्झना", "en" to "Picture Recall")) {
                compose.runOnIdle { prefs.edit().putString("code",code).commit() }
                compose.waitUntil(5000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Tea").assertExists()
                if(code!="en") compose.onNodeWithText("Picture Recall").assertDoesNotExist()
            }
        } finally { prefs.edit().putString("code",before).commit() }
    }
}
