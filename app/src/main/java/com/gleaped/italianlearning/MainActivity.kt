package com.gleaped.italianlearning

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gleaped.italianlearning.data.settings.Settings
import com.gleaped.italianlearning.reminders.DeepLinks
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.navigation.AppNavigation
import com.gleaped.italianlearning.ui.navigation.PendingDeepLink
import com.gleaped.italianlearning.ui.onboarding.WelcomeScreen
import com.gleaped.italianlearning.ui.theme.ItalianoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Set when a notification opened (or re-opened) the app; consumed by [AppNavigation]. */
    private var pendingDeepLink by mutableStateOf<PendingDeepLink?>(null)
    private var deepLinkCounter = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ItalianoApp).container
        // Only a fresh launch: after recreation the launch intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        // Warm the content cache in the background so the course map opens instantly.
        lifecycleScope.launch { container.content.load() }
        setContent {
            val loaded by container.settings.settings.collectAsStateWithLifecycle<Settings?>(initialValue = null)
            val settings = loaded ?: Settings(onboarded = true)
            LaunchedEffect(settings.speechRate) { container.tts.setRate(settings.speechRate) }
            CompositionLocalProvider(LocalAppContainer provides container) {
                ItalianoTheme(themeMode = settings.themeMode, textSize = settings.textSize) {
                    if (loaded != null && !settings.onboarded) {
                        WelcomeScreen(
                            onSave = { name -> lifecycleScope.launch { container.settings.setUserName(name) } },
                            onSkip = { lifecycleScope.launch { container.settings.skipOnboarding() } },
                        )
                    } else {
                        AppNavigation(
                            deepLink = pendingDeepLink,
                            onDeepLinkHandled = { handled -> if (pendingDeepLink == handled) pendingDeepLink = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val target = DeepLinks.parse(intent) ?: return
        pendingDeepLink = PendingDeepLink(target, ++deepLinkCounter)
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
