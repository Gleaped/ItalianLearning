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
