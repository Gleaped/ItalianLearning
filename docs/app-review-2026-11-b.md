# App review 2: what to build next (2026-11-14)

Second review of Italiano, written after every item of the first review (bugs B, learning L, comfort C, exam E, notifications N and export P) shipped in PRs #9 to #14. This one does not repeat them. It asks a different question: **what stands between the app as it is and a pass at CELI 3 (B2), then C1?**

- Reviewed: `main` at `8381a2a`, the course JSON, the dictionary database and the Kotlin sources.
- Not done: I could not run the app on a phone or emulator here. Everything about screens and feel comes from reading the Compose code, so treat the UI items as "check on the phone first".
- Review only. No app code was changed.

## Status of the suggestions

Built so far (phase D1: X2 a second mock paper, unit celi-09; phase C2: a first Biblioteca in the exam track, four reading texts of about 280 words and six listening texts of 150 to 200 words, as units celi-07 and celi-08; more texts and a dedicated Leggere section remain; phase C1: V1 core words and V4 verb drill, with a note that the top frequency words still have no example sentences; phase B: W1, W2, S2, S3; phase A): X1 exam date, countdown, pace and readiness; U3 flag button (as "Segnala"); L4 forgiving streak (one rest day a week); U2 simpler Today; U4 launcher shortcuts; W3 improved. The rest follow in the order at the end. Correction: the saved-texts screen already had a *Share* button when this review was written. W3 therefore became "Ask for feedback", a share that adds a ready-made request (mark mistakes, correct, score on the four exam criteria).

## How to read the ratings

| Rating | Meaning |
|---|---|
| **Impact** | High: moves the odds of passing or the speed of learning noticeably. Medium: clearly useful. Low: nice to have. |
| **Effort** | S: one small PR. M: one solid PR, or a PR plus a moderate content batch. L: several PRs, or a large amount of content writing. |

Suggestion ids use new letters (V, R, W, S, X, L, U, T) so they do not collide with the first review.

## The short version

The app is now a solid *teaching* tool: structured lessons, a working review deck, weak-spot loops, a CELI-shaped track. The gaps that matter most are about **volume and feedback**, not features:

1. **Too little language goes in.** The whole course lists 902 vocabulary headwords. Reading passages at B1 to C1 average 87 to 110 words and listening clips average 11 to 13 words. B2 and C1 need far more text and sound than that.
2. **Nothing checks what comes out.** Writing is self-scored with a checklist, and speaking is scored by keyword matching. Both are 40% to 50% of the CELI mark and neither gets real feedback.
3. **The exam is not yet planned for.** There is no exam date, no projected score against the real pass marks, and only one mock paper.
4. **A few cheap things would make the whole app easier to live with**: navigation, a "flag this exercise" button, a gentler streak.

Recommended order of work is at the end. The top six, in order:

| # | Item | Impact | Effort |
|---|---|---|---|
| 1 | X1 Exam date and readiness against the official pass marks | High | S |
| 2 | W1 + W2 Writing: share for feedback, and an offline checker | High | M |
| 3 | R1 + R2 Longer reading and listening | High | L (content) |
| 4 | V1 Core vocabulary deck from the dictionary | High | M + content |
| 5 | S1 + S2 Real photos and record-and-compare for the oral | High | M |
| 6 | L1 Check my level and test out of units | Medium-High | M |

## What the app measures today

Counted from the shipped content on `main`.

| | A1 | A2 | B1 | B2 | C1 | CELI 3 |
|---|---|---|---|---|---|---|
| Units / lessons | 10 / 46 | 10 / 46 | 15 / 68 | 11 / 51 | 10 / 47 | 6 / 19 |
| Exercises | 406 | 436 | 624 | 465 | 438 | 267 |
| `reading` exercises | 5 | 4 | 12 | 10 | 12 | 11 |
| Average reading passage (words) | 38 | 51 | 87 | 88 | 110 | 172 |
| Listening exercises (`listen_*`) | 41 | 46 | 65 | 52 | 45 | 35 |
| Average audio clip (words) | 5 | 8 | 11 | 13 | 12 | 30 |
| Longest audio clip (words) | 12 | 35 | 54 | 52 | 52 | 76 |
| Listed vocabulary headwords | 183 | 159 | 269 | 186 | 175 | 124 |

Other numbers:

- Distinct vocabulary headwords across the course: **902** (928 entries before removing duplicates).
- The offline dictionary has 43,135 lemmas and 360,466 forms. Of the 3,000 most frequent lemmas (`freq_rank`), only about **270 appear in the course vocabulary lists** (about 9%). The lessons use more words than the lists name, so this is a ceiling on the gap, not the exact gap.
- Of the 5,000 most frequent lemmas, **none has an example sentence or an Italian definition** in the dictionary. They only have English glosses and part of speech. This matters for V1.
- Of the 101 `translate` exercises, 19 carry 10 or more hand-written accepted answers (the largest has 32).
- `Streak.current` resets to 0 after one missed day. `lapses` is stored on every card but nothing reads it.
- All five bottom-bar slots are used and one of them is Settings. Progress, "Punti deboli", the daily mix and my texts are reached only from the Today screen, as far as I could see in `AppNavigation.kt`.

## Already built: not repeated here

Shuffled options, new-card count fix, accent keys, lesson resume, mistake re-ask round and Punti deboli, slip tolerance and "my answer was right", produce cards and grammar cards in the deck, word-level speech feedback, "say it", voice choice, listen-first role-plays, 152 dictations, progress by skill, saved writing, smarter reminders, today's plan, daily mix, timed mock paper with exam clock, backup and restore, dictionary with long-press lookup, Grammatica reference, congiuntivo track CG1 to CG8.

---

## 1. Vocabulary: the biggest gap

### V1. Core vocabulary deck from the dictionary

- **Impact: High. Effort: M plus content.**
- **Problem.** 902 headwords will not carry a learner to B2 reading, and the CELI Part A and C texts are authentic journalism. I do not have an official word-count requirement for B2 (the CELI publishes none that I could find), but commonly quoted ranges for B2 are in the thousands of words, not hundreds. Treat that as a rough guide.
- **Idea.** An optional "Parole di base" deck: the app already has frequency ranks for every lemma and a path for adding dictionary words to the deck (`Cards.dictionaryId`, `addDictionaryWord`, card ids `d:<id>`), which needs no schema change. Add a setting "new core words per day" (default 5, on top of the course cards) and feed the deck in frequency order, skipping words already in the course vocabulary.
- **Catch 1.** The dictionary has no examples for the top 5,000 lemmas, and a card with only a bare gloss is weak for verbs and abstract nouns. Plan to generate and check one short example per word in batches of about 500, as a build-time script like `tools/add_dictations.py`, stored in a small extra table or JSON (not in the 61 MB database). Without examples, ship nouns and adjectives first and hold verbs back.
- **Catch 2.** Raw frequency lists contain names, abbreviations and words a B2 learner does not need. Filter by part of speech and run a cheap quality pass before anything reaches Richard's phone.
- **Catch 3.** More new cards means more daily reviews. Pair this with the review load view in L2.

### V2. Topic vocabulary tied to CELI themes

- **Impact: Medium-High. Effort: L (content).**
- The exam texts are about contemporary society: work, school, health, environment, bureaucracy, media, housing, transport, consumer issues. The units teach grammar first and attach 15 to 25 words each. Add short **topic packs** (about 40 words each: collocations, a short text, 6 exercises) so the words arrive in the setting they appear in, ordered by exam theme rather than by grammar unit.
- Reuse the existing unit format (`vocabulary`, `reading`, `gap_fill`). No code change needed to start.

### V3. Word families for CELI C.3

- **Impact: Medium. Effort: M.**
- Part C.3 asks for a word derived from a given word (for example *scegliere to scelta*). The track has 7 practice items. Build a table of about 250 word families plus the common suffix patterns (*-zione, -mente, -tore, -ità, -bile, in-/dis-/ri-*), and a drill that uses it. The pattern part can be generated from the dictionary; the irregular families need to be written.

### V4. Endless verb conjugation drill from the dictionary

- **Impact: High. Effort: M.**
- Every verb in the dictionary carries its full forms table (`forms_json`). A drill screen can ask for any form: "congiuntivo imperfetto, voi, *dare*", weighted toward verbs and tenses the learner gets wrong, and scored by the existing `Grader.gradeConjugation`. This is unlimited practice with no new content, and the congiuntivo and condizionale forms are exactly where B1 to B2 learners stumble.
- Note: Settings could offer "tense set" (indicative only, with congiuntivo, all) so it follows the course.

---

## 2. Input: reading and listening

### R1. A graded reading library

- **Impact: High. Effort: L (content).**
- **Problem.** About 12 reading exercises per level, averaging under 110 words. In CELI-style reading (journalism, letters to the editor, interviews, adverts) the stamina to read several hundred words and still answer is a skill of its own, and a short passage cannot train it. The exam doc notes that text length is not published, so the target is "clearly longer than now", not an exact figure.
- **Idea.** A "Leggere" section outside the lessons: 60 to 100 texts of 250 to 500 words, tagged by level (B1, B2, C1) and by CELI text type, each with 4 to 6 questions in the real A.1, A.2 or A.3 shapes. Tap-to-lookup already exists (`LookupText`). Add: a word-count and reading-time line, "mark words I did not know" that sends them to the deck, and a read-aloud button with the sentence being spoken highlighted.
- **Sources.** Write original texts (safest for licences). For authentic style, Italian Wikinews and Wikipedia are CC BY-SA and could be adapted with attribution (the *Fonti* sheet already handles this for the dictionary). Public-domain literature (Italian Wikisource) suits C1 reading but not exam-style journalism.

### R2. Longer, more natural listening

- **Impact: High. Effort: M.**
- **Problem.** Average clip 11 to 13 words at B1 to C1. The CELI listening section has 20 items over two recordings and a completion task; real recordings are minutes long, with several speakers and mild regional accents. Text-to-speech is also very even and clean.
- **Ideas, cheapest first:**
  1. Write multi-turn dialogues and short radio-style monologues of 80 to 200 words. The app already has two-voice dialogue playback (`dialogue = true`, `second`) and a listen-first role-play mode.
  2. An **exam mode** for listening: plays twice at most, no transcript until the end, then shows the transcript with tap-to-lookup. (How many times the real recordings are played is not in the official documents we read, so make the count a setting, default 2.)
  3. A speed setting per exercise (now 100% or 70% "slow") with a 120% step, to train for fast speech.
  4. After answering, show the transcript with the answer sentence highlighted. This is the best moment to learn from a missed detail.
- **Later and bigger:** real recordings. Public-domain audiobook readings (for example LibriVox) are free to bundle but are literary, not exam-style, and add size to a 60 MB app. I would not start here.

### R3. A shadowing session

- **Impact: Medium. Effort: S to M.**
- Chain 8 to 10 sentences from lessons already finished (the app has the audio text and the speak-and-score machinery): listen, say it, get per-word feedback, repeat the worst words. It gives a daily 5-minute speaking habit that needs no new content.

---

## 3. Writing: the part with no feedback

Writing is B.1 plus B.2 at 20 marks each, 40 of the 140 written marks. Today's flow is: write, read the model answer, tick a checklist, optionally score yourself 1 to 5 on the four official criteria. It is honest, but a learner cannot see their own mistakes.

### W1. An offline "things to look at" checker

- **Impact: High. Effort: M.**
- Without a network or an AI service, a rule-based check can still catch real errors. It should be presented as notes, not a mark:
  - **Unknown words.** Check each word against the 360,466 known forms; flag misspellings and missing accents.
  - **Frequent learner errors**, from a small hand-written list: *qual'è*, *a me mi*, *un'amico*, *più meglio*, wrong *-ci/-ce* spellings, *piuttosto che* for "or", *io sono d'accordo con* patterns, missing *che* before the congiuntivo, a verb after *penso che* that is plainly indicative.
  - **Register.** Mixed *tu* and *Lei* in one letter.
  - **Task coverage.** The prompt has bullet points; check that each has a keyword hit (same idea as `speak_respond` keywords).
  - **Range.** Repeated words, very short or very long sentences, and a count of connectors used (*tuttavia, inoltre, nonostante*) against a short list.
- **Honest limit.** It cannot judge whether grammar is correct in general. Show it as "worth a second look", and keep the self-score step.

### W2. A rewrite step

- **Impact: Medium. Effort: S.**
- After the checker and the model answer, offer "Rewrite it" with the first draft beside the editor, and keep both drafts in *I miei testi*. Revising is where writing improves; today the model answer ends the exercise.

### W3. "Copy for feedback" share button

- **Impact: High. Effort: S.**
- On a saved text: one button that shares the prompt, the text and the CELI B.1/B.2 criteria as plain text through Android's share sheet, so Richard can paste it to a tutor (italki, a teacher) or to Claude on the phone. The app keeps **no network permission**, since the share sheet hands off to another app. This is the highest value-for-effort item in the review.
- Extend to speaking (S2) with the recogniser's transcript.

### W4. A writing toolkit in Grammatica

- **Impact: Medium. Effort: S (content).**
- A section "Scrivere" in the reference: openings and closings for formal and informal letters, connectors by function (contrast, cause, concession, sequence), phrases for giving an opinion, and the B.2 letter types the exam doc lists (information, advice, complaint, plans). The reference format already supports charts and examples.

---

## 4. Speaking

### S1. Real photos for the oral exam

- **Impact: High. Effort: M.**
- The CELI oral includes describing a photo. The app has no images: `celi-05` says "Immagina la foto: …" and the prompt describes the scene in words, which removes the actual task (looking, choosing what to say, hypothesising). Bundle about 40 photos (about 3 MB at modest size) from public-domain or CC0 sources such as Wikimedia Commons, with a credit line, and let `speak_respond` carry an optional `image`. Needs a small content-format change and a validator rule.

### S2. Record and compare

- **Impact: Medium-High. Effort: M.**
- Speaking is scored by recogniser overlap and keyword hits. That shows whether you said the words, not whether the sentence was good or the pronunciation clear. Add a record button on `speak_respond` and role-plays: play back your answer beside the model answer, then score yourself on the four official oral criteria (lessico, grammatica, sociolinguistica, fonetica) with the same sliders the writing exercise has. Save the scores in progress.
- The speech recogniser and the audio recorder both want the microphone, so they must run one after the other (record first, or recognise first). Another option: keep the recogniser's transcript and run the W1 checker on it.

### S3. A 15-minute prep timer

- **Impact: Low-Medium. Effort: S.**
- The real oral gives about 15 minutes of preparation with the materials. A "Prova orale" practice flow: a random set of three prompts (photo, text to summarise, role-play), a preparation timer with a notes box, then speaking. The mock paper has a single oral lesson; this makes it repeatable.

---

## 5. Exam preparation

### X1. Exam date, countdown and readiness

- **Impact: High. Effort: S.**
- **Problem.** The app does not store an exam date, and the pass marks from `docs/celi3-exam.md` (84/140 written, 33/60 oral, 117/200 overall) are not used anywhere in the code. The mock reports a percentage with a flat 60% target.
- **Idea.** A date field in Settings. Today then shows "73 days to the exam" and a **readiness card**: estimated written and oral score on the official scale, built from the skill accuracies the app already stores, with a plain "not enough data yet" until there are enough answers. Mark it clearly as a rough estimate; the examiners mark the real thing.
- Add a pacing line: "At 4 lessons a week you will finish B2 on 12 February", so the date means something. Keep the tone gentle, as Richard asked.

### X2. More than one mock paper

- **Impact: High. Effort: L (content).**
- `celi-06` is one paper (10 + 38 + 23 + 9 exercises). Once it is done it is done, and the memory of the answers makes a second sitting meaningless. Write at least three more papers, at the real shape: Fascicolo 1 with two texts for A.1, two texts compared for A.2 and one text with open questions for A.3, plus B.1 and B.2; the cloze, rebuild and word-formation tasks for C; two recordings for D. Randomise nothing; just add papers.
- The official past papers are on the Perugia site. Check their terms before bundling anything; the safer route is X4.

### X3. Open-answer questions (A.3)

- **Impact: Medium. Effort: M.**
- The doc says A.3 has four open questions marked 3, 2 or 1. The app has `reading` with multiple choice only, and `celi-01` handles A.3 with a `free_write` and self-check. Add an `open_answer` exercise: the learner types a short answer, the app shows the key points a full-mark answer contains (each as a tick-box the learner can judge) and the model answer. A keyword pre-check can suggest the tick-boxes.

### X4. Log a real practice paper

- **Impact: Medium. Effort: S.**
- The most valuable mock is an official past paper under real conditions on paper. Let the learner enter part scores for a real paper (A, B, C, D, oral) and see them against the pass marks, over time, next to the in-app mocks.

---

## 6. Learning engine

### L1. Check my level, and test out of units

- **Impact: Medium-High. Effort: M.**
- **Problem.** The main path (B1, then B2, then C1) opens one unit after another. Richard started at B1 and is aiming at B2 in a limited time; B1 alone is 68 lessons. The only way to skip is "Unlock all units", which skips everything with no check.
- **Idea.** A 15-minute "Check my level" made of existing exercises sampled from each unit. It marks units as "known" when the learner scores 85% or more on a sample from them, and offers the test as an option on each Ripasso checkpoint ("Test out of this module"). The result is advice, never a lock. This also makes the readiness estimate in X1 more honest.

### L2. Scheduler improvements

- **Impact: Medium. Effort: M.**
- The scheduler is plain SM-2 (`Sm2.kt`): no interval fuzz, no handling of cards that keep failing, and a fixed 1-day interval after a lapse. Options in order of value:
  1. **Leeches.** A card with 6 to 8 lapses is shown a different way: a prompt to rewrite it with a mnemonic or a better example, or to suspend it. `lapses` already exists on the card.
  2. **A review-load view** (cards due over the next 7 and 30 days) so the daily new-card setting is chosen with its cost in view. Needed with V1.
  3. **Fuzz and spacing of twins.** A small random spread on long intervals; keep a word and its produce twin from appearing in the same session.
  4. **FSRS.** An open-source scheduler that models stability and difficulty; published benchmarks report fewer reviews for the same retention than SM-2 style schedulers. I have not re-run those. It needs new columns, and the Room database is at version 1 with no migrations, so this would be the first migration. Do 1 to 3 first and decide on this later.
- **More card types**, in the same deck: hear-and-type (audio to Italian) and meaning-in-context cloze from lesson sentences (the produce cards already use cloze when the example allows it).

### L3. Weak spots by topic, not by unit

- **Impact: Medium. Effort: M.**
- `SkillStats.weakestTopics` groups wrong answers by unit. A unit mixes several rules, so "b2-02 is weak" does not say whether it is *se + presente* or *se + congiuntivo*. Tag each exercise with a topic id matching a Grammatica reference topic (a script can do most of this from grammar-note titles and the existing `grammarTips`), then show "Weak: periodo ipotetico, tipo 2" with a button into the reference page and a targeted drill.

### L4. Streak that forgives

- **Impact: Medium. Effort: S.**
- A single missed day resets the streak to 0. Richard asked for gentle, non-nagging behaviour, and a reset is the opposite of that. Options: one free rest day per week that does not break the streak; or count "days practised in the last 7" instead of a chain; or a daily-minutes goal. Any of these removes pressure without removing the habit signal.

### L5. Italian explanations at B2 and C1

- **Impact: Medium. Effort: L (content) or M (toggle first).**
- Grammar notes, explanations and goals are written in English at every level (the B2 sample I read was). The exam is entirely in Italian, including the instructions. A setting "Spiegazioni in italiano" for B2 and above that shows an Italian explanation and a "show English" tap would train reading metalanguage. The cost is writing about 550 explanations in Italian at B2 and C1, so start with the `takeaway` and `goal` lines (one line each) and grammar notes in the CELI track.

---

## 7. Interface

None of this has been looked at on a phone.

### U1. Rework the bottom bar

- **Impact: Medium-High. Effort: M.**
- Today: Oggi, Corso, Grammatica, Ripasso, Impostazioni. Settings is something you open a few times a month and takes a slot; the daily practice modes (Ripasso, Punti deboli, Mix del giorno, shadowing, verb drill) and progress are spread or hidden.
- Suggestion: **Oggi, Corso, Pratica, Grammatica, Progressi**, with Settings as a gear in the top bar next to the dictionary search. "Pratica" becomes the home of review cards, weak spots, the mix, V4 and R3.

### U2. Simplify Today

- **Impact: Medium. Effort: S.**
- The next lesson appears twice (the big card and the "A lesson" row of the plan). Levels list A1, A2, B1, B2, C1, CELI, which shows five bars most of which are not relevant on a given day. Suggest: one plan card as the single list; a single "your path" line (current module and the exam countdown from X1); full level bars on the Course tab only.

### U3. "Flag this exercise"

- **Impact: High (for quality). Effort: S.**
- A small flag on every exercise (and on cards): saves the lesson id, exercise id and an optional note locally and includes it in the backup file. With the share button from W3, Richard can send the list and each round of content gets corrected. The course has about 2,600 exercises written without a native-speaker review, so errors and over-strict grading are certain to exist, and this is how they get found.

### U4. Launcher shortcuts

- **Impact: Low-Medium. Effort: S.**
- Long-press the app icon: "Ripasso", "Prossima lezione", "Dizionario". Static shortcuts need only an XML file.

### U5. Lesson summary

- **Impact: Low-Medium. Effort: S.**
- On the wrap-up screen, add "words from this lesson" as tap-to-add-to-deck (the cards are added automatically today, so this would only appear for optional words), and a one-tap "practise what I missed" that starts the re-ask round without waiting for Punti deboli.

---

## 8. Quality and engineering

### T1. Close issue #5 after checking on the phone

- **Effort: S.** Issue #5 (keyboard covering the text inputs) is still open and the overhaul notes say it needs on-device confirmation. Confirm or close it.

### T2. Screen-level tests for text input

- **Impact: Medium. Effort: M.**
- Unit tests cover grading, scheduling, parsing and reminders. Nothing tests a screen. The keyboard problem in #5 came back after a first fix because no test could see it. Compose UI tests with Robolectric run on the CI machine without an emulator; start with the lesson answer field, the review screen and the writing screen.

### T3. Content checks beyond structure

- **Impact: Medium. Effort: S to M.**
- `tools/validate_content.py` checks structure and format 2 rules. Add warnings for: the correct option sitting in the same position more than 40% of the time within a unit, duplicate or near-duplicate prompts across lessons, `translate` answers with long hand-written variant lists, speak keywords that match the prompt's own words, and audio text with symbols that text-to-speech reads badly.
- **Accepted-answer syntax.** Instead of 28 listed variants, allow `Se (domani) piove, (restiamo|rimaniamo) a casa` and expand it when the content is parsed. Fewer typos, better coverage, easier editing. 19 of 101 translations would benefit now.

### T4. Keep the Room schema honest

- **Effort: S.** The database has no migrations, and later work (FSRS, extra card fields, topic tags) will need some. Turn on schema export and add one migration test before the first schema change, so a release cannot wipe Richard's progress.

---

## Considered, not recommended

- **An AI chat partner or tutor inside the app.** It would need a network permission and a service, which conflicts with the app's offline, no-account design. The share button (W3) gives most of the benefit without it.
- **Leaderboards, XP and friends.** Single learner; adds noise against the gentle tone he asked for.
- **Bundling official CELI past papers.** Copyright is unclear. Use them on paper and log the scores (X4).
- **Recorded native audio for everything.** Costly to source and licence, and it would grow the APK a lot. A small set later, if a clear need appears.
- **A second dictionary or a bigger one.** The existing 43,000 lemmas are enough; examples and use are the gap, not coverage.

## Suggested order

| Phase | Items | Why first |
|---|---|---|
| **A: small and quick** | X1 exam date and readiness, W3 share for feedback, U3 flag button, L4 forgiving streak, U2 simpler Today, U4 shortcuts, T1 issue #5 | Each is one small PR and makes everything after it easier to steer. |
| **B: feedback** | W1 checker, W2 rewrite step, S2 record and compare, S3 prep timer | Closes the loop on the two skills with no real marking. |
| **C: volume** | V1 core deck (with L2 load view and leeches), V4 verb drill, R2 longer listening, R1 reading library, V2 topic packs | The largest effect on reading and listening, and the largest amount of writing. Can run across several releases. |
| **D: exam shape** | S1 photos, X2 more mock papers, X3 open answers, X4 log real papers, V3 word families | Best done once the volume is up, so the mocks test something. |
| **E: structure** | L1 level check and test-out, L3 topic weak spots, U1 new navigation, L5 Italian explanations, T2 UI tests, T3 content checks, T4 migrations | Larger changes; U1 and L1 are worth doing early if the daily routine feels clumsy. |

## Questions for Richard

1. **When is the exam, or when would you like to sit it?** The date drives X1 and the pacing of everything else.
2. **Would you use an in-app writing checker (W1), or is "send it to a tutor or Claude" (W3) enough?** W3 is far cheaper.
3. **How many new core words a day is comfortable on top of the lesson cards?** (V1; 5 is my starting guess.)
4. **Is a second voice or a person (a tutor, a language exchange) in your plans?** It changes how much to build for speaking feedback.
5. **Should Settings leave the bottom bar (U1)?** Check how often you open it.

## How the numbers were gathered

All figures come from scripts run on the course JSON and `dictionary.db` at `8381a2a`: exercise and type counts per level (from the unit files and `course.json`), passage and audio-clip lengths in words (`passage` and `audioText`), vocabulary headwords with duplicates and articles removed, dictionary frequency coverage (`freq_rank`, English glosses only, no examples), and the `translate` answer-list lengths. The code observations (SM-2 without fuzz, unused `lapses`, streak reset, bottom bar, no exam date, no photos) come from reading the sources named above. Claims about published benchmarks and commonly quoted vocabulary ranges are from general knowledge and were not checked against sources in this review.
