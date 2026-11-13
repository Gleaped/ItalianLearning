# Lesson content format

All course content ships inside the app as JSON under `app/src/main/assets/course/`.
Adding or editing content never requires code changes.

```
app/src/main/assets/course/
  course.json          # index of levels and units
  units/<unitId>.json  # one file per unit
```

## course.json

```json
{
  "version": 1,
  "levels": [
    { "id": "A2R", "title": "Ripasso A2", "description": "Remedial refreshers", "remedial": true },
    { "id": "B1",  "title": "B1 – Intermedio", "description": "..." },
    { "id": "B2",  "title": "B2 – Intermedio superiore", "description": "..." },
    { "id": "C1",  "title": "C1 – Avanzato", "description": "..." }
  ],
  "units": [
    { "id": "b1-01", "level": "B1", "order": 1, "title": "...", "summary": "...", "available": true },
    { "id": "b1-02", "level": "B1", "order": 2, "title": "...", "summary": "...", "available": false }
  ]
}
```

`available: false` units are shown in the course map as "coming soon" and have no unit file yet.

## units/<unitId>.json

```json
{
  "id": "b1-01",
  "level": "B1",
  "title": "...",
  "objective": "By the end you can ...",
  "grammarFocus": "...",
  "remedialUnitIds": ["a2r-01"],
  "lessons": [
    {
      "id": "b1-01-l1",
      "title": "...",
      "minutes": 10,
      "exercises": [ { "type": "...", ... } ]
    }
  ],
  "vocabulary": [
    { "it": "la ricevuta", "en": "receipt", "example": "Mi può fare la ricevuta?" }
  ]
}
```

Unit vocabulary feeds the spaced-repetition review deck automatically.

## Exercise types

Every exercise has `type`, and optionally `id`, `instruction` (shown above the task) and `explanation` (shown after answering).
Typed answers are compared after normalising case, surrounding whitespace, final punctuation and apostrophe style.
Accents are required but a missing accent is reported as "almost" rather than wrong.

| type | fields | what the learner does |
|---|---|---|
| `grammar_note` | `title`, `body` (Markdown-lite: paragraphs, `**bold**`, `- ` bullets), `examples`: [{`it`,`en`}] | Reads a short explanation |
| `flashcard` | `front`, `back`, `example?` | Flips the card and rates recall |
| `multiple_choice` | `prompt`, `options`: [string], `answer`: index | Taps one option |
| `gap_fill` | `sentence` with `___` marking the gap, `answers`: [accepted strings], `hint?` | Types the missing word(s) |
| `conjugation` | `verb`, `tense`, `items`: [{`person`, `answer`}] | Types each form |
| `sentence_build` | `translation` (English meaning), `tiles`: [string] in correct order, `distractors?`: [string] | Taps tiles into order |
| `translate` | `direction`: `it_en` or `en_it`, `source`, `answers`: [accepted strings] | Types a translation |
| `match_pairs` | `pairs`: [{`left`, `right`}] | Matches items |
| `error_correction` | `sentence` (contains one error), `answers`: [accepted corrected sentences] | Rewrites the sentence correctly |
| `listen_transcribe` | `audioText` (spoken by text-to-speech), `answers`: [accepted strings], `slow?`: bool | Listens and types what was said |
| `listen_choose` | `audioText`, `question`, `options`, `answer`: index | Listens and answers a question |
| `reading` | `title`, `passage`, `questions`: [{`question`, `options`, `answer`}] | Reads and answers |
| `speak_repeat` | `text`, `translation?` | Says the sentence aloud; speech recognition scores word overlap |
| `speak_respond` | `prompt` (question or situation), `keywords`: [string], `modelAnswer` | Answers aloud; scored on keywords used, model answer shown |
| `roleplay` | `scenario`, `turns`: [{`speaker`: `npc` or `you`, `text` (npc line, or model answer for you), `hint?`, `keywords?`}] | NPC lines are spoken; on "you" turns the learner speaks |
| `free_write` | `prompt`, `minWords`, `checklist`: [string], `modelAnswer` | Writes freely, then self-checks against the checklist and model |

## Grammar tips (notification challenges)

Each unit file may carry `grammarTips`. Reminders draw from tips of units the learner has started (plus
remedial units they opened), so a notification can be a grammar reminder with a quick challenge rather than
only a flashcard.

```json
"grammarTips": [
  {
    "id": "b1-01-t1",
    "rule": "Imperfetto per lo sfondo",
    "tip": "Use the imperfetto for background and the passato prossimo for the event that happened.",
    "example": { "it": "Pioveva quando sono uscito.", "en": "It was raining when I went out." },
    "challenge": { "type": "gap_fill", "sentence": "Mentre ___ (cucinare), è suonato il telefono.", "answers": ["cucinavo"] }
  }
]
```

- `rule`: short title, max 40 characters (notification title).
- `tip`: one sentence, max 140 characters (notification body).
- `challenge`: one exercise object, limited to the quick types `multiple_choice`, `gap_fill`, `translate`,
  `error_correction` or `conjugation` with at most 3 items. It opens from the notification in a one-card screen.
- Aim for 3–6 tips per unit, one per key rule; checkpoint units mix rules from the units they review.

## Exam track

Level `CELI3` holds exam-preparation units aligned to the CELI 3 (B2) exam of the Università per Stranieri
di Perugia. They use the same exercise types; timed mock sections set `minutes` to the real section length.

## Format 2: the short-lesson anatomy

Units written to the new anatomy set `"format": 2`. The app reads format 1 and 2 the same way; the content
validator (`tools/validate_content.py`, run in CI) enforces the stricter rules below only for format 2.

### Unit fields (in addition to the ones above)

| field | meaning |
|---|---|
| `format` | `2` for units written to this anatomy |
| `module` | id of a module in `course.json` (`modules`), used to group units inside a level |
| `track` | optional track id, e.g. `"congiuntivo"`; track units are also listed in the Grammatica hub |

### Lesson fields (in addition to `id`, `title`, `minutes`, `exercises`)

| field | meaning |
|---|---|
| `goal` | one line, shown on the lesson's intro screen: what you will be able to say or do |
| `takeaway` | the lesson's rule in one line, shown on the wrap-up screen |

### Exercise field: `stage`

Every exercise may carry `"stage"`: `warmup`, `learn`, `practise` or `use`. The player shows it as a small label.

### Lesson anatomy (format 2)

| stage | count | content |
|---|---|---|
| `warmup` | 1 | one quick item from an earlier unit |
| `learn` | 1–2 | `grammar_note` rule cards |
| `practise` | 3–5 | recognise first (multiple choice, `choose_mood`, `find_trigger`, `sort_buckets`, `minimal_pair`), then produce (`gap_fill`, `transform`, `conjugation`) |
| `use` | 1 | `speak_respond`, `roleplay`, `free_write` or `reading` about real life |

Rules enforced for format 2:

- at most 10 exercises per lesson, `minutes` between 5 and 10;
- a `grammar_note` body has at most 60 words outside its table rows;
- every scored exercise has an `explanation`: one plain sentence that names the rule;
- every lesson has `goal` and `takeaway`.

### Rule-card markup (`grammar_note.body`, also allowed in `tip`)

On top of paragraphs, `**bold**`, `*italic*` and `- ` bullets:

- **Tables**: consecutive lines that start with `|`. The first row is the header; a `|---|---|` separator line is optional.
  ```
  | persona | parlare | prendere |
  |---|---|---|
  | io | parl==i== | prend==a== |
  ```
- **Highlight**: `==text==` marks an ending or trigger in the accent colour.
- **Callout**: a paragraph whose lines start with `> ` is shown as a tinted "watch out" box.

### New exercise types

| type | fields | what the learner does |
|---|---|---|
| `choose_mood` | `sentence` with `___`, `verb` (infinitive, shown as a hint), `options` (default `["indicativo","congiuntivo"]`), `answer`: index, `forms?`: [accepted strings], `trigger?` | Picks the mood; if `forms` is given, then types the verb form |
| `find_trigger` | `sentence`, `trigger` (exact words in the sentence, e.g. `"prima che"`) | Taps the word that triggers the mood |
| `transform` | `source` (sentence to change), `cue` (the change, e.g. `"Dubito che…"` or `"→ passato"`), `answers`, `hint?` | Rewrites the sentence |
| `sort_buckets` | `buckets`: [2–3 labels], `items`: [{`text`, `bucket`: index}] | Puts each item into the right group |
| `minimal_pair` | `sentences`: [2–3 near-identical sentences], `question`, `answer`: index, `translations?`: [same length] | Picks the sentence that matches the question |

`choose_mood`, `transform` and `find_trigger` may also be used as grammar-tip challenges.

## Personal name

Write `{name}` wherever a dialogue or text addresses or introduces the learner. The app replaces it with the name
the learner chose on first run (Settings → Your name).
