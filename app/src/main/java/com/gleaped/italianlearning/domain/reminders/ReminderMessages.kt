package com.gleaped.italianlearning.domain.reminders

data class ReminderMessage(val title: String, val body: String)

/**
 * Gentle, varied reminder copy: a short Italian phrase plus English.
 * Never guilt-tripping, never mentions streak loss.
 */
object ReminderMessages {

    private val regular = listOf(
        ReminderMessage("Un caffè e un po' d'italiano? ☕", "A coffee and a little Italian? A short lesson is ready when you are."),
        ReminderMessage("Pronto per due minuti?", "Ready for two minutes? Even one exercise counts."),
        ReminderMessage("Piano piano si va lontano", "Slowly, slowly you go far. Your next lesson is waiting."),
        ReminderMessage("Ciao! Facciamo un po' di pratica?", "Hi! Fancy a bit of practice?"),
        ReminderMessage("Qualche parola nuova?", "A few new words? Your review cards are here whenever you like."),
        ReminderMessage("Che bello imparare insieme", "Nice to learn together. Pick up where you left off."),
        ReminderMessage("Un momento per te", "A moment for yourself, in Italian."),
        ReminderMessage("Sempre avanti!", "Onwards! A quick lesson fits in nicely right now."),
        ReminderMessage("Ascolta, ripeti, sorridi", "Listen, repeat, smile. Your Italian is waiting."),
        ReminderMessage("Oggi impariamo qualcosa di nuovo?", "Shall we learn something new today?"),
    )

    private val welcomeBack = listOf(
        ReminderMessage("Bentornato quando vuoi", "Welcome back whenever you like. Your lessons are right where you left them."),
        ReminderMessage("Nessuna fretta", "No rush at all. A tiny lesson is here when you feel like it."),
        ReminderMessage("Ci sei? Ti aspettiamo con calma", "Still there? We'll wait calmly. One small step is enough."),
        ReminderMessage("Un passo alla volta", "One step at a time. Just open a lesson and see how it feels."),
    )

    private val alreadyPracticed = listOf(
        ReminderMessage("Bravo, oggi hai già studiato!", "Well done, you've already practised today. Fancy a quick review too?"),
        ReminderMessage("Ottimo lavoro oggi", "Great work today. A few review cards, if you're in the mood."),
    )

    /**
     * Picks a message. [seed] should vary per notification (e.g. epoch day + reminder slot);
     * [inactive] picks the low-pressure "welcome back" set.
     */
    fun pick(seed: Long, inactive: Boolean, practicedToday: Boolean): ReminderMessage {
        val pool = when {
            inactive -> welcomeBack
            practicedToday -> alreadyPracticed
            else -> regular
        }
        val index = Math.floorMod(seed, pool.size.toLong()).toInt()
        return pool[index]
    }

    /** All copy, for tests checking the tone rules. */
    val all: List<ReminderMessage> get() = regular + welcomeBack + alreadyPracticed
}
