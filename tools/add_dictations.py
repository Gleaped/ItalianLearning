#!/usr/bin/env python3
"""Adds one dictation (listen_transcribe) to every A2-C1 lesson that has no listening exercise.

The sentence is an example sentence from the unit's own vocabulary, so no new Italian is
written here. The step goes at the end of the practice stage. Lessons that already have a
listening exercise, or already have ten steps, are left alone. Safe to run again: lessons
that already have a dictation added by this script are skipped.

Usage: python3 tools/add_dictations.py [--dry-run]
"""
import glob
import json
import re
import sys

LEVELS = {"A2", "B1", "B2", "C1"}
MAX_STEPS = 10
SUFFIX = "-ld"
PLAIN = re.compile(r"^[A-Za-zÀ-ÿ' ,]+[.?!]$")


def words(text):
    return re.findall(r"[a-zà-ÿ]+", text.lower())


def suitable(sentence):
    n = len(sentence.split())
    return 4 <= n <= 10 and len(sentence) <= 60 and PLAIN.match(sentence) is not None


def lesson_words(lesson):
    return set(words(json.dumps(lesson, ensure_ascii=False)))


def main():
    dry = "--dry-run" in sys.argv
    added = 0
    for path in sorted(glob.glob("app/src/main/assets/course/units/*.json")):
        unit = json.load(open(path, encoding="utf-8"))
        if unit.get("level") not in LEVELS:
            continue
        pool = [v for v in unit.get("vocabulary", []) if suitable(v.get("example", ""))]
        used = set()
        changed = False
        for lesson in unit["lessons"]:
            steps = lesson["exercises"]
            if len(steps) >= MAX_STEPS or any(s["type"].startswith("listen") for s in steps):
                continue
            # Sentences already in the lesson would give the answer away or repeat a step.
            text = json.dumps(lesson, ensure_ascii=False)
            free = [v for v in pool if v["example"] not in used and v["example"] not in text]
            if not free:
                continue
            seen = lesson_words(lesson)
            # Prefer a word this lesson already works with.
            free.sort(key=lambda v: 0 if set(words(v["it"])) & seen - {"il", "la", "un", "di", "a", "e"} else 1)
            pick = free[0]
            used.add(pick["example"])
            step = {
                "type": "listen_transcribe",
                "id": lesson["id"] + SUFFIX,
                "stage": "practise",
                "instruction": "Ascolta e scrivi.",
                "audioText": pick["example"],
                "answers": [pick["example"]],
                "explanation": f"{pick['it']} = {pick['en']}",
            }
            if unit["level"] == "A2":
                step["slow"] = True
            last_practice = max((i for i, s in enumerate(steps) if s.get("stage") == "practise"), default=len(steps) - 2)
            steps.insert(last_practice + 1, step)
            changed = True
            added += 1
        if changed and not dry:
            with open(path, "w", encoding="utf-8") as out:
                out.write(json.dumps(unit, ensure_ascii=False, indent=2) + "\n")
    print(f"{added} dictations added")


if __name__ == "__main__":
    main()
