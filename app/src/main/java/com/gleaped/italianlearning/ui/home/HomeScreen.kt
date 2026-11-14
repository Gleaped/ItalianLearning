package com.gleaped.italianlearning.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.gleaped.italianlearning.domain.practice.DailyMix
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.TextButton
import com.gleaped.italianlearning.domain.progress.ExamPlan
import com.gleaped.italianlearning.domain.progress.ExamReadiness
import com.gleaped.italianlearning.domain.progress.Readiness
import com.gleaped.italianlearning.domain.progress.Standing
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
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    onStartLesson: (unitId: String, lessonId: String) -> Unit,
    onReview: () -> Unit,
    onCourse: () -> Unit,
    onPractice: () -> Unit = {},
    onProgress: () -> Unit = {},
    onMix: () -> Unit = {},
    onExamSettings: () -> Unit = {},
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

        // Today's plan: what is worth doing today, ticked off as it gets done.
        val reviewReady = o.dueReviews + o.newCards
        val lessonTarget: (() -> Unit)? = when {
            saved != null && savedUnit != null && savedLesson != null -> ({ onStartLesson(savedUnit.id, savedLesson.id) })
            next != null -> ({ onStartLesson(next.first.id, next.second.id) })
            else -> null
        }
        val weakRound = minOf(o.weakDue, WeakSpots.SESSION_SIZE)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Il piano di oggi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            o.streak > 1 -> "${o.streak} days in a row"
                            o.streak == 1 -> "1 day so far"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    when {
                        o.practicedToday -> "You have practised today. Anything more is a bonus."
                        o.streak > 0 -> "Any one of these keeps your streak going."
                        else -> "Any one of these counts as practice for today."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                // The card above already offers the next lesson; here it only shows once done.
                if (o.lessonsToday > 0) {
                    PlanRow(
                        done = true,
                        title = "A lesson",
                        detail = if (o.lessonsToday == 1) "Done: 1 lesson today" else "Done: ${o.lessonsToday} lessons today",
                        onClick = lessonTarget,
                    )
                }
                PlanRow(
                    done = reviewReady == 0,
                    title = "Review cards",
                    detail = when {
                        reviewReady == 0 -> "Nothing waiting right now"
                        o.newCards > 0 -> "${o.dueReviews} due and ${o.newCards} new, about ${(reviewReady + 5) / 6} min"
                        else -> "${o.dueReviews} due, about ${(reviewReady + 5) / 6} min"
                    },
                    onClick = onReview,
                )
                if (o.weakDue > 0) {
                    PlanRow(
                        done = false,
                        title = "Punti deboli",
                        detail = (if (o.weakDue == 1) "1 item you missed is" else "${o.weakDue} items you missed are") +
                            " ready for another go, about ${(weakRound + 1) / 2} min",
                        onClick = onPractice,
                    )
                }
                if (o.mixAvailable) {
                    PlanRow(
                        done = o.mixDoneToday,
                        title = "Mix del giorno",
                        detail = if (o.mixDoneToday) {
                            "Done today. Tap for another mix"
                        } else {
                            "${DailyMix.SIZE} mixed questions from lessons you have finished, about ${DailyMix.SIZE / 2} min"
                        },
                        onClick = onMix,
                    )
                }
            }
        }

        ExamCard(o, onProgress = onProgress, onSettings = onExamSettings)

        OutlinedButton(onClick = onProgress, modifier = Modifier.fillMaxWidth()) {
            Text("Progress by skill and my writing")
        }

        // Level progress
        Text("Your levels", style = MaterialTheme.typography.titleMedium)
        val foundation = o.content.course.levels.filter { it.alwaysOpen }.map { it.id }.toSet()
        o.progress.levelProgress().filter { lp -> lp.total > 0 && (lp.levelId !in foundation || lp.completed > 0) }.forEach { lp ->
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

/** Countdown to the exam, the weekly pace that keeps to it, and a rough score against the pass marks. */
@Composable
private fun ExamCard(o: Overview, onProgress: () -> Unit, onSettings: () -> Unit) {
    val today = LocalDate.now().toEpochDay()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Esame CELI 3", style = MaterialTheme.typography.titleMedium)
            if (o.examDateEpochDay <= 0L) {
                Text(
                    "Set the day of your exam to see a countdown and how many lessons a week keep you on course. It is optional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSettings) { Text("Set exam date") }
            } else {
                val left = ExamPlan.daysLeft(o.examDateEpochDay, today)
                Text(
                    when {
                        left > 1 -> "$left days to go"
                        left == 1 -> "The exam is tomorrow"
                        left == 0 -> "The exam is today. In bocca al lupo!"
                        else -> "The exam date has passed. You can set a new one in Settings."
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                val pace = ExamPlan.lessonsPerWeek(o.remainingLessons, left)
                val paceLine = when {
                    left < 0 -> null
                    o.remainingLessons == 0 -> "You have finished every lesson up to B2. Keep going with reviews and the mock papers."
                    pace == null -> null
                    else -> "${o.remainingLessons} lessons to finish B2: about $pace a week gets you there."
                }
                paceLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            ReadinessLines(o.readiness)
            TextButton(onClick = onProgress) { Text("See the estimate by part") }
        }
    }
}

@Composable
private fun ReadinessLines(r: Readiness) {
    val written = r.written
    val oral = r.oral
    if (written == null && oral == null) {
        Text(
            "A score estimate appears once you have answered enough in each skill.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    fun line(label: String, points: Int?, max: Int, pass: Int): String {
        if (points == null) return "$label: not enough answers yet"
        val where = when (ExamReadiness.standing(points, pass, max)) {
            Standing.ABOVE -> "above the pass mark"
            Standing.NEAR -> "close to the pass mark"
            Standing.BELOW -> "below the pass mark"
        }
        return "$label: about $points of $max, $where ($pass)"
    }
    Text(line("Written", written, ExamReadiness.WRITTEN_MAX, ExamReadiness.WRITTEN_PASS), style = MaterialTheme.typography.bodyMedium)
    Text(line("Oral", oral, ExamReadiness.ORAL_MAX, ExamReadiness.ORAL_PASS), style = MaterialTheme.typography.bodyMedium)
    Text(
        "A rough guide from your lesson answers, not a prediction.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One line of today's plan: a tick or an open circle, what it is, and how much there is. */
@Composable
private fun PlanRow(done: Boolean, title: String, detail: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (done) "✓" else "○",
            style = MaterialTheme.typography.titleLarge,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            // The detail line already says whether it is done.
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
