# Smriti offline chatbot audit — 14 September 2026

## Delivered build

`W:\NER\Smriti-latest.apk` is the newly built debug APK, installed on Pixel_API_34.
The offline model is LiquidAI LFM2.5-350M, Q4_K_M GGUF, using the existing llama.cpp JNI runtime. Its separate model download is about 229 MB.

## Changes

- `app/src/main/java/com/example/myapplication/chat/PatientRecordAnswers.kt`: one family lookup from the complete current snapshot. Names and custom relationships match after Unicode normalization, case folding and removal of punctuation/spaces. Every matching person is returned with saved relationship and caregiver notes. No name or relationship vocabulary is embedded in this lookup. Explicit missing-person queries report missing records. Unmatched short phrases now reach the model instead of being misclassified as relatives. Unique one-edit relationship typos are matched against saved relationships only. Added/checked factual profile, hydration, game summaries, named medicines/routines, completion status, recorded dosage, and medicine-decision guidance.
- `chat/PatientContextSelector.kt`: offline retrieval selects relevant categories, recognizes activity/relative queries, prioritizes names and normalized relationships, and uses recent conversation for follow-ups. Family context excludes game statistics. Exact care-item names also retrieve their records. Cloud selection was not rewritten during this audit.
- `chat/AiPatientContext.kt`: added the existing reminder timing and current notification/exact-alarm permissions to the same patient snapshot.
- `chat/HybridAi.kt`: removed duplicate family and generic topic handlers, shortened the local prompt, and moved saved facts into system context so the user message is the actual question. Local inference still uses a fresh runtime context per request. Failed companionship replies use an explicit support fallback. Bihu answers now attempt grounded local generation, with saved-reference fallback for invalid or repetitive output. Local difficulty requests retain their score/level/response-time fields.
- `chat/OfflineConversationSupport.kt`: one preference-aware topic selector, respecting avoided topics, plus a limited companionship quality check. Rejected companionship output is not streamed before validation.
- `chat/OfflineChatScreen.kt`: distinguishes the support fallback as `Support · offline`; the existing request identifier, message persistence, and cancellation handling remain in use.
- Android tests: `DynamicFamilyTest.kt`, `OfflineConversationTest.kt`, and `BriefPatientReplyTest.kt`; tests cover arbitrary relationships, multiple matches, missing records, patient switching, preferences, task status, hydration, scores, companionship, and conversation context.

Audited `AiPatientContext.kt`, `SmritiRepository.kt`, `LegacySmritiRepository.kt`, `LfmRuntime.kt`, `lfm_jni.cpp`, and `BriefReply.kt`. The snapshot already loads the active patient's complete family, medicines, today's routines, hydration, game sessions, profile and preferences. Legacy medicine reads refresh daily state. No native-runtime or database-schema change was needed for this audit. JNI accumulates UTF-8 bytes, stops on EOG, samples once per token, and resets request state in `finally`. Partial/final chat persistence remains intact.

## Validation

- Gradle debug APK, Android test APK, and JVM tests build successfully; 47 JVM tests pass.
- A 17-test offline integration suite passed after retrieval fixes, including model-state isolation, cancellation recovery and chat persistence.
- Nine model/conversation integration tests passed after the final prompt restructuring.
- Six family/support regression tests passed after the final missing-label and fallback changes.
- Tests ran with emulator Wi-Fi and mobile data disabled. Synthetic test snapshots were not saved into caregiver records.

## Phone demo checks

1. After the model download completes, disable Wi-Fi/mobile data. Open Smriti chat in English.
2. Using an existing caregiver record, ask `Who is my <saved relationship>?`, type the relationship alone, then ask `Tell me about <saved name>` and `What do you know about <saved name>?`. Expect the saved name, relationship and notes in Patient records mode.
3. For a multiword relationship, vary its case, spaces and hyphens. With two records sharing that relationship, verify both names appear. Custom relationships work the same way.
4. Ask `Who is my <relationship that has not been added>?`. Expect a missing-record answer. Switch patients and repeat: previous patient names must not appear.
5. Ask `What medicines do I have today?`, `What medicine is due?`, `What tasks are left?`, and `What have I completed?`. Mark one item complete in its screen, then repeat the question: the answer must reflect the changed record.
6. Ask `How much water have I had?`, `What is my hydration target?`, `How are my games going?`, and `What was my latest score?`. Compare against the app. Game results belong to the patient, not a relative.
7. Ask `Suggest a topic` after saving a liked and avoided topic. The suggestion must avoid the disliked topic.
8. Ask `I'm feeling lonely`, then `Tell me more`. Expect a brief model reply or clearly labeled offline support response. Navigate away and return; sent messages and completed replies should remain.
9. Ask `What is Bihu?`, then `What food is enjoyed then?` and `folklore`. Bihu first attempts grounded model generation; an inadequate answer falls back to a labeled saved reference. The regional story uses its saved reference.
10. Ask `What should I do about my medicine dose?`. Expect referral to a caregiver/clinician, with no dose-change instruction.

## Assumptions and remaining limitations

- Deterministic answers are English-only, as requested. Other languages retain the existing model path and have not been validated to the same standard.
- Direct record answers are exact lookups, not generated facts. An unchecked medicine is only “not marked complete”; it is not proof the medicine was not taken. A visit plan is not proof of a completed visit.
- The 350M model is limited: open-ended replies can still be awkward, repetitive or inaccurate. The companionship guard catches specific classes of failure; it is not a general factuality guarantee.
- An unfamiliar short phrase is no longer assumed to be a relative. Saved names and relationships still match directly; unmatched phrases reach the model. For an explicit missing-person answer, use `Who is ...?` or `Who is my ...?`. “My interests” and related interests questions read the saved preferences; ordinary sentences about interests or relatives can reach the model.
- Bihu now uses the local model with short source notes. If it confuses festival dates or repeats a follow-up without new detail, a labeled saved-reference answer is used. Folklore continues to use saved regional tales. These checks do not guarantee correctness for all general knowledge.
- The model reads relevant text records, not photograph pixels. Large profiles are bounded for inference; direct family lookup searches all supplied family records.
- Chat answers do not mark tasks complete, change prescriptions or contact anyone. Historical/future care queries are not answered from today's checklist.
- Emulator inference timings are examples, not a phone performance guarantee. The model must finish its one-time download before offline inference is available.

## Latest routing correction

Removed the broad unknown-label classifier and the rule treating every “my ...” phrase as family. Added regressions for “my interests”, “what are my hobbies”, “my mood”, “music”, and ordinary sentences mentioning saved people. Family facts remain exact record lookups. The cloud request path and backend were not changed during this offline audit.

Final verification: Gradle build and JVM tests passed. The full 20-test offline suite passed before the latest routing correction; all eight targeted routing/acceptance tests passed on the final installed APK. This last emulator run took 87 seconds overall: free-form inference remained much slower than direct record lookup, and generated cultural text still showed limitations. These tests do not establish general factual correctness or human-like understanding.

Additional changed files in the latest audit: `chat/OfflineKnowledge.kt` (shorter Bihu reference notes and follow-up references), `chat/AiPatientContext.kt` (reminder metadata), and `OfflineAcceptanceTest.kt` (end-to-end offline acceptance). No native code change was made.
