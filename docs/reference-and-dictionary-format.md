# Grammar reference and dictionary formats

## Grammar reference: `app/src/main/assets/grammar/reference.json`

```json
{
  "version": 1,
  "sections": [
    {
      "id": "verbi-tempi",
      "title": "Tempi verbali",
      "topics": [
        {
          "id": "passato-prossimo",
          "title": "Passato prossimo",
          "level": "A2",
          "summary": "One English sentence: what it is for.",
          "body": "Short explanation. Paragraphs, **bold**, and `- ` bullets only.",
          "tables": [
            {
              "title": "Regular verbs",
              "columns": ["", "parlare", "credere", "dormire"],
              "rows": [["io", "ho parlato", "ho creduto", "ho dormito"]],
              "note": "optional one-line note under the table"
            }
          ],
          "examples": [{ "it": "Ieri ho lavorato fino a tardi.", "en": "Yesterday I worked late." }],
          "pitfalls": ["optional short common-mistake notes"],
          "relatedUnitIds": ["a2r-01", "b1-01"]
        }
      ]
    }
  ]
}
```

- Tables are small charts: keep them to at most 5 columns so they fit a phone screen; split wider charts.
- `level` is one of A2, B1, B2, C1.
- `relatedUnitIds` must be unit ids from `course/course.json`.

## Dictionary: `app/src/main/assets/dictionary/dictionary.db`

A read-only SQLite database built by `tools/dictionary/build.py` (committed with its inputs or download steps).
The app copies it from assets on first use (or when `meta.version` changes) and opens it read-only.

```sql
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
-- keys: version, built_at, sources (human-readable attribution and licences), entry_count

CREATE TABLE lemma (
  id INTEGER PRIMARY KEY,
  lemma TEXT NOT NULL,          -- "andare", "casa", "bello"
  pos TEXT NOT NULL,            -- verb | noun | adj | adv | pron | prep | conj | det | interj | other
  gender TEXT,                  -- m | f | mf | NULL
  gloss_en TEXT NOT NULL,       -- senses separated by " ; ", most common first
  gloss_it TEXT,                -- optional short Italian definition
  example_it TEXT, example_en TEXT,
  freq_rank INTEGER,            -- 1 = most frequent; NULL if unknown
  notes TEXT,                   -- e.g. "aux: essere", "irregular", "plural: uova (f)"
  forms_json TEXT NOT NULL      -- display tables, see below
);
CREATE INDEX lemma_lemma ON lemma(lemma);

CREATE TABLE form (
  form TEXT NOT NULL,           -- as written, with accents: "andò"
  form_norm TEXT NOT NULL,      -- lowercase, accents and apostrophes stripped: "ando"
  lemma_id INTEGER NOT NULL REFERENCES lemma(id),
  tags TEXT NOT NULL            -- human-readable: "indicativo passato remoto, lui/lei"
);
CREATE INDEX form_norm_idx ON form(form_norm);
```

`forms_json`:

```json
{ "tables": [
    { "title": "Indicativo presente", "columns": ["", ""], "rows": [["io", "vado"], ["tu", "vai"]] },
    { "title": "Forme", "columns": ["", "singolare", "plurale"], "rows": [["maschile", "bello"], ["femminile", "bella", "belle"]] }
] }
```

Verbs list every simple tense and mood (indicativo presente, imperfetto, passato remoto, futuro; congiuntivo
presente, imperfetto; condizionale presente; imperativo; infinito, gerundio, participio presente/passato) plus
compound tenses (passato prossimo, trapassato prossimo, futuro anteriore, congiuntivo passato/trapassato,
condizionale passato) with the right auxiliary. Nouns list singular/plural; adjectives list m/f singular/plural
(and irregular comparatives where they exist).

Lookup: normalise the query the same way as `form_norm`; match `form.form_norm` exactly first, then prefix
matches on `lemma.lemma`; group results by lemma, ordered by `freq_rank`.
