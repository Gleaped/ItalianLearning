# Proposal: a full A1 to C2 lesson and learning expansion

Status: **proposal only, nothing here is built.** Written 2026-11-18 after the speaking overhaul (PR #23) and the Whisper recogniser (PR #24).
The unit-by-unit map is in [proposal-lesson-expansion-curriculum.md](proposal-lesson-expansion-curriculum.md). The UI directions Richard asked for in the same request are in [ui-designs/](ui-designs/index.html) and summarised in section 14.

## 1. Summary

The app today is a very good B1 to C1 course for one person. It has 71 units, 304 lessons, about 3,150 exercises, about 1,140 unit vocabulary items, an offline 43,000-lemma dictionary, 11 conversation scenes and a CELI 3 exam track. A1 and A2 exist (11 units each) but are thin, there is no C2, and vocabulary stops far short of what the upper levels need.

This proposal grows it into a complete course that a motivated beginner could follow from zero to C2, and that Richard can keep using to reach C1 and pass CELI 3:

| | Today | Proposed |
|---|---|---|
| Course units | 71 (incl. 10 exam, 5 speaking, 8 congiuntivo) | **270** core units, plus 10 exam units kept and extended to 30 |
| Core lessons (5 to 10 min each) | 304 | **about 1,350** (about 150 hours at 7 minutes) |
| Exercises | about 3,150 | **about 14,000** |
| Hand-written unit vocabulary | about 1,140 words | **about 7,000 words in context** |
| Frequency decks (auto-built, from the dictionary) | core deck only | **about 13,000 more words**, tiered by frequency |
| Graded reading texts | about 14 (celi-07 and reading exercises) | **600** |
| Listening clips | about 12 long, many short | **500 long clips**, 25,000 spoken sentences |
| Culture capsules | 0 | **200** |
| Real-life scenario missions (spoken) | 11 | **80** |
| Exercise types | 25 | **about 45** |
| Exam mocks | CELI 3 x2 | CELI 2, 3, 4, 5 and CILS equivalents: **14 mocks** |

The expansion costs nothing in cloud spend: every piece is authored in Claude sessions, checked by offline tools in CI, and shipped as JSON text plus optional downloadable audio packs. The APK itself grows by roughly 6 to 9 MB (section 12).

The hard part is not the volume, it is **trust**: nobody on this project is a native speaker, so 14,000 exercises need mechanical checks that catch wrong Italian without a human. Section 9 is therefore the core of the proposal.

## 2. What exists today (audit)

Counted from the repository on 2026-11-18.

| Level | Units | Lessons | Unit vocabulary |
|---|---|---|---|
| A1 | 11 | 48 | 183 |
| A2 | 11 (incl. 6 remedial "a2r") | 48 | 159 |
| B1 | 16 | 70 | 269 |
| B2 | 12 | 53 | 186 |
| C1 | 11 | 48 | 175 |
| CELI 3 exam | 10 | 37 | 168 |
| Total | 71 | 304 | about 1,140 |

Exercise mix (3,152 exercises): gap_fill 614, multiple_choice 432, grammar_note 394, listen_transcribe 207, error_correction 201, speak_respond 164, transform 143, listen_choose 130, translate 123, sort_buckets 99, conjugation 82, match_pairs 76, roleplay 74, choose_mood 65, reading 62, minimal_pair 60, free_write 59, sentence_build 56, flashcard 32, find_trigger 24, speak_repeat 14, shadow 14, gap_listen 10, minimal_pair_say 9, listen_order 8.

Platform pieces that already exist and that this proposal builds on: format 2 lesson anatomy with a validator in CI; SM-2 scheduling with recognise and produce twin cards and grammar cards; the core words deck; the 43,135-lemma dictionary with `freq_rank`; the rule-based morphology engine in `tools/dictionary/italian_morph.py` (360,466 forms); the conversation engine (11 scenes); the Kokoro pre-rendered voice pack as an optional download; the optional on-device Whisper recogniser; the writing checker; the flag button; the CELI 3 exam track and mocks; one Room database (version 1).

### Gaps

1. **A1 and A2 are a sample, not a course.** A1 needs about 30 units for a true beginner (writing system and sounds, articles and gender, numbers, time, the first present tenses). The existing 11 skip several of those.
2. **No C2**, and the C1 units are mostly grammar. C1 and C2 are about range, register and reading real texts, which needs a library of texts, not units.
3. **Vocabulary is far too small for the levels.** 1,140 words taught explicitly against a B2 learner needing roughly 4,000 to 5,000 word families receptively. The dictionary knows the words but nothing teaches them in order.
4. **Reading and listening are thin.** 62 reading exercises and no graded reader. Listening is text-to-speech of exercise sentences, with no long recordings except celi-08.
5. **Writing is self-checked.** There is a writing checker, but few prompts and no open-answer exercise type (item X3 in the second review).
6. **No culture or real-life task content** beyond conversation scenes. The words learned are not tied to Italy as a place.
7. **One exam.** Only CELI 3.
8. **Content cannot be navigated by skill or topic.** There is no "I want to practise listening today" or "show me the health vocabulary".
9. **Single learner assumptions** in data (names, progress), flagged in the speaking proposal.
10. **Quality is checked structurally, not linguistically.** `validate_content.py` checks fields and word counts, not whether the Italian is right.

## 3. Design principles

1. **Scale from zero to mastery without making Richard repeat what he knows.** A placement check lets him start at B1 with every earlier level open as "ripasso", exactly as now. A new learner starts at A1.
2. **Short, repeatable sessions.** Lessons stay at 5 to 10 minutes. A daily session is assembled automatically (section 8) so nobody chooses from 270 units.
3. **Four skills in balance** (section 6), tracked separately, so a learner who reads well but listens badly sees it.
4. **Everything offline and free.** Text and small images ship in the APK or in downloadable level packs from GitHub Releases, the same mechanism as the voice pack.
5. **Content is data.** All of the growth is JSON and tool code, not Kotlin, apart from about a dozen new exercise renderers.
6. **Never trade accuracy for volume.** Anything that fails the mechanical checks does not ship. 14,000 correct exercises beat 30,000 with errors.

## 4. Curriculum map, A1 to C2

Full unit-by-unit list: [proposal-lesson-expansion-curriculum.md](proposal-lesson-expansion-curriculum.md). The level totals:

| Level | Modules | Units | Lessons (5 per unit) | New vocabulary in units | Vocabulary tier covered (section 5) | Guided-hours equivalent of core lessons |
|---|---|---|---|---|---|---|
| A1 | 6 | 30 | 150 | 700 | T1 to T2 start | about 17 h |
| A2 | 6 | 36 | 180 | 900 | T2 | about 21 h |
| B1 | 7 | 48 | 240 | 1,300 | T3 | about 28 h |
| B2 | 9 | 60 | 300 | 1,500 | T4 | about 35 h |
| C1 | 8 | 60 | 300 | 1,500 | T5 | about 35 h |
| C2 | 6 | 36 | 180 | 1,100 | T6 | about 21 h |
| Total | 42 | 270 | 1,350 | about 7,000 | | about 157 h |

Cambridge and CEFR guidance puts the total learning effort to reach B2 at roughly 500 to 600 hours, C1 at 700 to 800, C2 at 1,000 or more, counting classroom, homework and immersion. The core lessons are therefore about a quarter of the total. The rest is covered by what this proposal adds around the units: reading library, listening library, scenarios, spaced review (about 5 hours per level), exam practice, and real-world use. The app should say this honestly on the course map ("this course covers the structure; reading and listening are what make it stick").

Each level ends with a **level check** (a 20 minute mixed test, skill by skill) and each module with a **checkpoint unit**, as today.

Every unit keeps format 2 and gains a `canDo` list tying it to CEFR descriptors (section 11), so progress can be reported as "you can now do 41 of 63 B1 descriptors".

## 5. Vocabulary: frequency tiers and targets

The dictionary already carries `freq_rank` (from wordfreq). Tiers use that rank on lemmas, with part-of-speech filtering so that function words and inflected forms are not double counted.

| Tier | Frequency ranks | Cumulative lemmas | Level it supports | Taught explicitly in units | Frequency deck (auto) | Seen in readers |
|---|---|---|---|---|---|---|
| T1 | 1 to 600 | 600 | A1 | 500 | 100 | yes |
| T2 | 601 to 1,500 | 1,500 | A2 | 800 | 100 | yes |
| T3 | 1,501 to 3,000 | 3,000 | B1 | 900 | 600 | yes |
| T4 | 3,001 to 6,000 | 6,000 | B2 | 1,200 | 1,800 | yes |
| T5 | 6,001 to 10,000 | 10,000 | C1 | 1,300 | 2,700 | yes |
| T6 | 10,001 to 16,000 | 16,000 | C2 | 2,300 | 3,700 | yes |

Notes:

- "Taught explicitly" words come with a hand-written example, are chosen by topic rather than rank so they are usable, and total about 7,000. They are the existing `vocabulary` arrays, now referenced by lemma id (section 11).
- The **frequency decks** are generated by `tools/vocab/build_decks.py` from the dictionary: the next unseen lemmas by rank, each with its best example sentence. About 13,000 extra words, offered as "Parole del giorno" (5 to 10 new words per day, skipped if the review queue is long). They are a deck, not lessons.
- **Example sentences for the decks** come from the dictionary's `example_it` where present, otherwise from a vetted sentence corpus (section 9.4). Never invented on the fly at runtime.
- **Coverage rule for readings and lessons:** at least 95% of tokens in a lesson text must belong to the unit's level tier or lower or be glossed in the text (the usual research threshold for comfortable reading is 95 to 98%). The validator computes this (section 9).
- **Word families** (celi-10 style) become a recurring exercise from B2: the dictionary links derivations by suffix rules, so "famiglia di parole" cards can be generated for a headword.
- **Receptive versus productive:** every word gets a recognise card; a produce card appears after two correct recognitions (existing behaviour). For T5 and T6 the produce card is optional (setting, default off), since the goal at C1 and C2 is mostly recognition.

## 6. Grammar sequencing and skill balance

### 6.1 Grammar sequence

The spine is the order Italian teaching has converged on, adjusted for what a learner can do early:

| Level | Grammar introduced |
|---|---|
| A1 | sounds and spelling, gender and number, articles, essere and avere, regular present (-are, -ere, -ire), common irregulars (fare, andare, venire, stare, volere, potere, dovere, sapere), c'e, basic prepositions, possessives, piacere, reflexives (present), passato prossimo (introduction), near future with andare and stare per |
| A2 | passato prossimo in full, imperfetto, imperfetto vs passato prossimo (introduction), direct and indirect pronouns, ci and ne (introduction), futuro semplice, condizionale of courtesy, imperative, comparatives and superlatives, articulated prepositions, impersonal si, dimostrativi, basic connectors |
| B1 | trapassato prossimo, ci and ne in full, combined pronouns, relative pronouns, si passivante, imperative with pronouns, condizionale in full, futuro anteriore, congiuntivo presente (CG1 to CG8 track already exists), periodo ipotetico type 1 |
| B2 | congiuntivo passato, imperfetto and trapassato, sequence of tenses, periodo ipotetico 2 and 3, condizionale passato, passive, reported speech, implicit clauses (gerundio, participio, infinito), concessive and final connectors, word formation |
| C1 | passato remoto, dislocation and cleft sentences, advanced subjunctive, ellipsis and inversion, nominalisation, register and bureaucratic style, discourse markers, idioms and pronominal verbs |
| C2 | stylistic nuance, irony and understatement, literary tenses and syntax, dialect awareness, academic and essay prose, rhetoric, translation shifts |

Rules for sequencing inside the app:

- Each unit declares `grammarFocus` ids from a controlled list (`grammar/topics.json`, about 220 topics). The validator rejects a lesson that uses a tense or structure from a topic not yet introduced (section 9.3), which stops accidental forward references.
- **Spiral, not blocks.** Following the congiuntivo track pattern: every topic appears three times (introduce, extend, contrast), with the later appearances at least one module apart. The warm-up item of every lesson draws from earlier topics through the SRS grammar cards.
- The Grammatica hub groups the same topics for reference, linking each topic to the lessons that teach it and the grammar reference chart.

### 6.2 Skill balance

Time per 7-minute lesson is not equal across skills, so balance is set per level at the **week** level (section 8) and tracked per skill. Target share of study time:

| Level | Vocabulary and grammar | Reading | Listening | Speaking | Writing |
|---|---|---|---|---|---|
| A1 | 35% | 15% | 25% | 20% | 5% |
| A2 | 30% | 20% | 25% | 20% | 5% |
| B1 | 25% | 25% | 22% | 18% | 10% |
| B2 | 20% | 25% | 22% | 18% | 15% |
| C1 | 15% | 30% | 22% | 18% | 15% |
| C2 | 10% | 35% | 20% | 20% | 15% |

Every exercise type is tagged with the skills it exercises (`skills`: `read`, `listen`, `speak`, `write`, `vocab`, `grammar`). The progress screen shows a five-bar skill profile from the last 30 days. If one bar falls under half its target the daily session quietly adds a lesson in that skill ("Oggi un po' di ascolto").

## 7. New lesson and exercise types

### 7.1 New lesson types (beyond the unit lessons)

| Lesson type | What it is | Count at full scale |
|---|---|---|
| **Lettura graduata** (graded reader) | 60 to 900 word text at the learner's level. Tap any word to see its dictionary entry and add it to review. Comprehension questions, then optional read-aloud scored by the recogniser. | 600 |
| **Ascolto intensivo** | A recorded dialogue or monologue with three passes: gist question, detail questions, then transcript with tap-to-replay. Speed control (0.75x, 1x). | 500 |
| **Serie: la famiglia di Anna** | A serialised story in 60 short episodes per level band (A1 to B1), each 2 minutes of audio with the same characters. Gives the course a continuing thread and recycles vocabulary. | 180 episodes |
| **Cultura** | A two-minute read (A2 to C2) about Italian life: regions, food, bureaucracy, holidays, music, history. Three questions and four target words. | 200 |
| **Missione** | A real-life task run through the conversation engine: "get a codice fiscale", "ask the pharmacist about a rash", "complain about a late delivery". | 80 |
| **Laboratorio di pronuncia** | Short pronunciation drills: double consonants, stress and accents, gl, gn, sc, open and closed e and o, intonation of questions. Uses the recogniser. | 40 |
| **Laboratorio di scrittura** | Guided writing of one real text type (email, complaint, CV line, summary) with a checklist, model, and the writing checker. | 120 |
| **Dettato** | Dictation sets of increasing speed and length. | 100 |
| **Modi di dire e proverbi** | 15 minutes of idioms and proverbs per week from B2, with the literal image and the meaning. | 80 |
| **Poesia e prosa** (C1 and C2) | Annotated public-domain literary passages with vocabulary support (Dante, Boccaccio, Leopardi, Manzoni, Verga, Pirandello, Svevo, Deledda). | 60 |
| **Simulazione d'esame** | Timed mock papers (section 10). | 14 |

### 7.2 New exercise types (about 20 added to the current 25)

New renderers are required for the ones marked "Kotlin". Others reuse existing UI with new content rules.

| type | What the learner does | Skill | Needs |
|---|---|---|---|
| `cloze_text` | Fills 5 to 12 gaps in a passage, typing or choosing from a dropdown | read, grammar | Kotlin |
| `reorder_paragraph` | Puts shuffled sentences or paragraphs in order | read | Kotlin |
| `tap_gloss_reading` | Reads a text and taps words for glosses; taps are logged to find unknown words | read, vocab | Kotlin |
| `open_answer` | Answers a question in 1 to 3 sentences, scored against a keyword rubric (review item X3, CELI A.3) | read, write | Kotlin |
| `article_swipe` | Swipes a noun left for il or right for la (and lo, l', un, una) against the clock | vocab, grammar | Kotlin |
| `image_match` / `image_label` | Matches words to small illustrations or labels a picture | vocab | Kotlin and images |
| `dialogue_complete` | Chooses or types the missing line in a dialogue | read, speak | existing UI |
| `register_switch` | Rewrites a sentence from informal to formal or the reverse | write, grammar | `transform` UI |
| `word_formation` | Forms a word from a base given a suffix or prefix rule (celi-03, celi-10) | vocab | `gap_fill` UI |
| `collocation_match` | Matches verbs and nouns that go together (fare una domanda, prendere una decisione) | vocab | `match_pairs` UI |
| `error_hunt` | Taps the wrong word in a paragraph | grammar | Kotlin |
| `stress_mark` | Taps the syllable that carries the stress, or places the accent | listen, speak | Kotlin |
| `numbers_listen` | Hears numbers, prices, dates, phone numbers and types them | listen | existing UI |
| `dictogloss` | Hears a short passage twice and rebuilds it from notes | listen, write | `free_write` UI |
| `speed_round` | 60-second mixed drill of earlier items with a streak count | all | Kotlin |
| `summary_write` | Summarises a text in a given word limit; checker compares to key points | read, write | `free_write` UI |
| `speak_scenario` | A single spoken turn in a picture or situation card | speak | existing |
| `listen_gist` | Hears a clip once and picks the main idea before the detail questions | listen | existing UI |
| `spot_the_false_friend` | Chooses the correct Italian for an English cognate trap | vocab | `multiple_choice` UI |
| `sort_register` | Puts expressions in formal, neutral, colloquial buckets | vocab | `sort_buckets` UI |

Example of the new `cloze_text` and `open_answer` shapes:

```json
{ "type": "cloze_text", "skills": ["read","grammar"], "stage": "practise",
  "title": "Una giornata al mercato",
  "text": "Ieri mattina ___ al mercato e ___ delle arance.",
  "gaps": [ { "answers": ["sono andato", "sono andata"], "options": ["sono andato","andavo","vado"] },
            { "answers": ["ho comprato"] } ],
  "explanation": "Azioni concluse: passato prossimo." }
```

```json
{ "type": "open_answer", "skills": ["read","write"],
  "question": "Perche l'autrice cambia casa?",
  "rubric": [ { "points": 1, "anyOf": ["lavoro","trasferit"] },
              { "points": 1, "anyOf": ["costo","affitto","caro"] } ],
  "modelAnswer": "Si trasferisce per il lavoro e perche l'affitto e troppo alto." }
```

Scoring by rubric words is generous by design (stems, accent-insensitive). It tells the learner which points they covered and shows the model; it never fails them harshly.

## 8. Daily session, review and SRS design

### 8.1 The assembled daily session

A learner opens the app and taps **Oggi**. The session is built in this order and takes about 12 minutes by default (setting: 8, 12 or 20):

1. **Ripasso** (3 to 5 min): due SRS cards, capped by a daily load limit (section 8.2).
2. **Lezione** (5 to 7 min): the next lesson on the learner's path.
3. **Micro-abilita** (2 min): the weakest skill of the week, as one short item from the libraries (a 60-word reader, a listening clip, one pronunciation drill, one writing line).
4. **Parola del giorno** (1 min, optional): 5 new words from the frequency deck.

The home screen offers "Continue", "Ripasso only" (for tired days), and "More" which opens the skill menu. Streaks stay gentle as today.

### 8.2 SRS changes

| Change | Why |
|---|---|
| Replace SM-2 with **FSRS** (the open-source Free Spaced Repetition Scheduler, MIT licensed, a few hundred lines in Kotlin) using default parameters at first | Fewer reviews for the same retention, and it models difficulty and stability separately. Retention target 90% (setting 85% to 95%). With 1,000 or more reviews the app can fit its own parameters on the phone. |
| Keep the card id scheme (`v:`, `r:`, `d:`, `g:`, `c:`) and add `s:` (sentence cloze), `p:` (pronunciation), `w:` (word family) | Backwards compatible; existing progress is preserved |
| **Sentence cloze cards** | From B1 on, a card shows a sentence with the word blanked, which trains use and not only meaning |
| **Reading mining** | Tapping a word in a reader adds a card, with the sentence as context |
| **Daily review cap** and **new-card throttle**: new words stop when the due queue exceeds 120 cards | Prevents the classic overload that makes people quit |
| **Leech handling** | A card lapsed 8 times is suspended and replaced by a mnemonic prompt with an image or a word family link |
| **Forecast** | A small bar chart on Ripasso: "tomorrow 38, this week 210" |
| **Interleaving** | Due cards are mixed across units and card types rather than shown by unit |
| **Grammar cards** | The existing `g:` cards expand from about 270 tips to about 1,100 (one per topic appearance), drawing on the grammar tip challenges |
| **Backups** | The export keeps working; FSRS fields are added with defaults on import |

### 8.3 Placement and skipping

- **Adaptive placement** (8 to 12 minutes, offline): starts at A2, moves up or down on correct and incorrect answers over grammar, vocabulary, reading and listening items drawn from a dedicated bank of 300 tagged items. It suggests a starting level and which modules to open; it never locks anything.
- **Test out of a module** already exists (E1) and extends to all modules.
- **Level checks** at the end of each level give a skill profile and recommend where to go next.

### 8.4 Reminders

The reminder engine already supports grammar challenges. Add a "Parola del giorno" reminder kind and a gentle "your listening is behind this week" kind, both subject to the existing quiet hours and per-kind toggles.

## 9. Content authoring pipeline and quality checks

Goal: produce about 14,000 exercises, 600 texts, 500 recordings' scripts and 200 capsules of Italian that is correct, level-appropriate and natural, using only free offline tools and Claude sessions, with no native-speaker review available as a routine step.

### 9.1 Authoring flow (per batch of 4 to 6 units)

1. **Brief** (generated by a script): unit ids, level, tier, grammar topic ids, `canDo` descriptors, allowed grammar (all topics up to this unit), vocabulary list drawn from the tier plan (section 5), cultural theme, exercise-type quota, previous units for warm-ups.
2. **Draft** by an authoring session using the `italian-language-coach` agent definition, writing JSON straight into the unit files.
3. **Mechanical gate** (section 9.2 to 9.4), run locally in the session. The session fixes everything flagged before committing.
4. **Independent review pass**: a second session with a different prompt reads each lesson cold and answers a checklist (is every answer key correct, is there exactly one defensible answer per gap, is the Italian natural, are the English glosses right, any forward references). It outputs a defects list; the authoring session fixes them. Two passes catch a large share of the errors a single pass leaves.
5. **PR** with a machine-generated report: counts, coverage figures, flagged items fixed, items overridden with a reason.
6. **CI** runs the full gate again plus the existing app build.

### 9.2 Structural checks (extend `tools/validate_content.py`)

- JSON schema per exercise type (required fields, answer index in range, unique options, no duplicate distractors).
- Format 2 anatomy and word limits (exists).
- Stable, unique exercise ids and lesson ids across the whole course.
- Every `answers` list contains at least one answer that, substituted into the gap, produces a sentence that passes the spell check.
- Duplicate and near-duplicate detection (same sentence in two lessons; edit distance under 3).
- `skills`, `canDo` and `grammarFocus` ids exist in the controlled lists.

### 9.3 Linguistic checks, all offline

| Check | How | Catches |
|---|---|---|
| **Spell check against the dictionary** | Tokenise every Italian string and look each token up in the 360,466 known forms plus Hunspell `it_IT` (open source). Unknown tokens are listed; a small allow-list covers names and loanwords. | Typos, invented words, wrong accents |
| **Level and coverage check** | Map tokens to lemmas and `freq_rank`; compute the percentage above the unit's tier. Warn above 5%, error above 10% unless glossed. Flag any word from a tier above the unit that is not in the unit's vocabulary. | Text too hard for its level |
| **Grammar-topic gate** | A rule-based detector (regexes and the morphology engine) tags tenses and structures in each sentence (condizionale, congiuntivo, passato remoto, passive, relative cui...). Any topic not yet introduced at that unit, and not the unit's own focus, is an error in learner-facing text. | Forward references, e.g. a congiuntivo in A2 |
| **Morphology cross-check** | `conjugation` tables, `gap_fill` verb forms and `forms` lists are regenerated by `italian_morph.py` and compared. | Wrong verb forms |
| **Single-answer test** | For each `gap_fill`, the validator tries other plausible forms of the same lemma (all persons of the tense, neighbouring tenses) in the gap and reports any that also pass the spell and agreement check but are not in `answers`. A human or the review pass decides if the item is ambiguous. | Gaps with two right answers (the most common authoring defect) |
| **Agreement check** | Rule-based check of article, noun and adjective agreement using the dictionary's gender and forms | `la problema`, `i nuovo libro` |
| **LanguageTool** (Italian rules, open source, runs locally in Java) | Run over all learner-facing Italian text and model answers in CI | Punctuation, apostrophes, common grammar slips, register mismatches. Needs a one-off check that the Italian rule set is sufficient, since its Italian coverage is thinner than for English. |
| **English gloss check** | Compare each `en` gloss with the dictionary senses for that lemma; flag glosses with no overlap | Wrong translations |

### 9.4 Sources of verified Italian (so the model does not have to invent everything)

| Need | Free offline-capable source | Licence to confirm before use |
|---|---|---|
| Example sentences with translations | Tatoeba Italian to English sentence pairs, filtered for quality | CC BY 2.0 FR, attribution in the app's licences screen |
| Word senses and forms | Wiktionary (already used) | CC BY-SA 3.0 (already shown in the app) |
| Public-domain literature (C1, C2) | Wikisource and Project Gutenberg Italian texts of authors who died before 1955 | Public domain in Italy (70 years after death); verify each author |
| Contemporary short texts | Italian Wikipedia and Wikinews, rewritten and shortened for level | CC BY-SA; attribution; rewriting must be marked as adapted |
| Spoken audio for dictation realism | Mozilla Common Voice Italian (optional; read speech) | CC0 |
| Pictures | Wikimedia Commons public-domain and CC0 images, downscaled to under 40 KB WebP | per-image licence; list in `licenses/` |
| Grammar facts | Existing reference charts and morphology engine | own |

Generated text still dominates, but each batch draws example sentences from the vetted corpus where it can, and the single-answer and spell checks work against the same corpus.

### 9.5 Audio quality loop

Every sentence rendered by Kokoro is re-transcribed by the Whisper model already in the repo; if the word error rate exceeds 10% the sentence is flagged (usually a mispronounced homograph such as *ancora*, *principi*, *subito*, or a stress error) and re-rendered with a respelling override list. Samples of each batch (5%) are checked by ear by Richard on the phone, a 3-minute task per level pack.

### 9.6 In-app feedback closes the loop

The flag button already exists. Flags become issues via a small export script. The validator gets a "known bad patterns" list that grows with each confirmed error, so a class of mistake is never shipped twice.

### 9.7 Human check, minimal and optional

Because there is no native speaker on the project, one optional paid step is worth naming, though the proposal does not depend on it: a one-off spot audit of 100 random exercises per level by a tutor (a few euros per level on common marketplaces), used only to measure the error rate and tune the checks. If the audited error rate is above 1%, batches pause until the checks are improved.

## 10. Exam preparation

| Exam | Level | Today | Proposed |
|---|---|---|---|
| CELI 1 (Perugia) | A2 | none | 2 mocks, 4 section skill units |
| CELI 2 | B1 | none | 3 mocks, 6 units |
| **CELI 3** | **B2 (Richard's goal)** | 10 units, 2 mocks, log of real papers | 14 units, **6 mocks**, 40 reading papers (from the library), 40 listening sets, 60 writing prompts, 10 oral sets |
| CELI 4 | C1 | none | 3 mocks, 8 units |
| CELI 5 | C2 | none | 2 mocks, 4 units |
| CILS (Siena) B1, B2, C1 | | none | one mock each, with format notes; shares the units above |

All exam content follows the verified formats in [celi3-exam.md](celi3-exam.md); formats for the other exams are researched from the universities' published sample papers at the time they are built, and checked against the official sites before release (the app already carries a disclaimer that checkpoints are not certification).

Features for CELI 3, building on the second review:

- Timed sections with the real timings, and the pass-mark scoring already in celi-06.
- Weak-area report per section and per task type (review item L3).
- Writing marked against the official criteria in four bands with a checklist, using the writing checker.
- Oral rehearsal with the conversation engine and the oral exam screen (PR #23), expanded from 3 to 10 sets.
- A **readiness page** (exists) that now draws on the skill profile.

## 11. Data-model changes

### 11.1 Content

| Change | Detail | Why |
|---|---|---|
| **Per-level index files** | `course/index.json` lists levels and modules; `course/<level>/index.json` lists its units. Units load lazily. | 270 units in one `course.json` would make start-up slow |
| **Unit schema version 3** | adds `schemaVersion`, `tier`, `canDo`: [ids], `skills` on exercises, `grammarFocus`: [topic ids], `vocabulary` entries as `{ "lemma": <id>, "it", "en", "example" }` | Lemma ids connect the SRS card to dictionary forms, audio and word families |
| **Stable exercise ids** | `<unit>-l<n>-<hash8>` where the hash is of the prompt, so an edited exercise keeps its identity unless its meaning changes | Progress, stats and flags survive content edits |
| **`libraries/` folders** | `readers/`, `listening/`, `culture/`, `scenes/`, `writing/` with an index each | Texts are not units; they are tagged by level, topic, tier and length |
| **`grammar/topics.json`** | The controlled list of grammar topics with level, prerequisites and the units that introduce them | Powers the grammar gate and the Grammatica hub |
| **`canDo.json`** | About 450 CEFR "can do" descriptors, written for this app, mapped to units | Progress in learner terms |
| **`placement.json`** | 300 tagged items | Placement test |

All existing units migrate with a script (`tools/migrate_v3.py`) that adds ids and maps vocabulary to lemmas by lookup; items it cannot match are listed for review.

### 11.2 Database (Room)

Room is at version 1 with no migrations, so this needs a proper migration path with tests (the same constraint noted in the speaking proposal):

- `card` gains FSRS fields (`stability`, `difficulty`, `lapses`, `lastReview`) with defaults derived from SM-2 values; the old fields stay for one release.
- New tables: `skill_log` (date, skill, seconds, score), `lesson_result` (lesson id, score, date), `word_state` (lemma id, stage, first seen), `exercise_stat` (exercise id, attempts, errors), `placement` (level, date, per-skill estimate), `reader_progress`.
- `profileId` is added to every table now (default 1), so that family profiles remain a single later change, as the speaking proposal asks.
- Backup export gains a version number and imports old versions.

### 11.3 Code

- A `ContentRepository` interface in front of the loader, so lazy loading and packs are invisible to the screens.
- A `Skill` enum and a `SkillTracker` used by the exercise grader and session builder.
- About twelve new renderers in `ui/lesson`, each small and isolated (`ExerciseRegistry`, so adding one does not touch the lesson screen).
- `SessionBuilder` (daily session), `Fsrs` scheduler, `PlacementEngine`.

## 12. APK size and storage impact

Baseline: the current release (v0.1.83) is a 17.4 MB APK, which includes the 61 MB dictionary database (compressed in the APK).

| Item | Raw size | In the APK (zip) | Where it ships |
|---|---|---|---|
| Course JSON today | 2.5 MB | about 0.5 MB | APK |
| Course JSON at 270 units plus libraries | about 14 MB (units 9 MB, readers 2 MB, culture 1 MB, scenes 1 MB, other 1 MB) | **about 2.5 to 3 MB** (JSON compresses roughly 5:1) | APK |
| Frequency decks and example sentences | about 4 MB | about 1 MB | APK |
| Grammar topics, canDo, placement | 1 MB | 0.2 MB | APK |
| Small images (about 600 at 30 KB) | 18 MB | 18 MB (already compressed) | **Level pack download**, not the APK |
| FSRS, new renderers, tooling | negligible | under 0.5 MB | APK |
| Hunspell or LanguageTool | build-time only | 0 | CI only |
| **APK growth** | | **about 4 to 5 MB, to roughly 22 MB** | |
| Pre-rendered audio (25,000 sentences at about 5 KB, 500 clips at about 100 KB) | about 175 MB | not in APK | Voice pack, split into six level packs of about 15 to 40 MB each |
| Images | 18 MB | not in APK | Image pack, 18 MB |

The APK stays comfortably small; everything heavy is optional, downloaded once from GitHub Releases like the voice pack, and every level pack degrades gracefully (a missing audio line is read by the phone's voice; a missing picture is hidden). Storage on the phone, with everything installed, is about 270 MB, which is the number to show on the Settings screen before download.

Obtainium updates keep working: more units mean a longer first-start index load, so the lazy index in section 11 is on the critical path for phase 0.

## 13. Phased roadmap and effort

Effort is in **authoring batches** (one Claude session producing about 5 units or 25 lessons, plus its review pass and gate fixes, usually 1 to 2 hours of session time, and subject to the usage limits that interrupted earlier long runs) and **build PRs** (one reviewed change of code). Calendar time assumes part-time pacing with a PR or two merged per day; it is limited most by Richard's reviewing time and the 4 to 5 minute CI cycle, not by writing.

| Phase | Scope | Effort | Calendar |
|---|---|---|---|
| **0. Foundations** | Unit schema v3 and migration script, lazy per-level index, stable ids, Room migration with tests, `profileId`, `ContentRepository`, validator v2 structure checks | 4 PRs | 1 week |
| **1. QA tooling** | Spell check on dictionary forms, coverage and level check, grammar-topic gate and `topics.json`, single-answer test, agreement check, morphology cross-check, LanguageTool trial, Whisper audio loop script, batch brief generator | 3 PRs, plus pilot: re-run on all 71 existing units and fix what it finds (expect a few hundred flags) | 1.5 weeks |
| **2. Engine features** | FSRS, daily session builder, skill tracker and profile, `cloze_text`, `open_answer`, `reorder_paragraph`, `error_hunt`, `article_swipe`, `tap_gloss_reading`, reader screen | 6 PRs | 2 weeks |
| **3. A1 and A2 complete** | 66 units, 330 lessons (reusing the 22 existing units) | 14 batches | 2 weeks |
| **4. Frequency decks and placement** | Deck builder, Parole del giorno, placement bank (300 items), placement UI | 2 PRs, 2 batches | 1 week |
| **5. B1 complete plus CELI 2** | 48 units, 240 lessons, exam units | 10 batches | 2 weeks |
| **6. B2 complete plus CELI 3 expansion** | 60 units, 300 lessons, 6 mocks, 40 papers, exam analytics | 14 batches | 3 weeks |
| **7. Libraries I** | 300 readers (A1 to B2), 250 listening scripts, 80 writing tasks, graded reader UI polish, first audio level packs (A1, A2, B1, B2), Kokoro render and Whisper loop | 16 batches, 2 PRs | 3 weeks |
| **8. C1 complete plus CELI 4** | 60 units, 300 lessons | 14 batches | 3 weeks |
| **9. C2 and literature** | 36 units, 180 lessons, 60 literary passages, 100 more readers, CELI 5 | 12 batches | 3 weeks |
| **10. Culture, scenarios, speaking** | 200 capsules, 69 more scenes (to 80), 20 oral sets, 40 pronunciation drills, image pack | 12 batches, 2 PRs | 3 weeks |
| **11. Polish and audit** | Spot audit of 100 random exercises per level, tuned checks, level checks, canDo reporting, UI redesign (section 14) | 4 PRs | 2 weeks |

Totals: about **22 build PRs** and about **94 authoring batches**, which is roughly **26 weeks** if run back to back, **16 to 20 weeks** with overlap (QA tooling and engine work run while A1 and A2 are authored). The most valuable order for Richard's own goal is different from the course order:

1. Phases 0 and 1 (foundations and QA tooling) before anything large is written, because they decide how much of the later work is trustworthy.
2. **B2 and the CELI 3 expansion (phase 6) and the reading and listening libraries (phase 7) next**, since they serve the exam.
3. Then A1 and A2, which matter for future users but not for Richard.
4. C1 and C2 last.

If time is short, a smaller **core release** is: phases 0, 1, 2 and 6, plus 100 readers and 100 listening clips. That is about 12 PRs and 30 batches (about 8 weeks) and delivers most of the benefit for a B1 to C1 learner.

### Risks

| Risk | Mitigation |
|---|---|
| Subtle errors in Italian slip through | Layered mechanical checks, two-pass review, 100-exercise audit, flag button, "known bad" list, pause rule at 1% error |
| Usage limits interrupt long batch runs | Batches are small and independent; the brief generator makes any batch resumable |
| Level creep (A2 text that is really B1) | Coverage check and grammar gate are hard errors in CI |
| Learner overwhelm with 270 units | The daily session hides the course map; the map is collapsed by module |
| Licence mistakes with imported texts or images | A `licenses/` register is required by the validator for every non-original text, image and audio file |
| Room migration data loss | Backup written first, migration tests, FSRS fields additive |
| Repetitive content (same patterns 1,000 times) | The brief rotates topics and scenes; near-duplicate detector; a per-batch variety quota on exercise types |

## 14. UI directions

Richard asked for ten very different, readable UI designs to choose from. They are static mockups of the Home and a lesson screen, in [ui-designs/index.html](ui-designs/index.html), built as plain HTML and CSS so they open in any browser. The choice is listed in section 15. Applying the chosen design touches `ui/theme` and the layout of Home, Lesson and Review only; the content expansion above does not depend on it.

## 15. Decisions

Decided 2026-11-18: **core release first** (phases 0, 1, 2 and 6, plus 100 readers and 100 listening clips). The UI redesign waits while Richard reviews the ten directions with his wife, so new screens stay theme-neutral. Optional audio packs are undecided and are not built yet. Open questions that remain:

## 15a. Original questions

1. **Scope:** full A1 to C2 (about 26 weeks), or the core release first (about 8 weeks)? Recommended: core release first, then continue in the order given in section 13.
2. **Audio:** is a roughly 175 MB set of optional downloadable voice packs acceptable? Recommended yes, one pack per level so only the needed levels are downloaded.
3. **Optional paid audit:** is a small paid tutor spot check of 100 random exercises per level acceptable, or should this stay fully free? The plan works without it but loses its only human error-rate measurement.
4. **UI:** which of the ten directions (or which two to combine)?
5. **Exam breadth:** CELI 1 to 5 and CILS, or only CELI 3 plus CELI 4 for now? Recommended: CELI 3 and 4 first.
6. **Family profiles:** the data model adds `profileId` now at almost no cost. Does that change the priority of building profile screens?
