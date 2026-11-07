"""Rule-based Italian inflection: verb conjugation (with an irregular-verb table and prefix
derivation), noun plurals/articles and adjective agreement.

Generation is deterministic; build.py chooses between alternative candidates (e.g. -isc- or not,
-chi or -ci) by checking which one the Wiktionary inflection list for that lemma attests.
"""
from __future__ import annotations

import re
import unicodedata

PERSONS = ["io", "tu", "lui/lei", "noi", "voi", "loro"]
IMP_PERSONS = ["tu", "Lei", "noi", "voi", "Loro"]
VOWELS = "aeiouàèéìíòóùú"


def norm(s: str) -> str:
    """Normalisation shared with the app: lowercase, strip accents and apostrophes, squash spaces."""
    s = s.lower()
    s = "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")
    s = re.sub(r"['’‘ʼ`´]", "", s)
    return " ".join(s.split())


# ---------------------------------------------------------------------------------------------
# Verbs
# ---------------------------------------------------------------------------------------------

# Irregular base verbs. Keys:
#   like  - "underlying" regular infinitive used for imperfetto, gerundio, congiuntivo imperfetto
#           and the weak passato remoto persons (fare -> facere: facevo, facendo, facessi, facesti)
#   pres  - indicativo presente (6)
#   pr    - passato remoto: a list of 6, or a strong stem ("pres" -> presi/prese/presero)
#   fut   - future/conditional stem ("andr" -> andrò, andrei)
#   pp    - participio passato
#   cong  - congiuntivo presente (6); otherwise derived from pres io/noi
#   imp   - imperativo [tu, Lei, noi, voi, Loro]; None entry in IRR means "no imperative"
#   impf, cimpf, ger, ppres - full overrides
#   prefix_only - only used as the base of prefixed verbs (e.g. "durre": produrre, tradurre)
IRR: dict[str, dict] = {
    "essere": dict(pres="sono sei è siamo siete sono", impf="ero eri era eravamo eravate erano",
                   pr="fui fosti fu fummo foste furono", fut="sar", cong="sia sia sia siamo siate siano",
                   cimpf="fossi fossi fosse fossimo foste fossero", imp="sii|sia|siamo|siate|siano",
                   ger="essendo", pp="stato", ppres=None),
    "avere": dict(pres="ho hai ha abbiamo avete hanno", pr="ebbi avesti ebbe avemmo aveste ebbero",
                  fut="avr", cong="abbia abbia abbia abbiamo abbiate abbiano",
                  imp="abbi|abbia|abbiamo|abbiate|abbiano", pp="avuto"),
    "andare": dict(pres="vado vai va andiamo andate vanno", fut="andr",
                   cong="vada vada vada andiamo andiate vadano", imp="va' / vai|vada|andiamo|andate|vadano",
                   pp="andato"),
    "fare": dict(like="facere", pres="faccio fai fa facciamo fate fanno", pr="fec", fut="far",
                 cong="faccia faccia faccia facciamo facciate facciano",
                 imp="fa' / fai|faccia|facciamo|fate|facciano", pp="fatto"),
    "dire": dict(like="dicere", pres="dico dici dice diciamo dite dicono", pr="diss", fut="dir",
                 imp="di'|dica|diciamo|dite|dicano", pp="detto"),
    "dare": dict(pres="do dai dà diamo date danno", pr="diedi desti diede demmo deste diedero",
                 fut="dar", cong="dia dia dia diamo diate diano",
                 cimpf="dessi dessi desse dessimo deste dessero",
                 imp="da' / dai|dia|diamo|date|diano", pp="dato"),
    "stare": dict(pres="sto stai sta stiamo state stanno", pr="stetti stesti stette stemmo steste stettero",
                  fut="star", cong="stia stia stia stiamo stiate stiano",
                  cimpf="stessi stessi stesse stessimo steste stessero",
                  imp="sta' / stai|stia|stiamo|state|stiano", pp="stato"),
    "venire": dict(pres="vengo vieni viene veniamo venite vengono", pr="venn", fut="verr",
                   imp="vieni|venga|veniamo|venite|vengano", pp="venuto"),
    "tenere": dict(pres="tengo tieni tiene teniamo tenete tengono", pr="tenn", fut="terr",
                   imp="tieni|tenga|teniamo|tenete|tengano", pp="tenuto"),
    "potere": dict(pres="posso puoi può possiamo potete possono",
                   pr="potei potesti poté potemmo poteste poterono", fut="potr",
                   cong="possa possa possa possiamo possiate possano", imp=None, pp="potuto"),
    "volere": dict(pres="voglio vuoi vuole vogliamo volete vogliono", pr="voll", fut="vorr",
                   cong="voglia voglia voglia vogliamo vogliate vogliano", imp=None, pp="voluto"),
    "dovere": dict(pres="devo devi deve dobbiamo dovete devono",
                   pr="dovetti dovesti dovette dovemmo doveste dovettero", fut="dovr",
                   cong="debba debba debba dobbiamo dobbiate debbano", imp=None, pp="dovuto"),
    "sapere": dict(pres="so sai sa sappiamo sapete sanno", pr="sepp", fut="sapr",
                   cong="sappia sappia sappia sappiamo sappiate sappiano",
                   imp="sappi|sappia|sappiamo|sappiate|sappiano", pp="saputo"),
    "uscire": dict(pres="esco esci esce usciamo uscite escono", imp="esci|esca|usciamo|uscite|escano",
                   pp="uscito"),
    "rimanere": dict(pres="rimango rimani rimane rimaniamo rimanete rimangono", pr="rimas", fut="rimarr",
                     imp="rimani|rimanga|rimaniamo|rimanete|rimangano", pp="rimasto"),
    "bere": dict(like="bevere", pres="bevo bevi beve beviamo bevete bevono", pr="bevv", fut="berr",
                 imp="bevi|beva|beviamo|bevete|bevano", pp="bevuto"),
    "porre": dict(like="ponere", pres="pongo poni pone poniamo ponete pongono", pr="pos", fut="porr",
                  imp="poni|ponga|poniamo|ponete|pongano", pp="posto"),
    "trarre": dict(like="traere", pres="traggo trai trae traiamo traete traggono", pr="trass", fut="trarr",
                   imp="trai|tragga|traiamo|traete|traggano", pp="tratto"),
    "durre": dict(like="ducere", pres="duco duci duce duciamo ducete ducono", pr="duss", fut="durr",
                  imp="duci|duca|duciamo|ducete|ducano", pp="dotto", prefix_only=True),
    "morire": dict(pres="muoio muori muore moriamo morite muoiono", imp="muori|muoia|moriamo|morite|muoiano",
                   pp="morto"),
    "nascere": dict(pr="nacqu", pp="nato"),
    "piacere": dict(pres="piaccio piaci piace piacciamo piacete piacciono", pr="piacqu",
                    cong="piaccia piaccia piaccia piacciamo piacciate piacciano", pp="piaciuto"),
    "tacere": dict(pres="taccio taci tace tacciamo tacete tacciono", pr="tacqu",
                   cong="taccia taccia taccia tacciamo tacciate tacciano", pp="taciuto"),
    "giacere": dict(pres="giaccio giaci giace giacciamo giacete giacciono", pr="giacqu",
                    cong="giaccia giaccia giaccia giacciamo giacciate giacciano", pp="giaciuto"),
    "parere": dict(pres="paio pari pare paiamo parete paiono", pr="parv", fut="parr",
                   cong="paia paia paia paiamo paiate paiano", imp=None, pp="parso"),
    "valere": dict(pres="valgo vali vale valiamo valete valgono", pr="vals", fut="varr", pp="valso"),
    "dolere": dict(pres="dolgo duoli duole doliamo dolete dolgono", pr="dols", fut="dorr", pp="doluto"),
    "sedere": dict(pres="siedo siedi siede sediamo sedete siedono", imp="siedi|sieda|sediamo|sedete|siedano",
                   pp="seduto"),
    "vedere": dict(pr="vid", fut="vedr", pp="visto"),
    "vivere": dict(pr="viss", fut="vivr", pp="vissuto"),
    "cadere": dict(pr="cadd", fut="cadr", pp="caduto"),
    "correre": dict(pr="cors", pp="corso"),
    "chiedere": dict(pr="chies", pp="chiesto"),
    "rispondere": dict(pr="rispos", pp="risposto"),
    "nascondere": dict(pr="nascos", pp="nascosto"),
    "chiudere": dict(pr="chius", pp="chiuso"),
    "cludere": dict(pr="clus", pp="cluso", prefix_only=True),
    "cidere": dict(pr="cis", pp="ciso", prefix_only=True),
    "ridere": dict(pr="ris", pp="riso"),
    "dividere": dict(pr="divis", pp="diviso"),
    "mettere": dict(pr="mis", pp="messo"),
    "prendere": dict(pr="pres", pp="preso"),
    "rendere": dict(pr="res", pp="reso"),
    "scendere": dict(pr="sces", pp="sceso"),
    "spendere": dict(pr="spes", pp="speso"),
    "cendere": dict(pr="ces", pp="ceso", prefix_only=True),       # accendere
    "fendere": dict(pr="fes", pp="feso", prefix_only=True),       # difendere, offendere
    "tendere": dict(pr="tes", pp="teso"),
    "pendere": dict(pr="pes", pp="peso", prefix_only=True),       # dipendere, sospendere, appendere
    "perdere": dict(pr="pers", pp="perso"),
    "leggere": dict(pr="less", pp="letto"),
    "reggere": dict(pr="ress", pp="retto"),
    "teggere": dict(pr="tess", pp="tetto", prefix_only=True),     # proteggere
    "friggere": dict(pr="friss", pp="fritto"),
    "struggere": dict(pr="struss", pp="strutto", prefix_only=True),  # distruggere
    "rigere": dict(pr="ress", pp="retto", prefix_only=True),      # dirigere -> diressi, diretto
    "scrivere": dict(pr="scriss", pp="scritto"),
    "vincere": dict(pr="vins", pp="vinto"),
    "spingere": dict(pr="spins", pp="spinto"),
    "pingere": dict(pr="pins", pp="pinto", prefix_only=True),     # dipingere
    "fingere": dict(pr="fins", pp="finto"),
    "piangere": dict(pr="pians", pp="pianto"),
    "giungere": dict(pr="giuns", pp="giunto"),
    "stringere": dict(pr="strins", pp="stretto"),
    "spegnere": dict(pres="spengo spegni spegne spegniamo spegnete spengono", pr="spens", pp="spento"),
    "tingere": dict(pr="tins", pp="tinto"),
    "stinguere": dict(pr="stins", pp="stinto", prefix_only=True),  # distinguere, estinguere
    "volgere": dict(pr="vols", pp="volto"),
    "sciogliere": dict(pres="sciolgo sciogli scioglie sciogliamo sciogliete sciolgono", pr="sciols", pp="sciolto"),
    "cogliere": dict(pres="colgo cogli coglie cogliamo cogliete colgono", pr="cols", pp="colto"),
    "togliere": dict(pres="tolgo togli toglie togliamo togliete tolgono", pr="tols", pp="tolto"),
    "scegliere": dict(pres="scelgo scegli sceglie scegliamo scegliete scelgono", pr="scels", pp="scelto"),
    "porgere": dict(pr="pors", pp="porto"),
    "sorgere": dict(pr="sors", pp="sorto"),
    "corgere": dict(pr="cors", pp="corto", prefix_only=True),     # accorgersi
    "muovere": dict(pr="moss", pp="mosso"),
    "scuotere": dict(pr="scoss", pp="scosso"),
    "cuotere": dict(pr="cuss", pp="cusso", prefix_only=True),     # percuotere
    "conoscere": dict(pr="conobb", pp="conosciuto"),
    "crescere": dict(pr="crebb", pp="cresciuto"),
    "rompere": dict(pr="rupp", pp="rotto"),
    "sumere": dict(pr="suns", pp="sunto", prefix_only=True),      # assumere, presumere
    "cutere": dict(pr="cuss", pp="cusso", prefix_only=True),      # discutere
    "cedere": dict(pr="cess", pp="cesso", prefix_only=True),      # concedere, succedere
    "primere": dict(pr="press", pp="presso", prefix_only=True),   # esprimere, imprimere
    "cuocere": dict(pres="cuocio cuoci cuoce cuociamo cuocete cuociono", pr="coss", pp="cotto"),
    "apparire": dict(pres="appaio appari appare appariamo apparite appaiono", pr="apparv", pp="apparso",
                     imp="appari|appaia|appariamo|apparite|appaiano"),
    "parire": dict(pres="paio pari pare pariamo parite paiono", pr="parv", pp="parso", prefix_only=True,
                   imp="pari|paia|pariamo|parite|paiano"),        # comparire, scomparire
    "aprire": dict(pp="aperto"),
    "coprire": dict(pp="coperto"),
    "offrire": dict(pp="offerto"),
    "soffrire": dict(pp="sofferto"),
    "salire": dict(pres="salgo sali sale saliamo salite salgono", imp="sali|salga|saliamo|salite|salgano"),
    "udire": dict(pres="odo odi ode udiamo udite odono", imp="odi|oda|udiamo|udite|odano"),
    "cucire": dict(pres="cucio cuci cuce cuciamo cucite cuciono",
                   cong="cucia cucia cucia cuciamo cuciate cuciano"),
    "emergere": dict(pr="emers", pp="emerso"),
    "mergere": dict(pr="mers", pp="merso", prefix_only=True),     # immergere
    "spargere": dict(pr="spars", pp="sparso"),
    "mordere": dict(pr="mors", pp="morso"),
    "ardere": dict(pr="ars", pp="arso"),
    "pandere": dict(pr="pans", pp="panso", prefix_only=True),     # espandere
    "fondere": dict(pr="fus", pp="fuso"),
    "radere": dict(pr="ras", pp="raso"),
    "vadere": dict(pr="vas", pp="vaso", prefix_only=True),        # invadere, evadere
    "suadere": dict(pr="suas", pp="suaso", prefix_only=True),     # persuadere
    "nettere": dict(pr="ness", pp="nesso", prefix_only=True),     # connettere
    "esigere": dict(pp="esatto"),
    "redigere": dict(pr="redass", pp="redatto"),
    "flettere": dict(pr="fless", pp="flesso", prefix_only=True),  # riflettere (physics), flettere
    "mungere": dict(pr="muns", pp="munto"),
    "torcere": dict(pr="tors", pp="torto"),
    "vellere": dict(pr="vels", pp="velto", prefix_only=True),     # svellere
    "solvere": dict(pr="sols", pp="solto", prefix_only=True),     # risolvere -> risolsi, risolto
    "fliggere": dict(pr="fliss", pp="flitto", prefix_only=True),    # affliggere, infliggere
    "figgere": dict(pr="fiss", pp="fitto", prefix_only=True),       # sconfiggere, crocifiggere
    "plodere": dict(pr="plos", pp="ploso", prefix_only=True),       # esplodere
    "ludere": dict(pr="lus", pp="luso", prefix_only=True),          # deludere, alludere, illudere
    "scindere": dict(pr="sciss", pp="scisso"),                      # prescindere
    "ungere": dict(pr="uns", pp="unto"),                            # fungere, pungere, ungere
    "rodere": dict(pr="ros", pp="roso"),                            # corrodere, erodere
    "ledere": dict(pr="les", pp="leso"),
    "pellere": dict(pr="puls", pp="pulso", prefix_only=True),       # espellere
    "cellere": dict(pr="cels", pp="celso", prefix_only=True),       # eccellere
    "ergere": dict(pr="ers", pp="erto"),
    "frangere": dict(pr="frans", pp="franto"),                      # infrangere
    "diligere": dict(pr="diless", pp="diletto", prefix_only=True),  # prediligere
    "volvere": dict(pp="voluto", prefix_only=True),                 # evolvere, devolvere
    "compiere": dict(like="compire", pres="compio compi compie compiamo compite compiono",
                     cong="compia compia compia compiamo compiate compiano", ger="compiendo", pp="compiuto",
                     imp="compi|compia|compiamo|compite|compiano"),
    "empire": dict(pres="empio empi empie empiamo empite empiono", cong="empia empia empia empiamo empiate empiano",
                   ger="empiendo", imp="empi|empia|empiamo|empite|empiano", prefix_only=True),   # riempire
    "manere": dict(pres="mango mani mane maniamo manete mangono", pr="mas", fut="marr",
                   imp="mani|manga|maniamo|manete|mangano", pp="masto", prefix_only=True),       # permanere
    "nuocere": dict(pres="nuoccio nuoci nuoce nuociamo nuocete nuocciono", pr="nocqu", pp="nuociuto",
                    cong="nuoccia nuoccia nuoccia nuociamo nuociate nuocciano"),
}
for _k, _v in IRR.items():
    for _f in ("pres", "pr", "cong", "cimpf", "impf"):
        if isinstance(_v.get(_f), str) and " " in _v[_f]:
            _v[_f] = _v[_f].split()
    if isinstance(_v.get("imp"), str):
        _v["imp"] = _v["imp"].split("|")

# Prefixes that may precede an irregular base (validation against Wiktionary picks the best
# candidate, so an over-generous list here only costs time).
PREFIXES = sorted(set("""a ab ac ad af ag al am an ap ar as at av ben bene co col com con contra contrad
contro cor de di dis e es estra fra im in inter intra intro male mal man mante o ob of op per post pre pro
r re ri ra rin sa sop sor sos sotto sovra sopra so sor sub sup s sc so tra tras ul circo circon
ec ef eb sod soddis com cos des dif dir ins inst res ris sop sup trat ricon ri+ap ot op ottem""".split()),
                  key=len, reverse=True)

ENDINGS = {
    "are": dict(pres=["o", "i", "a", "iamo", "ate", "ano"], impf="av", pr=["ai", "asti", "ò", "ammo", "aste", "arono"],
                cong=["i", "i", "i", "iamo", "iate", "ino"], cimpf="ass", ger="ando", ppres="ante", pp="ato"),
    "ere": dict(pres=["o", "i", "e", "iamo", "ete", "ono"], impf="ev", pr=["ei", "esti", "é", "emmo", "este", "erono"],
                cong=["a", "a", "a", "iamo", "iate", "ano"], cimpf="ess", ger="endo", ppres="ente", pp="uto"),
    "ire": dict(pres=["o", "i", "e", "iamo", "ite", "ono"], impf="iv", pr=["ii", "isti", "ì", "immo", "iste", "irono"],
                cong=["a", "a", "a", "iamo", "iate", "ano"], cimpf="iss", ger="endo", ppres="ente", pp="ito"),
}
IMPF_END = ["o", "i", "a", "amo", "ate", "ano"]
CIMPF_END = ["i", "i", "e", "imo", "te", "ero"]
FUT_END = ["ò", "ai", "à", "emo", "ete", "anno"]
COND_END = ["ei", "esti", "ebbe", "emmo", "este", "ebbero"]


def join(stem: str, end: str, conj: str, keep_i: bool = False) -> str:
    """Attach an ending to a stem with Italian spelling adjustments."""
    if conj == "are":
        if keep_i and stem.endswith("i") and end[:1] in ("i", "e") and not end.startswith("ia"):
            return stem + end                       # invii, scii, scierò
        if stem.endswith(("c", "g")) and end[:1] in ("i", "e", "ì", "è", "é"):
            return stem + "h" + end
        if stem.endswith(("ci", "gi")) and end[:1] in ("i", "e", "ì", "è", "é"):
            return stem[:-1] + end
        if stem.endswith("i") and end[:1] == "i" and not keep_i:
            return stem[:-1] + end
        if stem.endswith("i") and end.startswith("ia"):
            return stem[:-1] + end
        return stem + end
    if end.startswith("u") and stem.endswith(("c", "g")):  # conosc+uto -> conosciuto
        return stem + "i" + end
    if stem.endswith("i") and end.startswith("i"):
        return stem[:-1] + end
    return stem + end


def _accent_mono(prefix: str, form: str) -> str:
    """rifà, ridà, disfò: a prefixed monosyllable gets a written accent."""
    if prefix == "soddis":
        return prefix + form
    if prefix and form in ("fa", "da", "sta", "do", "sto", "fo", "so", "sa", "va", "vo"):
        return prefix + form[:-1] + {"a": "à", "o": "ò"}[form[-1]]
    if prefix and form == "dà":
        return prefix + "dà"
    return prefix + form


def conjugate(inf: str, base: str | None = None, prefix: str = "", isc: bool = False,
              keep_i: bool = False, pr_etti: bool = False) -> dict:
    """Return the simple forms of a verb.

    base/prefix: irregular base from IRR and the prefix in front of it (ottenere = ot + tenere).
    Returns dict with keys pres, impf, pr, fut, cond, cong, cimpf (lists of 6), imp (list of 5 or
    None), inf, ger, ppres (str or None), pp (str), irregular (bool).
    """
    spec = IRR.get(base, {}) if base else {}
    like = spec.get("like", base or inf)
    # 'like' may be e.g. "facere" while the real infinitive is "fare"
    conj = like[-3:]
    if like.endswith(("rre",)):
        conj = "ere"
    if conj not in ENDINGS:
        raise ValueError(f"not a verb infinitive: {inf}")
    E = ENDINGS[conj]
    stem = like[:-3]
    P = prefix

    def wrap(forms):
        return [_accent_mono(P, f) if P else f for f in forms]

    # presente
    if "pres" in spec:
        pres = wrap(spec["pres"])
    else:
        if isc:
            pres = [stem + "isco", stem + "isci", stem + "isce", join(stem, "iamo", conj), stem + "ite", stem + "iscono"]
        else:
            pres = [join(stem, e, conj, keep_i) for e in E["pres"]]
        pres = [P + f for f in pres]
    # imperfetto
    if "impf" in spec:
        impf = wrap(spec["impf"])
    else:
        impf = [P + stem + E["impf"] + e for e in IMPF_END]
    # passato remoto
    pr = spec.get("pr")
    weak = [join(stem, e, conj) for e in E["pr"]]
    if conj == "ere" and pr_etti:
        weak = [stem + e for e in ["etti", "esti", "ette", "emmo", "este", "ettero"]]
    if isinstance(pr, list):
        prl = wrap(pr)
    elif isinstance(pr, str):
        prl = [P + pr + "i", P + weak[1], P + pr + "e", P + weak[3], P + weak[4], P + pr + "ero"]
    else:
        prl = [P + f for f in weak]
    # futuro / condizionale
    if "fut" in spec:
        fstem = P + spec["fut"]
    elif conj == "are":
        s = stem
        if s.endswith(("c", "g")):
            s += "h"
        elif s.endswith(("ci", "gi")) and not keep_i:
            s = s[:-1]
        fstem = P + s + "er"
    else:
        # porre -> porr, tradurre -> tradurr, credere -> creder
        fstem = P + (inf[len(P):][:-1] if inf.endswith("rre") else stem + conj[0] + "r")
    fut = [fstem + e for e in FUT_END]
    cond = [fstem + e for e in COND_END]
    # congiuntivo presente
    if "cong" in spec:
        cong = wrap(spec["cong"])
    elif spec.get("pres"):
        io = pres[0]
        cstem = io[:-1]
        e = "i" if conj == "are" else "a"
        noi = pres[3]
        cong = [cstem + e] * 3 + [noi, noi[:-2] + "te", cstem + ("ino" if conj == "are" else "ano")]
    elif isc:
        cong = [P + stem + "isca"] * 3 + [P + join(stem, "iamo", conj), P + join(stem, "iate", conj), P + stem + "iscano"]
    else:
        cong = [P + join(stem, e, conj, keep_i) for e in E["cong"]]
    # congiuntivo imperfetto
    if "cimpf" in spec:
        cimpf = wrap(spec["cimpf"])
    else:
        cimpf = [P + stem + E["cimpf"] + e for e in CIMPF_END]
    # imperativo
    if "imp" in spec and spec["imp"] is None:
        imp = None
    elif "imp" in spec:
        imp = list(spec["imp"])
        if P:
            # contraddici, rifai, disfa': prefixed verbs use the full present-tense form for tu
            if "'" in imp[0]:
                imp[0] = pres[1]
            imp = [imp[0] if i == 0 and imp[0] == pres[1] else _accent_mono(P, f) for i, f in enumerate(imp)]
            imp = [f if f.startswith(P) else P + f for f in imp]
    else:
        tu = (P + join(stem, "a", conj)) if conj == "are" else pres[1]
        imp = [tu, cong[2], pres[3], pres[4], cong[5]]
    ger = P + spec["ger"] if "ger" in spec else P + stem + E["ger"]
    if "ppres" in spec:
        ppres = P + spec["ppres"] if spec["ppres"] else None
    else:
        ppres = P + stem + E["ppres"]
    pp = P + spec["pp"] if "pp" in spec else P + join(stem, E["pp"], conj)
    if base and spec and "pp" in spec and spec["pp"] == "piaciuto":
        pp = P + "piaciuto"
    return dict(inf=inf, pres=pres, impf=impf, pr=prl, fut=fut, cond=cond, cong=cong, cimpf=cimpf,
                imp=imp, ger=ger, ppres=ppres, pp=pp, irregular=bool(spec))


def simple_forms(c: dict) -> list[str]:
    out = []
    for k in ("pres", "impf", "pr", "fut", "cond", "cong", "cimpf"):
        out += c[k]
    if c["imp"]:
        for f in c["imp"]:
            out += [x.strip() for x in f.split("/")]
    out += [c["inf"], c["ger"], c["pp"]]
    return out


def candidates_for(inf: str) -> list[dict]:
    """All plausible conjugations of an infinitive, most trusted first: exact irregular, irregular
    base with a known prefix, regular variants, irregular base with any other short prefix.
    build.py keeps the candidate whose forms best match the Wiktionary inflection list."""
    exact, known, loose, reg = [], [], [], []
    if inf in IRR and not IRR[inf].get("prefix_only"):
        exact.append(dict(base=inf, prefix=""))
    for b, spec in IRR.items():
        if inf.endswith(b) and inf != b:
            p = inf[: -len(b)]
            if p in PREFIXES:
                known.append(dict(base=b, prefix=p))
            elif len(p) <= 9:
                loose.append(dict(base=b, prefix=p))
    if inf.endswith(("are", "ere", "ire")) and not inf.endswith("rre"):
        reg.append(dict())
        if inf.endswith("ire"):
            reg.append(dict(isc=True))
        if inf.endswith("iare"):
            reg.append(dict(keep_i=True))
        if inf.endswith("ere"):
            reg.append(dict(pr_etti=True))
    # longest base first within each group (riempire: empire before ire-like bases)
    known.sort(key=lambda c: len(c["prefix"]))
    loose.sort(key=lambda c: len(c["prefix"]))
    return exact + known + reg + loose


# Clitic suffixes of pronominal lemmas: suffix -> (proclitic per person or constant, enclitic per
# [infinitive, tu, noi, voi], participle agreement)
def _pp_display(pp: str, agree: bool, plural: bool) -> str:
    if not agree or not pp.endswith("o"):
        return pp
    return pp[:-1] + ("i/e" if plural else "o/a")


REFL = ["mi", "ti", "si", "ci", "vi", "si"]
CLITIC_SUFFIXES = {
    # suffix: (proclitics (6) , enclitic for inf/ger, imperative tu, noi, voi, reflexive?, fem object)
    "sene": (["me ne", "te ne", "se ne", "ce ne", "ve ne", "se ne"], "sene", "tene", "cene", "vene", True, False),
    "sela": (["me la", "te la", "se la", "ce la", "ve la", "se la"], "sela", "tela", "cela", "vela", True, True),
    "si": (REFL, "si", "ti", "ci", "vi", True, False),
    "cela": (["ce la"] * 6, "cela", "cela", "cela", "cela", False, True),
    "ci": (["ci"] * 6, "ci", "ci", "ci", "ci", False, False),
    "ne": (["ne"] * 6, "ne", "ne", "ne", "ne", False, False),
    "la": (["la"] * 6, "la", "la", "la", "la", False, True),
}


def split_pronominal(lemma: str, known_verbs) -> tuple[str, str] | None:
    """alzarsi -> (alzare, si); andarsene -> (andare, sene); porsi -> (porre, si)."""
    for suf in ("sene", "sela", "cela", "si", "ci", "ne", "la"):
        if lemma.endswith(suf):
            stem = lemma[: -len(suf)]
            if not stem.endswith("r"):
                continue
            for base in (stem + "e", stem + "re"):
                if base in known_verbs:
                    return base, suf
            if suf in ("si", "sene") and stem.endswith(("ar", "er", "ir")):
                return stem + "e", suf        # accorgersi: "accorgere" has no entry of its own
            if suf == "si" and stem.endswith(("or", "ur", "ar")) and stem[:-1].endswith(("p", "d", "tr")):
                return stem + "re", suf       # porsi, ridursi, sottrarsi
    return None


def proclitic(cl: str, verb: str) -> str:
    """Join a proclitic and a finite verb with the usual elisions (c'è, l'ho)."""
    if cl.endswith(("la", "lo")) and verb[:1] in "aeiouhàèéìòù" and verb[:2] != "ha" or \
            cl.endswith(("la", "lo")) and verb.startswith(("ho", "ha", "hai", "hanno")):
        return cl[:-1] + "'" + verb
    if cl == "ci" and verb[:1] in "eièé":
        return "c'" + verb
    if cl.endswith(" ne") and verb[:1] in "eèé":
        return cl[:-1] + "'" + verb
    return cl + " " + verb


def enclitic(verb: str, cl: str) -> str:
    """dimmi, vattene, alzati, andandosene."""
    v = verb
    if v.endswith("'"):
        v = v[:-1]
        if not cl.startswith("gli"):
            return v + cl[0] + cl
        return v + cl
    if v in ("da", "fa", "sta", "di", "va"):
        return v + (cl[0] + cl if not cl.startswith("gli") else cl)
    if v.endswith("re") and len(v) > 3:
        v = v[:-1]   # alzare+si -> alzarsi ; porre -> porsi
    return v + cl


AUX_FORMS = {
    "avere": conjugate("avere", base="avere"),
    "essere": conjugate("essere", base="essere"),
}


def verb_tables(c: dict, aux: str, alt_aux: str | None = None, clitic: str | None = None) -> tuple[list, list]:
    """Build forms_json tables and (form, tag) pairs for a conjugated verb.

    aux: "avere" | "essere"; clitic: None or a CLITIC_SUFFIXES key for pronominal lemmas.
    """
    tables, pairs = [], []
    A = AUX_FORMS[aux]
    agree = aux == "essere"
    if clitic:
        procl, encl_inf, encl_tu, encl_noi, encl_voi, refl, fem = CLITIC_SUFFIXES[clitic]
        if fem:
            agree = True
    else:
        procl, fem = None, False

    def pp_for(i):
        if fem and clitic in ("cela", "la", "sela"):
            return c["pp"][:-1] + "a" if c["pp"].endswith("o") else c["pp"]
        return _pp_display(c["pp"], agree, i >= 3)

    def fin(i, f):
        return proclitic(procl[i], f) if procl else f

    def simple(title, key, tag):
        rows = []
        for i, f in enumerate(c[key]):
            disp = fin(i, f)
            rows.append([PERSONS[i], disp])
            if not procl:
                pairs.append((f, f"{tag}, {PERSONS[i]}"))
            else:
                pairs.append((disp, f"{tag}, {PERSONS[i]}"))
        tables.append(dict(title=title, columns=["", ""], rows=rows))

    def compound(title, auxkey, aux_conj=A, agree_=None):
        rows = []
        for i, f in enumerate(aux_conj[auxkey]):
            if agree_ is None:
                p = pp_for(i)
            else:
                p = _pp_display(c["pp"], agree_, i >= 3)
            rows.append([PERSONS[i], (proclitic(procl[i], f) if procl else f) + " " + p])
        tables.append(dict(title=title, columns=["", ""], rows=rows))

    simple("Indicativo presente", "pres", "indicativo presente")
    compound("Indicativo passato prossimo", "pres")
    simple("Indicativo imperfetto", "impf", "indicativo imperfetto")
    compound("Indicativo trapassato prossimo", "impf")
    simple("Indicativo passato remoto", "pr", "indicativo passato remoto")
    simple("Indicativo futuro semplice", "fut", "indicativo futuro semplice")
    compound("Indicativo futuro anteriore", "fut")
    simple("Congiuntivo presente", "cong", "congiuntivo presente")
    compound("Congiuntivo passato", "cong")
    simple("Congiuntivo imperfetto", "cimpf", "congiuntivo imperfetto")
    compound("Congiuntivo trapassato", "cimpf")
    simple("Condizionale presente", "cond", "condizionale presente")
    compound("Condizionale passato", "cond")
    if alt_aux:
        B = AUX_FORMS[alt_aux]
        compound(f"Indicativo passato prossimo (con {alt_aux})", "pres", B, alt_aux == "essere")
    # imperativo
    if c["imp"] and not (clitic == "ci" and c["inf"] == "essere"):
        rows = []
        for i, f in enumerate(c["imp"]):
            if procl:
                if i in (1, 4):
                    pi = 2 if i == 1 else 5
                    disp = proclitic(procl[pi], f)
                else:
                    e = {0: encl_tu, 2: encl_noi, 3: encl_voi}[i]
                    parts = [p.strip() for p in f.split("/")]
                    if parts[0].endswith("'"):
                        parts = parts[:1]      # vattene, fatti, dimmi (not *vaitene)
                    disp = " / ".join(enclitic(p, e) for p in parts)
            else:
                disp = f
            rows.append([IMP_PERSONS[i], disp])
            if not procl:
                for p in f.split("/"):
                    pairs.append((p.strip(), f"imperativo, {IMP_PERSONS[i]}"))
            else:
                for p in disp.split("/"):
                    pairs.append((p.strip(), f"imperativo, {IMP_PERSONS[i]}"))
        neg_inf = c["inf"] if not procl else enclitic(c["inf"], encl_tu)
        rows.append(["negativo (tu)", "non " + neg_inf])
        tables.append(dict(title="Imperativo", columns=["", ""], rows=rows))
    # infinito / gerundio / participio
    aux_inf = "essere" if aux == "essere" else "avere"
    if procl:
        inf_disp = enclitic(c["inf"], encl_inf)
        inf_past = enclitic(aux_inf, encl_inf) + " " + pp_for(0)
        ger_disp = c["ger"] + encl_inf
        ger_past = ("essendo" if aux == "essere" else "avendo") + encl_inf + " " + pp_for(0)
        pairs.append((ger_disp, "gerundio presente"))
    else:
        inf_disp = c["inf"]
        inf_past = ("essere" if aux == "essere" else "aver") + " " + _pp_display(c["pp"], agree, False)
        ger_disp = c["ger"]
        ger_past = ("essendo" if aux == "essere" else "avendo") + " " + _pp_display(c["pp"], agree, False)
    tables.append(dict(title="Infinito", columns=["", ""], rows=[["presente", inf_disp], ["passato", inf_past]]))
    tables.append(dict(title="Gerundio", columns=["", ""], rows=[["presente", ger_disp], ["passato", ger_past]]))
    prow = []
    bare = not procl
    if c.get("ppres") and bare:
        prow.append(["presente", c["ppres"]])
        pairs.append((c["ppres"], "participio presente"))
    pp = c["pp"]
    if pp.endswith("o"):
        prow.append(["passato", f"{pp}, {pp[:-1]}a, {pp[:-1]}i, {pp[:-1]}e"])
        if bare:
            for suf, t in (("o", "m. sing."), ("a", "f. sing."), ("i", "m. pl."), ("e", "f. pl.")):
                pairs.append((pp[:-1] + suf, f"participio passato, {t}"))
    else:
        prow.append(["passato", pp])
        if bare:
            pairs.append((pp, "participio passato"))
    tables.append(dict(title="Participio", columns=["", ""], rows=prow))
    if bare:
        pairs.append((c["inf"], "infinito"))
        pairs.append((c["ger"], "gerundio presente"))
    if procl:
        pairs.append((inf_disp, "infinito"))
    return tables, pairs


# Enclitic object pronouns attached to infinitive / gerund / tu-noi-voi imperative (prenderlo, dimmi)
ENCLITICS = ["mi", "ti", "lo", "la", "li", "le", "gli", "ci", "vi", "si", "ne",
             "glielo", "gliela", "glieli", "gliele", "gliene", "melo", "telo"]


def enclitic_pairs(c: dict) -> list[tuple[str, str]]:
    out = []
    inf, ger = c["inf"], c["ger"]
    for cl in ENCLITICS:
        out.append((enclitic(inf, cl), f"infinito + {cl}"))
        out.append((ger + cl, f"gerundio + {cl}"))
    if c["imp"]:
        for i in (0, 2, 3):
            parts = [p.strip() for p in c["imp"][i].split("/")]
            if parts[0].endswith("'"):
                parts = parts[:1]          # dimmi, vattene, fallo (not *vaimi)
            for p in parts:
                for cl in ENCLITICS:
                    if cl == "si":
                        continue
                    out.append((enclitic(p, cl), f"imperativo {IMP_PERSONS[i]} + {cl}"))
    return out


# ---------------------------------------------------------------------------------------------
# Nouns and adjectives
# ---------------------------------------------------------------------------------------------

def is_vowel(ch: str) -> bool:
    return ch in VOWELS


def needs_lo(w: str) -> bool:
    w = w.lower()
    return (w[:1] == "s" and len(w) > 1 and not is_vowel(w[1])) or w[:1] in ("z", "x", "y") or \
        w[:2] in ("gn", "ps", "pn") or (w[:1] == "i" and len(w) > 1 and is_vowel(w[1]))


def article(w: str, gender: str, plural: bool) -> str:
    """Definite article + noun: il libro, lo zio, l'amico, gli amici, la casa, le case."""
    starts_vowel = is_vowel(w[:1].lower()) or w[:1].lower() == "h"
    if gender == "f":
        if plural:
            return "le " + w
        return ("l'" + w) if starts_vowel and not (w[:1] == "i" and len(w) > 1 and is_vowel(w[1])) else "la " + w
    if plural:
        return ("gli " + w) if starts_vowel or needs_lo(w) else "i " + w
    if starts_vowel and not needs_lo(w):
        return "l'" + w
    return ("lo " + w) if needs_lo(w) else "il " + w


ACCENTED_END = tuple("àèéìòù")


def plural_candidates(sg: str, gender: str) -> list[str]:
    """Rule-based plural candidates for a noun or adjective form, most likely first."""
    w = sg
    if " " in w or "-" in w:
        return [w]
    if w.endswith(ACCENTED_END) or not is_vowel(w[-1]) or len(w) <= 2:
        return [w]
    if w.endswith("ie"):
        return [w, w[:-1]]                # specie (inv), moglie -> mogli
    if w.endswith("i"):
        return [w]
    if w.endswith("o"):
        if w.endswith(("co", "go")):
            return [w[:-1] + "hi", w[:-1] + "i"] if not w.endswith(("ico", "ogo")) else [w[:-1] + "i", w[:-1] + "hi"]
        if w.endswith("io"):
            return [w[:-1], w[:-1] + "i"]
        return [w[:-1] + "i", w]          # foto/radio/auto are invariable
    if w.endswith("a"):
        if gender == "m":
            if w.endswith(("ca", "ga")):
                return [w[:-1] + "hi"]
            return [w[:-1] + "i", w]
        if w.endswith(("ca", "ga")):
            return [w[:-1] + "he"]
        if w.endswith(("cia", "gia")):
            if len(w) > 3 and not is_vowel(w[-4]):
                return [w[:-2] + "e", w[:-1] + "e"]
            return [w[:-1] + "e", w[:-2] + "e"]
        return [w[:-1] + "e", w]
    if w.endswith("e"):
        return [w[:-1] + "i", w]
    return [w]


def adj_forms(ms: str, wiki: set[str]) -> dict | None:
    """Return {"ms","fs","mp","fp"} for an adjective in its masculine singular, using the
    Wiktionary form list (normalised) to resolve -chi/-ci, -cie/-ce etc."""
    def pick(cands):
        for c in cands:
            if norm(c) in wiki:
                return c
        return cands[0]
    if ms.endswith("o"):
        fs = ms[:-1] + "a"
        mp = pick(plural_candidates(ms, "m"))
        fp = pick(plural_candidates(fs, "f"))
        return dict(ms=ms, fs=fs, mp=mp, fp=fp)
    if ms.endswith("e"):
        p = ms[:-1] + "i"
        return dict(ms=ms, fs=ms, mp=p, fp=p)
    if ms.endswith("a"):   # ottimista, belga
        if ms.endswith(("ca", "ga")):
            return dict(ms=ms, fs=ms, mp=ms[:-1] + "hi", fp=ms[:-1] + "he")
        return dict(ms=ms, fs=ms, mp=ms[:-1] + "i", fp=ms[:-1] + "e")
    return dict(ms=ms, fs=ms, mp=ms, fp=ms)


SPECIAL_ADJ = {
    # lemma: extra rows (label, singular, plural) and tags
    "bello": [("davanti a consonante", "bel", "bei"), ("davanti a vocale / s+cons., z", "bell'", "begli")],
    "buono": [("davanti a nome maschile", "buon", "")],
    "grande": [("forma tronca", "gran, grand'", "")],
    "santo": [("forma tronca", "san, sant'", "")],
    "quello": [("davanti a consonante", "quel", "quei"), ("davanti a vocale / s+cons., z", "quell'", "quegli")],
    "nessuno": [("davanti a nome maschile", "nessun", "")],
    "alcuno": [("davanti a nome maschile", "alcun", "")],
    "ciascuno": [("davanti a nome maschile", "ciascun", "")],
    "uno": [("davanti a nome maschile", "un", ""), ("davanti a vocale (f.)", "un'", "")],
    "tale": [("forma tronca", "tal", "")],
    "quale": [("forma tronca", "qual", "")],
}

COMPARATIVES = {
    "buono": ("migliore", "ottimo", "buonissimo"),
    "cattivo": ("peggiore", "pessimo", "cattivissimo"),
    "grande": ("maggiore", "massimo", "grandissimo"),
    "piccolo": ("minore", "minimo", "piccolissimo"),
    "alto": ("superiore", "supremo / sommo", "altissimo"),
    "basso": ("inferiore", "infimo", "bassissimo"),
    "bene": ("meglio", "benissimo / ottimamente", ""),
    "male": ("peggio", "malissimo / pessimamente", ""),
    "molto": ("più", "moltissimo", ""),
    "poco": ("meno", "pochissimo", ""),
}
