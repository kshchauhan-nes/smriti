# Smriti demo stability audit — 14 September 2026

## Files changed in this pass

- `app/src/main/java/com/example/myapplication/chat/OfflineConversationSupport.kt`: consolidated recognition of lonely/sad/bored/upset messages and requests for company. `talk to me` now reaches the local conversation path instead of the topic-list handler.
- `chat/PatientContextSelector.kt`: uses that same recognition; companionship and its immediate follow-ups use profile/preferences without loading family, games, medicines or routines from earlier messages.
- `chat/PatientRecordAnswers.kt`: emotion requests bypass identity matching, even if a saved name or custom relationship happens to resemble an emotion word. Explicit identity questions still use saved records.
- `data/LegacySmritiRepository.kt`: failed family edits only delete a newly imported photo. They no longer delete an existing photo reused by the record.
- Deleted unused `chat/PatientChatContext.kt`: no source/test callers existed. It duplicated snapshot construction with incompatible names including `todayRoutines`; the live path remains `AiPatientContext.snapshot` using `routines`.
- Added `app/src/androidTest/java/com/example/myapplication/CompanionshipRoutingTest.kt`: covers emotion/name collisions, old game-history leakage into emotional follow-ups, explicit custom relationships and preference-based suggestions.

## Verification and scope

`assembleDebug` passed after each logical code fix. Final debug/test APK builds and 47 JVM tests passed. Eleven targeted Android regression tests passed, covering companionship routing, dynamic family records, patient-context selection and persisted chat history.

The previously exercised eleven conversation prompts remain covered by the existing routing and model tests; this quick pass did not repeat long free-form model benchmarks. The new photo failure-path fix was inspected and compiled, not tested through an injected SQLite failure on real caregiver data.

Audited the call path through `HybridAi`, `AiPatientContext`, the chat screen, repository family save/load, `BriefReply`, `LfmRuntime` and JNI. The live keys are `family`, `medicines`, `routines`, `hydration`, `recentGames`, `patient`, `likedTopics`, `avoidedTopics`, and `selectedInterests`. Existing local daily-state refresh and patient-scoped family persistence remain in place.

No changes to cloud/Gemini request handling, the native inference runtime, database schema, dependencies, reminder scheduling, or game screens were required. Existing partial/final response fallback and request-identity checks were preserved.

## Exact phone checks

Once the offline model is ready, turn off Wi-Fi and mobile data. Use a real saved caregiver name and relationship for the placeholders below.

1. `Who is my <saved relationship>?` — saved person/people, patient-record mode.
2. `<saved relationship>` — same people; try case, spaces and hyphens. No game scores.
3. `Tell me about <saved name>` — saved relationship and caregiver notes.
4. `What medicines do I have today?` — stored names, doses where recorded, times and statuses. Unchecked must not be described as proof a dose was missed.
5. `What tasks are left?` — pending routines. Mark one done in its screen, ask again, and compare.
6. `How much water have I had?` — recorded glasses and target. Change the count in the hydration screen and repeat.
7. `How are my games going?` — the patient's recorded score/level summary; no raw enum or diagnosis.
8. `I'm feeling lonely` — brief conversation or labeled support fallback. Also try `I'm sad`, `I'm bored`, `talk to me`, and `keep me company`; no unrelated family/game facts.
9. `Suggest a topic` — saved likes first, avoiding saved dislikes.
10. `What is Bihu?` — grounded local response, with a labeled reference fallback if needed.
11. `Tell me more` — relevant follow-up. Leave chat and reopen it: sent messages and completed replies should persist without duplicates.

Also edit an existing relative's notes while keeping their photo, save, reopen the caregiver entry and ask about that person again. Confirm the photo and edited notes remain available.

## Remaining risks

The 350M model can still be slow, repetitive or factually wrong in open conversation. Passing these regressions does not guarantee human-like understanding, universal factual accuracy, or a crash-free app. Direct factual questions use saved records; inference is bounded and quality fallbacks are limited. English direct answers are the validated demo path; other languages were not revalidated here. Notification delivery still depends on device permissions and Android scheduling. Existing test coverage does not constitute a full physical-device or Firebase sync audit.

Latest built APK: `W:\NER\Smriti-latest.apk`.
