# Content QA

`lint_italian.py` checks the Italian in every unit against the offline dictionary (see the header of the script).

- Run: `python3 tools/qa/lint_italian.py --baseline tools/qa/baseline.txt [--coverage] [unit-id ...]`
- A word that is correct but unknown (a name, place, acronym, loanword) goes in `allowlist.txt`.
- `baseline.txt` holds findings that existed when the lint was added. Most are deliberate mistakes inside
  `error_correction` exercises, English words in glosses, and a few real typos to fix. Fix one and delete its line;
  never add lines for new content.
- `--coverage` prints, per unit, the share of words more frequent-rank than the level's tier (A1 600, A2 1,500,
  B1 3,000, B2 6,000, C1 10,000, C2 16,000). It is a warning only; ranks come from the dictionary's wordfreq data.
- The accent warning means a word is only known without its accent (for example `sara` for `sarà`).

Planned next: grammar-topic gate, single-answer test for gaps, agreement check (docs/proposal-lesson-expansion.md, 9.3).
