package com.example.myapplication.chat

/** UTF-8 byte arrays preserve non-ASCII names and token fragments across JNI. */
internal object LfmRuntime {
    init { System.loadLibrary("smriti_lfm") }
    external fun load(path: ByteArray): Long
    external fun begin(handle: Long, prompt: ByteArray, threads: Int, reserve: Int)
    external fun prefill(handle: Long): Boolean
    external fun next(handle: Long): ByteArray?
    external fun reset(handle: Long)

    // Text-only form of LiquidAI/LFM2.5-350M's published chat template.
    // Escape control tokens from patient content so records cannot create new roles.
    fun message(role: String, text: String) =
        "<|im_start|>$role\n${text.replace("<|", "< |") }<|im_end|>\n"
    fun prompt(instruction: String, content: String, history: List<com.example.myapplication.data.ChatMessage> = emptyList()) =
        "<|startoftext|>" + message("system", instruction) +
            history.takeLast(4).joinToString("") { message(if (it.sender == "user") "user" else "assistant", it.text.take(300)) } +
            message("user", content) + "<|im_start|>assistant\n"
}
