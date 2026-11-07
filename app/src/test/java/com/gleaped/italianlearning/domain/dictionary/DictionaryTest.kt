package com.gleaped.italianlearning.domain.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordNormalizerTest {
    @Test
    fun matchesFormNormSpec() {
        assertEquals("ando", WordNormalizer.normalize("andò"))
        assertEquals("perche", WordNormalizer.normalize("Perché"))
        assertEquals("citta", WordNormalizer.normalize("  CITTÀ "))
        assertEquals("dellacqua", WordNormalizer.normalize("dell'acqua"))
        assertEquals("po", WordNormalizer.normalize("po’"))
        assertEquals("e", WordNormalizer.normalize("È"))
        // Decomposed input (e + combining grave) behaves like the precomposed letter.
        assertEquals("ando", WordNormalizer.normalize("andò"))
        assertEquals("fa male", WordNormalizer.normalize("fa   male"))
        assertEquals("", WordNormalizer.normalize(" ' "))
    }

    @Test
    fun lowerKeepAccents() {
        assertEquals("città", WordNormalizer.lowerKeepAccents("Città "))
    }

    @Test
    fun wordAtOffset() {
        val s = "Ieri andò dell'acqua, po' di più."
        assertEquals("Ieri", WordNormalizer.wordAt(s, 0))
        assertEquals("andò", WordNormalizer.wordAt(s, 7))
        assertEquals("andò", WordNormalizer.wordAt(s, 9)) // just after the word
        assertEquals("dell'", WordNormalizer.wordAt(s, 11))
        assertEquals("acqua", WordNormalizer.wordAt(s, 16))
        assertEquals("po", WordNormalizer.wordAt(s, 22))
        assertEquals("più", WordNormalizer.wordAt(s, 30))
        assertNull(WordNormalizer.wordAt("a  b", 2))
        assertNull(WordNormalizer.wordAt("", 0))
        assertEquals("b", WordNormalizer.wordAt("a b", 99))
    }
}

class DictionaryLookupTest {

    private fun lemma(id: Long, lemma: String, pos: String = "verb", freq: Int? = null, gloss: String = "x", gender: String? = null) =
        LemmaRow(id, lemma, pos, gender, gloss, null, null, null, freq, null, """{"tables":[]}""")

    private val andare = lemma(1, "andare", freq = 50, gloss = "go ; walk")
    private val anda = lemma(2, "anda", pos = "noun", freq = null)
    private val andata = lemma(3, "andata", pos = "noun", freq = 3000, gender = "f")
    private val andante = lemma(4, "andante", pos = "adj", freq = 9000)
    private val citta = lemma(5, "città", pos = "noun", freq = 200, gender = "f")
    private val casa = lemma(6, "casa", pos = "noun", freq = 100, gender = "f")
    private val all = listOf(andare, anda, andata, andante, citta, casa)

    private val forms = listOf(
        FormMatch("andò", "indicativo passato remoto, lui/lei", 1),
        FormMatch("anda", "singolare", 2),
        FormMatch("andare", "infinito", 1),
        FormMatch("città", "singolare", 5),
        FormMatch("città", "plurale", 5),
        FormMatch("casa", "singolare", 6),
        FormMatch("va", "indicativo presente, lui/lei", 1),
        FormMatch("va", "imperativo, tu", 1),
    )

    /** Fake that behaves like the SQL: exact form_norm, then a lemma range scan ordered by freq. */
    private inner class FakeStore : DictionaryStore {
        val calls = mutableListOf<String>()
        override fun formsByNorm(norm: String, limit: Int): List<Pair<FormMatch, LemmaRow>> {
            calls += "forms:$norm"
            return forms.filter { WordNormalizer.normalize(it.form) == norm }
                .map { f -> f to all.first { it.id == f.lemmaId } }
                .sortedBy { it.second.freqRank ?: Int.MAX_VALUE }
                .take(limit)
        }
        override fun lemmasInRange(lower: String, upper: String, limit: Int): List<LemmaRow> {
            calls += "range:$lower"
            return all.filter { it.lemma >= lower && it.lemma < upper }.sortedBy { it.freqRank ?: Int.MAX_VALUE }.take(limit)
        }
        override fun lemma(id: Long) = all.firstOrNull { it.id == id }
        override fun meta() = mapOf("version" to "1")
    }

    @Test
    fun planNormalisesAndKeepsAccentedPrefix() {
        val p = DictionaryLookup.plan(" Città ")
        assertEquals("citta", p.norm)
        assertEquals(listOf("città", "citta"), p.prefixes)
        assertEquals(listOf("and"), DictionaryLookup.plan("AND").prefixes)
        assertTrue(DictionaryLookup.plan("a").prefixes.isEmpty())
        assertTrue(DictionaryLookup.plan("  ").isEmpty)
    }

    @Test
    fun prefixUpperBoundCoversPrefix() {
        val upper = DictionarySql.prefixUpperBound("and")
        assertTrue("andare" < upper && "andzzz" < upper && "and" < upper)
        assertTrue("ane" > upper)
    }

    @Test
    fun exactFormFirstThenPrefixByFrequency() {
        val store = FakeStore()
        val groups = DictionaryLookup(store).search("ando")
        // "andò" matches andare exactly; no lemma starts with "ando".
        assertEquals(listOf("andare"), groups.map { it.lemma.lemma })
        assertEquals("andò → andare · indicativo passato remoto, lui/lei", groups[0].matchLine)
        assertEquals(listOf("forms:ando", "range:ando"), store.calls)
        // Prefix only: by freq_rank, unknown frequency last.
        val prefix = DictionaryLookup(store).search("and")
        assertEquals(listOf("andare", "andata", "andante", "anda"), prefix.map { it.lemma.lemma })
        assertNull(prefix[0].matchLine)
    }

    @Test
    fun lemmaSpelledLikeQueryRanksFirstEvenIfRarer() {
        val groups = DictionaryLookup(FakeStore()).search("anda")
        assertEquals("anda", groups.first().lemma.lemma)
        assertFalse(groups.first().matchedInflection)
        assertEquals(listOf("anda", "andare", "andata", "andante"), groups.map { it.lemma.lemma })
    }

    @Test
    fun sameFormSeveralReadingsMergeIntoOneGroup() {
        val groups = DictionaryLookup(FakeStore()).search("va")
        assertEquals(1, groups.size)
        assertEquals("va → andare · indicativo presente, lui/lei / imperativo, tu", groups.single().matchLine)
    }

    @Test
    fun accentlessQueryFindsAccentedLemmaOnce() {
        val groups = DictionaryLookup(FakeStore()).search("citta")
        assertEquals(listOf("città"), groups.map { it.lemma.lemma })
        assertNull(groups.single().matchLine)
    }

    @Test
    fun emptyQueryRunsNothing() {
        val store = FakeStore()
        assertTrue(DictionaryLookup(store).search(" ").isEmpty())
        assertTrue(store.calls.isEmpty())
    }

    @Test
    fun glossesAndLabels() {
        assertEquals(listOf("go", "walk"), andare.glosses)
        assertEquals("go", andare.firstGloss)
        assertEquals("nome f.", citta.posLabel)
        assertEquals("verbo", andare.posLabel)
    }

    @Test
    fun recentLookups() {
        var r = emptyList<String>()
        r = RecentLookups.add(r, "andare")
        r = RecentLookups.add(r, "casa")
        r = RecentLookups.add(r, "Andare")
        assertEquals(listOf("Andare", "casa"), r)
        assertEquals(r, RecentLookups.decode(RecentLookups.encode(r)))
        assertEquals(3, (1..10).fold(emptyList<String>()) { acc, i -> RecentLookups.add(acc, "w$i", max = 3) }.size)
        assertTrue(RecentLookups.decode(null).isEmpty())
    }

    @Test
    fun installPlan() {
        assertEquals(InstallAction.COPY, DictionaryInstallPlan.decide(null, null, "s1"))
        assertEquals(InstallAction.COPY, DictionaryInstallPlan.decide(null, "s1", "s1"))
        assertEquals(InstallAction.USE_INSTALLED, DictionaryInstallPlan.decide("3", "s1", "s1"))
        assertEquals(InstallAction.COPY_AND_COMPARE, DictionaryInstallPlan.decide("3", "s1", "s2"))
        assertTrue(DictionaryInstallPlan.shouldReplace("3", "4"))
        assertFalse(DictionaryInstallPlan.shouldReplace("3", "3"))
        assertFalse(DictionaryInstallPlan.shouldReplace("3", null))
    }
}

class FormsParserTest {
    private val verbJson = """
        { "tables": [
          { "title": "Indicativo presente", "columns": ["", ""], "rows": [["io", "vado"], ["tu", "vai"]] },
          { "title": "Infinito", "columns": [""], "rows": [["andare"]] },
          { "title": "Congiuntivo presente", "columns": ["", ""], "rows": [["che io", "vada"]] },
          { "title": "Passato prossimo", "columns": ["", ""], "rows": [["io", "sono andato/a"]] },
          { "title": "Condizionale passato", "columns": ["", ""], "rows": [["io", "sarei andato/a"]] },
          { "title": "Imperativo", "columns": ["", ""], "rows": [["tu", "va'/vai"]] },
          { "title": "Empty", "columns": ["", ""], "rows": [] },
          { "title": "Weird", "extra": 1, "rows": [["x"]] }
        ] }
    """.trimIndent()

    @Test
    fun parsesTablesAndDropsEmptyOnes() {
        val tables = FormsParser.parse(verbJson)
        assertEquals(7, tables.size)
        assertEquals("vado", tables[0].rows[0][1])
        assertTrue(FormsParser.parse("not json").isEmpty())
        assertTrue(FormsParser.parse(null).isEmpty())
        assertTrue(FormsParser.parse("{}").isEmpty())
    }

    @Test
    fun adjectiveRowsPad() {
        val t = FormsParser.parse(
            """{"tables":[{"title":"Forme","columns":["","singolare","plurale"],"rows":[["maschile","bello"],["femminile","bella","belle"]]}]}""",
        ).single()
        assertEquals(listOf("maschile", "bello", ""), t.cells(0))
        assertEquals(1, FormsParser.group("adj", listOf(t)).size)
        assertNull(FormsParser.group("adj", listOf(t)).single().title)
    }

    @Test
    fun verbsGroupByMoodInOrder() {
        val groups = FormsParser.group("verb", FormsParser.parse(verbJson))
        assertEquals(
            listOf("Indicativo", "Congiuntivo", "Condizionale", "Imperativo", "Forme indefinite", "Altre forme"),
            groups.map { it.title },
        )
        assertEquals(listOf("Indicativo presente", "Passato prossimo"), groups[0].tables.map { it.title })
        assertEquals("Indicativo", FormsParser.moodOf("Trapassato prossimo"))
        assertEquals("Forme indefinite", FormsParser.moodOf("Participio passato"))
        assertEquals("Congiuntivo", FormsParser.moodOf("congiuntivo trapassato"))
    }
}
