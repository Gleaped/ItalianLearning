package com.gleaped.italianlearning.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gleaped.italianlearning.domain.conversation.ConversationEngine
import com.gleaped.italianlearning.domain.conversation.ConversationLog
import com.gleaped.italianlearning.domain.conversation.Correction
import com.gleaped.italianlearning.domain.conversation.Debrief
import com.gleaped.italianlearning.domain.conversation.SavedConversation
import com.gleaped.italianlearning.domain.conversation.Scenario
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.LookupText
import com.gleaped.italianlearning.ui.components.SpeakerButton
import com.gleaped.italianlearning.ui.lesson.SpeakOrType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The conversation practice: pick a scene, then talk with a partner who reacts to what you say. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    var scenarios by remember { mutableStateOf<List<Scenario>?>(null) }
    var chosen by remember { mutableStateOf<Scenario?>(null) }
    LaunchedEffect(Unit) {
        container.conversations.loadLog()
        scenarios = container.conversations.scenarios()
    }
    DisposableEffect(Unit) { onDispose { container.tts.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chosen?.title ?: "Conversazione") },
                navigationIcon = {
                    IconButton(onClick = {
                        container.tts.stop()
                        if (chosen != null) chosen = null else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val list = scenarios
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                chosen != null -> ScenePlayer(scenario = chosen!!, onExit = { chosen = null })
                else -> ScenarioList(list, onChoose = { chosen = it })
            }
        }
    }
}

@Composable
private fun ScenarioList(scenarios: List<Scenario>, onChoose: (Scenario) -> Unit) {
    val container = LocalAppContainer.current
    val log by container.conversations.entries.collectAsStateWithLifecycle()
    val done = ConversationLog.completions(log)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Talk with a partner who answers what you actually say. Speak or type; if you get stuck, the partner helps and you can ask for a hint. " +
                "Corrections come at the end, so the conversation keeps flowing.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (scenarios.isEmpty()) Text("No scenes were found.", style = MaterialTheme.typography.bodyLarge)
        var level = ""
        scenarios.forEach { s ->
            if (s.level != level) {
                level = s.level
                Text(level, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedCard(onClick = { onChoose(s) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        val count = done[s.id] ?: 0
                        if (count > 0) Text("done $count×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(s.setting, style = MaterialTheme.typography.bodyMedium)
                    Text("Goal: ${s.goal}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("about ${s.minutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private enum class Who { PARTNER, YOU, TIP, HELP }

private class Message(val who: Who, val text: String, val sub: String? = null)

@Composable
private fun ScenePlayer(scenario: Scenario, onExit: () -> Unit) {
    val container = LocalAppContainer.current
    val tts = container.tts
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)
    val speech = settings?.speechNow() ?: true
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var round by remember { mutableIntStateOf(0) }
    val engine = remember(scenario, round) { ConversationEngine(scenario) }
    val messages = remember(scenario, round) { mutableStateListOf<Message>() }
    var ended by remember(scenario, round) { mutableStateOf(false) }
    var debrief by remember(scenario, round) { mutableStateOf<Debrief?>(null) }
    var showText by remember { mutableStateOf(true) }
    var showHints by remember { mutableStateOf(true) }
    val revealed = remember(scenario, round) { mutableStateListOf<Int>() }
    val scroll = rememberScrollState()

    fun partnerSays(lines: List<String>, slow: Boolean) {
        lines.filter { it.isNotBlank() }.forEach { messages.add(Message(Who.PARTNER, it)) }
        tts.speakSequence(lines, slow = slow, second = true)
    }

    fun finish() {
        ended = true
        val d = engine.debrief()
        debrief = d
        val now = System.currentTimeMillis()
        scope.launch {
            container.conversations.save(
                SavedConversation(scenario.id, d.goalReached, d.understood, d.missed, d.corrections.size, now),
            )
        }
    }

    LaunchedEffect(scenario, round) {
        partnerSays(engine.open().lines, slow = false)
    }
    LaunchedEffect(messages.size, ended) {
        delay(80)
        scroll.animateScrollTo(scroll.maxValue)
    }

    fun learnerSaid(alternatives: List<String>, skipped: Boolean) {
        if (ended) return
        val said = alternatives.firstOrNull { it.isNotBlank() }
        messages.add(Message(Who.YOU, if (skipped) "…" else said.orEmpty()))
        val r = engine.respond(alternatives, skipped)
        if (r.corrections.isNotEmpty()) {
            val c = r.corrections.first()
            messages.add(Message(Who.TIP, tipText(c), c.note))
        }
        partnerSays(r.lines, slow = r.slow)
        if (r.hint != null && showHints) {
            messages.add(Message(Who.HELP, r.hint, r.model?.let { "Try: $it" }))
        }
        if (r.ended) finish()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(scenario.setting, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            Text("Goal: ${scenario.goal}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showText, onClick = { showText = !showText }, label = { Text("Show text") })
                FilterChip(selected = showHints, onClick = { showHints = !showHints }, label = { Text("Hints") })
            }
            messages.forEachIndexed { index, m ->
                Bubble(
                    m,
                    partnerName = scenario.partner,
                    hidden = m.who == Who.PARTNER && !showText && index !in revealed,
                    onReveal = { revealed.add(index) },
                )
            }
            debrief?.let { DebriefCard(scenario, it, onAgain = { round++ }, onExit = onExit) }
        }
        if (!ended) {
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SpeakOrType(
                    answered = false,
                    speechEnabled = speech,
                    onResponses = { learnerSaid(it, skipped = false) },
                    onSkip = { learnerSaid(emptyList(), skipped = true) },
                )
                if (showHints) {
                    TextButton(
                        onClick = {
                            val (hint, model) = engine.help()
                            messages.add(Message(Who.HELP, hint ?: "Answer the question in your own words.", model?.let { "Try: $it" }))
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("Help me say it") }
                }
            }
        }
    }
}

private fun tipText(c: Correction): String = "Si dice: “${c.better}”  (not “${c.said}”)"

@Composable
private fun Bubble(m: Message, partnerName: String, hidden: Boolean, onReveal: () -> Unit) {
    val partner = m.who == Who.PARTNER
    val you = m.who == Who.YOU
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (you) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when (m.who) {
                    Who.PARTNER -> MaterialTheme.colorScheme.surfaceVariant
                    Who.YOU -> MaterialTheme.colorScheme.primaryContainer
                    Who.TIP -> MaterialTheme.colorScheme.tertiaryContainer
                    Who.HELP -> MaterialTheme.colorScheme.secondaryContainer
                },
            ),
            modifier = Modifier.fillMaxWidth(if (partner || you) 0.88f else 1f),
        ) {
            Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 6.dp)) {
                if (partner && partnerName.isNotBlank()) {
                    Text(partnerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hidden) {
                        Text("Ascolta…", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
                        TextButton(onClick = onReveal) { Text("Show text") }
                    } else if (partner || you) {
                        LookupText(m.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    } else {
                        Text(m.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    if (partner) SpeakerButton(m.text, second = true)
                }
                m.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun DebriefCard(scenario: Scenario, d: Debrief, onAgain: () -> Unit, onExit: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (d.goalReached) "Obiettivo raggiunto!" else "Scene finished",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Goal: ${scenario.goal}\nUnderstood first time: ${d.understood}   Needed help: ${d.missed}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (d.corrections.isEmpty()) {
                Text("No common slips spotted. Bravo!", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Worth fixing", style = MaterialTheme.typography.titleSmall)
                d.corrections.forEach { c ->
                    Column {
                        Text("“${c.said}” → “${c.better}”", style = MaterialTheme.typography.bodyLarge)
                        Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (d.phrases.isNotEmpty()) {
                Text("Useful phrases", style = MaterialTheme.typography.titleSmall)
                d.phrases.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.it, style = MaterialTheme.typography.bodyLarge)
                            Text(p.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SpeakerButton(p.it)
                    }
                }
            }
            Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("Play it again (different replies)") }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Choose another scene") }
        }
    }
}
