# Proposal: a much better speaking app, with free AI as an optional extra

Status: **phases 0, 2, 3, 4 and 5 are built** (this PR). Phase 1 (family profiles) and the AI layers (phase 6) are not built, by Richard's decision on 2026-11-18: profiles wait, and AI stays a documented option if the scripted engine proves insufficient. Section 12 records the answers.

## 0. What was built

| Phase | Built |
|---|---|
| 0 | Live captions and a level bar while speaking, a prompt to add Italian offline speech when the phone lacks it |
| 2 | Natural-voice pack: `tools/audio/render_pack.py` (Kokoro Italian voices `if_sara` and `im_nicola`, tested here), a manual workflow that publishes it as a pre-release asset, and in the app a Settings download and a player that falls back to the phone voice for any line not in the pack |
| 3 | Scripted conversation engine (`domain/conversation`), 11 scenes from A1 to C1, a Conversazione screen, format docs and unit tests that play every scene through its model answers |
| 4 | New exercise types `shadow`, `minimal_pair_say`, `listen_order`, `gap_listen`, and five new units (A1, A2, B1, B2, C1) using them |
| 5 | Esame orale: three rehearsal papers, examiner voice, preparation time with notes, recorded answers with a pause meter, self-scoring and a rough score out of 60 |

Not built: phase 1 profiles, phase 6 AI, phase 7 Whisper and pronunciation spike, phase 8 kids track. Scene count is 11, not 40, and photo tasks describe the picture in words until licensed photos exist. The voice pack has to be rendered once by running the "Voice pack" workflow after this merges, since no one has listened to the output yet.

## 1. The short version

1. **Speaking is the weakest part of the app today.** Only about 8% of exercises involve the learner speaking (243 of about 3,090), the voice is whatever the phone's built-in voice is, and the "conversation" exercise is a fixed script that cannot react to what you say.
2. **The biggest quality jump needs no AI and no cost.** Nearly all the Italian the app speaks is fixed text in the lesson files. We can pre-render it with a good neural Italian voice at build time and ship it as small audio files. That gives every family phone the same natural voice, offline, for about 35 to 60 MB.
3. **Conversation should work in three layers, each a fallback for the next.** (a) A scripted, branching engine with variety, repair and correction that works offline for everyone. (b) Gemini Nano on the few phones that have it. (c) Gemini in the cloud on the free tier, adults only, opt-in, with your own key. The app never needs layer b or c, and never stops working if they fail.
4. **Gemini is not safe to build the family experience on.** Per Google's terms as reported by third parties (to be checked first-hand before building), the Gemini API is for users 18 and over, free-tier content may be used to improve Google's products and read by human reviewers, and apps used in the EEA, UK or Switzerland should use the paid tier. Free limits have been cut several times. So Gemini is an adult bonus, off by default, and kids never touch it.
5. **Family profiles are a prerequisite.** Everything (progress, level, voice, reminders, saved recordings) is currently single-user. Profiles come in the first phase.

Recommended path: Phase 0 to 3 below (about 6 to 8 weeks of part-time sessions, all zero cost), then decide on Gemini with real use in hand.

## 2. Current state of speaking (audited from the repo, main at PR #22)

### What exists

| Area | What the code does | Where |
|---|---|---|
| Voice | Android `TextToSpeech`, it-IT. Picks the best installed offline Italian voice, or the one chosen in Settings. Dialogues alternate two voices, or pitch the same voice down by 20% if only one is installed. One speed setting, "slow" is 0.7x. | `speech/TtsManager.kt`, `domain/speech/VoiceChoice.kt` |
| Listening to the learner | Android `SpeechRecognizer`, it-IT, free-form, up to 5 alternatives, `EXTRA_PREFER_OFFLINE`. No live partial text. The tap-to-record button handles permission and errors. | `speech/SpeechCapture.kt` |
| Scoring a spoken sentence | Word overlap: each target word is marked heard or missed after accent, case and punctuation are ignored. The best of the recogniser's alternatives is used. | `domain/speech/SpokenWords.kt`, `Grader.gradeSpeakRepeat` |
| Record and compare | Records one answer to a temp file, plays it back next to the model answer. Not kept. | `speech/AnswerRecorder.kt` |
| Oral practice | Rounds of 3 tasks from the exam units, preparation timer (1, 3, 5 min), self-scoring on the four CELI criteria, saved to `oral.json`. | `domain/oral/OralPractice.kt`, `ui/oral/OralScreen.kt` |
| Exercise types that speak | `speak_repeat` (14 items), `speak_respond` (155), `roleplay` (74), `listen_transcribe` (207), `listen_choose` (122). | `assets/course/units/*.json` |

### The gaps that matter

1. **Voice quality is out of our hands.** It depends on the phone: some have one robotic offline voice, and the "second speaker" is often just the same voice pitched down. A family on different phones hears different quality.
2. **Roleplay is linear.** Each turn has a fixed NPC line and a "you" line scored by keywords. If you say something sensible but different, nothing changes. The partner never asks a follow-up, never says "scusa, non ho capito", and never reacts to what you said.
3. **Speaking feedback is shallow.** "Which words did the recogniser hear" says little about pronunciation, nothing about grammar in what you said, and nothing about fluency, pauses or rhythm. `speak_respond` is scored on keywords.
4. **Almost no shadowing, dictation of connected speech, or listening at speed.** `speak_repeat` is 14 items out of 3,090. Listening is mostly TTS reading short sentences.
5. **No speed or voice variety in listening**, which the CELI listening paper has (different speakers, ordinary speed).
6. **No live captions or waveform** while speaking, so the learner doesn't know whether the mic is working until it fails.
7. **Single learner.** One name, one level, one set of reminders, one SRS deck, one DataStore. The Room database is at version 1 with no migrations.
8. **The mock oral is self-marked.** The real CELI oral is a conversation with an examiner, with materials received about 15 minutes before (see `docs/celi3-exam.md`).

## 3. Voice options and recommendation

The goal: a voice that sounds like a person, the same on every family phone, free, working offline.

| Option | Quality | Offline | Size | Effort | Notes |
|---|---|---|---|---|---|
| A. Phone's own TTS (today) | Varies, often mediocre | Yes if installed | 0 | done | Keep as the fallback. |
| B. Guide each phone to the best system voice | Better on Google TTS "high quality" voices | Yes after download | 20 to 100 MB per phone | small | Settings gets a voice preview list and a shortcut to the system voice download screen. Cheap, do it first. |
| C. Pre-rendered audio files for all lesson text | Neural quality, identical everywhere | Yes | roughly 35 to 60 MB for about 3,000 sentences (estimate: 3 to 5 s each at 24 to 32 kbps Opus) | medium | Generated by a script in CI, so the app only plays files. No runtime model. |
| D. On-device neural TTS engine (sherpa-onnx with Piper or Kokoro voices) | Neural quality for any text, including dynamic reply text | Yes | Piper voice about 60 MB (to verify); Kokoro model about 330 MB | large | Needed only for text that isn't in the lessons (generated replies, the learner's own words). |
| E. Gemini TTS | Excellent | No | 0 | medium | Reported as free for now but needs network, a key and the cloud terms. Not for core features. |

**Recommendation: B now, then C, then D only if the conversation engine needs it.**

- C is the key idea. About 99% of spoken Italian in the app is fixed text. Rendering it once at build time with a high-quality voice (Kokoro `if_sara` and `im_nicola` for two distinct speakers, which the sherpa-onnx documentation lists as Italian voices) means no phone needs to run a heavy model. Audio is stored per sentence, keyed by a hash of the text, so editing a lesson regenerates only that file.
- For scripted conversation we pre-render every variant line too, so the conversation feels as natural as a recording.
- D adds a runtime engine only for dynamic text. Piper is much faster than Kokoro on phones (the VoxSherpa project's own estimates are about 5 to 20 s per minute of audio for Piper versus 20 s to 3 min for Kokoro, depending on the phone), so Piper is the likely runtime voice, and Kokoro is for build-time rendering.
- Do not copy code from VoxSherpa-TTS: it is GPL-3.0 and this app is not. Use sherpa-onnx directly.
- **Licenses must be confirmed before any of this ships.** I could not confirm the license of Kokoro, Piper's Italian voices, or sherpa-onnx from the pages I could read. The app is a private family tool, but Obtainium releases are public on GitHub, so I'll treat licensing as a gate in Phase 2.
- **Android 16 KB page size and APK size:** native libraries add roughly 15 to 30 MB (estimate). This must build in CI first, since the cloud environment has no Android SDK.

## 4. Speech recognition (hearing the learner)

| Option | Quality for Italian | Offline | Size | Notes |
|---|---|---|---|---|
| Android `SpeechRecognizer` (today) | Good when the Italian pack is installed or online | Only with the offline pack | 0 in the APK | `PREFER_OFFLINE` is a preference, not a guarantee. It may send audio to Google's servers. |
| Vosk Italian | Moderate | Yes | tens of MB (Italian size to verify) | Apache 2.0, simple, but weaker than Whisper. |
| Whisper tiny/base/small via sherpa-onnx or whisper.cpp | tiny about 12%, base about 10%, small about 7% average word error across languages (not Italian-specific; Italian on tiny is known to be weak) | Yes | 75 / 142 / 466 MB | MIT. Small is the realistic minimum for learner speech, and is slow on mid phones. |
| Gemini Transcribe / Live | Very good | No | 0 | Cloud, same terms problems as section 5. |

**Recommendation:** keep `SpeechRecognizer` as the default (Phase 0 improves it: live partial text, an "install the Italian offline pack" prompt, and retry). Prototype one on-device Whisper model in Phase 4 as a consistent, private engine for the family. Decide after measuring accuracy on learner speech, not before.

## 5. AI options and the free-tier reality

Nothing below is verified from Google's own pages unless stated. Three of the pages I needed blocked automated reading or returned an empty page, so the numbers come from third-party trackers. **Phase 0 starts by reading the primary pages in a browser.**

### Gemini API (cloud)

- **Limits are not published as a fixed table.** Google shows each project's limits in AI Studio. Quotas are per project, not per key, and reset at midnight Pacific. Trackers report roughly 20 requests/day for the main Flash models and 500 requests/day for Flash-Lite (not official). Limits were cut in December 2025, so planning on today's numbers is risky.
- **Reported free models** include Flash and Flash-Lite generations, plus audio and text-to-speech models. Pro models are paid only.
- **Terms (from secondary sources, to verify):** users must be 18 or older; the API may not be used in a service directed at, or likely to be used by, under-18s; free-tier content may be used to improve Google's products; for developers outside the EEA, Switzerland and the UK, human reviewers may read free-tier prompts and responses; apps serving the EEA, Switzerland or the UK may use only the paid tier.
- **Consequences for this app:** (1) kids must not use it; (2) nothing personal in prompts, so no name, no recordings, only text; (3) if anyone in the family is in the EEA, UK or Switzerland, the free tier is not allowed for them; (4) the model names and limits will change, so the app must read the model name from a setting, not hardcode it.

### On-device: Gemini Nano via ML Kit GenAI

- The Prompt API is **Beta**, with structured output in **Alpha**, and speech recognition and text-to-speech in **Alpha**. It runs only on supported phones (Pixel 9 and newer, Galaxy S25 and newer, some OnePlus and Xiaomi models) and only while the app is in the foreground. The page does not say Italian is supported for the Prompt API. So: free, private and offline, but not guaranteed on a family's phones, and Italian quality is unknown.
- **Use it as "if available" with a capability probe**, never as a requirement.

### Other free options

- **Small open models on-device** (for example Gemma-class models via MediaPipe LLM Inference or llama.cpp): free and offline, but 1 to 3 GB downloads, slow on mid phones, and Italian quality at small sizes is mixed. Worth a spike in Phase 4, not a plan.
- **No-AI "dynamic" engine** (section 6): most of what makes a conversation feel alive can be done with a good script design. This is the base layer.

### Recommended AI posture

| Layer | Who | Needs | Default |
|---|---|---|---|
| 1. Scripted engine | Everyone, including kids | nothing | **On** |
| 2. Gemini Nano | Adults on supported phones | the phone | Off, offered in Settings if the probe passes |
| 3. Gemini cloud, free tier | Adults only | own API key, network, consent screen | Off |

## 6. Conversation and role-play engine design

### 6.1 Layer 1: scripted engine that feels alive

Replace the linear `roleplay` turn list with a **scenario graph**: states, expected intents, and the partner's reactions. Everything is JSON, so scenarios are content, not code.

```json
{
  "id": "rest-01", "title": "Al ristorante", "level": "B1", "partner": "cameriere",
  "goal": "Order a meal, ask about an ingredient, pay.",
  "states": [
    { "id": "greet",
      "say": ["Buonasera, benvenuti! Avete prenotato?", "Buonasera! Siete in due?"],
      "expect": [
        { "intent": "have_booking", "match": ["prenotato", "prenotazione"], "next": "seat_booked" },
        { "intent": "no_booking",   "match": ["non ho", "senza"],           "next": "seat_walkin" },
        { "intent": "ask_table",    "match": ["tavolo", "posto"],           "next": "seat_walkin" }
      ],
      "reprompt": ["Scusi, non ho capito. Avete prenotato?", "Come, scusi?"],
      "hint": "Say you have or haven't booked.",
      "onSilence": "reprompt" }
  ]
}
```

Techniques that make it dynamic without AI:

1. **Variant pools** for every partner line, so a replay differs. Pre-rendered audio per variant (section 3, option C).
2. **Intent matching, not exact words.** Normalise the transcript, accept synonyms and inflections (the app already has a 43k-lemma dictionary in `assets/dictionary`), and fuzzy-match to tolerate recogniser slips.
3. **Slot filling.** Remember what you said (a dish, a time, a name) and use it later: "Quindi, un tavolo per **quattro** alle **otto**, giusto?" Slots are filled from the transcript with small extractors (numbers, times, dates, food list per scenario).
4. **Repair behaviour like a real person**: "Come?", "Può ripetere?", "Intende dire…?", with a slow replay of the line when the learner fails twice.
5. **Recasts, not red marks.** If the learner says "Io voglio un acqua", the partner replies naturally with "Certo, **un'acqua**, naturale o frizzante?" and the end-of-scene review lists the correction. A small rule set from the grammar reference (articles, agreement, common congiuntivo slips) tags likely errors. This is the main teaching mechanism of good tutors.
6. **Optional branches and surprises** ("Mi dispiace, il pesce è finito."), so the learner must adapt.
7. **Level knobs**: partner speaking speed, vocabulary tier, number of hints, whether a transcript is shown. Same scenario, different difficulty per family member.
8. **Scene debrief**: goal achieved (yes/partly/no), phrases used, three corrections, three useful phrases to add to the SRS deck.

Authoring cost is the real limit: a good scenario graph is about 60 to 100 lines of JSON. **Target: 40 scenarios at launch** (10 each for A2, B1, B2 CELI-flavoured, plus a kid set), written with the Italian coach agent and checked by a validator in `tools/` and a unit test, as the project already does for lessons.

### 6.2 Layer 2 and 3: AI behind the same interface

Both are implemented behind one interface so the screen doesn't care:

```kotlin
interface ConversationPartner {
    suspend fun respond(history: List<Turn>, learnerText: String, scenario: Scenario, level: Level): PartnerReply
}
data class PartnerReply(val text: String, val corrections: List<Correction>, val nextHint: String?, val source: Source)
```

- `ScriptedPartner` (layer 1), `NanoPartner` (layer 2), `GeminiPartner` (layer 3).
- A `FallbackPartner` tries the chosen layer with a short timeout (for example 6 s), and on any failure, quota error or empty answer **falls through to the scripted partner for that turn**. The learner sees at most a small "offline mode" chip.
- The AI is **constrained by the scenario**: system prompt carries the scenario goal, level, persona and rules ("reply in at most 2 short Italian sentences at B1 level, ask one follow-up question, never switch to English"), and asks for structured JSON `{reply, corrections[], hint}` so the app can show corrections separately and render the reply with a pre-checked length. Replies are validated (length, Italian only) before use.

### 6.3 Free-tier safety (layer 3)

- **Client-side budget:** count requests per day per profile, stop at a conservative cap (for example 60% of the project's observed quota), show "AI warm-up used for today".
- **Fewer calls:** one call per learner turn, short history (last 6 turns), cache answers for repeated scenario states, never call for turns the scripted layer handles confidently.
- **Circuit breaker:** after 429 or 5xx, stop calling for 15 minutes.
- **Model name in a setting with a safe default**, because Google retires models (for example reports of 2.5 models shutting down on 2026-12-01 and 3.1 Flash-Lite in May 2027).
- **No spend risk:** use only a key from a project with billing never enabled. The app shows how to create it.

### 6.4 API key handling for a family

- **Never ship a key in the repo or the APK.** The repo is public and APKs can be unpacked. A shared key would also share one project quota across everyone.
- **Bring your own key:** each adult creates a free AI Studio key (steps shown in the app) and pastes it into their profile. It is stored with Android Keystore-backed encrypted storage, and is excluded from the app's backup export.
- **One key per adult profile** means separate free quotas, and no kid profile has the AI option at all.
- A parent PIN gates the AI settings.

## 7. Pronunciation feedback

Be honest about what is achievable at zero cost: **word-level and pattern-level feedback is realistic; true phoneme scoring is a research project.**

Stages, from cheap to ambitious:

1. **Word-level (today, improved).** Show live text while speaking, mark missed words, let the learner replay their recording next to the model line (already built). Use the recogniser's per-alternative confidence where the engine reports it.
2. **Targeted drills for known Italian trouble spots.** Double consonants (pala/palla, caro/carro), gli, gn, sc, c/g before e and i, stress position, open and closed e/o, final vowels. The app already has `minimal_pair` exercises for listening; add the *speaking* version: say one of two words, the recogniser must hear the right one.
3. **Fluency measures from the audio itself.** Speaking time, pauses longer than 0.6 s, words per minute versus the model answer. Needs only the recording and the transcript timing from Whisper or partial results. Useful for the CELI "adeguatezza, little hesitation" criterion.
4. **Shadowing score.** Learner repeats along with the model audio; compare duration and pause pattern, and word match.
5. **Phoneme-level scoring (stretch, Phase 5 spike).** Forced alignment with a small phoneme model on-device. Quality for learners is uncertain, so it only ships if the spike beats the simple methods.
6. **AI feedback (optional layer 2 or 3).** On the *transcript*, an AI can give grammar and register feedback. It cannot hear pronunciation unless audio is sent, and **we will not send audio**.

## 8. New listening, dictation and shadowing exercise types

All additions use the existing JSON format (see `docs/lesson-format.md`) and the content validator.

| New type | What the learner does | Notes |
|---|---|---|
| `shadow` | Hears a sentence, repeats it along with or right after the audio, sees fluency and word match | Short, 8 to 12 per session. Builds on `speak_repeat`. |
| `dialogue_dictation` | Hears a short two-speaker exchange at natural speed, types it (turn by turn) | Pre-rendered two voices. |
| `gap_listen` | Hears a passage, fills the missing words in the transcript | Mirrors CELI D.2. |
| `listen_order` | Hears sentences out of order, sorts them into the right sequence | Tests comprehension. |
| `speed_ladder` | Same passage at 0.7x, 1.0x, 1.2x, with questions | Trains listening at speed. |
| `answer_back` | Hears a question, answers aloud freely, gets keyword and fluency feedback and a model answer | The core of `speak_respond`, with variants. |
| `describe_photo` | Describes an image aloud for 60 s, then compares with a model description | Needs licensed images (not solved yet, see risks). |
| `summarise_aloud` | Reads a short text, then summarises it aloud | CELI oral task 2. |
| `minimal_pair_say` | Says one of two words (pala/palla) and the app checks which one was heard | Pronunciation drill. |
| `phone_call` | Voicemail or phone dialogue with poor-quality audio effect | Realistic listening. |
| `radio_clip` | Short news or announcement with a gist question | B2 and C1 listening. |

Targets: raise speaking and listening from about 8% (243 speaking) and 11% (329 listening) of exercises to roughly 20% and 18%, by adding about 600 new items over the phases, authored by the Italian coach agent.

## 9. CELI B2 speaking-task simulation

Per `docs/celi3-exam.md`: about 15 minutes, three materials handed over about 15 minutes beforehand: a short self-presentation, then (1) describe and discuss a photo, (2) summarise a short text and discuss it, (3) a role-play (compito comunicativo). Pass mark is 33 of 60 in the oral, and the criteria are *lessico, grammatica, adeguatezza sociolinguistica, fonetica*.

Design of an **Esame orale simulato** mode:

1. **Preparation phase.** The three materials appear with a 15-minute countdown (the existing prep timer, extended), with a note pad. Notes are available during the exam like the real one, if the learner chooses (the real rules should be confirmed).
2. **Examiner voice.** Pre-rendered examiner lines in a distinct voice: greeting, "Si presenti", follow-up questions, closing.
3. **Examiner follow-ups.** Layer 1 asks pre-written follow-ups tagged to the material, selected by keywords from the answer ("Perché pensa che…?", "E nel suo paese com'è?"). Layer 2 or 3 can improvise follow-ups for adults.
4. **Timing and pacing.** Per-task timers and a visible "you've spoken for 1:20" meter. No pause longer than 5 seconds without an examiner prompt.
5. **Recording.** The whole session is recorded locally, played back with a transcript, and deleted unless the learner saves it.
6. **Marking.** Auto: keyword coverage of the task requirements (like the writing checker's "coerenza"), fluency numbers, a grammar tag list from the transcript. Learner: self-scoring against the four criteria (existing). Optional adult layer: AI marks the *transcript* against the published criteria and suggests a band, shown as a guess, never as an official result.
7. **Trend.** Average per criterion over the last 10 sessions (already in `OralLog.averages`), now with fluency and task-completion lines.
8. **Content.** 12 complete simulated orals at launch (36 materials), photos being the main open problem. Prior review item S1 (real oral photos) needed licensed images. Options: CC0 or Unsplash-licence photos pulled in during authoring, with the licence recorded per image.

## 10. Family profiles

### 10.1 Who it needs to serve

A single shared phone or several, with: Richard (B1 to C1, CELI B2), other adults (different levels and goals), and kids (need very different content and rules).

### 10.2 Design

- **Profile** = name, avatar, age band (child 6 to 9, child 10 to 13, teen, adult), level (A1 to C1), voice and speed, reminders, exam goal (optional), AI options (adults only), PIN-protected flag.
- **Per-profile data:** SRS cards, exercise results, lesson checkpoint, writing and oral logs, flags, reminders, settings. Today these live in one Room database (v1, no migrations), one DataStore, and several JSON files in `filesDir`.
- **Technical plan:** add a `profileId` to Room entities via a **real migration** (tests with Room's migration testing) and move DataStore and JSON files under `filesDir/profiles/<id>/`. Migrate the existing data into profile #1 "Richard" on first run, with a backup written first. This is the riskiest data change in the proposal, so it gets its own phase and a rollback path (the existing backup export).
- **Switcher:** a profile picker on launch (skippable when there is one profile), and a quick switch from the Home screen.
- **Adult controls:** a parent PIN guarding profile management, AI settings and exports.
- **Reminders per profile**, with the existing gentle-notification rules, and the notification names the learner.

### 10.3 Kids and difficulty

- Kid profiles use the A1 track plus new **kid lessons** (colours, family, animals, school, food, simple games), larger buttons, more audio, fewer typed answers, and short sessions (about 3 to 5 minutes).
- **Kids get the scripted engine only.** No AI, no free typing to external services, no stored recordings by default.
- Difficulty is a profile property: partner speed, hint level, vocabulary tier, whether the transcript is shown, and how strict the answer check is.
- This is a **large content job** (kid content does not exist). It is its own phase and Richard should decide whether kids are in scope for the first release.

## 11. Privacy

- **Default: nothing leaves the phone.** Recordings are temp files deleted after use, unless a learner saves one.
- **Android's recogniser may use Google servers** unless the offline Italian pack is installed. The app says so in plain words and prompts for the offline pack. An on-device Whisper (Phase 4) removes that uncertainty.
- **AI layers send text only, never audio**, with no names or personal data, behind an explicit consent screen per profile that says what is sent, to whom, and that free-tier text may be reviewed by people.
- **Kids:** no network calls for speech or AI, no analytics, no cloud. This keeps the app clear of child-data rules (US COPPA, EU GDPR for children) for the child profiles.
- **Keys** are stored encrypted, never in backups or logs.
- Backup export carries profile data and optionally excludes recordings and keys.
- The manifest already declares `INTERNET`. The AI layers would be the only speech code that uses it deliberately.

## 12. Decisions needed from Richard

1. **Is the family multi-profile (Phase 1) in the first release, and are kids in scope?** Kids need a new content track.
2. **Voice route:** C (pre-rendered neural audio, recommended) alone, or also D (runtime neural TTS)?
3. **Gemini:** do you want the optional adult layers at all? Where is the family located (EEA, UK or Switzerland rules matter), and are you happy with the 18+ and free-tier data-use terms?
4. **Photos for the oral:** OK to use freely licensed photos with the licence recorded?
5. **Order:** my default is the phase order below.

## 13. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Free-tier limits or terms change | High | AI layer loses value | AI is optional, model name configurable, scripted layer is the base |
| Gemini terms forbid the family use (kids, EEA) | High for kids | Compliance | Adults only, per-profile consent, kids never use it |
| Voice or model licences prevent redistribution | Medium | Voice pack blocked | Licence check is a Phase 2 gate; fall back to option B |
| Native libs fail CI, grow APK, or fail the 16 KB page-size rule | Medium | Delay | Spike in CI first; ship voices as a separate downloadable pack, not in the APK |
| No Android SDK in the cloud; CI is the only compile (about 4 min) | Certain | Slow cycles | Small PRs per phase, pure-Kotlin logic with unit tests, as done so far |
| Profile migration corrupts data | Low | Severe | Backup first, migration tests, rollback via export, ship before anything else builds on it |
| Authoring 40 scenarios and 600 items is large | Certain | Time | Italian coach agent plus validator and tests; ship in waves |
| Recogniser errors on learner speech frustrate | High | Motivation | Generous matching, "I said it right" override (exists for typed answers), show alternatives |
| Gemini Nano missing or Italian poor | Medium | Layer 2 unused | Probe at runtime; layer 2 is optional |
| Photo licensing | Medium | Oral mode incomplete | Use CC0 and recorded-licence images, or text-only tasks |

## 14. Phased roadmap

Estimates are part-time working sessions, one PR per step, CI-compiled. "S" is about 1 session, "M" 2 to 4, "L" 5 or more.

| Phase | Goal | Contents | Effort |
|---|---|---|---|
| **0. Foundations and quick wins** | Better today, no new engines | Read Google's primary terms and limits; voice preview list and system-voice install shortcut; live partial captions and a level meter while speaking; offline-pack prompt; `answer_back` and fixes to `speak_respond` feedback; audio focus; capability probes for AI | M |
| **1. Family profiles** | Everyone has their own progress | Profile model, Room migration with tests, per-profile files and settings, switcher, PIN, per-profile reminders and voice | L |
| **2. Neural voice pack** | Natural voices everywhere | CI render script with Kokoro, hash-keyed audio files, downloadable pack or bundled assets, player with caching, fallback to system TTS, licence gate | L |
| **3. Conversation engine v1** | Role-play that reacts | Scenario graph JSON schema and validator, intent matcher, slot filling, variants, repair, recasts, debrief; 40 scenarios in waves; new Conversation screen; dialogue voices from Phase 2 | L |
| **4. Listening, shadowing, dictation** | More hearing and saying | `shadow`, `dialogue_dictation`, `gap_listen`, `listen_order`, `speed_ladder`, `minimal_pair_say`, fluency measures, about 300 new items | L |
| **5. CELI B2 oral simulator** | The exam, rehearsed | Prep timer and notes, examiner voice, follow-up logic, whole-session recording, marking and trend, 12 mock orals | M to L |
| **6. Optional AI layers** | Open-ended conversation for adults | `ConversationPartner` interface, Nano probe and partner, Gemini partner with budget guard, key entry, consent, fallback chain | M |
| **7. On-device Whisper and pronunciation spike** | Consistent private recognition | Whisper (small, if speed allows) via sherpa-onnx or whisper.cpp, fluency from word timings, optional phoneme alignment spike | M to L |
| **8. Kids track** | Family-wide | Kid UI mode, about 20 kid lessons, rewards, restricted settings | L |

Rough total for phases 0 to 5: about 8 to 12 weeks of part-time sessions. Phases 6 to 8 can follow in any order once the base is in.

**Why this order:** profiles first because every later feature writes per-person data; voice before conversation because conversation lines should be spoken in the final voices; AI after the scripted engine, because the scripted engine is the safety net the AI falls back to.

## 15. Sources

Google's own pages blocked automated reading for some of this research, so treat the Gemini details as "to verify" until Phase 0.

- ML Kit GenAI overview: https://developers.google.com/ml-kit/genai (read 2026-11-18, updated 2026-11-10)
- Gemini API additional terms: https://ai.google.dev/terms (not readable in research; verify first-hand)
- Gemini API limits and pricing tracker (third party): https://agentdeals.dev/gemini-api-pricing-2026
- sherpa-onnx Kokoro multi-language model (Italian voices `if_sara`, `im_nicola`): https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/kokoro-multi-lang-v1_0.html
- Kokoro on Android guide: https://soniqo.audio/guides/kokoro/android
- VoxSherpa (GPL-3.0, reference only): https://github.com/CodeBySonu95/VoxSherpa-TTS
- Vosk versus Whisper overview: https://www.sinologic.net/en/2026-05/vosk-vs-whisper-local-the-ultimate-2026-guide-to-self-hosted-speech-recognition-stt.html
- Whisper model sizes: https://openwhispr.com/blog/whisper-model-sizes-explained
- Repo facts: `speech/`, `domain/speech/`, `domain/oral/`, `assets/course/units/`, `docs/celi3-exam.md`, `docs/lesson-format.md`
