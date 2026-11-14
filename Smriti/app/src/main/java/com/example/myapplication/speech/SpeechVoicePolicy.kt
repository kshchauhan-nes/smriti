package com.example.myapplication.speech

import java.util.Locale

object SpeechVoicePolicy {
    fun sameLanguage(locale: Locale, language: String): Boolean =
        locale.language == language || runCatching { locale.isO3Language == Locale.forLanguageTag(language).isO3Language }.getOrDefault(false)

    fun languageFor(text: String, selected: String): String = when {
        text.any { it in '\u0900'..'\u097f' } -> "ne"
        text.any { it in '\u0980'..'\u09ff' } -> if (selected == "as" || text.any { it == 'ৰ' || it == 'ৱ' }) "as" else "bn"
        else -> "en"
    }

    fun rank(engine: String, locale: Locale, name: String): Int {
        var score = when (engine) { "com.k2fsa.sherpa.onnx.tts.engine" -> 3000; "com.github.olga_yakovleva.rhvoice.android" -> 1000; else -> 0 }
        if (sameLanguage(locale, "en")) {
            score += when (locale.country.uppercase(Locale.ROOT)) { "US" -> 100; "IN" -> 90; "GB" -> 50; else -> 0 }
            if (name.equals("SLT", true) || name.startsWith("SLT-", true)) score += 200
        }
        if ('+' in name || name.contains("whisper", true) || name.contains("croak", true)) score -= 500
        return score
    }
}
