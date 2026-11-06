package com.gleaped.italianlearning

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gleaped.italianlearning.data.settings.Settings
import com.gleaped.italianlearning.reminders.Notifications
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.navigation.AppNavigation
import com.gleaped.italianlearning.ui.theme.ItalianoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var openTodaySignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ItalianoApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            LaunchedEffect(settings.speechRate) { container.tts.setRate(settings.speechRate) }
            CompositionLocalProvider(LocalAppContainer provides container) {
                ItalianoTheme(themeMode = settings.themeMode) {
                    AppNavigation(openTodaySignal = openTodaySignal)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(Notifications.EXTRA_OPEN_TODAY, false)) openTodaySignal++
    }

    override fun onStart() {
        super.onStart()
        // Opening the app ends "quiet mode" and re-arms the next reminder.
        val container = (application as ItalianoApp).container
        lifecycleScope.launch {
            container.settings.markOpened(System.currentTimeMillis())
            container.reminderScheduler.reschedule()
        }
    }
}
