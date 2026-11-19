package com.gleaped.italianlearning.domain.profile

/** A one-time note after an update. Obtainium installs updates silently, so changes would otherwise go unnoticed. */
data class WhatsNewNote(val id: Int, val text: String)

object WhatsNew {
    /** Oldest first. Add a note with the next id when a release changes something a learner would notice. */
    val notes: List<WhatsNewNote> = listOf(
        WhatsNewNote(1, "New: after a few days away, Ripasso shows a lighter day instead of a pile of cards. Settings now folds the rarely used options away."),
    )

    /** The newest note the learner has not dismissed yet. */
    fun pending(seen: Int, all: List<WhatsNewNote> = notes): WhatsNewNote? =
        all.filter { it.id > seen }.maxByOrNull { it.id }
}
