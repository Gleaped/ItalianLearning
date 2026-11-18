#!/usr/bin/env python3
"""Linguistic lint for course content (offline).

Usage: python3 tools/qa/lint_italian.py [--baseline FILE] [--write-baseline FILE] [--coverage] [unit-id ...]

Checks learner-facing Italian text in every unit:
  spell      every word must be a known form in assets/dictionary/dictionary.db or tools/qa/allowlist.txt
  coverage   share of words above the unit's level tier by corpus frequency (warning, with --coverage)
  duplicate  the same Italian sentence in two lessons of one unit

With --baseline, only findings that are not in the baseline file are errors, so existing content can be
cleaned gradually while new content is held to the standard. See docs/proposal-lesson-expansion.md, 9.3.
"""
import json
import re
import sqlite3
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
COURSE = ROOT / "app/src/main/assets/course"
DB = ROOT / "app/src/main/assets/dictionary/dictionary.db"
ALLOW = Path(__file__).with_name("allowlist.txt")
WORD = re.compile(r"[A-Za-zÀ-ÖØ-öø-ÿ]+(?:['’][A-Za-zÀ-ÖØ-öø-ÿ]+)*", re.UNICODE)
# Highest frequency rank (lemma, from wordfreq) that is expected at each level.
TIER_MAX = {"A1": 600, "A2": 1500, "B1": 3000, "B2": 6000, "C1": 10000, "C2": 16000, "CELI3": 6000}


def norm(w: str) -> str:
    w = w.lower().replace("’", "'")
    return unicodedata.normalize("NFC", w)


def strip_accents(w: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", w) if unicodedata.category(c) != "Mn")


def italian_strings(e: dict):
    """Yield (field, text) for the Italian text of one exercise."""
    t = e.get("type")
    for k in ("sentence", "text", "audioText", "passage", "front", "modelAnswer", "cue", "source" if t == "transform" else "-"):
        if isinstance(e.get(k), str) and not (k == "front" and False):
            yield k, e[k]
    if t == "translate":
        yield ("source" if e.get("direction") == "it_en" else "answers"), (
            e.get("source", "") if e.get("direction") == "it_en" else " ".join(e.get("answers", [])))
    for k in ("answers", "forms", "sentences", "tiles"):
        v = e.get(k)
        if isinstance(v, list) and t != "translate":
            for s in v:
                if isinstance(s, str):
                    yield k, s
    for q in e.get("questions", []) or []:
        for o in q.get("options", []) or []:
            yield "option", o
    for turn in e.get("turns", []) or []:
        if isinstance(turn, dict) and turn.get("text"):
            yield "turn", turn["text"]
    for ex in e.get("examples", []) or []:
        if isinstance(ex, dict) and ex.get("it"):
            yield "example", ex["it"]
    if t == "grammar_note":
        return


ELIDED = {"c", "com", "dov", "quant", "anch", "l", "un", "d", "all", "dell", "nell", "sull", "quell", "bell", "mezz",
          "trent", "sant", "gliel", "m", "t", "s", "v", "ch", "cos", "po", "n", "nient", "qualcos", "tutt", "quest",
          "dall", "coll", "ognun", "buon", "nessun", "cent", "vent", "quarant", "cinquant", "sessant", "settant",
          "ottant", "novant", "tant", "pover", "grand", "qual", "tutt", "ogn", "senz", "dev", "gl"}
CLITICS = ("gliela", "gliele", "glielo", "glieli", "gliene", "mela", "mele", "melo", "meli", "tela", "tele", "telo",
           "teli", "cela", "cele", "celo", "celi", "vela", "vele", "velo", "veli", "sela", "sele", "selo", "seli",
           "mene", "tene", "sene", "cene", "vene", "gli", "mi", "ti", "ci", "vi", "si", "lo", "la", "li", "le", "ne")


def known_word(w, known, extra):
    if len(w) == 1:
        return True
    if w in known or w in extra:
        return True
    if "'" in w:
        a, _, b = w.partition("'")
        return (a in ELIDED or a in known or a in extra) and known_word(b, known, extra)
    for c in CLITICS:
        if w.endswith(c) and len(w) > len(c) + 2:
            stem = w[: -len(c)]
            if stem in known or stem + "e" in known or stem + "re" in known:
                return True
    return False


def load_known():
    con = sqlite3.connect(DB)
    known = {norm(r[0]) for r in con.execute("select form from form")}
    known |= {r[0].lower() for r in con.execute("select lemma from lemma")}
    rank = {}
    for form, fr in con.execute("select f.form, min(l.freq_rank) from form f join lemma l on l.id = f.lemma_id "
                                "where l.freq_rank is not null group by f.form"):
        rank[norm(form)] = fr
    con.close()
    extra = set()
    if ALLOW.exists():
        extra = {norm(l.strip()) for l in ALLOW.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}
    return known, rank, extra


def main(argv):
    base = None
    write_base = None
    coverage = False
    wanted = []
    it = iter(argv)
    for a in it:
        if a == "--baseline":
            base = next(it)
        elif a == "--write-baseline":
            write_base = next(it)
        elif a == "--coverage":
            coverage = True
        else:
            wanted.append(a)
    known, rank, extra = load_known()
    known_plain = {strip_accents(k) for k in known}
    course = json.loads((COURSE / "course.json").read_text(encoding="utf-8"))
    findings = []
    warnings = []
    for ref in course["units"]:
        uid = ref["id"]
        if wanted and uid not in wanted:
            continue
        f = COURSE / "units" / f"{uid}.json"
        if not f.exists():
            continue
        unit = json.loads(f.read_text(encoding="utf-8"))
        seen = defaultdict(set)
        total = above = 0
        tier = TIER_MAX.get(ref.get("level"), 16000)
        for lesson in unit.get("lessons", []):
            for e in lesson.get("exercises", []):
                if e.get("type") == "grammar_note":
                    strings = [("example", ex["it"]) for ex in e.get("examples", []) if isinstance(ex, dict) and ex.get("it")]
                else:
                    strings = list(italian_strings(e))
                for field, s in strings:
                    clean = s.replace("___", " ")
                    for m in WORD.finditer(clean):
                        w = norm(m.group())
                        total += 1
                        r = rank.get(w)
                        if r is not None and r > tier:
                            above += 1
                        if known_word(w, known, extra):
                            continue
                        if strip_accents(w) in known_plain:
                            warnings.append(f"{uid}|{lesson['id']}|accent|{w}")
                            continue
                        findings.append(f"{uid}|{lesson['id']}|spell|{w}")
                    if field in ("sentence", "audioText") and len(clean) > 25:
                        seen[norm(clean)].add(lesson["id"])
        for s, lessons in seen.items():
            if len(lessons) > 1:
                findings.append(f"{uid}|{','.join(sorted(lessons))}|duplicate|{s[:50]}")
        if coverage and total:
            warnings.append(f"{uid}|-|coverage|{100 * above / total:.1f}% of {total} words above tier {tier}")
    findings = sorted(set(findings))
    if write_base:
        Path(write_base).write_text("\n".join(findings) + "\n", encoding="utf-8")
        print(f"wrote {len(findings)} findings to {write_base}")
        return 0
    old = set()
    if base and Path(base).exists():
        old = {l for l in Path(base).read_text(encoding="utf-8").splitlines() if l}
    new = [f for f in findings if f not in old]
    for w in sorted(set(warnings)):
        print("warning:", w)
    for f in new:
        print("ERROR:", f)
    print(f"{len(findings)} findings, {len(findings) - len(new)} in baseline, {len(new)} new")
    return 1 if new else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
