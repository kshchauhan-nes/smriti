# Offline inference dependency

`llama.cpp` is vendored from https://github.com/ggml-org/llama.cpp at
commit `3057bb66c86c46d5781e50e85462a760ba7d1feb` (MIT; see its LICENSE).
The Android CMake build uses CPU inference, compiled in Release mode for
arm64-v8a phones and x86_64 emulators, with a portable CPU instruction baseline.

Model: LiquidAI/LFM2.5-350M-GGUF, `LFM2.5-350M-Q4_K_M.gguf`.
Download size: 229,312,224 bytes. SHA-256:
`7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4`.
Weights are downloaded separately and governed by Liquid's LFM Open License v1.0:
https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/blob/main/LICENSE

Model card: https://huggingface.co/LiquidAI/LFM2.5-350M
Assamese is not a listed model language. No fluency guarantee is made for it.
The small model is not used to invent regional folklore; saved tales remain
available offline and online AI can adapt their supplied source summaries.

Requests use fresh native state, a 4096-token context, at most four CPU threads,
128 output tokens for ordinary chat, and a 45-second deadline checked between native calls.
Native operations are serialized; cancellation releases request state. Patient
records and chat history remain in the existing app storage, outside model files.

Validation on Pixel_API_34 x86_64 emulator (4 GB / four CPU cores):
- Both Android native architectures built successfully; APK contains libsmriti_lfm.so.
- 46 JVM tests and 9 Android integration tests passed.
- Integration checks include supplied family/medicine/routine facts, sequential
  patient isolation, cancellation recovery, JSON difficulty, context selection,
  exact record replies and chat persistence.
- Two warm family-record inference calls took 3.4 seconds each. This is not a
  guarantee for larger contexts or physical phones. First inference process PSS
  measured about 453 MB; total memory varies with the UI and supplied context.

Offline conversation fixes:
- Four recent messages retain their actual chat roles. Ordinary replies allow two
  short sentences instead of stopping after the first sentence.
- Schedule summaries use the current snapshot directly. Completion, hydration and
  game-trend summaries use the same field names/order as the repository snapshot.
- Bihu facts use saved Assam Tourism reference text; regional folklore uses the
  existing sourced excerpts. These are labeled "Saved reference", not presented
  as newly generated tales. This avoids unsupported cultural claims from the tiny model.
- Family follow-ups continue through LFM with current patient notes. Unit and
  emulator tests cover isolation, persistence, cancellation and current statuses.
