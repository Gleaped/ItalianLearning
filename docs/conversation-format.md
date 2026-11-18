# Conversation scenes and oral rehearsal papers

Scenes live in `app/src/main/assets/conversations/<id>.json`, rehearsal orals in `app/src/main/assets/oral/<id>.json`. Both are plain data: the engine is in `domain/conversation`, the oral screen in `ui/oral`. The unit tests in `ConversationEngineTest` check every scene on build.

## Scene

```json
{
  "id": "a1-bar", "title": "Al bar", "level": "A1",
  "setting": "English sentence that sets the scene", "goal": "What the learner must achieve",
  "partner": "il barista", "minutes": 4,
  "slots": { "drink": ["un caffè", "un cappuccino"] },
  "start": "greet",
  "states": [
    { "id": "greet",
      "say": ["Buongiorno! Cosa prendi?", "Ciao! Dimmi, cosa ti porto?"],
      "expect": [ { "match": ["caffè", "espresso"], "next": "food", "reply": ["Un caffè? Subito!"], "capture": "drink" } ],
      "reprompt": ["Scusa, cosa prendi?"],
      "hint": "English help shown when stuck", "model": "Vorrei un cappuccino, per favore.",
      "otherwise": "what" },
    { "id": "bye", "say": ["Arrivederci!"], "goal": true, "expect": [ ... ], "otherwise": "end" },
    { "id": "end", "say": ["A presto!"], "end": true }
  ],
  "phrases": [ { "it": "Vorrei…", "en": "I would like…" } ]
}
```

- **Branches are tried in order and the first match wins**, so put specific answers before general ones ("no grazie" before "grazie").
- `match` words match when equal, one letter off, or sharing a stem of 5+ letters; a phrase needs all its words, in any order. Accents and apostrophes are ignored.
- `{slot|default}` in any line is filled from what the learner said (`capture`), else the default. List every option under `slots`, so the voice pack can render each variant.
- `otherwise` is where a learner who cannot answer after two tries is taken. Every state needs one unless it is an `end`.
- Exactly the states reached by the learner's answers must reach a `goal` state and then an `end`.
- Every non-end state needs a `model` that the engine understands; the test feeds the models through the scene and checks it finishes with the goal reached.
- Built in, for every scene: "può ripetere?" repeats the last line slowly, and common slips (un acqua, ho andato, sono 25 anni, credo che è…) are noted at the end.

## Oral rehearsal paper

`presentation`, `photo`, `text` and `roleplay` tasks with `examiner` lines, `material`, `cues`, `followUps`, `modelAnswer` and `minSeconds`/`maxSeconds`. A photo task may name an `image` shipped in assets; until there are licensed photos the picture is described in words.
