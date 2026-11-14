package com.example.myapplication.speech

object NarrationText {
    fun chunks(text: String, limit: Int = 2800): List<String> {
        require(limit >= 2)
        val clean = text.replace(Regex("[\\p{So}\\p{Cs}]"), " ").trim()
        val result = mutableListOf<String>()
        for (paragraph in clean.lines().filter { it.isNotBlank() }) {
            var rest = paragraph.trim()
            while (rest.length > limit) {
                var cut = rest.lastIndexOf(' ', limit).takeIf { it > 0 } ?: limit
                if (Character.isHighSurrogate(rest[cut - 1])) cut--
                result.add(rest.substring(0, cut).trim())
                rest = rest.substring(cut).trimStart()
            }
            if (rest.isNotEmpty()) result.add(rest)
        }
        return result
    }
}
