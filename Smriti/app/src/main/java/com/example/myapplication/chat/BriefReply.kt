package com.example.myapplication.chat

/** Hide unfinished reasoning and stop only at a real sentence boundary. */
object BriefReply {
    fun concise(raw: String, sentences: Int = 2, maxChars: Int = 420): String {
        val text = visible(raw)
        var offset = 0
        repeat(sentences) {
            val boundary = end(text.substring(offset))
            if (boundary == null) return if (text.length <= maxChars) text else text.take(maxChars - 1).substringBeforeLast(' ') + "…"
            offset += boundary
        }
        return text.take(offset).trim()
    }
    fun visible(raw: String): String = raw
        .replace(Regex("<think>[\\s\\S]*?</think>"), "")
        .substringBefore("<think>").trim()

    fun end(text: String): Int? {
        for (i in text.indices) {
            val c = text[i]
            if (c !in ".!?।" || i < 12) continue
            if (i + 1 < text.length && !text[i + 1].isWhitespace()) continue
            val word = text.substring(0, i).substringAfterLast(' ').lowercase()
            if (c == '.' && (word in setOf("dr", "mr", "mrs", "ms", "prof", "a.m", "p.m") || word.length == 1)) continue
            // A trailing dot may be the start of a decimal or abbreviation in a stream.
            if (c == '.' && i == text.lastIndex) continue
            return i + 1
        }
        return null
    }
    fun shorten(raw: String): String {
        val text = visible(raw)
        end(text)?.let { return text.take(it) }
        if (text.length <= 240) return text
        return text.take(237).substringBeforeLast(' ', text.take(237)).trimEnd() + "…"
    }
}
