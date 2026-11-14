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
import com.gleaped.italianlearning.domain.progress.LessonCheckpoint
import com.gleaped.italianlearning.domain.progress.LessonCheckpointCodec
import com.gleaped.italianlearning.domain.dictionary.RecentLookups
import com.gleaped.italianlearning.domain.profile.LearnerName
import com.gleaped.italianlearning.domain.reminders.ReminderKind
import com.gleaped.italianlearning.domain.reminders.TipSelector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Text-size setting; [scale] multiplies the whole type scale. */
enum class TextSize(val scale: Float, val label: String) {
    SMALL(0.9f, "S"),
    MEDIUM(1.0f, "M"),
    LARGE(1.15f, "L"),
    EXTRA_LARGE(1.3f, "XL"),
}

/** Fallback when the learner skipped the name prompt; used in dialogues that address them. */
const val DEFAULT_LEARNER_NAME = "Alex"

data class Settings(
    val speechRate: Float = 0.9f,
    val autoPlayAudio: Boolean = true,
    val speechPracticeEnabled: Boolean = true,
    /** Name of the Italian voice chosen in Settings; blank = the best one installed. */
    val voiceName: String = "",
    /** In role-plays the partner's line is heard before its text is shown. */
    val listenFirst: Boolean = true,
    val dailyNewCards: Int = 10,
    val unlockAllUnits: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val remindersPaused: Boolean = false,
    /** No reminders for the rest of a day on which the learner has practised. */
    val quietAfterPractice: Boolean = false,
    /** 0 = never opened. */
    val lastOpenedMillis: Long = 0L,
    /** 0 = never fired. */
    val lastReminderFiredMillis: Long = 0L,
    /** Which reminder kinds may be sent (all on by default). */
    val reminderGrammar: Boolean = true,
    val reminderReview: Boolean = true,
    val reminderNextLesson: Boolean = true,
    val textSize: TextSize = TextSize.MEDIUM,
    /** The learner's name as typed; blank until set. */
    val userName: String = "",
    /** True once the first-run welcome (name prompt) has been answered or skipped. */
    val onboarded: Boolean = false,
) {
    /** Name used in lesson dialogues. */
    val displayName: String get() = userName.trim().ifEmpty { DEFAULT_LEARNER_NAME }

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
        val recentLookups = stringPreferencesKey("recent_lookups")
        val textSize = stringPreferencesKey("text_size")
        val userName = stringPreferencesKey("user_name")
        val onboarded = booleanPreferencesKey("onboarded")
        val lessonCheckpoint = stringPreferencesKey("lesson_checkpoint")
        val voiceName = stringPreferencesKey("voice_name")
        val listenFirst = booleanPreferencesKey("listen_first")
        val quietAfterPractice = booleanPreferencesKey("quiet_after_practice")
    }

    /** The lesson the learner left part-way, if any. */
    val lessonCheckpoint: Flow<LessonCheckpoint?> =
        store.data.map { LessonCheckpointCodec.decode(it[Keys.lessonCheckpoint]) }.distinctUntilChanged()

    suspend fun currentLessonCheckpoint(): LessonCheckpoint? = lessonCheckpoint.first()

    suspend fun saveLessonCheckpoint(checkpoint: LessonCheckpoint) =
        store.edit { it[Keys.lessonCheckpoint] = LessonCheckpointCodec.encode(checkpoint) }

    suspend fun clearLessonCheckpoint() = store.edit { it.remove(Keys.lessonCheckpoint) }

    val settings: Flow<Settings> = store.data.map { p ->
        val defaults = Settings()
        Settings(
            speechRate = p[Keys.speechRate] ?: defaults.speechRate,
            autoPlayAudio = p[Keys.autoPlay] ?: defaults.autoPlayAudio,
            speechPracticeEnabled = p[Keys.speechPractice] ?: defaults.speechPracticeEnabled,
            voiceName = p[Keys.voiceName] ?: defaults.voiceName,
            listenFirst = p[Keys.listenFirst] ?: defaults.listenFirst,
            dailyNewCards = p[Keys.dailyNewCards] ?: defaults.dailyNewCards,
            unlockAllUnits = p[Keys.unlockAll] ?: defaults.unlockAllUnits,
            themeMode = p[Keys.theme]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            remindersPaused = p[Keys.remindersPaused] ?: defaults.remindersPaused,
            quietAfterPractice = p[Keys.quietAfterPractice] ?: defaults.quietAfterPractice,
            lastOpenedMillis = p[Keys.lastOpened] ?: 0L,
            lastReminderFiredMillis = p[Keys.lastFired] ?: 0L,
            reminderGrammar = p[Keys.reminderGrammar] ?: defaults.reminderGrammar,
            reminderReview = p[Keys.reminderReview] ?: defaults.reminderReview,
            reminderNextLesson = p[Keys.reminderNextLesson] ?: defaults.reminderNextLesson,
            textSize = p[Keys.textSize]?.let { name -> TextSize.entries.firstOrNull { it.name == name } } ?: defaults.textSize,
            userName = p[Keys.userName] ?: "",
            onboarded = p[Keys.onboarded] ?: false,
        )
    }.distinctUntilChanged()

    val reminderState: Flow<ReminderState> = store.data.map { p ->
        ReminderState(
            sequence = p[Keys.reminderSequence] ?: 0L,
            tipHistory = decodeHistory(p[Keys.tipHistory]),
            openedUnitIds = p[Keys.openedUnits] ?: emptySet(),
        )
    }.distinctUntilChanged()

    /** Words opened in the dictionary, most recent first. */
    val recentLookups: Flow<List<String>> = store.data.map { RecentLookups.decode(it[Keys.recentLookups]) }.distinctUntilChanged()

    suspend fun addRecentLookup(lemma: String) = store.edit { p ->
        p[Keys.recentLookups] = RecentLookups.encode(RecentLookups.add(RecentLookups.decode(p[Keys.recentLookups]), lemma))
    }

    suspend fun clearRecentLookups() = store.edit { it.remove(Keys.recentLookups) }

    suspend fun current(): Settings = settings.first()
    suspend fun currentReminderState(): ReminderState = reminderState.first()

    suspend fun setSpeechRate(value: Float) = store.edit { it[Keys.speechRate] = value }
    suspend fun setAutoPlay(value: Boolean) = store.edit { it[Keys.autoPlay] = value }
    suspend fun setSpeechPractice(value: Boolean) = store.edit { it[Keys.speechPractice] = value }
    suspend fun setVoiceName(value: String) = store.edit { it[Keys.voiceName] = value }
    suspend fun setListenFirst(value: Boolean) = store.edit { it[Keys.listenFirst] = value }
    suspend fun setDailyNewCards(value: Int) = store.edit { it[Keys.dailyNewCards] = value }
    suspend fun setUnlockAll(value: Boolean) = store.edit { it[Keys.unlockAll] = value }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[Keys.theme] = value.name }
    suspend fun setRemindersPaused(value: Boolean) = store.edit { it[Keys.remindersPaused] = value }
    suspend fun setQuietAfterPractice(value: Boolean) = store.edit { it[Keys.quietAfterPractice] = value }
    suspend fun markOpened(nowMillis: Long) = store.edit { it[Keys.lastOpened] = nowMillis }
    suspend fun markReminderFired(nowMillis: Long) = store.edit { it[Keys.lastFired] = nowMillis }
    suspend fun setReminderGrammar(value: Boolean) = store.edit { it[Keys.reminderGrammar] = value }
    suspend fun setReminderReview(value: Boolean) = store.edit { it[Keys.reminderReview] = value }
    suspend fun setReminderNextLesson(value: Boolean) = store.edit { it[Keys.reminderNextLesson] = value }
    suspend fun setTextSize(value: TextSize) = store.edit { it[Keys.textSize] = value.name }

    /** Saves the learner's name (trimmed, at most 30 characters) and ends the first-run prompt. */
    suspend fun setUserName(value: String) = store.edit {
        it[Keys.userName] = LearnerName.clean(value)
        it[Keys.onboarded] = true
    }

    suspend fun skipOnboarding() = store.edit { it[Keys.onboarded] = true }

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
