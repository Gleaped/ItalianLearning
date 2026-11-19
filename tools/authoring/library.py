"""Builders for the reading and listening libraries (units of four texts each).

A batch script describes texts as plain tuples and calls `write_reading_unit` / `write_listening_unit`.
The builders write units/<id>.json, register the unit in course.json (and its level and module when new),
and shuffle answer positions deterministically so the right option is not always the same letter.
See docs/proposal-lesson-expansion.md (section 9) for the checks that run afterwards.

Text tuple:      (title, text, [(question, right, wrong, wrong, ...), ...], [(front, back), ...])
"""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
COURSE = ROOT / "app/src/main/assets/course"

LEVELS = {
    "LET": {"id": "LET", "title": "Biblioteca di lettura", "foundation": True,
            "description": "Graded reading texts from A1 to C1, four per unit, each with questions and key words. Always open."},
    "ASC": {"id": "ASC", "title": "Biblioteca di ascolto", "foundation": True,
            "description": "Graded listening texts from A1 to C1, four per unit, each with questions. Always open."},
}

TIP = ("Prima di ascoltare, leggi le domande.\n\n- La prima volta cerca l'idea generale.\n"
       "- La seconda volta cerca numeri, giorni, nomi e motivi.\n- Se perdi una parola, vai avanti.")


def _shuffle(uid, q, right, wrongs):
    opts = [right] + list(wrongs)
    seed = int(hashlib.md5(f"{uid}|{q}".encode()).hexdigest(), 16)
    k = seed % len(opts)
    # put the right answer at position k, keep the others in the given order
    others = list(wrongs)
    opts = others[:k] + [right] + others[k:]
    return opts, k


def _example(front, text):
    key = re.sub(r"\s*\(.*?\)", "", front).strip().lower()
    key = re.sub(r"^(il|lo|la|l'|i|gli|le|un|uno|una|un')\s*", "", key)
    for s in re.split(r"(?<=[.!?])\s+", text.replace("\n", " ")):
        if key and key in s.lower() and len(s) < 140:
            return s.strip()
    return ""


def _cards(prefix, cards, text):
    out = []
    for i, (front, back) in enumerate(cards):
        c = {"type": "flashcard", "id": f"{prefix}-c{i + 1}", "stage": "learn", "front": front, "back": back}
        ex = _example(front, text)
        if ex:
            c["example"] = ex
        out.append(c)
    return out


def reading_lesson(uid, n, title, text, qs, cards):
    lid = f"{uid}-l{n}"
    questions = []
    for q in qs:
        opts, a = _shuffle(lid, q[0], q[1], q[2:])
        questions.append({"question": q[0], "options": opts, "answer": a})
    ex = [{"type": "reading", "id": f"{lid}-e1", "stage": "practise",
           "instruction": "Leggi il testo con calma, poi rispondi. Cerca nel testo la frase che conferma la tua scelta.",
           "title": title, "passage": text, "questions": questions}]
    ex += _cards(lid, cards, text)
    return {"id": lid, "title": title, "minutes": 8,
            "goal": "Leggere un testo e rispondere alle domande.",
            "takeaway": "Cerca la risposta nel testo, non nella tua opinione.", "exercises": ex}


def listening_lesson(uid, n, title, text, qs, cards):
    lid = f"{uid}-l{n}"
    ex = [{"type": "grammar_note", "id": f"{lid}-e1", "stage": "learn", "title": "Come ascoltare", "body": TIP, "examples": []}]
    for i, q in enumerate(qs, 2):
        opts, a = _shuffle(lid, q[0], q[1], q[2:])
        ex.append({"type": "listen_choose", "id": f"{lid}-e{i}", "stage": "practise", "audioText": text, "question": q[0],
                   "options": opts, "answer": a,
                   "explanation": "La risposta è nel testo: riascolta la parte con il numero, il nome o il motivo."})
    ex += _cards(lid, cards, text)
    return {"id": lid, "title": title, "minutes": 8,
            "goal": "Ascoltare un testo e rispondere alle domande.",
            "takeaway": "Leggi le domande prima di ascoltare e cerca numeri, giorni e motivi.", "exercises": ex}


def _write(uid, level, module, module_title, title, objective, focus, summary, lessons, cefr):
    vocab = []
    for l in lessons:
        for e in l["exercises"]:
            if e["type"] == "flashcard":
                v = {"it": e["front"], "en": e["back"]}
                if e.get("example"):
                    v["example"] = e["example"]
                vocab.append(v)
    unit = {"id": uid, "level": level, "title": title, "objective": objective, "grammarFocus": focus,
            "lessons": lessons, "vocabulary": vocab}
    (COURSE / "units" / f"{uid}.json").write_text(json.dumps(unit, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    cp = COURSE / "course.json"
    c = json.loads(cp.read_text(encoding="utf-8"))
    if not any(l["id"] == level for l in c["levels"]):
        c["levels"].append(LEVELS[level])
    if not any(m["id"] == module for m in c["modules"]):
        c["modules"].append({"id": module, "level": level, "title": module_title,
                             "description": f"Livello {cefr}."})
    c["units"] = [u for u in c["units"] if u["id"] != uid]
    order = 1 + max([u.get("order", 0) for u in c["units"] if u["level"] == level] or [0])
    c["units"].append({"id": uid, "level": level, "order": order, "title": title, "summary": summary,
                       "available": True, "module": module})
    cp.write_text(json.dumps(c, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_reading_unit(uid, cefr, title, texts):
    lessons = [reading_lesson(uid, i + 1, *t) for i, t in enumerate(texts)]
    words = sum(len(t[1].split()) for t in texts) // len(texts)
    _write(uid, "LET", f"let-{cefr.lower()}", f"Lettura {cefr}", title,
           f"Alla fine dell'unità hai letto {len(texts)} testi di livello {cefr} (circa {words} parole ciascuno).",
           "Lettura estensiva: idea generale, dettagli e parole chiave.",
           f"{len(texts)} reading texts at {cefr} (about {words} words each), with questions and key words.", lessons, cefr)


def write_listening_unit(uid, cefr, title, texts):
    lessons = [listening_lesson(uid, i + 1, *t) for i, t in enumerate(texts)]
    words = sum(len(t[1].split()) for t in texts) // len(texts)
    _write(uid, "ASC", f"asc-{cefr.lower()}", f"Ascolto {cefr}", title,
           f"Alla fine dell'unità hai ascoltato {len(texts)} testi di livello {cefr} (circa {words} parole ciascuno).",
           "Ascolto esteso: leggere le domande prima, idea generale e dettagli.",
           f"{len(texts)} listening texts at {cefr} (about {words} words each), with questions.", lessons, cefr)
