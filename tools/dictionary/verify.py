#!/usr/bin/env python3
"""Spot-check dictionary.db: lookups of tricky inflected forms, irregular verb charts, auxiliaries,
reflexive verbs, noun and adjective forms. Expected values are written by hand here (independently
of the generator tables). Exit status 1 if any check fails.

    python3 tools/dictionary/verify.py [path/to/dictionary.db]
"""
import json
import os
import re
import sqlite3
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
DB = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "app", "src", "main", "assets",
                                                         "dictionary", "dictionary.db")


def norm(s):
    s = s.lower()
    s = "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")
    s = re.sub(r"['’‘ʼ`´]", "", s)
    return " ".join(s.split())


db = sqlite3.connect(DB)
fails, passes = [], 0


def check(ok, msg):
    global passes
    if ok:
        passes += 1
    else:
        fails.append(msg)


def lookup(q):
    """App algorithm: exact form_norm match, then lemma prefix; grouped by lemma, by freq_rank."""
    rows = db.execute("""SELECT l.lemma, l.pos, l.freq_rank, f.tags FROM form f JOIN lemma l ON l.id=f.lemma_id
                         WHERE f.form_norm=? ORDER BY l.freq_rank""", (norm(q),)).fetchall()
    if not rows:
        rows = db.execute("SELECT lemma, pos, freq_rank, '' FROM lemma WHERE lemma LIKE ? ORDER BY freq_rank LIMIT 20",
                          (norm(q) + "%",)).fetchall()
    out = []
    for r in rows:
        if r[0] not in [o[0] for o in out]:
            out.append(r)
    return out


def tables(lemma, pos=None):
    q = "SELECT forms_json, notes, gender, gloss_en FROM lemma WHERE lemma=?" + (" AND pos=?" if pos else "") + \
        " ORDER BY freq_rank LIMIT 1"
    r = db.execute(q, (lemma, pos) if pos else (lemma,)).fetchone()
    if not r:
        return None, None, None, None
    return {t["title"]: t for t in json.loads(r[0])["tables"]}, r[1] or "", r[2], r[3]


def cell(t, title, label, col=1):
    tb = t.get(title)
    if not tb:
        return None
    for row in tb["rows"]:
        if row[0] == label:
            return row[col]
    return None


# 1. inflected-form lookups ------------------------------------------------------------------
LOOKUPS = {
    "andò": "andare", "vadano": "andare", "vado": "andare", "andrebbe": "andare", "fossi": "essere",
    "siano": "essere", "è": "essere", "ebbe": "avere", "abbia": "avere", "tolto": "togliere", "tolgo": "togliere",
    "uova": "uovo", "belle": "bello", "bei": "bello", "migliore": "buono", "migliori": "buono",
    "glielo": "glielo", "gliene": "glielo", "ci": "ci", "ne": "ne", "dell'": "di", "nella": "in",
    "sulle": "su", "feci": "fare", "facendo": "fare", "dissero": "dire", "detto": "dire", "venne": "venire",
    "vengano": "venire", "stette": "stare", "diedi": "dare", "dia": "dare", "possa": "potere",
    "vorrei": "volere", "dobbiamo": "dovere", "debba": "dovere", "sappia": "sapere", "seppi": "sapere",
    "escono": "uscire", "rimasto": "rimanere", "bevve": "bere", "tengono": "tenere", "posto": "porre",
    "tratto": "trarre", "prodotto": "produrre", "ottenne": "ottenere", "chiesto": "chiedere",
    "presero": "prendere", "nacque": "nascere", "morto": "morire", "piacque": "piacere", "vissuto": "vivere",
    "visto": "vedere", "scelse": "scegliere", "finisco": "finire", "capiscono": "capire", "uomini": "uomo",
    "mani": "mano", "dita": "dito", "braccia": "braccio", "amici": "amico", "amiche": "amico", "problemi": "problema",
    "città": "città", "ragazze": "ragazza", "valori": "valore", "artiste": "artista", "dimmi": "dire", "fammi": "fare", "prenderlo": "prendere", "dandogli": "dare",
    "mi alzo": "alzarsi", "alzati": "alzarsi", "me ne vado": "andarsene", "vattene": "andarsene",
    "bianchi": "bianco", "pratici": "pratico", "ottima": "buono", "peggiore": "cattivo", "del": "di",
    "gli": "il", "lo": "il", "mie": "mio", "suoi": "suo", "quegli": "quello", "bel": "bello",
}
for form, lemma in LOOKUPS.items():
    res = lookup(form)
    lemmas = [r[0] for r in res]
    check(lemma in lemmas, f"lookup {form!r}: expected {lemma}, got {lemmas[:5]}")

# 2. irregular verb charts --------------------------------------------------------------------
P = ["io", "tu", "lui/lei", "noi", "voi", "loro"]
VERBS = {
    # pres (6) | passato remoto io, lui | futuro io | cong pres io | cong impf io | imper tu | pp | pass. pross. io
    "essere": ("sono sei è siamo siete sono", "fui fu", "sarò", "sia", "fossi", "sii", "stato", "sono stato/a"),
    "avere": ("ho hai ha abbiamo avete hanno", "ebbi ebbe", "avrò", "abbia", "avessi", "abbi", "avuto", "ho avuto"),
    "andare": ("vado vai va andiamo andate vanno", "andai andò", "andrò", "vada", "andassi", "va' / vai", "andato", "sono andato/a"),
    "fare": ("faccio fai fa facciamo fate fanno", "feci fece", "farò", "faccia", "facessi", "fa' / fai", "fatto", "ho fatto"),
    "dire": ("dico dici dice diciamo dite dicono", "dissi disse", "dirò", "dica", "dicessi", "di'", "detto", "ho detto"),
    "venire": ("vengo vieni viene veniamo venite vengono", "venni venne", "verrò", "venga", "venissi", "vieni", "venuto", "sono venuto/a"),
    "stare": ("sto stai sta stiamo state stanno", "stetti stette", "starò", "stia", "stessi", "sta' / stai", "stato", "sono stato/a"),
    "dare": ("do dai dà diamo date danno", "diedi diede", "darò", "dia", "dessi", "da' / dai", "dato", "ho dato"),
    "potere": ("posso puoi può possiamo potete possono", "potei poté", "potrò", "possa", "potessi", None, "potuto", "ho potuto"),
    "volere": ("voglio vuoi vuole vogliamo volete vogliono", "volli volle", "vorrò", "voglia", "volessi", None, "voluto", "ho voluto"),
    "dovere": ("devo devi deve dobbiamo dovete devono", "dovetti dovette", "dovrò", "debba", "dovessi", None, "dovuto", "ho dovuto"),
    "sapere": ("so sai sa sappiamo sapete sanno", "seppi seppe", "saprò", "sappia", "sapessi", "sappi", "saputo", "ho saputo"),
    "uscire": ("esco esci esce usciamo uscite escono", "uscii uscì", "uscirò", "esca", "uscissi", "esci", "uscito", "sono uscito/a"),
    "rimanere": ("rimango rimani rimane rimaniamo rimanete rimangono", "rimasi rimase", "rimarrò", "rimanga", "rimanessi", "rimani", "rimasto", "sono rimasto/a"),
    "bere": ("bevo bevi beve beviamo bevete bevono", "bevvi bevve", "berrò", "beva", "bevessi", "bevi", "bevuto", "ho bevuto"),
    "tenere": ("tengo tieni tiene teniamo tenete tengono", "tenni tenne", "terrò", "tenga", "tenessi", "tieni", "tenuto", "ho tenuto"),
    "porre": ("pongo poni pone poniamo ponete pongono", "posi pose", "porrò", "ponga", "ponessi", "poni", "posto", "ho posto"),
    "trarre": ("traggo trai trae traiamo traete traggono", "trassi trasse", "trarrò", "tragga", "traessi", "trai", "tratto", "ho tratto"),
    "togliere": ("tolgo togli toglie togliamo togliete tolgono", "tolsi tolse", "toglierò", "tolga", "togliessi", "togli", "tolto", "ho tolto"),
    "finire": ("finisco finisci finisce finiamo finite finiscono", "finii finì", "finirò", "finisca", "finissi", "finisci", "finito", None),
    "produrre": ("produco produci produce produciamo producete producono", "produssi produsse", "produrrò", "produca", "producessi", "produci", "prodotto", "ho prodotto"),
    "ottenere": ("ottengo ottieni ottiene otteniamo ottenete ottengono", "ottenni ottenne", "otterrò", "ottenga", "ottenessi", "ottieni", "ottenuto", "ho ottenuto"),
    "cercare": ("cerco cerchi cerca cerchiamo cercate cercano", "cercai cercò", "cercherò", "cerchi", "cercassi", "cerca", "cercato", "ho cercato"),
    "mangiare": ("mangio mangi mangia mangiamo mangiate mangiano", "mangiai mangiò", "mangerò", "mangi", "mangiassi", "mangia", "mangiato", "ho mangiato"),
}
for v, (pres, pr, fut, cong, cimpf, imp, pp, ppross) in VERBS.items():
    t, notes, _, _ = tables(v, "verb")
    if t is None:
        check(False, f"verb {v} missing")
        continue
    got = [cell(t, "Indicativo presente", p) for p in P]
    check(got == pres.split(), f"{v} presente: {got}")
    a, b = pr.split()
    check(cell(t, "Indicativo passato remoto", "io") == a and cell(t, "Indicativo passato remoto", "lui/lei") == b,
          f"{v} passato remoto: {cell(t, 'Indicativo passato remoto', 'io')}, {cell(t, 'Indicativo passato remoto', 'lui/lei')}")
    check(cell(t, "Indicativo futuro semplice", "io") == fut, f"{v} futuro: {cell(t, 'Indicativo futuro semplice', 'io')}")
    check(cell(t, "Congiuntivo presente", "io") == cong, f"{v} congiuntivo: {cell(t, 'Congiuntivo presente', 'io')}")
    check(cell(t, "Congiuntivo imperfetto", "io") == cimpf, f"{v} cong. imperfetto: {cell(t, 'Congiuntivo imperfetto', 'io')}")
    check(cell(t, "Imperativo", "tu") == imp, f"{v} imperativo tu: {cell(t, 'Imperativo', 'tu')} (expected {imp})")
    pps = cell(t, "Participio", "passato") or ""
    check(pps.split(",")[0] == pp, f"{v} participio: {pps}")
    if ppross:
        check(cell(t, "Indicativo passato prossimo", "io") == ppross,
              f"{v} passato prossimo: {cell(t, 'Indicativo passato prossimo', 'io')} (expected {ppross})")

# 3. auxiliaries ------------------------------------------------------------------------------
for v in ["andare", "venire", "arrivare", "partire", "nascere", "morire", "diventare", "piacere", "uscire",
          "rimanere", "tornare", "entrare", "restare", "succedere", "sembrare", "cadere", "riuscire", "costare"]:
    t, notes, _, _ = tables(v, "verb")
    pp = cell(t, "Indicativo passato prossimo", "noi") if t else None
    check(bool(pp) and pp.startswith("siamo ") and pp.endswith("i/e"), f"aux essere {v}: {pp}")
for v in ["mangiare", "fare", "dire", "avere", "parlare", "vedere", "dormire", "lavorare", "scrivere", "leggere"]:
    t, notes, _, _ = tables(v, "verb")
    pp = cell(t, "Indicativo passato prossimo", "io") if t else None
    check(bool(pp) and pp.startswith("ho "), f"aux avere {v}: {pp}")

# 4. reflexive / pronominal verbs ---------------------------------------------------------------
REFL = {
    "alzarsi": {("Indicativo presente", "io"): "mi alzo", ("Indicativo presente", "loro"): "si alzano",
                ("Indicativo passato prossimo", "io"): "mi sono alzato/a",
                ("Indicativo passato prossimo", "noi"): "ci siamo alzati/e", ("Imperativo", "tu"): "alzati",
                ("Imperativo", "voi"): "alzatevi", ("Imperativo", "negativo (tu)"): "non alzarti",
                ("Gerundio", "presente"): "alzandosi", ("Infinito", "passato"): "essersi alzato/a"},
    "divertirsi": {("Indicativo presente", "tu"): "ti diverti", ("Indicativo passato prossimo", "lui/lei"): "si è divertito/a"},
    "andarsene": {("Indicativo presente", "io"): "me ne vado", ("Indicativo passato prossimo", "lui/lei"): "se n'è andato/a",
                  ("Imperativo", "tu"): "vattene"},
    "sentirsi": {("Condizionale presente", "io"): "mi sentirei"},
}
for v, exp in REFL.items():
    t, notes, _, _ = tables(v)
    for (title, label), val in exp.items():
        got = cell(t, title, label) if t else None
        check(got == val, f"{v} {title} {label}: {got} (expected {val})")

# 5. nouns ------------------------------------------------------------------------------------
NOUNS = {  # lemma: (gender, singular cell, plural cell)
    "casa": ("f", "la casa", "le case"), "uovo": ("m", "l'uovo", "le uova"), "problema": ("m", "il problema", "i problemi"),
    "città": ("f", "la città", "le città"), "amico": ("m", "l'amico", "gli amici"), "mano": ("f", "la mano", "le mani"),
    "uomo": ("m", "l'uomo", "gli uomini"), "lago": ("m", "il lago", "i laghi"), "crisi": ("f", "la crisi", "le crisi"),
    "zio": ("m", "lo zio", "gli zii"), "studente": ("m", "lo studente", "gli studenti"), "notte": ("f", "la notte", "le notti"),
    "film": ("m", "il film", "i film"), "specie": ("f", "la specie", "le specie"), "medico": ("m", "il medico", "i medici"), "tavolo": ("m", "il tavolo", "i tavoli"),
    "arancia": ("f", "l'arancia", "le arance"), "valigia": ("f", "la valigia", "le valigie"), "re": ("m", "il re", "i re"),
    "foto": ("f", "la foto", "le foto"), "giorno": ("m", "il giorno", "i giorni"), "chiave": ("f", "la chiave", "le chiavi"),
    "fiore": ("m", "il fiore", "i fiori"), "ragazzo": ("m", "il ragazzo", "i ragazzi"), "dito": ("m", "il dito", "le dita"),
    "braccio": ("m", "il braccio", "le braccia"), "gnocco": ("m", "lo gnocco", "gli gnocchi"), "ragazza": ("f", "la ragazza", "le ragazze"),
    "artista": ("mf", "l'artista", "gli artisti / le artiste"), "cantante": ("mf", "il / la cantante", "i / le cantanti"),
    "valore": ("m", "il valore", "i valori"),
}
for n, (g, sg, pl) in NOUNS.items():
    t, notes, gender, _ = tables(n, "noun")
    if not t:
        check(False, f"noun {n} missing")
        continue
    row = t["Forme"]["rows"][0]
    check(gender == g, f"noun {n} gender {gender} (expected {g})")
    check(row[1] == sg and row[2] == pl, f"noun {n} forms {row[1:]} (expected {sg} / {pl})")

# 6. adjectives -------------------------------------------------------------------------------
ADJ = {
    "bello": ("bello", "belli", "bella", "belle"), "bianco": ("bianco", "bianchi", "bianca", "bianche"),
    "pratico": ("pratico", "pratici", "pratica", "pratiche"), "lungo": ("lungo", "lunghi", "lunga", "lunghe"),
    "grigio": ("grigio", "grigi", "grigia", "grigie"), "vecchio": ("vecchio", "vecchi", "vecchia", "vecchie"),
    "felice": ("felice", "felici", "felice", "felici"), "blu": ("blu", "blu", "blu", "blu"),
    "ottimista": ("ottimista", "ottimisti", "ottimista", "ottimiste"), "mio": ("mio", "miei", "mia", "mie"),
}
for a, (ms, mp, fs, fp) in ADJ.items():
    t, notes, _, _ = tables(a, "adj") if a != "mio" else tables(a)
    if not t:
        check(False, f"adj {a} missing")
        continue
    rows = {r[0]: r for r in t["Forme"]["rows"]}
    m = rows.get("maschile") or rows.get("maschile e femminile")
    f = rows.get("femminile") or rows.get("maschile e femminile")
    check(m and f and (m[1], m[2], f[1], f[2]) == (ms, mp, fs, fp), f"adj {a}: {m} {f}")
t, notes, _, _ = tables("buono", "adj")
check(t is not None and "migliore" in (cell(t, "Gradi", "comparativo") or ""), "buono comparativo")
check(t is not None and "ottimo" in (cell(t, "Gradi", "superlativo assoluto") or ""), "buono superlativo")
t, _, _, _ = tables("bello", "adj")
check(t is not None and any("bei" in r for r in t["Forme"]["rows"]), "bello short forms bel/bei/begli")

# 7. meta -------------------------------------------------------------------------------------
meta = dict(db.execute("SELECT key, value FROM meta"))
for k in ("version", "built_at", "sources", "entry_count"):
    check(bool(meta.get(k)), f"meta.{k} missing")
check(int(db.execute("SELECT count(*) FROM lemma").fetchone()[0]) >= 8000, "fewer than 8000 lemmas")
check(db.execute("SELECT count(*) FROM lemma WHERE lemma != lower(lemma)").fetchone()[0] == 0, "uppercase lemma")
check(db.execute("SELECT count(*) FROM form WHERE form_norm != lower(form_norm)").fetchone()[0] == 0, "form_norm not lowercase")

print(f"{passes} passed, {len(fails)} failed")
for f in fails:
    print("FAIL", f)
sys.exit(1 if fails else 0)
