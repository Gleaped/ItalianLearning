# Offline Italian→English dictionary

`build.py` produces `app/src/main/assets/dictionary/dictionary.db`, the read-only SQLite database
described in `docs/reference-and-dictionary-format.md` (Dictionary section). `verify.py` spot-checks it.

## What is in it

- About 43,000 lemmas: every usable Italian headword in the Wiktionary source (version 2; version 1 had the 11,000 most frequent), ranked by corpus frequency. Each one has a POS, a gender for nouns, 1–4 English
  senses (secondary parts of speech appear as `(n.) …`, `(adj.) …`), notes (auxiliary, irregular,
  irregular plural…) and `forms_json` charts.
- About 360,000 lookup forms, with readable tags: every conjugated form, plural, feminine and
  superlative, plus articulated prepositions, elided forms (`dell'`, `l'`, `quell'`, `bel`, `aver`)
  and combined pronouns (`glielo`, `me ne`). The 800 most frequent verbs also get
  infinitive/gerund/imperative + clitic forms (`dimmi`, `prenderlo`, `vattene`). Multi-word forms of
  pronominal verbs (`mi alzo`, `me ne vado`) are included.
- Verbs: every simple tense and mood, compound tenses with the right auxiliary (participle agreement
  shown as `andato/a`), imperativo (with the negative tu form), infinito, gerundio and participio.
  Pronominal verbs (`alzarsi`, `andarsene`, `farcela`, `metterci`) are conjugated with their pronouns.
  Verbs that take either auxiliary get an extra `Indicativo passato prossimo (con essere|avere)` table.
- Nouns: singular and plural with the article (`l'uovo / le uova`, `il / la cantante`), plus a
  feminine row where the feminine has no entry of its own (`attore / attrice`).
- Adjectives: m/f singular and plural, short forms (`bel/bei/begli`, `buon`, `gran`) and
  comparative and superlative forms (`buono → migliore, ottimo`, `bellissimo`).

## Sources and licences (also stored in `meta.sources`, which the app must show)

| Source | Used for | Licence |
|---|---|---|
| Wiktionary (en.wiktionary.org), through the Italian–English TSV in [Vuizur/Wiktionary-Dictionaries](https://github.com/Vuizur/Wiktionary-Dictionaries) (built from kaikki.org / wiktextract data) | English glosses, POS, `[auxiliary …]` notes, inflection lists used to *check* the generated forms | CC BY-SA 3.0 and GFDL (dual) |
| [wordfreq](https://github.com/rspeer/wordfreq) 3.1.1 (Robyn Speer) | Choosing and ranking lemmas | code Apache-2.0, data CC BY-SA 4.0 |
| This directory (`italian_morph.py`, `build.py`, `data/*.tsv`) | Conjugation engine and irregular-verb table, plural and gender rules, essere-verb list, gender list, function-word charts, gloss overrides for a few core words | written for this app |

**Attribution obligation:** the database is a derivative of Wiktionary, so it is shared under
**CC BY-SA 3.0**. The app has to show `meta.sources` (or an equivalent credit line naming Wiktionary
and the CC BY-SA licence) on an About or attribution screen. The glosses were filtered, shortened and
merged, and all forms were regenerated. The sources line says so.

No GPL data is used. The hunspell `dictionary-it` (GPL-3) was looked at but not used.

## Rebuild

```sh
# 1. third-party data goes in a scratch directory outside the repo (treated as untrusted data)
D=/tmp/dictionary-build && mkdir -p $D/raw
curl -sSfo $D/raw/it-en-wiktionary.tsv \
  "https://raw.githubusercontent.com/Vuizur/Wiktionary-Dictionaries/master/Italian-English%20Wiktionary%20dictionary.tsv"
sha256sum $D/raw/it-en-wiktionary.tsv
# snapshot used for dictionary.db version 1:
# ffd074433c5b4e020e98ca2d18b2ac20dcd5fd356b39c1b76be564a95fd57b53  (22,084,360 bytes, 114,426 lines)

# 2. pinned dependencies
python3 -m venv $D/venv && $D/venv/bin/pip install -r tools/dictionary/requirements.txt

# 3. build (about 30 s) and verify
$D/venv/bin/python -I tools/dictionary/build.py --wiktionary $D/raw/it-en-wiktionary.tsv
python3 tools/dictionary/verify.py
```

Options:
- `--lemmas N` (default 60000, i.e. every candidate) caps the number of lemmas. The database is about
  63 MB at the full 43,000 lemmas (about 14 MB compressed inside the APK) and 24.6 MB at 11,000.
  Verb charts take most of the space.
- `--enclitic-top N` (default 800) sets how many verbs get the clitic-attached lookup forms.
- `--out PATH` writes the database somewhere else. `--report FILE` writes a TSV with each lemma's
  rank, POS, gender, how well its verb forms match Wiktionary, and its notes.

Raise `DB_VERSION` in `build.py` whenever the contents change, so the app re-copies the asset.

## How it works

1. **Candidates.** Lowercase Wiktionary headwords with a usable part of speech. Form-of senses
   ("plural of", "alternative form of"), letter names and entries whose only senses are
   archaic, dialectal or literary are dropped, along with the headwords in `data/skip.tsv`.
2. **Forms.** `italian_morph.py` conjugates each verb in several candidate ways: an exact entry in
   the irregular table, an irregular base plus a prefix (`ot+tenere`, `pro+durre`), regular, `-isc-`,
   `-etti`, and stressed `-ìo` (`invii`). It keeps the candidate whose forms best match the
   Wiktionary inflection list for that verb. 98% of the selected verbs match at least 95% of their
   generated simple forms; the rest are mostly defective or impersonal verbs that Wiktionary lists
   only partly. Noun plurals (`-chi`/`-ci`, `-cie`/`-ce`, invariables) are chosen the same way.
3. **Auxiliary.** `data/aux_essere.tsv` comes first (E, EA or AE codes). Then the Wiktionary
   `[auxiliary …]` notes. Pronominal verbs always take essere. Everything else takes avere.
4. **Gender.** Suffix rules plus the Wiktionary plural list (`-a` nouns with only an `-i` plural are
   masculine; with both `-i` and `-e` they are m/f). `data/noun_gender.tsv` covers the rest
   (mostly nouns in `-e`, hand-checked).
5. **Ranking.** Each corpus token's frequency is split among the lemmas that can produce it, in
   proportion to each lemma's own exclusive evidence. A lemma whose evidence is almost all borrowed
   (a rare verb whose forms happen to be common nouns) is dropped. Pronominal verbs are scaled up,
   because most of their forms are multi-word.
6. **Write.** The tables follow the binding schema. `form_norm` uses the app's normalisation:
   lowercase, NFD with combining marks removed, apostrophes ' ’ ‘ ʼ ` ´ removed, whitespace collapsed.
   Then `VACUUM`.

## Known gaps

- No proper nouns (place names, people). Foreign words appear only when Wiktionary has an Italian
  entry for them (`film`, `computer`).
- Glosses are Wiktionary's English senses, cleaned automatically. A few top words have hand-written
  glosses (`GLOSS_OVERRIDE`). Some long senses are cut with "…", and some rare first senses remain.
  `gloss_it` and the example fields are empty.
- Verbs that take either auxiliary get one extra passato prossimo table with the other auxiliary; the
  other compound tenses show only the main one. Impersonal and weather verbs are conjugated for every
  person.
- Clitic-attached forms are generated only for the top 800 verbs, and only with single clitics plus
  `glie-`, `me-` and `te-` clusters.
- Nouns whose feminine has its own entry (`ragazza`, `amica`) are separate lemmas.
