package com.gleaped.italianlearning.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.speech.tts.TextToSpeech
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gleaped.italianlearning.AppContainer
import com.gleaped.italianlearning.data.db.ReminderEntity
import com.gleaped.italianlearning.data.settings.Settings
import com.gleaped.italianlearning.data.settings.ThemeMode
import com.gleaped.italianlearning.reminders.Notifications
import com.gleaped.italianlearning.reminders.toMask
import com.gleaped.italianlearning.reminders.toSpec
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.roundToInt

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.settings
    private val reminderDao = container.database.reminderDao()

    val settings: StateFlow<Settings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val reminders: StateFlow<List<ReminderEntity>> =
        reminderDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun updateReminders(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            container.reminderScheduler.reschedule()
        }
    }

    fun setSpeechRate(v: Float) {
        container.tts.setRate(v)
        update { repo.setSpeechRate(v) }
    }

    fun setAutoPlay(v: Boolean) = update { repo.setAutoPlay(v) }
    fun setSpeechPractice(v: Boolean) = update { repo.setSpeechPractice(v) }
    fun setDailyNewCards(v: Int) = update { repo.setDailyNewCards(v) }
    fun setUnlockAll(v: Boolean) = update { repo.setUnlockAll(v) }
    fun setTheme(v: ThemeMode) = update { repo.setThemeMode(v) }
    fun setPaused(v: Boolean) = updateReminders { repo.setRemindersPaused(v) }
    fun setReminderGrammar(v: Boolean) = update { repo.setReminderGrammar(v) }
    fun setReminderReview(v: Boolean) = update { repo.setReminderReview(v) }
    fun setReminderNextLesson(v: Boolean) = update { repo.setReminderNextLesson(v) }

    fun addReminder(hour: Int, minute: Int) = updateReminders {
        reminderDao.insert(ReminderEntity(hour = hour, minute = minute, daysMask = DayOfWeek.entries.toSet().toMask(), enabled = true))
    }

    fun updateReminder(r: ReminderEntity) = updateReminders { reminderDao.update(r) }
    fun deleteReminder(r: ReminderEntity) = updateReminders { reminderDao.delete(r) }

    fun toggleDay(r: ReminderEntity, day: DayOfWeek) {
        val days = r.toSpec().days.toMutableSet()
        if (!days.add(day)) days.remove(day)
        updateReminder(r.copy(daysMask = days.toMask()))
    }

    fun testVoice() = container.tts.speak("Ciao! Questa è la voce italiana.")

    fun resetProgress() = update { container.learning.resetProgress() }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}

private fun formatTime(context: Context, hour: Int, minute: Int): String =
    if (DateFormat.is24HourFormat(context)) {
        String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    } else {
        val h = if (hour % 12 == 0) 12 else hour % 12
        String.format(Locale.ROOT, "%d:%02d %s", h, minute, if (hour < 12) "AM" else "PM")
    }

private fun showTimePicker(context: Context, hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h, m) }, hour, minute, DateFormat.is24HourFormat(context)).show()
}

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Nothing to open on this device; the setting simply isn't available.
    }
}

private val DAY_LABELS = mapOf(
    DayOfWeek.MONDAY to "Mon",
    DayOfWeek.TUESDAY to "Tue",
    DayOfWeek.WEDNESDAY to "Wed",
    DayOfWeek.THURSDAY to "Thu",
    DayOfWeek.FRIDAY to "Fri",
    DayOfWeek.SATURDAY to "Sat",
    DayOfWeek.SUNDAY to "Sun",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settingsOrNull by vm.settings.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val settings = settingsOrNull ?: return

    var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
    var confirmReset by remember { mutableStateOf(false) }

    val addWithPicker = {
        showTimePicker(context, 19, 0) { h, m -> vm.addReminder(h, m) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted && Notifications.canPost(context)
        // Create the reminder either way; a banner explains how to enable notifications.
        addWithPicker()
    }
    val onAddReminder = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationsAllowed = Notifications.canPost(context)
            addWithPicker()
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("This clears completed lessons, review cards and your streak. Reminders and settings are kept. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetProgress()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineMedium)

        // ---------------- Reminders
        SectionTitle("Reminders")
        SwitchRow(
            title = "Pause all reminders",
            subtitle = "Keeps your reminders but stops notifications until you switch this off.",
            checked = settings.remindersPaused,
            onChange = vm::setPaused,
        )
        if (reminders.isNotEmpty() && !notificationsAllowed) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Notifications are turned off for Italiano, so reminders can't appear.")
                    TextButton(onClick = {
                        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        safeStart(context, intent)
                    }) { Text("Open notification settings") }
                }
            }
        }
        if (reminders.isEmpty()) {
            Text(
                "No reminders yet. Add one or more gentle nudges at times that suit you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reminders.forEach { r ->
            val days = r.toSpec().days
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            showTimePicker(context, r.hour, r.minute) { h, m -> vm.updateReminder(r.copy(hour = h, minute = m)) }
                        }) {
                            Text(formatTime(context, r.hour, r.minute), style = MaterialTheme.typography.headlineSmall)
                        }
                        Spacer(Modifier.weight(1f))
                        Switch(checked = r.enabled, onCheckedChange = { vm.updateReminder(r.copy(enabled = it)) })
                        IconButton(onClick = { vm.deleteReminder(r) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete reminder")
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in days,
                                onClick = { vm.toggleDay(r, day) },
                                label = { Text(DAY_LABELS.getValue(day)) },
                            )
                        }
                    }
                    if (days.isEmpty()) {
                        Text("Pick at least one day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        FilledTonalButton(onClick = onAddReminder, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("Add reminder")
        }
        Text(
            "If you haven't opened the app for a couple of days, reminders quieten down to at most one a day until you're back.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("What reminders can be about", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        SwitchRow(
            "Grammar tips & challenges",
            "A rule from a unit you've started, with a quick challenge to try.",
            settings.reminderGrammar,
            vm::setReminderGrammar,
        )
        SwitchRow(
            "Vocabulary review",
            "When review cards are ready.",
            settings.reminderReview,
            vm::setReminderReview,
        )
        SwitchRow(
            "Next lesson",
            "Your next lesson on the path.",
            settings.reminderNextLesson,
            vm::setReminderNextLesson,
        )
        if (!settings.reminderGrammar && !settings.reminderReview && !settings.reminderNextLesson) {
            Text(
                "With all three off, reminders use a short friendly nudge instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        // ---------------- Audio and speech
        SectionTitle("Audio & speaking")
        Text("Speech speed: ${(settings.speechRate * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = settings.speechRate,
            onValueChange = vm::setSpeechRate,
            valueRange = 0.5f..1.3f,
            steps = 7,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::testVoice) { Text("Test voice") }
            OutlinedButton(onClick = {
                safeStart(context, Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Install Italian voice") }
        }
        SwitchRow("Play audio automatically", "Listening exercises and role-plays start playing on their own.", settings.autoPlayAudio, vm::setAutoPlay)
        SwitchRow(
            "Speaking practice",
            "Use the microphone for speaking exercises. Off = type your answers instead.",
            settings.speechPracticeEnabled,
            vm::setSpeechPractice,
        )

        HorizontalDivider()

        // ---------------- Learning
        SectionTitle("Learning")
        Text("New review cards per day: ${settings.dailyNewCards}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = settings.dailyNewCards.toFloat(),
            onValueChange = { vm.setDailyNewCards(it.roundToInt()) },
            valueRange = 0f..40f,
            steps = 7,
        )
        SwitchRow("Unlock all units", "Skip ahead freely instead of finishing units in order.", settings.unlockAllUnits, vm::setUnlockAll)

        HorizontalDivider()

        // ---------------- Appearance
        SectionTitle("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { vm.setTheme(mode) },
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                        )
                    },
                )
            }
        }

        HorizontalDivider()

        SectionTitle("Data")
        OutlinedButton(onClick = { confirmReset = true }) { Text("Reset progress…") }
        Text(
            "Italiano works fully offline. Nothing leaves your phone: no account, no tracking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Version ${appVersion(context)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Suppress("DEPRECATION")
private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
