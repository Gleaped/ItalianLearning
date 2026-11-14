package com.gleaped.italianlearning.ui.progress

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gleaped.italianlearning.domain.writing.CeliCriterion
import com.gleaped.italianlearning.domain.writing.SavedWriting
import com.gleaped.italianlearning.domain.writing.WritingLog
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.LookupText
import com.gleaped.italianlearning.ui.components.WritingNotes
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    // No app able to receive text is unusual but must not crash the screen.
    runCatching { context.startActivity(Intent.createChooser(send, "Share your text")) }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Italiano", text))
}

/** "I miei testi": every finished writing exercise, newest first; tap one to open it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val entries by container.writing.entries.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        container.writing.load()
        loaded = true
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<SavedWriting?>(null) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("I miei testi") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loaded && entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No texts yet. When you finish a writing exercise, your text is saved here so you can reread it, " +
                            "compare it with the model answer, or share it with a tutor.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                val date = remember(entry.savedAtMillis) {
                    Instant.ofEpochMilli(entry.savedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
                }
                WritingCard(
                    entry = entry,
                    date = date,
                    open = openId == entry.id,
                    onToggle = { openId = if (openId == entry.id) null else entry.id },
                    onDelete = { deleting = entry },
                    onRevise = { revised -> scope.launch { container.writing.save(entry.copy(revision = revised.trim())) } },
                )
            }
        }
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this text?") },
            text = { Text("It cannot be brought back.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { container.writing.delete(entry.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun WritingCard(
    entry: SavedWriting,
    date: String,
    open: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onRevise: (String) -> Unit,
) {
    val context = LocalContext.current
    var checking by remember(entry.id) { mutableStateOf(false) }
    var rewriting by remember(entry.id) { mutableStateOf(false) }
    var draft by remember(entry.id) { mutableStateOf(entry.latest) }
    Card(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                entry.prompt,
                style = MaterialTheme.typography.titleMedium,
                maxLines = if (open) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!open) {
                Text(entry.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                return@Column
            }
            LookupText(entry.text, style = MaterialTheme.typography.bodyLarge)
            if (entry.revision.isNotBlank()) {
                Text("Second draft", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LookupText(entry.revision, style = MaterialTheme.typography.bodyLarge)
            }
            val scored = CeliCriterion.entries.mapNotNull { c -> entry.scores[c.key]?.let { c to it } }
            if (scored.isNotEmpty()) {
                Text(
                    "Your scores: " + scored.joinToString(", ") { (c, n) -> "${c.italian} $n/${CeliCriterion.MAX_SCORE}" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (entry.modelAnswer.isNotBlank()) {
                HorizontalDivider()
                Text("Model answer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LookupText(entry.modelAnswer, style = MaterialTheme.typography.bodyLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = { copy(context, entry.text) }) { Text("Copy") }
                TextButton(onClick = { share(context, WritingLog.shareText(entry)) }) { Text("Share") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { checking = !checking }) { Text(if (checking) "Hide notes" else "Check my text") }
                TextButton(onClick = {
                    draft = entry.latest
                    rewriting = !rewriting
                }) { Text(if (rewriting) "Cancel" else "Rewrite") }
                TextButton(onClick = { share(context, WritingLog.feedbackRequest(entry)) }) { Text("Ask for feedback") }
            }
            if (checking) WritingNotes(entry.latest)
            if (rewriting) {
                Text(
                    "Rewrite it with the notes and the model answer in mind. Your first version is kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    label = { Text("Second draft") },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            onRevise(draft)
                            rewriting = false
                        },
                    ) { Text("Save second draft") }
                }
            }
            Text(
                "Ask for feedback shares your text with a request that marks mistakes, corrects them and scores it on the exam's criteria. " +
                    "Send it to a tutor or any assistant you use; the app itself stays offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
