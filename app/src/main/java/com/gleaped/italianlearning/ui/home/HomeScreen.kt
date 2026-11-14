package com.gleaped.italianlearning.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gleaped.italianlearning.domain.practice.WeakSpots
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import java.time.LocalTime

@Composable
fun HomeScreen(
    onStartLesson: (unitId: String, lessonId: String) -> Unit,
    onReview: () -> Unit,
    onCourse: () -> Unit,
    onPractice: () -> Unit = {},
    onProgress: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val vm: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(container))
    val overview by vm.overview.collectAsStateWithLifecycle()
    val o = overview
    if (o == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Buongiorno!"
        in 12..17 -> "Buon pomeriggio!"
        else -> "Buonasera!"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(greeting, style = MaterialTheme.typography.headlineMedium)

        if (o.content.loadErrors.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Some course content could not be loaded:\n" + o.content.loadErrors.joinToString("\n"),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Next lesson
        val next = o.progress.nextLesson()
        // A lesson left part-way comes first: finishing it is the quickest win.
        val saved = o.checkpoint
        val savedUnit = saved?.let { o.content.units[it.unitId] }
        val savedLesson = savedUnit?.lessons?.firstOrNull { it.id == saved?.lessonId }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saved != null && savedUnit != null && savedLesson != null) {
                    val total = savedLesson.exercises.size
                    Text("Continue your lesson", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(savedLesson.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${savedUnit.title} · step ${minOf(saved.index + 1, total)} of $total",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { onStartLesson(savedUnit.id, savedLesson.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue lesson")
                    }
                } else if (next != null) {
                    val (unit, lesson) = next
                    Text("Next lesson", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(lesson.title, style = MaterialTheme.typography.titleLarge)
                    Text("${unit.title} · ${unit.level} · about ${lesson.minutes} min", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { onStartLesson(unit.id, lesson.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start lesson")
                    }
                } else {
                    Text("You're up to date!", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "All available lessons are done. New units will appear as content is added — meanwhile, review or revisit a refresher.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onCourse, modifier = Modifier.fillMaxWidth()) { Text("Open course map") }
                }
            }
        }

        // Missed exercises that are due to be asked again
        if (o.weakDue > 0) {
            val round = minOf(o.weakDue, WeakSpots.SESSION_SIZE)
            Card(
                onClick = onPractice,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Punti deboli", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (o.weakDue == 1) "1 item to revisit" else "${o.weakDue} items to revisit",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Things you missed earlier, ready for another go. $round now, about ${(round + 1) / 2} min.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Review + streak
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(onClick = onReview, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Review", style = MaterialTheme.typography.labelLarge)
                    Text("${o.dueReviews + o.newCards}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            o.dueReviews + o.newCards == 0 -> "Nothing due"
                            o.newCards > 0 -> "${o.dueReviews} due · ${o.newCards} new"
                            else -> "cards due"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Streak", style = MaterialTheme.typography.labelLarge)
                    Text("${o.streak}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            o.practicedToday -> "Done for today ✓"
                            o.streak > 0 -> "Practise today to keep it going"
                            else -> "Start one today"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        OutlinedButton(onClick = onProgress, modifier = Modifier.fillMaxWidth()) {
            Text("Progress by skill and my writing")
        }

        // Level progress
        Text("Your levels", style = MaterialTheme.typography.titleMedium)
        o.progress.levelProgress().forEach { lp ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(lp.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        if (lp.total == 0) "coming soon" else "${lp.completed}/${lp.total} lessons",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LinearProgressIndicator(progress = { lp.fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
