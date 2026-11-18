#!/usr/bin/env python3
"""Renders the natural-voice pack: one small .m4a per spoken line of the course.

The app looks lines up by a key (SHA-256 of "voice|text", first 16 hex digits, see
domain/speech/AudioKey.kt), so every text collected here must be spoken by the app exactly as
written. A line the pack lacks is read by the phone's own voice, so gaps are harmless.

Usage:
  pip install kokoro-onnx soundfile numpy      (needs ffmpeg and espeak-ng data via espeakng-loader)
  python3 tools/audio/render_pack.py --model kokoro-v1.0.onnx --voices voices-v1.0.bin --out build/voicepack
Add --list to only count the lines, --limit N to render a few, --zip to build voicepack.zip.

Voices: "a" = if_sara (main), "b" = im_nicola (second speaker, partner, examiner).
Kokoro-82M is Apache-2.0 licensed.
"""
import argparse
import hashlib
import io
import itertools
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
# Voice tuning: each role is a weighted blend of Kokoro voice styles plus a speaking speed.
# Bump TUNING whenever these change so that audio rendered with older settings is thrown away.
TUNING = "2"
VOICES = {
    "a": ({"if_sara": 0.7, "af_bella": 0.3}, 0.95),
    "b": ({"im_nicola": 0.65, "if_sara": 0.35}, 1.0),
}

TURN_BREAK = re.compile(r"(?:^|\s+)[–—]\s+")
BRACKETED = re.compile(r"\s*\([^)]*\)")
SLOT = re.compile(r"\{(\w+)(?:\|([^}]*))?\}")


def norm(text: str) -> str:
    return " ".join(text.split())


def key(text: str, voice: str) -> str:
    return hashlib.sha256(f"{voice}|{norm(text)}".encode("utf-8")).hexdigest()[:16]


def for_card(text: str) -> str:
    stripped = BRACKETED.sub(" ", text)
    tidy = re.sub(r"\s+([,.;:!?])", r"\1", " ".join(stripped.split()))
    return tidy or text


def dialogue_turns(text: str):
    parts = [p.strip() for p in TURN_BREAK.split(text.strip()) if p.strip()]
    return parts or [text]


class Lines:
    def __init__(self):
        self.items = {}  # key -> (text, voice)

    def add(self, text, voice="a"):
        if isinstance(text, str) and text.strip():
            self.items.setdefault(key(text, voice), (norm(text), voice))

    def add_dialogue(self, text, second=False):
        for i, turn in enumerate(dialogue_turns(text)):
            self.add(turn, "b" if second != (i % 2 == 1) else "a")


def collect_exercise(e, out: Lines):
    t = e.get("type")
    if t in ("listen_transcribe", "listen_choose", "listen_order", "gap_listen", "speed_ladder", "dialogue_dictation"):
        if "audioText" in e:
            out.add_dialogue(e["audioText"])
        for part in e.get("lines", []) or []:
            if isinstance(part, str):
                out.add(part)
            elif isinstance(part, dict):
                out.add(part.get("text"), "b" if part.get("speaker") == "b" else "a")
        for s in e.get("sentences", []) or []:
            out.add(s)
    elif t in ("speak_repeat", "shadow"):
        out.add(e.get("text"))
    elif t == "speak_respond":
        out.add(e.get("prompt"))
        out.add(e.get("modelAnswer"))
    elif t == "minimal_pair":
        for s in e.get("sentences", []):
            out.add(s)
    elif t == "minimal_pair_say":
        for s in e.get("words", []) or []:
            out.add(s)
    elif t == "flashcard":
        out.add(for_card(e.get("front", "")))
        out.add(e.get("example"))
    elif t == "grammar_note":
        for ex in e.get("examples", []) or []:
            out.add(ex.get("it"))
    elif t == "reading":
        out.add(e.get("passage"))
    elif t == "roleplay":
        for turn in e.get("turns", []):
            out.add(turn.get("text"), "b" if turn.get("speaker") != "you" else "a")
    elif t == "free_write":
        out.add(e.get("modelAnswer"))


def collect_units(out: Lines):
    for f in sorted((ASSETS / "course" / "units").glob("*.json")):
        unit = json.loads(f.read_text(encoding="utf-8"))
        for lesson in unit.get("lessons", []):
            for e in lesson.get("exercises", []):
                collect_exercise(e, out)
        for tip in unit.get("grammarTips", []):
            ex = tip.get("example") or {}
            out.add(ex.get("it"))


def expand(text, slots):
    """Every way the text can be spoken once its {slot|default} placeholders are filled."""
    names = []
    for m in SLOT.finditer(text):
        if m.group(1) not in names:
            names.append(m.group(1))
    if not names:
        return [text]
    domains = []
    for n in names:
        d = SLOT.search(text)
        defaults = [m.group(2) for m in SLOT.finditer(text) if m.group(1) == n and m.group(2) is not None]
        domains.append(list(slots.get(n, [])) + defaults[:1] + ([""] if not defaults else []))
    results = set()
    for combo in itertools.islice(itertools.product(*domains), 200):
        values = dict(zip(names, combo))
        filled = SLOT.sub(lambda m: values.get(m.group(1), m.group(2) or ""), text)
        results.add(" ".join(filled.split()))
    return sorted(results)


def collect_conversations(out: Lines):
    for f in sorted((ASSETS / "conversations").glob("*.json")):
        sc = json.loads(f.read_text(encoding="utf-8"))
        slots = sc.get("slots", {})
        for st in sc.get("states", []):
            for line in st.get("say", []) + st.get("reprompt", []) + [m for b in st.get("expect", []) for m in b.get("reply", [])]:
                for filled in expand(line, slots):
                    out.add(filled, "b")
            if st.get("model"):
                out.add(st["model"], "a")
        for ph in sc.get("phrases", []):
            out.add(ph.get("it"), "a")


def collect_oral(out: Lines):
    for f in sorted((ASSETS / "oral").glob("*.json")):
        data = json.loads(f.read_text(encoding="utf-8"))

        def walk(o, examiner=False):
            if isinstance(o, dict):
                for k, v in o.items():
                    walk(v, examiner or k in ("examiner", "followUps", "greeting", "closing"))
            elif isinstance(o, list):
                for v in o:
                    walk(v, examiner)
            elif isinstance(o, str) and examiner:
                out.add(o, "b")

        walk(data)


def encode(samples, sr) -> bytes:
    import os
    import soundfile as sf
    import tempfile

    wav = io.BytesIO()
    sf.write(wav, samples, sr, format="WAV")
    # MP4 output needs a seekable file, so ffmpeg writes to a temporary one.
    with tempfile.TemporaryDirectory() as tmp:
        target = os.path.join(tmp, "line.m4a")
        subprocess.run(
            ["ffmpeg", "-loglevel", "error", "-y", "-i", "pipe:0", "-c:a", "aac", "-b:a", "32k", "-ac", "1", target],
            input=wav.getvalue(), capture_output=True, check=True,
        )
        with open(target, "rb") as f:
            return f.read()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="kokoro-v1.0.onnx")
    ap.add_argument("--voices", default="voices-v1.0.bin")
    ap.add_argument("--out", default="build/voicepack")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--zip", action="store_true")
    args = ap.parse_args()

    lines = Lines()
    collect_units(lines)
    collect_conversations(lines)
    collect_oral(lines)
    items = sorted(lines.items.items())
    chars = sum(len(t) for _, (t, _) in items)
    print(f"{len(items)} lines, {chars} characters", file=sys.stderr)
    if args.list:
        return
    if args.limit:
        items = items[: args.limit]

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    manifest = out / "manifest.json"
    try:
        old_tuning = json.loads(manifest.read_text()).get("tuning")
    except (OSError, ValueError):
        old_tuning = None
    if old_tuning != TUNING:
        for f in out.glob("*.m4a"):
            f.unlink()
        print("voice tuning changed: re-rendering everything", file=sys.stderr)
    todo = [(k, t, v) for k, (t, v) in items if not (out / f"{k}.m4a").exists()]
    print(f"{len(todo)} to render", file=sys.stderr)
    if todo:
        from kokoro_onnx import Kokoro

        kokoro = Kokoro(args.model, args.voices)
        styles = {
            role: (sum(kokoro.get_voice_style(name) * weight for name, weight in mix.items()), speed)
            for role, (mix, speed) in VOICES.items()
        }
        for i, (k, text, voice) in enumerate(todo, 1):
            try:
                style, speed = styles[voice]
                samples, sr = kokoro.create(text, voice=style, speed=speed, lang="it")
                (out / f"{k}.m4a").write_bytes(encode(samples, sr))
            except Exception as ex:  # one bad line must not stop the pack
                print(f"skipped {text!r}: {ex}", file=sys.stderr)
            if i % 100 == 0:
                print(f"{i}/{len(todo)}", file=sys.stderr)
    files = sorted(out.glob("*.m4a"))
    (out / "manifest.json").write_text(json.dumps({"count": len(files), "tuning": TUNING}))
    if args.zip:
        zpath = out.parent / "voicepack.zip"
        with zipfile.ZipFile(zpath, "w", zipfile.ZIP_STORED) as z:
            for f in files:
                z.write(f, f.name)
        print(f"{zpath}: {zpath.stat().st_size / 1e6:.1f} MB, {len(files)} files", file=sys.stderr)


if __name__ == "__main__":
    main()
