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
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setSpeechRate(value: Float) = store.edit { it[Keys.speechRate] = value }
    suspend fun setAutoPlay(value: Boolean) = store.edit { it[Keys.autoPlay] = value }
    suspend fun setSpeechPractice(value: Boolean) = store.edit { it[Keys.speechPractice] = value }
    suspend fun setDailyNewCards(value: Int) = store.edit { it[Keys.dailyNewCards] = value }
    suspend fun setUnlockAll(value: Boolean) = store.edit { it[Keys.unlockAll] = value }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[Keys.theme] = value.name }
    suspend fun setRemindersPaused(value: Boolean) = store.edit { it[Keys.remindersPaused] = value }
    suspend fun markOpened(nowMillis: Long) = store.edit { it[Keys.lastOpened] = nowMillis }
    suspend fun markReminderFired(nowMillis: Long) = store.edit { it[Keys.lastFired] = nowMillis }
}
