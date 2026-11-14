package com.gleaped.italianlearning.data.core

import com.gleaped.italianlearning.data.LearningRepository
import com.gleaped.italianlearning.data.content.ContentRepository
import com.gleaped.italianlearning.data.dictionary.DictionaryRepository
import com.gleaped.italianlearning.data.settings.SettingsRepository
import com.gleaped.italianlearning.domain.srs.CoreDeck
import java.time.LocalDate

/** Adds the day's core words to the review deck, once a day. */
class CoreDeckService(
    private val settings: SettingsRepository,
    private val content: ContentRepository,
    private val dictionary: DictionaryRepository,
    private val learning: LearningRepository,
) {
    /**
     * Adds up to the daily number of the next core words. Does nothing when the setting is off,
     * when it already ran today, or when plenty of new cards are waiting. Returns how many were added.
     */
    suspend fun topUp(): Int {
        val perDay = settings.current().coreWordsPerDay
        if (perDay <= 0) return 0
        val today = LocalDate.now().toEpochDay()
        val (nextRank, lastDay) = settings.coreDeckState()
        if (lastDay == today || nextRank >= CoreDeck.END_RANK) return 0
        if (learning.waitingNewCardCount() >= CoreDeck.MAX_WAITING) return 0

        // The words the course teaches are left to the lessons.
        val known = CoreDeck.knownWords(content.load().units.values.flatMap { unit -> unit.vocabulary.map { it.it } })
        // Fetch more than needed: some candidates are skipped as known or unclear.
        val candidates = dictionary.contentWordsAfter(nextRank, perDay * 6 + 20)
        if (candidates.isEmpty()) return 0
        val chosen = CoreDeck.select(candidates, known, perDay)
        learning.addCoreWords(chosen)
        // Continue after the last word looked at, so skipped words are not looked at again.
        val reached = if (chosen.size < perDay) candidates.last() else chosen.last()
        settings.saveCoreDeckState(reached.freqRank ?: (nextRank + candidates.size), today)
        return chosen.size
    }
}
