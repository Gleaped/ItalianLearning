package com.gleaped.italianlearning

import com.gleaped.italianlearning.data.backup.BackupRepository
import android.app.Application
import android.content.Context
import com.gleaped.italianlearning.data.LearningRepository
import com.gleaped.italianlearning.data.content.ContentRepository
import com.gleaped.italianlearning.data.db.AppDatabase
import com.gleaped.italianlearning.data.dictionary.DictionaryRepository
import com.gleaped.italianlearning.data.reference.ReferenceRepository
import com.gleaped.italianlearning.data.settings.SettingsRepository
import com.gleaped.italianlearning.reminders.Notifications
import com.gleaped.italianlearning.reminders.ReminderContentBuilder
import com.gleaped.italianlearning.reminders.ReminderScheduler
import com.gleaped.italianlearning.reminders.Shortcuts
import com.gleaped.italianlearning.data.flags.FlagRepository
import com.gleaped.italianlearning.data.writing.WritingRepository
import com.gleaped.italianlearning.speech.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val content: ContentRepository by lazy { ContentRepository(appContext, settings) }
    val reference: ReferenceRepository by lazy { ReferenceRepository(appContext) }
    val dictionary: DictionaryRepository by lazy { DictionaryRepository(appContext) }
    val learning: LearningRepository by lazy { LearningRepository(database) }
    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(appContext, database.reminderDao(), settings)
    }
    val reminderContent: ReminderContentBuilder by lazy { ReminderContentBuilder(content, learning, settings) }
    val tts: TtsManager by lazy { TtsManager(appContext) }
    val writing: WritingRepository by lazy { WritingRepository(appContext) }
    val flags: FlagRepository by lazy { FlagRepository(appContext) }
    val backup: BackupRepository by lazy { BackupRepository(database, writing, settings) }
}

class ItalianoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        Shortcuts.publish(this)
    }
}
