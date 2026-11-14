package com.gleaped.italianlearning.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gleaped.italianlearning.domain.exam.PaperLog
import com.gleaped.italianlearning.domain.exam.PaperTotals
import com.gleaped.italianlearning.domain.exam.SavedPaper
import com.gleaped.italianlearning.domain.progress.ExamPart
import com.gleaped.italianlearning.domain.progress.ExamReadiness
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "Prove ufficiali": the points you gave yourself on a real past paper done on paper, part by
 * part, against the CELI 3 pass marks. A real paper under exam conditions is the best practice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapersScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val papers by container.papers.papers.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.papers.load() }
    var label by remember { mutableStateOf("") }
    val fields = remember { mutableStateMapOf<ExamPart, String>() }
    val format = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prove ufficiali") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Do a past paper from the Perugia university site on paper, under exam time. Mark it with the official keys and criteria, then enter your points for each part. Leave a part empty if you skipped it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                label = { Text("Name of the paper (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ExamPart.entries.forEach { part ->
                val text = fields[part].orEmpty()
                OutlinedTextField(
                    value = text,
                    onValueChange = { fields[part] = it.filter(Char::isDigit).take(3) },
                    label = { Text("${part.italian} (${part.english}), out of ${part.max}") },
                    isError = text.isNotBlank() && PaperLog.parseScore(text, part) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val entered = ExamPart.entries.mapNotNull { p -> PaperLog.parseScore(fields[p].orEmpty(), p)?.let { p.name to it } }.toMap()
            Button(
                enabled = entered.isNotEmpty(),
                onClick = {
                    val paper = SavedPaper(
                        id = System.currentTimeMillis().toString(),
                        label = label.trim(),
                        epochDay = LocalDate.now().toEpochDay(),
                        scores = entered,
                    )
                    scope.launch { container.papers.save(paper) }
                    label = ""
                    fields.clear()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save this paper") }

            Text("Your papers", style = MaterialTheme.typography.titleLarge)
            if (papers.isEmpty()) {
                Text(
                    "Nothing yet. The pass marks are 84 of 140 written, 33 of 60 oral and 117 of 200 overall.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            papers.forEach { paper ->
                PaperCard(
                    paper = paper,
                    date = LocalDate.ofEpochDay(paper.epochDay).format(format),
                    onDelete = { scope.launch { container.papers.delete(paper.id) } },
                )
            }
        }
    }
}

@Composable
private fun PaperCard(paper: SavedPaper, date: String, onDelete: () -> Unit) {
    val totals = PaperLog.totals(paper)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(paper.label.ifBlank { "Practice paper" } + " · " + date, style = MaterialTheme.typography.titleMedium)
            ExamPart.entries.forEach { part ->
                paper.scores[part.name]?.let { Text("${part.italian}: $it of ${part.max}", style = MaterialTheme.typography.bodyMedium) }
            }
            Text(totalLine(totals), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private fun totalLine(t: PaperTotals): String {
    val parts = ArrayList<String>()
    t.written?.let { parts += "Written $it of ${ExamReadiness.WRITTEN_MAX} (pass ${ExamReadiness.WRITTEN_PASS})" }
    t.oral?.let { parts += "Oral $it of ${ExamReadiness.ORAL_MAX} (pass ${ExamReadiness.ORAL_PASS})" }
    t.overall?.let {
        parts += "Overall $it of 200, grade ${PaperLog.grade(it)}: " + if (t.passed == true) "pass" else "not yet a pass"
    }
    return if (parts.isEmpty()) "Enter all four written parts for a written total, and the oral for an overall total." else parts.joinToString("\n")
}
