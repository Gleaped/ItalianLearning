package com.gleaped.italianlearning.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gleaped.italianlearning.domain.reminders.ReminderKind
import com.gleaped.italianlearning.domain.reminders.TipSelector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val speechRate: Float = 0.9f,
    val autoPlayAudio: Boolean = true,
    val speechPracticeEnabled: Boolean = true,
    val dailyNewCards: Int = 10,
    val unlockAllUnits: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val remindersPaused: Boolean = false,
    /** 0 = never opened. */
    val lastOpenedMillis: Long = 0L,
    /** 0 = never fired. */
    val lastReminderFiredMillis: Long = 0L,
    /** Which reminder kinds may be sent (all on by default). */
    val reminderGrammar: Boolean = true,
    val reminderReview: Boolean = true,
    val reminderNextLesson: Boolean = true,
) {
    val allowedReminderKinds: Set<ReminderKind>
        get() = buildSet {
            if (reminderGrammar) add(ReminderKind.GRAMMAR)
            if (reminderReview) add(ReminderKind.REVIEW)
            if (reminderNextLesson) add(ReminderKind.NEXT_LESSON)
        }
}

/** Reminder rotation state, kept apart from [Settings] so the UI does not recompose on every reminder. */
data class ReminderState(
    /** Increments with every reminder posted; drives [ReminderRotation]. */
    val sequence: Long = 0L,
    /** Grammar tip ids shown (in a notification or the challenge screen), oldest first. */
    val tipHistory: List<String> = emptyList(),
    /** Units the learner has opened (remedial ones feed grammar tips). */
    val openedUnitIds: Set<String> = emptySet(),
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val speechRate = floatPreferencesKey("speech_rate")
        val autoPlay = booleanPreferencesKey("auto_play_audio")
        val speechPractice = booleanPreferencesKey("speech_practice")
        val dailyNewCards = intPreferencesKey("daily_new_cards")
        val unlockAll = booleanPreferencesKey("unlock_all_units")
        val theme = stringPreferencesKey("theme_mode")
        val remindersPaused = booleanPreferencesKey("reminders_paused")
        val lastOpened = longPreferencesKey("last_opened_millis")
        val lastFired = longPreferencesKey("last_reminder_fired_millis")
        val reminderGrammar = booleanPreferencesKey("reminder_kind_grammar")
        val reminderReview = booleanPreferencesKey("reminder_kind_review")
        val reminderNextLesson = booleanPreferencesKey("reminder_kind_next_lesson")
        val reminderSequence = longPreferencesKey("reminder_sequence")
        val tipHistory = stringPreferencesKey("tip_history")
        val openedUnits = stringSetPreferencesKey("opened_units")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        val defaults = Settings()
        Settings(
            speechRate = p[Keys.speechRate] ?: defaults.speechRate,
            autoPlayAudio = p[Keys.autoPlay] ?: defaults.autoPlayAudio,
            speechPracticeEnabled = p[Keys.speechPractice] ?: defaults.speechPracticeEnabled,
            dailyNewCards = p[Keys.dailyNewCards] ?: defaults.dailyNewCards,
            unlockAllUnits = p[Keys.unlockAll] ?: defaults.unlockAllUnits,
            themeMode = p[Keys.theme]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            remindersPaused = p[Keys.remindersPaused] ?: defaults.remindersPaused,
            lastOpenedMillis = p[Keys.lastOpened] ?: 0L,
            lastReminderFiredMillis = p[Keys.lastFired] ?: 0L,
            reminderGrammar = p[Keys.reminderGrammar] ?: defaults.reminderGrammar,
            reminderReview = p[Keys.reminderReview] ?: defaults.reminderReview,
            reminderNextLesson = p[Keys.reminderNextLesson] ?: defaults.reminderNextLesson,
        )
    }.distinctUntilChanged()

    val reminderState: Flow<ReminderState> = store.data.map { p ->
        ReminderState(
            sequence = p[Keys.reminderSequence] ?: 0L,
            tipHistory = decodeHistory(p[Keys.tipHistory]),
            openedUnitIds = p[Keys.openedUnits] ?: emptySet(),
        )
    }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()
    suspend fun currentReminderState(): ReminderState = reminderState.first()

    suspend fun setSpeechRate(value: Float) = store.edit { it[Keys.speechRate] = value }
    suspend fun setAutoPlay(value: Boolean) = store.edit { it[Keys.autoPlay] = value }
    suspend fun setSpeechPractice(value: Boolean) = store.edit { it[Keys.speechPractice] = value }
    suspend fun setDailyNewCards(value: Int) = store.edit { it[Keys.dailyNewCards] = value }
    suspend fun setUnlockAll(value: Boolean) = store.edit { it[Keys.unlockAll] = value }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[Keys.theme] = value.name }
    suspend fun setRemindersPaused(value: Boolean) = store.edit { it[Keys.remindersPaused] = value }
    suspend fun markOpened(nowMillis: Long) = store.edit { it[Keys.lastOpened] = nowMillis }
    suspend fun markReminderFired(nowMillis: Long) = store.edit { it[Keys.lastFired] = nowMillis }
    suspend fun setReminderGrammar(value: Boolean) = store.edit { it[Keys.reminderGrammar] = value }
    suspend fun setReminderReview(value: Boolean) = store.edit { it[Keys.reminderReview] = value }
    suspend fun setReminderNextLesson(value: Boolean) = store.edit { it[Keys.reminderNextLesson] = value }

    /** Records a posted reminder: advances the rotation and, for a grammar tip, its history. */
    suspend fun recordReminder(nowMillis: Long, tipId: String?) = store.edit { p ->
        p[Keys.lastFired] = nowMillis
        p[Keys.reminderSequence] = (p[Keys.reminderSequence] ?: 0L) + 1
        if (tipId != null) p[Keys.tipHistory] = encodeHistory(TipSelector.recordShown(decodeHistory(p[Keys.tipHistory]), tipId))
    }

    /** A tip seen in the app counts as shown, so a reminder does not repeat it straight away. */
    suspend fun markTipShown(tipId: String) = store.edit { p ->
        p[Keys.tipHistory] = encodeHistory(TipSelector.recordShown(decodeHistory(p[Keys.tipHistory]), tipId))
    }

    suspend fun markUnitOpened(unitId: String) = store.edit { p ->
        val current = p[Keys.openedUnits] ?: emptySet()
        if (unitId !in current) p[Keys.openedUnits] = current + unitId
    }

    private companion object {
        fun decodeHistory(raw: String?): List<String> =
            raw?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

        fun encodeHistory(ids: List<String>): String = ids.joinToString("\n")
    }
}
