#!/usr/bin/env python3
"""Build app/src/main/assets/dictionary/dictionary.db (see docs/reference-and-dictionary-format.md).

Usage (see README.md):
    python -I tools/dictionary/build.py --wiktionary <it-en-wiktionary.tsv> [--out <db>] [--lemmas N]

Inputs
  * Italian-English Wiktionary TSV (Vuizur/Wiktionary-Dictionaries, CC BY-SA 3.0 / GFDL): glosses, POS,
    auxiliary annotations and the inflection list used to validate generated forms.
  * wordfreq (Python package) Italian frequency list: lemma ranking.
  * data/*.tsv and the tables in this file / italian_morph.py: hand-written morphology, genders,
    function words.
The third-party TSV is treated as untrusted data: it is only parsed as text, never executed.
"""
from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import os
import re
import sqlite3
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import italian_morph as M  # noqa: E402
from italian_morph import norm  # noqa: E402

DB_VERSION = "2"
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
DEFAULT_OUT = os.path.join(REPO, "app", "src", "main", "assets", "dictionary", "dictionary.db")

SOURCES = (
    "English glosses and part of speech: Wiktionary (en.wiktionary.org), via the Italian–English "
    "extract of Vuizur/Wiktionary-Dictionaries (built from kaikki.org/wiktextract data); "
    "licensed CC BY-SA 3.0 and GFDL. Changes: senses filtered, shortened and merged; forms regenerated. "
    "Word frequencies: wordfreq 3.1.1 by Robyn Speer (data CC BY-SA 4.0). "
    "Conjugation, plurals, genders and function-word charts: written for this app (rule-based "
    "with irregular tables, checked against Wiktionary inflection lists). "
    "This dictionary database is shared under CC BY-SA 3.0."
)

POS_MAP = {"noun": "noun", "verb": "verb", "adj": "adj", "adv": "adv", "pron": "pron", "prep": "prep",
           "conj": "conj", "det": "det", "article": "det", "intj": "interj", "num": "other",
           "particle": "other", "contraction": "other"}
POS_ABBR = {"noun": "n.", "verb": "v.", "adj": "adj.", "adv": "adv.", "pron": "pron.", "prep": "prep.",
            "conj": "conj.", "det": "det.", "interj": "interj.", "other": ""}

WORD_RE = re.compile(r"^[a-zàáèéìíòóùú]+(?:['’][a-zàèéìòù]*)?$")
FORMOF_RE = re.compile(
    r"\b(alternative|obsolete|archaic|dated|apocopic|apheretic|elided|plural|feminine|masculine|singular|"
    r"superlative|comparative|diminutive|augmentative|pejorative|misspelling|variant|nonstandard|"
    r"contraction|abbreviation|initialism|clipping|syncopated|truncation|eye dialect|participle|"
    r"person|imperative|gerund|infinitive|indicative|subjunctive|conditional)\b[^.;]*\b(form|of|spelling)\b",
    re.I)
MARGINAL_RE = re.compile(r"^\((?:[^)]*\b)?(archaic|obsolete|dialectal|regional|literary|poetic|rare|"
                         r"Tuscany|Southern Italy|Northern Italy|Rome|Sicily|Naples|historical|dated)\b", re.I)


# ---------------------------------------------------------------------------------------------
# Wiktionary TSV
# ---------------------------------------------------------------------------------------------

def parse_wiktionary(path):
    """headword -> {"forms": set(norm forms), "raw_forms": set, "blocks": [(pos, [senses])], "aux": set}"""
    entries = {}
    block_re = re.compile(r"<i>([^<]+)</i><br><ol>(.*?)</ol>", re.S)
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if "\t" not in line:
                continue
            heads, body = line.rstrip("\n").split("\t", 1)
            parts = heads.split("|")
            head = parts[0].strip()
            if not head or head != head.lower() or not WORD_RE.match(head):
                continue
            e = entries.setdefault(head, dict(forms=set(), raw_forms=set(), blocks=[], aux=set()))
            for f in parts[1:]:
                f = f.strip().lower()
                if f and len(f) < 40 and re.match(r"^[a-zàáèéìíòóùú' ’]+$", f):
                    e["raw_forms"].add(f)
                    if " " not in f:
                        e["forms"].add(norm(f))
            for pos, ol in block_re.findall(body):
                senses = []
                for li in re.findall(r"<li>(.*?)</li>", ol, re.S):
                    t = html.unescape(re.sub(r"<[^>]+>", "", li)).strip()
                    for a in re.findall(r"\[auxiliary ([^\]|]*)\]", t):
                        if "essere" in a and "avere" in a:
                            e["aux"].add("both")
                        elif "essere" in a:
                            e["aux"].add("essere")
                        elif "avere" in a:
                            e["aux"].add("avere")
                    senses.append(t)
                e["blocks"].append((pos.strip(), senses))
    return entries


def clean_sense(s):
    s = re.sub(r"\[[^\]]*\]", "", s)                 # [auxiliary essere], [+ a (person)]
    s = re.sub(r"\(see above\)|See Category:[^.;]*[.;]?", "", s)
    s = s.replace(";", ",")
    s = re.sub(r"\s+", " ", s).strip(" ,.:")
    if len(s) > 110:
        cut = s[:110]
        cut = cut[: max(cut.rfind(","), cut.rfind(" "))]
        s = cut.rstrip(" ,(") + "…"
    if s.count("(") > s.count(")"):
        s += ")"
    return s


LETTER_RE = re.compile(r"(name of the .*letter|letter of the .*alphabet|musical note|^The \w+ letter)", re.I)


def good_senses(senses, limit):
    """Clean senses; drop form-of senses and letter names. If only archaic/dialectal/literary senses
    remain, the block is dropped (returns [])."""
    out, marginal = [], []
    for s in senses:
        if LETTER_RE.search(s):
            continue
        m = re.match(r"^(?:\([^)]*\)\s*)?(?:Clipping|Abbreviation|Short|Shortening|Ellipsis) (?:form )?of [^:(]+(?::\s*(.+)|\(“([^”]+)”\))", s)
        if m:
            s = m.group(1) or m.group(2)
        elif FORMOF_RE.search(s) or re.match(r"^(Used|Forms|Form) (to|part|of)", s) and len(s) > 60:
            continue
        c = clean_sense(s)
        if not c or len(c) < 2:
            continue
        (marginal if MARGINAL_RE.match(c) else out).append(c)
    seen, res = set(), []
    for c in out:
        if c.lower() not in seen:
            seen.add(c.lower())
            res.append(c)
    return res[:limit]


# ---------------------------------------------------------------------------------------------
# Hand-written data
# ---------------------------------------------------------------------------------------------

def load_tsv(name):
    rows = []
    p = os.path.join(HERE, "data", name)
    with open(p, encoding="utf-8") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            rows.append(line.split("\t"))
    return rows


IRREG_PLURAL = {
    # singular: (plural display, plural gender or None, note)
    "uomo": ("uomini", None), "uovo": ("uova", "f"), "dito": ("dita", "f"), "braccio": ("braccia", "f"),
    "labbro": ("labbra", "f"), "ginocchio": ("ginocchia", "f"), "osso": ("ossa", "f"), "lenzuolo": ("lenzuola", "f"),
    "paio": ("paia", "f"), "centinaio": ("centinaia", "f"), "migliaio": ("migliaia", "f"), "miglio": ("miglia", "f"),
    "grido": ("grida", "f"), "ciglio": ("ciglia", "f"), "sopracciglio": ("sopracciglia", "f"),
    "orecchio": ("orecchie", "f"), "dio": ("dei", None), "tempio": ("templi", None), "bue": ("buoi", None),
    "mano": ("mani", None), "eco": ("echi", "m"), "ala": ("ali", None), "arma": ("armi", None),
    "moglie": ("mogli", None), "mille": ("mila", None), "frutto": ("frutti / frutta", None),
    "muro": ("muri / mura (f, città)", None), "membro": ("membri", None), "corno": ("corna", "f"),
    "riso": ("risi / risa (f, risate)", None), "fondamento": ("fondamenti / fondamenta (f)", None),
    "gesto": ("gesti", None), "filo": ("fili", None), "urlo": ("urli / urla (f)", None),
    "amico": ("amici", None), "nemico": ("nemici", None), "porco": ("porci", None), "greco": ("greci", None),
    "medico": ("medici", None), "mago": ("maghi", None), "belga": ("belgi", None), "collega": ("colleghi", None),
    "albergo": ("alberghi", None), "dialogo": ("dialoghi", None), "catalogo": ("cataloghi", None),
    "psicologo": ("psicologi", None), "biologo": ("biologi", None), "chirurgo": ("chirurghi", None),
    "stomaco": ("stomaci / stomachi", None), "carico": ("carichi", None), "valico": ("valichi", None),
    "incarico": ("incarichi", None), "obbligo": ("obblighi", None), "parroco": ("parroci", None),
    "sindaco": ("sindaci", None), "monaco": ("monaci", None), "manico": ("manici", None), "equivoco": ("equivoci", None),
    "uovo di pasqua": ("uova di pasqua", "f"), "re": ("re", None), "cinema": ("cinema", None),
    "vaglia": ("vaglia", None), "gorilla": ("gorilla", None), "boia": ("boia", None),
    "specie": ("specie", None), "serie": ("serie", None), "superficie": ("superfici", None),
    "pigiama": ("pigiami", None), "programma": ("programmi", None), "zio": ("zii", None),
    "addio": ("addii", None), "pendio": ("pendii", None), "leggio": ("leggii", None), "fruscio": ("fruscii", None),
    "brusio": ("brusii", None), "rinvio": ("rinvii", None), "invio": ("invii", None), "oblio": ("oblii", None),
}
F_IN_O = {"mano", "radio", "foto", "moto", "auto", "dinamo", "biro", "libido", "virago", "polio", "eco", "bici"}
FEM_OVERRIDE = {"dio": "dea", "re": "regina", "uomo": None, "padre": None, "fratello": None, "marito": None,
                "caso": None, "modo": None, "posto": None, "conto": None, "pezzo": None, "fatto": None,
                "tipo": None, "punto": None, "campo": None, "mondo": None, "corso": None, "porto": None,
                "volto": None, "colpo": None, "gesto": None, "senso": None, "torto": None, "testo": None}
INVARIABLE_O = {"radio", "foto", "moto", "auto", "dinamo", "biro", "libido", "euro", "video", "audio", "zero",
                "stereo", "disco", "cinema", "lavabo", "rododendro"} - {"disco"}

FEM_RULES = ("zione", "sione", "gione", "nione", "tudine", "udine", "igine", "trice", "ie", "tà", "tù")
MASC_RULES = ("ore", "ame", "ume", "ile", "one", "ale", "iere", "ere", "ente", "ante", "ese", "ere")


def guess_gender(lemma, wiki, gender_map):
    if lemma in gender_map:
        return gender_map[lemma]
    if lemma.endswith("o"):
        return "f" if lemma in F_IN_O else "m"
    if lemma.endswith("a") and len(lemma) > 2:
        stem = lemma[:-2] + ("ch" if lemma.endswith("ca") else "gh" if lemma.endswith("ga") else lemma[-2])
        mp = norm(stem + "i")
        fp = norm(stem + "e") if not lemma.endswith(("cia", "gia")) else norm(lemma[:-1] + "e")
        has_m, has_f = mp in wiki, fp in wiki
        if norm(lemma[:-1] + "o") in wiki:
            return "f"      # ragazza (ragazzo/ragazzi listed too): a feminine noun with a masculine pair
        if lemma.endswith(("cida", "iatra")) or has_m and has_f:
            return "mf"     # artista (artisti/artiste); lista, vista, rivista have only -e
        if lemma.endswith(("ma", "eta")) and has_m and not has_f:
            return "m"
        if has_m and not has_f:
            return "m"
        return "f"
    if lemma.endswith(("à", "tù")):
        return "f"
    if lemma.endswith(("è", "ì", "ò", "ù")):
        return "m"
    if lemma.endswith("i"):
        return "f" if lemma.endswith("si") else "m"
    if not M.is_vowel(lemma[-1]):
        return "m"
    if lemma.endswith("e"):
        if lemma.endswith(FEM_RULES):
            return "f"
        if lemma.endswith("ione") and not lemma.endswith(("zione", "sione", "gione")):
            return None   # milione (m) vs opinione (f): needs the manual list
        if lemma.endswith(("tore", "sore", "nore", "lore", "more", "dore", "rore", "pore")) and lemma not in gender_map:
            return "m"
        if lemma.endswith("one") and not lemma.endswith("ione"):
            return "m"
        if lemma.endswith(("ame", "ume", "ime")):
            return "m"
    return None


# ---------------------------------------------------------------------------------------------
# Morphology for one lemma
# ---------------------------------------------------------------------------------------------

def best_conjugation(inf, wiki):
    best, best_score = None, -1.0
    for i, cand in enumerate(M.candidates_for(inf)):
        try:
            c = M.conjugate(inf, **cand)
        except Exception:
            continue
        sf = {norm(x) for x in M.simple_forms(c)}
        score = len(sf & wiki) / max(1, len(sf)) if wiki else 0.0
        score -= i * 1e-6       # tie-break: earlier candidates (exact irregular, known prefix) win
        if cand.get("isc") or cand.get("pr_etti") or cand.get("keep_i"):
            score -= 0.02       # Wiktionary often lists both variants: prefer the plain pattern
        if score > best_score:
            best, best_score = (c, cand), score
    return best[0], best[1], max(best_score, 0.0)


def verb_entry(lemma, entries, aux_map, known_verbs):
    """Return (tables, pairs, notes, coverage) for a verb lemma (incl. pronominal ones)."""
    split = None if lemma.endswith(("are", "ere", "ire", "rre")) else M.split_pronominal(lemma, known_verbs)
    base, clitic = (split if split else (lemma, None))
    wiki = entries.get(base, {}).get("forms", set()) | entries.get(lemma, {}).get("forms", set())
    c, cand, cov = best_conjugation(base, wiki)
    notes = []
    if clitic and clitic in M.CLITIC_SUFFIXES and M.CLITIC_SUFFIXES[clitic][5]:
        aux, alt = "essere", None
    else:
        code = aux_map.get(lemma) or aux_map.get(base)
        waux = entries.get(lemma, {}).get("aux", set())
        if code is None:
            if waux == {"essere"}:
                code = "E"
            elif "both" in waux or waux == {"essere", "avere"}:
                code = "AE"
            else:
                code = "A"
        aux, alt = {"E": ("essere", None), "EA": ("essere", "avere"), "AE": ("avere", "essere"),
                    "A": ("avere", None)}[code]
    if c["irregular"]:
        notes.append("irregular")
    if cand.get("isc"):
        notes.append("-isc- verb")
    if alt == "essere":
        notes.append("aux: avere (transitive) / essere (intransitive)")
    elif alt == "avere":
        notes.append("aux: essere; avere when transitive")
    else:
        notes.append(f"aux: {aux}")
    tables, pairs = M.verb_tables(c, aux, alt, clitic)
    if not clitic:
        pairs += M.enclitic_pairs(c)
        pairs.append((c["inf"][:-1], "infinito, forma tronca"))
    else:
        notes.append(f"pronominal form of {base}")
    return tables, pairs, notes, cov, c


NOUN_HEADWORDS = set()   # filled in main(): nouns that have their own Wiktionary entry


def noun_entry(lemma, wiki, gender_map):
    g = guess_gender(lemma, wiki, gender_map)
    gg = g if g in ("m", "f") else "m"
    notes = []
    pairs = [(lemma, "singolare")]
    if lemma.endswith(("ore", "iere")) and len(lemma) > 4:
        pairs.append((lemma[:-1], "forma tronca (signor Rossi, dottor Bianchi)"))
    if lemma in IRREG_PLURAL:
        pl, plg = IRREG_PLURAL[lemma]
        if plg and plg != gg:
            notes.append(f"plural: {pl.split(' /')[0]} ({plg})")
        else:
            notes.append(f"plural: {pl}")
    else:
        plg = None
        cands = M.plural_candidates(lemma, gg)
        pl = cands[0]
        for cnd in cands:
            if norm(cnd) in wiki and cnd != lemma:
                pl = cnd
                break
        else:
            # Wiktionary lists no plural: invariable if the rules allow it
            if lemma in cands and (lemma in INVARIABLE_O or len(cands) > 1 and lemma.endswith(("a", "o"))
                                   and lemma not in wiki and False):
                pl = lemma
        if lemma in INVARIABLE_O or lemma.endswith(("ie",)) and lemma not in ("moglie",):
            pl = lemma
        if not (wiki - {norm(lemma)}):
            # Wiktionary lists no plural at all: invariable (cinema, foto, senzatetto) or plural-only
            pl = lemma
        if pl == lemma:
            notes.append("invariable")
    for p in [x.strip() for x in re.split(r"/", pl)]:
        p = re.sub(r"\s*\(.*\)", "", p).strip()
        if p and p != lemma:
            pairs.append((p, "plurale"))
    rows = []
    gl = {"m": "maschile", "f": "femminile", "mf": "maschile e femminile"}.get(g, "")

    def art(w, gender, plural):
        if g == "mf" and not plural:
            m_art, f_art = M.article(w, "m", False), M.article(w, "f", False)
            if m_art == f_art:
                return m_art                          # l'artista
            return m_art.split(" ")[0] + " / " + f_art   # il / la cantante
        return M.article(w, gender, plural)

    def pl_disp(pl_, gender):
        out = []
        for p in pl_.split(" / "):
            m = re.match(r"^(\S+)\s*(\(.*\))?$", p.strip())
            if not m:
                out.append(p)
                continue
            w, extra = m.group(1), m.group(2) or ""
            gpl = gender
            if extra.startswith("(f"):
                gpl = "f"
            elif lemma in IRREG_PLURAL and IRREG_PLURAL[lemma][1] and p == pl_:
                gpl = IRREG_PLURAL[lemma][1]
            if g == "mf" and gpl != "f":
                if lemma.endswith("a") and w.endswith(("i", "hi")) and w != lemma:
                    fpl = lemma[:-1] + ("he" if lemma.endswith(("ca", "ga")) else "e")
                    out.append(M.article(w, "m", True) + " / " + M.article(fpl, "f", True))
                    pairs.append((fpl, "plurale femminile"))
                else:
                    out.append(M.article(w, "m", True).split(" ")[0] + " / " + M.article(w, "f", True) + (" " + extra if extra else ""))
            else:
                out.append(M.article(w, gpl, True) + (" " + extra if extra else ""))
        return " / ".join(out)

    if g:
        rows.append([gl, art(lemma, gg, False), pl_disp(pl, gg)])
    else:
        rows.append(["", lemma, pl])
    # feminine counterpart (ragazzo/ragazza, attore/attrice, studente/studentessa)
    fem = FEM_OVERRIDE.get(lemma)
    if g == "m" and lemma not in FEM_OVERRIDE:
        if lemma.endswith("o") and not lemma.endswith(("co", "go")) or lemma.endswith(("co", "go")):
            cand = lemma[:-1] + "a"
            if norm(cand) in wiki and norm(M.plural_candidates(cand, "f")[0]) in wiki:
                fem = cand
        elif lemma.endswith("tore") and norm(lemma[:-4] + "trice") in wiki:
            fem = lemma[:-4] + "trice"
        elif lemma.endswith("e") and norm(lemma[:-1] + "essa") in wiki:
            fem = lemma[:-1] + "essa"
    if fem and fem in NOUN_HEADWORDS and lemma not in FEM_OVERRIDE:
        fem = None   # ragazza, arancia, amica have their own entries: do not merge them here
    if fem:
        fpl = M.plural_candidates(fem, "f")
        fp = next((x for x in fpl if norm(x) in wiki), fpl[0])
        rows.append(["femminile", M.article(fem, "f", False), M.article(fp, "f", True)])
        pairs += [(fem, "femminile singolare"), (fp, "femminile plurale")]
    tables = [dict(title="Forme", columns=["", "singolare", "plurale"], rows=rows)]
    return tables, pairs, notes, g


def adj_entry(lemma, wiki):
    f = M.adj_forms(lemma, wiki)
    pairs, notes = [], []
    tags = dict(ms="maschile singolare", fs="femminile singolare", mp="maschile plurale", fp="femminile plurale")
    invariable = lemma in INVARIABLE_ADJ or lemma.endswith(M.ACCENTED_END) or not M.is_vowel(lemma[-1])
    if invariable:
        f = dict(ms=lemma, fs=lemma, mp=lemma, fp=lemma)
        notes.append("invariable")
    if lemma in POSSESSIVE:
        f = POSSESSIVE[lemma]
    merged = defaultdict(list)
    for k in ("ms", "fs", "mp", "fp"):
        merged[f[k]].append(tags[k])
    for w, t in merged.items():
        pairs.append((w, ", ".join(t)))
    if f["ms"] == f["fs"]:
        rows = [["maschile e femminile", f["ms"], f["mp"]]] if f["mp"] == f["fp"] else \
            [["maschile", f["ms"], f["mp"]], ["femminile", f["fs"], f["fp"]]]
    else:
        rows = [["maschile", f["ms"], f["mp"]], ["femminile", f["fs"], f["fp"]]]
    for label, sg, pl in M.SPECIAL_ADJ.get(lemma, []):
        rows.append([label, sg, pl or "—"])
        for w in re.split(r",\s*", sg) + ([pl] if pl else []):
            pairs.append((w, f"forma breve ({label})"))
    tables = [dict(title="Forme", columns=["", "singolare", "plurale"], rows=rows)]
    grad = []
    if lemma in M.COMPARATIVES:
        comp, sup, sup2 = M.COMPARATIVES[lemma]
        grad = [["comparativo", f"più {lemma} / {comp}"], ["superlativo relativo", f"il più {lemma} / il {comp}"],
                ["superlativo assoluto", f"{sup2 + ' / ' if sup2 else ''}{sup}"]]
        pairs += [(comp, "comparativo"), (comp[:-1] + "i", "comparativo, plurale")]
        if comp.endswith("ore"):
            pairs.append((comp[:-1], "comparativo, forma tronca"))
        for s in sup.split(" / "):
            pairs.append((s, "superlativo assoluto"))
            if s.endswith("o"):
                pairs += [(s[:-1] + x, "superlativo assoluto") for x in "aie"]
        notes.append(f"comparative: {comp}; superlative: {sup}")
    elif not invariable and lemma not in POSSESSIVE:
        mp = f["mp"]
        sup = mp[:-1] + "issimo" if mp.endswith("i") else None
        if sup and norm(sup) in wiki:
            grad = [["comparativo", f"più {lemma}"], ["superlativo assoluto", sup]]
    if grad:
        sup_words = [r[1] for r in grad if r[0] == "superlativo assoluto"]
        for s in sup_words:
            for w in s.split(" / "):
                if w.endswith("issimo"):
                    pairs += [(w[:-1] + x, "superlativo assoluto") for x in "oaie"]
        tables.append(dict(title="Gradi", columns=["", ""], rows=grad))
    return tables, pairs, notes


DEFECTIVE = {"solere", "garbare", "vigere", "urgere", "ostare"}
NUM_NOUNS = {"milione", "miliardo", "mille", "paio", "centinaio", "migliaio", "bilione"}
POS_PRIMARY = {"specie": "noun", "gente": "noun", "ci": "pron", "ti": "pron", "vi": "pron", "mi": "pron", "bene": "adv", "male": "adv", "molto": "adv", "poco": "adv", "tanto": "adv", "troppo": "adv",
               "medico": "noun", "amico": "noun", "nemico": "noun", "italiano": "adj", "giovane": "adj",
               "uno": "det", "che": "pron", "come": "adv"}
ADJ_LIKE = {"questo", "quello", "codesto", "alcuno", "nessuno", "ciascuno", "tutto", "altro", "molto", "poco",
            "tanto", "troppo", "certo", "stesso", "medesimo", "quale", "tale", "quanto", "parecchio", "vario",
            "diverso", "ultimo", "primo", "secondo", "terzo", "proprio", "altrui", "entrambi", "ambedue"}
INVARIABLE_ADJ = {"blu", "rosa", "viola", "pari", "dispari", "beige", "ogni", "qualche", "qualsiasi", "qualunque",
                  "super", "extra", "chic", "standard", "antinebbia", "perbene", "dappoco", "arrosto", "lilla",
                  "ocra", "indaco", "magenta", "fucsia", "nord", "sud", "est", "ovest", "anti", "loro"}
POSSESSIVE = {
    "mio": dict(ms="mio", fs="mia", mp="miei", fp="mie"), "tuo": dict(ms="tuo", fs="tua", mp="tuoi", fp="tue"),
    "suo": dict(ms="suo", fs="sua", mp="suoi", fp="sue"),
    "nostro": dict(ms="nostro", fs="nostra", mp="nostri", fp="nostre"),
    "vostro": dict(ms="vostro", fs="vostra", mp="vostri", fp="vostre"),
    "loro": dict(ms="loro", fs="loro", mp="loro", fp="loro"),
}


# ---------------------------------------------------------------------------------------------
# Function words (articles, prepositions, pronouns): hand-written charts
# ---------------------------------------------------------------------------------------------

# Hand-written glosses where the Wiktionary sense list is unhelpful for a learner (meta-linguistic
# explanations, a rare sense listed first).
GLOSS_OVERRIDE = {
    "essere": "to be ; to exist ; (auxiliary) forms compound tenses of intransitive and reflexive verbs (sono andato, mi sono alzato) and the passive",
    "avere": "to have ; (auxiliary) forms compound tenses of transitive and many intransitive verbs (ho mangiato) ; to get, receive",
    "stare": "to stay, remain ; to be (located; of health: come stai?) ; (stare + gerund) to be doing something ; (stare per) to be about to",
    "ci": "us, to us ; ourselves, each other ; there, here (ci vado = I'm going there) ; about it, of it (ci penso io) ; c'è / ci sono = there is / there are",
    "ti": "you, to you (informal object pronoun) ; yourself",
    "mi": "me, to me ; myself",
    "vi": "you, to you (plural object pronoun) ; yourselves ; there (formal)",
    "gente": "people ; folk ; (literary) nation, family",
    "gli": "the (masc. pl. before vowel, s+consonant, z…) ; to him ; (colloquial) to them",
    "lo": "the (masc. sing. before s+consonant, z…) ; him, it",
    "la": "the (fem. sing.) ; her, it ; (La) you (formal)",
    "le": "the (fem. pl.) ; them (fem.) ; to her ; (Le) to you (formal)",
    "i": "the (masc. pl.)",
    "potere": "can, to be able to ; may, to be allowed to ; (n.) power",
    "dovere": "must, to have to ; to owe ; (n.) duty",
    "volere": "to want ; (volerci) to take, be needed ; (n.) will",
}
ART_PREP = {"di": "de", "a": "a", "da": "da", "in": "ne", "su": "su"}


def function_words():
    """Return {lemma: dict(pos, gloss, tables, pairs, notes)} hand-written entries/overrides."""
    out = {}
    for p, s in ART_PREP.items():
        rows = [["il / i", f"{s}l", f"{s}i"], ["lo / gli", f"{s}llo", f"{s}gli"], ["la / le", f"{s}lla", f"{s}lle"],
                ["l' (vocale)", f"{s}ll'", f"{s}gli / {s}lle"]]
        pairs = [(f"{s}l", f"{p} + il"), (f"{s}i", f"{p} + i"), (f"{s}llo", f"{p} + lo"), (f"{s}gli", f"{p} + gli"),
                 (f"{s}lla", f"{p} + la"), (f"{s}lle", f"{p} + le"), (f"{s}ll'", f"{p} + l'")]
        if p == "di":
            pairs.append(("d'", "di (davanti a vocale)"))
        if p == "a":
            pairs.append(("ad", "a (davanti a vocale)"))
        out[p] = dict(tables=[dict(title="Preposizione articolata", columns=["", "singolare", "plurale"], rows=rows)],
                      pairs=pairs, notes=[])
    out["con"] = dict(tables=[dict(title="Preposizione articolata", columns=["", "singolare", "plurale"],
                                   rows=[["il / i", "col (con il)", "coi (con i)"], ["altre", "con lo, con la…", "con gli, con le"]])],
                      pairs=[("col", "con + il"), ("coi", "con + i")], notes=[])
    out["il"] = dict(pos="det", gloss="the (definite article)", tables=[dict(
        title="Articolo determinativo", columns=["", "singolare", "plurale"],
        rows=[["maschile", "il", "i"], ["maschile (s+cons., z, gn, ps, x, y)", "lo", "gli"],
              ["maschile (vocale)", "l'", "gli"], ["femminile", "la", "le"], ["femminile (vocale)", "l'", "le"]])],
        pairs=[("il", "articolo m. sing."), ("lo", "articolo m. sing."), ("l'", "articolo davanti a vocale"),
               ("la", "articolo f. sing."), ("i", "articolo m. pl."), ("gli", "articolo m. pl."), ("le", "articolo f. pl.")],
        notes=[])
    out["uno"] = dict(tables=[dict(title="Articolo indeterminativo", columns=["", "singolare"],
                                   rows=[["maschile", "un"], ["maschile (s+cons., z, gn, ps, x, y)", "uno"],
                                         ["femminile", "una"], ["femminile (vocale)", "un'"],
                                         ["plurale (partitivo)", "dei, degli, delle"]])],
                      pairs=[("un", "articolo m."), ("uno", "articolo m."), ("una", "articolo f."), ("un'", "articolo f. davanti a vocale")],
                      notes=[])
    pers = [["io", "me", "mi", "mi"], ["tu", "te", "ti", "ti"], ["lui", "lui", "lo", "gli"], ["lei", "lei", "la", "le"],
            ["Lei (formale)", "Lei", "La", "Le"], ["noi", "noi", "ci", "ci"], ["voi", "voi", "vi", "vi"],
            ["loro", "loro", "li / le", "gli (loro)"], ["riflessivo", "sé", "si", "si"]]
    ptab = dict(title="Pronomi personali", columns=["soggetto", "tonico", "diretto", "indiretto"], rows=pers)
    pron_forms = {"io": [("me", "pronome tonico"), ("mi", "pronome atono (diretto / indiretto)"), ("me", "davanti a lo/la/li/le/ne")],
                  "tu": [("te", "pronome tonico"), ("ti", "pronome atono (diretto / indiretto)")],
                  "lui": [("lo", "pronome diretto"), ("gli", "pronome indiretto"), ("egli", "soggetto (formale)"), ("esso", "soggetto (cosa)")],
                  "lei": [("la", "pronome diretto"), ("le", "pronome indiretto"), ("ella", "soggetto (letterario)"), ("essa", "soggetto (cosa)")],
                  "noi": [("ci", "pronome atono (diretto / indiretto)"), ("ce", "davanti a lo/la/li/le/ne")],
                  "voi": [("vi", "pronome atono (diretto / indiretto)"), ("ve", "davanti a lo/la/li/le/ne")],
                  "loro": [("li", "pronome diretto m."), ("le", "pronome diretto f."), ("gli", "pronome indiretto (colloquiale)"),
                           ("essi", "soggetto m. (formale)"), ("esse", "soggetto f. (formale)")],
                  "sé": [("si", "pronome riflessivo"), ("se", "davanti a lo/la/li/le/ne")]}
    for k, prs in pron_forms.items():
        out[k] = dict(tables=[ptab], pairs=prs, notes=[])
    comb_rows = [["mi", "me lo", "me la", "me li", "me le"], ["ti", "te lo", "te la", "te li", "te le"],
                 ["gli / le", "glielo", "gliela", "glieli", "gliele"], ["ci", "ce lo", "ce la", "ce li", "ce le"],
                 ["vi", "ve lo", "ve la", "ve li", "ve le"], ["gli (loro)", "glielo", "gliela", "glieli", "gliele"],
                 ["si", "se lo", "se la", "se li", "se le"]]
    ne_rows = [["mi", "me ne"], ["ti", "te ne"], ["gli / le", "gliene"], ["ci", "ce ne"], ["vi", "ve ne"], ["si", "se ne"]]
    cpairs = []
    for r in comb_rows:
        for obj, w in zip(["lo", "la", "li", "le"], r[1:]):
            cpairs.append((w, f"{r[0]} + {obj}"))
    for r in ne_rows:
        cpairs.append((r[1], f"{r[0]} + ne"))
    out["glielo"] = dict(pos="pron", gloss="it to him, it to her, it to them (combined pronoun gli/le + lo)",
                         tables=[dict(title="Pronomi combinati", columns=["", "+ lo", "+ la", "+ li", "+ le"], rows=comb_rows),
                                 dict(title="Pronomi combinati con ne", columns=["", "+ ne"], rows=ne_rows)],
                         pairs=cpairs, notes=["gli/le + lo/la/li/le/ne → glie-: glielo, gliela, glieli, gliele, gliene"])
    out["ci"] = dict(tables=[], pairs=[("ci", "pronome (noi) / avverbio di luogo / ci + verbo"), ("ce", "davanti a lo/la/li/le/ne"),
                                       ("c'", "davanti a e/i: c'è, c'era")],
                     notes=["Also: 'there' (ci vado), 'about/of it' (ci penso), c'è / ci sono = there is / there are"])
    out["ne"] = dict(tables=[], pairs=[("ne", "pronome partitivo / avverbio"), ("n'", "davanti a vocale")],
                     notes=["Replaces di/da + noun: ne voglio due = I want two of them; me ne vado = I'm leaving"])
    extra = {"essere": [("son", "indicativo presente, io/loro (forma tronca)")],
             "volere": [("vuol", "indicativo presente, lui/lei (forma tronca: vuol dire)")],
             "bene": [("ben", "forma tronca"), ("meglio", "comparativo"), ("benissimo", "superlativo")],
             "male": [("mal", "forma tronca"), ("peggio", "comparativo"), ("malissimo", "superlativo")],
             "molto": [("moltissimo", "superlativo")], "poco": [("pochissimo", "superlativo"), ("po'", "forma tronca (un po')")],
             "pure": [("pur", "forma tronca")], "fino": [("fin", "forma tronca")], "ora": [("or", "forma tronca")],
             "e": [("ed", "davanti a vocale")], "entrambi": [("entrambe", "femminile")],
             "mi": [("m'", "davanti a vocale")], "ti": [("t'", "davanti a vocale")], "si": [("s'", "davanti a vocale")],
             "vi": [("v'", "davanti a vocale")], "lo": [("l'", "davanti a vocale")], "la": [("l'", "davanti a vocale")], "o": [("od", "davanti a vocale")], "signore": [("sig.", "abbreviazione")]}
    for k, prs in extra.items():
        out[k] = dict(tables=None, pairs=prs, notes=[])
    for k, g in GLOSS_OVERRIDE.items():
        out.setdefault(k, dict(tables=None, pairs=[], notes=[]))
        out[k]["gloss"] = g
    out["questo"] = dict(tables=None, pairs=[("quest'", "davanti a vocale")], notes=[])
    out["quello"] = dict(tables=None, pairs=[], notes=[])
    out["po'"] = None
    for k in ("bene", "male"):
        comp, sup, _ = M.COMPARATIVES[k]
        out[k]["tables"] = [dict(title="Gradi", columns=["", ""], rows=[["comparativo", comp], ["superlativo", sup]])]
    return out


# ---------------------------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--wiktionary", required=True)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--lemmas", type=int, default=60000, help="maximum number of lemmas (default: every usable headword)")
    ap.add_argument("--enclitic-top", type=int, default=800,
                    help="verbs (by rank) that also get infinitive/gerund/imperative + clitic lookup forms")
    ap.add_argument("--report", default=None, help="write a TSV of selected lemmas and verb coverage here")
    args = ap.parse_args()

    import wordfreq  # noqa: imported here so --help works without it
    # frequencies keyed by the exact lowercase form (accents matter: e/è, da/dà, si/sì);
    # elided tokens such as "dell'attore" count for both parts ("dell'" and "attore")
    freq = defaultdict(float)
    for w in wordfreq.top_n_list("it", 400000):
        fr = wordfreq.word_frequency(w, "it")
        w = w.replace("’", "'")
        if "'" in w:
            a, _, b = w.partition("'")
            freq[a + "'"] += fr
            if b:
                freq[b] += fr
        else:
            freq[w] += fr
    nfreq = defaultdict(float)
    for w, fr in freq.items():
        nfreq[norm(w)] += fr

    entries = parse_wiktionary(args.wiktionary)
    print(f"wiktionary headwords: {len(entries)}", file=sys.stderr)
    aux_map = {r[0]: r[1] for r in load_tsv("aux_essere.tsv")}
    gender_map = {r[0]: r[1] for r in load_tsv("noun_gender.tsv")} if os.path.exists(
        os.path.join(HERE, "data", "noun_gender.tsv")) else {}
    skip = {r[0] for r in load_tsv("skip.tsv")} if os.path.exists(os.path.join(HERE, "data", "skip.tsv")) else set()
    fw = function_words()

    known_verbs = {h for h, e in entries.items() if any(p == "verb" for p, _ in e["blocks"])
                   and h.endswith(("are", "ere", "ire", "rre"))}

    # ---- candidate lemmas -----------------------------------------------------------------
    cands = []
    for h, e in entries.items():
        if h in skip or ("'" in h and h not in fw):
            continue
        blocks = []
        for pos, senses in e["blocks"]:
            P = POS_MAP.get(pos)
            if not P:
                continue
            gs = good_senses(senses, 4)
            if gs:
                blocks.append((P, gs))
        if not blocks and h not in fw:
            continue
        if not blocks:
            blocks = [("other", [])]
        # merge duplicate POS blocks
        merged = {}
        for P, gs in blocks:
            merged.setdefault(P, [])
            for g in gs:
                if g not in merged[P]:
                    merged[P].append(g)
        blocks = list(merged.items())
        if h in POS_PRIMARY:
            blocks.sort(key=lambda b: b[0] != POS_PRIMARY[h])
        elif blocks[0][0] in ("adv", "interj", "other") and any(
                b[0] == "noun" and len(b[1]) > len(blocks[0][1]) for b in blocks):
            blocks.sort(key=lambda b: b[0] != "noun")       # valore: a stray adverb sense listed first
        pos = blocks[0][0]
        if pos == "verb" and not (h.endswith(("are", "ere", "ire", "rre")) or M.split_pronominal(h, known_verbs)):
            blocks = [b for b in blocks if b[0] != "verb"]
            if not blocks:
                continue
            pos = blocks[0][0]
        if any(p == "verb" for p, _ in blocks) and (h.endswith(("are", "ere", "ire", "rre")) or M.split_pronominal(h, known_verbs)):
            blocks.sort(key=lambda b: b[0] != "verb")
            pos = "verb"
        # cheap prefilter: the lemma or one of its listed forms must occur in the frequency list
        nf = norm(h)
        if not (nfreq.get(nf) or any(nfreq.get(f) for f in e["forms"])) and not h.endswith("rsi") and not h.endswith("rsene"):
            continue
        cands.append((h, pos, blocks))
    print(f"candidates: {len(cands)}", file=sys.stderr)

    # ---- morphology -----------------------------------------------------------------------
    NOUN_HEADWORDS.update(h for h, pos, blocks in cands if any(b[0] == "noun" for b in blocks))
    built = []
    for h, pos, blocks in cands:
        e = entries[h]
        wiki = e["forms"] | {norm(h)}
        notes, gender, cov, conj = [], None, None, None
        try:
            if pos == "verb":
                tables, pairs, notes, cov, conj = verb_entry(h, entries, aux_map, known_verbs)
            elif pos == "noun":
                tables, pairs, notes, gender = noun_entry(h, wiki, gender_map)
            elif pos == "other" and h in NUM_NOUNS:
                tables, pairs, notes, gender = noun_entry(h, wiki, {h: "m"})
            elif pos == "adj" or (pos in ("det", "pron", "adv") and (h in POSSESSIVE or h in ADJ_LIKE)):
                tables, pairs, notes = adj_entry(h, wiki)
            else:
                tables, pairs = [], []
        except Exception as ex:  # pragma: no cover - reported, entry skipped
            print(f"skip {h}: {ex}", file=sys.stderr)
            continue
        # secondary noun table for verbs used as nouns (il potere / i poteri)
        if pos == "verb" and any(p == "noun" for p, _ in blocks) and h.endswith(("are", "ere", "ire", "rre")):
            nt, npairs, _, _ = noun_entry(h, wiki, {h: "m"})
            nt[0]["title"] = "Sostantivo"
            tables = tables + nt
            pairs = pairs + [(w, "sostantivo, " + t) for w, t in npairs]
        if pos == "noun" and any(p == "adj" for p, _ in blocks) and not h.endswith(("e",)):
            at, apairs, _ = adj_entry(h, wiki)
            at[0]["title"] = "Aggettivo"
            tables = tables + at[:1]
            pairs = pairs + [(w, "aggettivo, " + t) for w, t in apairs]
        if h in fw and fw[h] is not None:
            f = fw[h]
            if f.get("tables") is not None:
                # hand-written charts replace a generated "Forme" table
                tables = f["tables"] + [t for t in tables if t["title"] != "Forme"]
            pairs = pairs + f["pairs"]
            notes = notes + f["notes"]
            if f.get("pos"):
                pos = f["pos"]
        pairs.append((h, "lemma"))
        built.append(dict(lemma=h, pos=pos, blocks=blocks, gender=gender, tables=tables, pairs=pairs,
                          notes=notes, cov=cov))

    # function words absent from Wiktionary as headwords (or filtered out): add them
    have = {b["lemma"] for b in built}
    for k, f in fw.items():
        if f is None or k in have or not f.get("gloss"):
            continue
        built.append(dict(lemma=k, pos=f.get("pos", "other"), blocks=[(f.get("pos", "other"), [f["gloss"]])],
                          gender=None, tables=f["tables"] or [], pairs=f["pairs"] + [(k, "lemma")],
                          notes=f["notes"], cov=None))
    for b in built:
        if b["lemma"] in fw and fw[b["lemma"]] and fw[b["lemma"]].get("gloss"):
            keep = [x for x in b["blocks"] if x[0] != b["pos"]] if b["lemma"] not in GLOSS_OVERRIDE else []
            b["blocks"] = [(b["pos"], [fw[b["lemma"]]["gloss"]])] + keep

    # ---- frequency scoring ---------------------------------------------------------------
    # A form shared by several lemmas (stato: essere/stare/stato n.) is split between them in
    # proportion to their current scores (a few EM-style iterations, starting from the frequency
    # of the headword itself).
    claim = defaultdict(list)
    core = []
    for i, b in enumerate(built):
        fs = {w.lower() for w, t in b["pairs"] if " " not in w and not ENCL_TAG.match(t)}
        core.append(fs)
        for f in fs:
            claim[f].append(i)
    # prior = frequency of the headword itself + forms no other lemma claims; a single split pass
    # (iterating would let the lemma with more forms swallow shared ones: arancio vs arancia)
    w0 = []
    for b, fs in zip(built, core):
        own = freq.get(b["lemma"], 0.0) if len(claim.get(b["lemma"], [])) <= 1 else freq.get(b["lemma"], 0.0) * 0.5
        excl = own + sum(freq.get(f, 0.0) for f in fs if len(claim[f]) == 1 and f != b["lemma"])
        # no exclusive evidence at all (rare Wiktionary verbs like "ottare" whose forms are all
        # common words of other lemmas): give it no share
        w0.append(excl + 1e-9 if excl > 3e-8 or b["lemma"] in fw else 1e-15)
    w = [0.0] * len(built)
    for f, ids in claim.items():
        fr = freq.get(f, 0.0)
        if not fr:
            continue
        tot = sum(w0[i] for i in ids)
        for i in ids:
            w[i] += fr * w0[i] / tot
    for i, b in enumerate(built):
        # most of the evidence borrowed from other lemmas' forms (ottare <- otto): drop
        if w0[i] < 0.03 * w[i] and b["lemma"] not in fw:
            w[i] = 0.0
        # a verb whose infinitive never occurs in the corpus is a rare Wiktionary entry whose
        # forms collide with common words (ottare: otto, camerare: camera); pronominal lemmas and
        # defective verbs excepted
        if b["pos"] == "verb" and freq.get(b["lemma"], 0.0) < 1e-9 and b["lemma"] not in DEFECTIVE \
                and not M.split_pronominal(b["lemma"], known_verbs):
            w[i] = 0.0
    for b, sc in zip(built, w):
        b["score"] = sc
    # Pronominal verbs (alzarsi, andarsene, farcela): most of their forms are multi-word ("mi alzo")
    # and invisible to a unigram list, so their score (from alzarsi, alzandosi, alzatevi...) is
    # scaled up to approximate the frequency of the whole paradigm.
    for b in built:
        if b["pos"] == "verb" and M.split_pronominal(b["lemma"], known_verbs):
            b["score"] *= 8 if b["lemma"].endswith(("rsi", "rsene")) else 4
    built.sort(key=lambda b: -b["score"])
    selected = [b for b in built if b["score"] > 0][: args.lemmas]
    sel_names = {b["lemma"] for b in selected}
    # always keep hand-written function words
    for b in built:
        if b["lemma"] in fw and b["lemma"] not in sel_names and fw[b["lemma"]] is not None:
            selected.append(b)
    print(f"selected lemmas: {len(selected)} (min score {selected[args.lemmas - 1]['score'] if len(selected) >= args.lemmas else 0:.2e})",
          file=sys.stderr)

    # ---- write DB ------------------------------------------------------------------------
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    if os.path.exists(args.out):
        os.remove(args.out)
    db = sqlite3.connect(args.out)
    db.executescript("""
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE lemma (
  id INTEGER PRIMARY KEY, lemma TEXT NOT NULL, pos TEXT NOT NULL, gender TEXT, gloss_en TEXT NOT NULL,
  gloss_it TEXT, example_it TEXT, example_en TEXT, freq_rank INTEGER, notes TEXT, forms_json TEXT NOT NULL);
CREATE INDEX lemma_lemma ON lemma(lemma);
CREATE TABLE form (form TEXT NOT NULL, form_norm TEXT NOT NULL, lemma_id INTEGER NOT NULL REFERENCES lemma(id), tags TEXT NOT NULL);
CREATE INDEX form_norm_idx ON form(form_norm);
""")
    n_forms = 0
    report = []
    for rank, b in enumerate(selected, 1):
        lid = rank
        senses = []
        prim = b["blocks"][0]
        senses += prim[1][:4]
        for P, gs in b["blocks"][1:3]:
            ab = POS_ABBR.get(P, "")
            senses += [f"({ab}) {g}" if ab else g for g in gs[:2]]
        gloss = " ; ".join(senses) if senses else "—"
        notes = "; ".join(dict.fromkeys(b["notes"])) or None
        db.execute("INSERT INTO lemma VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                   (lid, b["lemma"], b["pos"], b["gender"], gloss, None, None, None, rank, notes,
                    json.dumps(dict(tables=b["tables"]), ensure_ascii=False, separators=(",", ":"))))
        tagmap = {}
        is_verb_top = b["pos"] == "verb" and rank <= args.enclitic_top
        for w, t in b["pairs"]:
            w = w.strip()
            if not w or (not is_verb_top and " + " in t and t.startswith(("infinito +", "gerundio +", "imperativo"))):
                continue
            tagmap.setdefault(w, [])
            if t not in tagmap[w]:
                tagmap[w].append(t)
        rows = []
        for w, ts in tagmap.items():
            rows.append((w, norm(w), lid, compact_tags(ts)))
        db.executemany("INSERT INTO form VALUES (?,?,?,?)", rows)
        n_forms += len(rows)
        report.append((rank, b["lemma"], b["pos"], b["gender"] or "", f"{b['cov']:.3f}" if b["cov"] is not None else "",
                       notes or ""))
    meta = dict(version=DB_VERSION, built_at=dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                sources=SOURCES, entry_count=str(len(selected)), form_count=str(n_forms))
    db.executemany("INSERT INTO meta VALUES (?,?)", list(meta.items()))
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"lemmas {len(selected)}, forms {n_forms}, size {os.path.getsize(args.out) / 1e6:.1f} MB", file=sys.stderr)
    if args.report:
        with open(args.report, "w", encoding="utf-8") as fh:
            for r in report:
                fh.write("\t".join(map(str, r)) + "\n")


ENCL_TAG = re.compile(r"^(infinito|gerundio|imperativo \w+) \+ ")


def compact_tags(ts):
    """Merge "congiuntivo presente, io" + "..., tu" into "congiuntivo presente, io/tu"."""
    groups = {}
    for t in ts:
        if ", " in t:
            head, tail = t.rsplit(", ", 1)
            if tail in M.PERSONS or tail in M.IMP_PERSONS:
                groups.setdefault(head, []).append(tail)
                continue
        groups.setdefault(t, [])
    out = []
    for head, persons in groups.items():
        out.append(f"{head}, {'/'.join(dict.fromkeys(persons))}" if persons else head)
    return "; ".join(out)


if __name__ == "__main__":
    main()
