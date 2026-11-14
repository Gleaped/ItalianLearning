package com.gleaped.italianlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.domain.writing.NoteKind
import com.gleaped.italianlearning.domain.writing.WritingReport

/**
 * "Da rivedere": what an offline check of [text] found. It is a list of things to look at, not a mark:
 * the check knows spelling and a few frequent errors, not whether the grammar is right.
 */
@Composable
fun WritingNotes(text: String, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val report by produceState<WritingReport?>(null, text) { value = container.dictionary.checkWriting(text) }
    val r = report ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Da rivedere", style = MaterialTheme.typography.titleMedium)
        Text(
            "${r.words} words, ${r.sentences} sentences" +
                if (r.connectors.isNotEmpty()) ", linking words: ${r.connectors.take(6).joinToString(", ")}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (r.notes.isEmpty()) {
            Text(
                "Nothing stood out. The check finds spelling and frequent slips, not every grammar mistake, so read it through once more.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        r.notes.forEach { note ->
            val label = when (note.kind) {
                NoteKind.SPELLING -> "Spelling"
                NoteKind.PATTERN -> "Usage"
                NoteKind.REGISTER -> "Register"
                NoteKind.STYLE -> "Style"
            }
            Text(
                buildString {
                    append(label).append(": ")
                    note.quote?.let { append("“").append(it).append("” ") }
                    append(note.message)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        if (!r.spellingChecked) {
            Text(
                "The dictionary is not installed, so spelling was not checked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
