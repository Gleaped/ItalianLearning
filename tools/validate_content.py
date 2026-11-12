#!/usr/bin/env python3
"""Validate course content under app/src/main/assets/course.

Usage: python3 tools/validate_content.py [unit-id ...]

Checks every unit listed in course.json (or only the ids given). Errors fail the run (exit 1);
warnings are printed only. Units with "format": 2 get the stricter short-lesson rules from
docs/lesson-format.md ("Format 2").
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COURSE_DIR = ROOT / "app" / "src" / "main" / "assets" / "course"

KNOWN_TYPES = {
    "grammar_note", "flashcard", "multiple_choice", "gap_fill", "conjugation", "sentence_build",
    "translate", "match_pairs", "error_correction", "listen_transcribe", "listen_choose", "reading",
    "speak_repeat", "speak_respond", "roleplay", "free_write",
    "choose_mood", "find_trigger", "transform", "sort_buckets", "minimal_pair",
}
UNSCORED = {"grammar_note"}
QUICK_TYPES = {"multiple_choice", "gap_fill", "translate", "error_correction", "conjugation",
               "choose_mood", "transform", "find_trigger"}
STAGES = {"warmup", "learn", "practise", "use"}
REQUIRED = {
    "grammar_note": ["body"],
    "flashcard": ["front", "back"],
    "multiple_choice": ["prompt", "options", "answer"],
    "gap_fill": ["sentence", "answers"],
    "conjugation": ["verb", "items"],
    "sentence_build": ["tiles"],
    "translate": ["source", "answers"],
    "match_pairs": ["pairs"],
    "error_correction": ["sentence", "answers"],
    "listen_transcribe": ["audioText", "answers"],
    "listen_choose": ["audioText", "options", "answer"],
    "reading": ["passage"],
    "speak_repeat": ["text"],
    "speak_respond": ["prompt"],
    "roleplay": ["turns"],
    "free_write": ["prompt"],
    "choose_mood": ["sentence", "answer"],
    "find_trigger": ["sentence", "trigger"],
    "transform": ["source", "cue", "answers"],
    "sort_buckets": ["buckets", "items"],
    "minimal_pair": ["sentences", "question", "answer"],
}

TOKEN_EDGE = re.compile(r"^[^\w']+|[^\w']+$", re.UNICODE)


def words_outside_tables(body: str) -> int:
    lines = [l for l in body.splitlines() if not l.strip().startswith("|")]
    text = re.sub(r"[*=>#-]", " ", " ".join(lines))
    return len([w for w in text.split() if w.strip()])


def clean_token(t: str) -> str:
    return TOKEN_EDGE.sub("", t).lower()


def trigger_found(sentence: str, trigger: str) -> bool:
    tokens = [clean_token(t) for t in sentence.split()]
    want = [clean_token(t) for t in trigger.split()]
    want = [w for w in want if w]
    if not want:
        return False
    for i in range(len(tokens) - len(want) + 1):
        if tokens[i:i + len(want)] == want:
            return True
    return False


class Report:
    def __init__(self):
        self.errors = []
        self.warnings = []

    def error(self, where, msg):
        self.errors.append(f"{where}: {msg}")

    def warn(self, where, msg):
        self.warnings.append(f"{where}: {msg}")


def check_exercise(e, where, r: Report, strict: bool, quick_only=False):
    if not isinstance(e, dict):
        r.error(where, "exercise is not an object")
        return
    t = e.get("type")
    if t not in KNOWN_TYPES:
        r.error(where, f"unknown type {t!r}")
        return
    if quick_only and t not in QUICK_TYPES:
        r.error(where, f"challenge type {t!r} is not a quick type")
    for f in REQUIRED[t]:
        if f not in e or e[f] in ("", [], None):
            r.error(where, f"{t} is missing {f}")
    stage = e.get("stage")
    if stage is not None and stage not in STAGES:
        r.error(where, f"unknown stage {stage!r}")

    def idx_ok(options_key, answer_key="answer"):
        opts = e.get(options_key) or []
        a = e.get(answer_key)
        if not isinstance(a, int) or not (0 <= a < len(opts)):
            r.error(where, f"{answer_key} {a!r} out of range for {len(opts)} {options_key}")

    if t in ("multiple_choice", "listen_choose"):
        idx_ok("options")
        if len(set(e.get("options", []))) != len(e.get("options", [])):
            r.error(where, "duplicate options")
    if t == "gap_fill" and "___" not in e.get("sentence", ""):
        r.error(where, "gap_fill sentence has no ___")
    if t == "choose_mood":
        if "___" not in e.get("sentence", ""):
            r.error(where, "choose_mood sentence has no ___")
        opts = e.get("options") or ["indicativo", "congiuntivo"]
        a = e.get("answer")
        if not isinstance(a, int) or not (0 <= a < len(opts)):
            r.error(where, f"answer {a!r} out of range for {len(opts)} options")
    if t == "find_trigger" and not trigger_found(e.get("sentence", ""), e.get("trigger", "")):
        r.error(where, f"trigger {e.get('trigger')!r} not found as whole words in the sentence")
    if t == "sort_buckets":
        nb = len(e.get("buckets", []))
        if not 2 <= nb <= 3:
            r.error(where, "sort_buckets needs 2 or 3 buckets")
        for i, it in enumerate(e.get("items", [])):
            b = it.get("bucket") if isinstance(it, dict) else None
            if not isinstance(b, int) or not 0 <= b < nb or not it.get("text"):
                r.error(where, f"item {i} has a bad bucket or no text")
    if t == "minimal_pair":
        idx_ok("sentences")
        tr = e.get("translations")
        if tr is not None and len(tr) != len(e.get("sentences", [])):
            r.error(where, "translations must match sentences in length")
    if t == "reading":
        for i, q in enumerate(e.get("questions", [])):
            if not (isinstance(q.get("answer"), int) and 0 <= q["answer"] < len(q.get("options", []))):
                r.error(where, f"reading question {i} answer out of range")
    if t == "roleplay":
        for i, turn in enumerate(e.get("turns", [])):
            if turn.get("speaker") not in ("npc", "you") or not turn.get("text"):
                r.error(where, f"roleplay turn {i} needs speaker npc/you and text")
    if t == "conjugation":
        for i, it in enumerate(e.get("items", [])):
            if not it.get("person") or not it.get("answer"):
                r.error(where, f"conjugation item {i} needs person and answer")
        if quick_only and len(e.get("items", [])) > 3:
            r.error(where, "challenge conjugation has more than 3 items")
    if t == "match_pairs":
        for i, p in enumerate(e.get("pairs", [])):
            if not p.get("left") or not p.get("right"):
                r.error(where, f"pair {i} needs left and right")
    for key in ("answers", "forms"):
        for a in e.get(key, []) or []:
            if not isinstance(a, str) or not a.strip():
                r.error(where, f"empty entry in {key}")
    if "Richard" in json.dumps(e, ensure_ascii=False):
        r.warn(where, "hard-coded learner name: use {name}")
    if strict:
        if t == "grammar_note":
            n = words_outside_tables(e.get("body", ""))
            if n > 60:
                r.error(where, f"rule card has {n} words outside tables (max 60)")
        elif not quick_only and not (e.get("explanation") or "").strip() and t not in ("flashcard", "speak_repeat", "roleplay", "free_write", "reading", "speak_respond"):
            r.error(where, f"{t} has no explanation")
        if not quick_only and stage is None:
            r.warn(where, "no stage")


def check_unit(ref, r: Report):
    uid = ref["id"]
    path = COURSE_DIR / "units" / f"{uid}.json"
    if not path.exists():
        r.error(uid, "unit file missing")
        return None
    try:
        unit = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as ex:
        r.error(uid, f"invalid JSON: {ex}")
        return None
    if unit.get("id") != uid:
        r.error(uid, f"file id {unit.get('id')!r} does not match")
    if unit.get("level") != ref.get("level"):
        r.error(uid, f"level {unit.get('level')!r} differs from course.json {ref.get('level')!r}")
    strict = unit.get("format") == 2
    lesson_ids = set()
    exercise_ids = set()
    for li, lesson in enumerate(unit.get("lessons", [])):
        lid = lesson.get("id") or f"{uid}-lesson{li + 1}"
        if lid in lesson_ids:
            r.error(lid, "duplicate lesson id")
        lesson_ids.add(lid)
        exs = lesson.get("exercises", [])
        if not exs:
            r.error(lid, "lesson has no exercises")
        if strict:
            if len(exs) > 10:
                r.error(lid, f"{len(exs)} exercises (max 10)")
            m = lesson.get("minutes", 10)
            if not 5 <= m <= 10:
                r.error(lid, f"minutes {m} outside 5-10")
            for f in ("goal", "takeaway"):
                if not (lesson.get(f) or "").strip():
                    r.error(lid, f"lesson has no {f}")
        elif len(exs) > 15:
            r.warn(lid, f"long lesson: {len(exs)} exercises")
        for ei, e in enumerate(exs):
            eid = e.get("id") if isinstance(e, dict) else None
            where = f"{lid}#{ei + 1}" + (f" ({eid})" if eid else "")
            if eid:
                if eid in exercise_ids:
                    r.error(where, "duplicate exercise id")
                exercise_ids.add(eid)
            check_exercise(e, where, r, strict)
    for ti, tip in enumerate(unit.get("grammarTips", [])):
        where = f"{uid} tip {ti + 1}"
        if len(tip.get("rule", "")) > 40:
            r.error(where, "rule longer than 40 characters")
        if len(tip.get("tip", "")) > 140:
            r.error(where, "tip longer than 140 characters")
        if tip.get("challenge"):
            check_exercise(tip["challenge"], where + " challenge", r, False, quick_only=True)
    for vi, v in enumerate(unit.get("vocabulary", [])):
        if not v.get("it") or not v.get("en"):
            r.error(f"{uid} vocab {vi + 1}", "needs it and en")
    return unit


def main(argv):
    r = Report()
    course = json.loads((COURSE_DIR / "course.json").read_text(encoding="utf-8"))
    levels = {l["id"] for l in course.get("levels", [])}
    modules = {m["id"]: m for m in course.get("modules", [])}
    for m in modules.values():
        if m.get("level") not in levels:
            r.error(f"module {m['id']}", f"unknown level {m.get('level')!r}")
    ids = [u["id"] for u in course["units"]]
    for dup in {i for i in ids if ids.count(i) > 1}:
        r.error(dup, "listed twice in course.json")
    wanted = set(argv)
    units = {}
    for ref in course["units"]:
        if ref.get("level") not in levels:
            r.error(ref["id"], f"unknown level {ref.get('level')!r}")
        mod = ref.get("module")
        if mod is not None:
            if mod not in modules:
                r.error(ref["id"], f"unknown module {mod!r}")
            elif modules[mod].get("level") != ref.get("level"):
                r.error(ref["id"], f"module {mod!r} belongs to another level")
        if not ref.get("available", True):
            continue
        if wanted and ref["id"] not in wanted:
            continue
        u = check_unit(ref, r)
        if u is not None:
            units[ref["id"]] = u
    available = {u["id"] for u in course["units"] if u.get("available", True)}
    for uid, u in units.items():
        for rid in u.get("remedialUnitIds", []):
            if rid not in available:
                r.error(uid, f"remedialUnitIds points to unknown unit {rid!r}")
    ref_path = ROOT / "app" / "src" / "main" / "assets" / "grammar" / "reference.json"
    if ref_path.exists() and not wanted:
        ref = json.loads(ref_path.read_text(encoding="utf-8"))

        def walk(node):
            if isinstance(node, dict):
                for k, v in node.items():
                    if k == "units" and isinstance(v, list):
                        for uid in v:
                            if isinstance(uid, str) and uid not in available:
                                r.error("grammar/reference.json", f"links to unknown unit {uid!r}")
                    else:
                        walk(v)
            elif isinstance(node, list):
                for v in node:
                    walk(v)
        walk(ref)
    for w in r.warnings:
        print("warning:", w)
    for e in r.errors:
        print("ERROR:", e)
    print(f"{len(units)} units checked, {len(r.errors)} errors, {len(r.warnings)} warnings")
    return 1 if r.errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
