package com.gleaped.italianlearning.ui.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gleaped.italianlearning.content.GrammarTip
import com.gleaped.italianlearning.content.UnitRef
import com.gleaped.italianlearning.domain.progress.UnitStatus
import com.gleaped.italianlearning.ui.components.ItalianText
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.home.Overview
import com.gleaped.italianlearning.ui.home.OverviewViewModel

@Composable
private fun rememberOverview(): Overview? {
    val container = LocalAppContainer.current
    val vm: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(container))
    val overview by vm.overview.collectAsStateWithLifecycle()
    return overview
}

@Composable
fun CourseMapScreen(onOpenUnit: (String) -> Unit) {
    val o = rememberOverview()
    if (o == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }
    val course = o.content.course
    // Main path first (B1, B2, C1), refreshers last but always open.
    val levels = course.levels.sortedBy { if (it.remedial) 1 else 0 }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        levels.forEach { level ->
            item(key = "level-${level.id}") {
                Column(Modifier.padding(top = 16.dp)) {
                    Text(level.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    if (level.description.isNotBlank()) {
                        Text(level.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (level.remedial) {
                        Text(
                            "Refreshers are always open — dip in whenever something feels shaky.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (level.isExam) {
                        Text(
                            "Exam prep · always open. Practise any section of the exam whenever you like.",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            val units = course.units.filter { it.level == level.id }.sortedBy { it.order }
            items(units, key = { it.id }) { ref ->
                UnitRow(ref, o, onOpenUnit)
            }
        }
        // Units whose level course.json does not declare: still reachable, never lost.
        val declared = course.levels.map { it.id }.toSet()
        val orphans = course.units.filter { it.level !in declared }.sortedWith(compareBy({ it.level }, { it.order }))
        if (orphans.isNotEmpty()) {
            item(key = "level-other") {
                Text(
                    "Altre unità",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            items(orphans, key = { it.id }) { ref ->
                UnitRow(ref, o, onOpenUnit)
            }
        }
    }
}

@Composable
private fun UnitRow(ref: UnitRef, o: Overview, onOpenUnit: (String) -> Unit) {
    val status = o.progress.status(ref)
    val unit = o.content.units[ref.id]
    val done = unit?.lessons?.count { it.id in o.completedLessons } ?: 0
    val total = unit?.lessons?.size ?: 0
    val enabled = status == UnitStatus.UNLOCKED
    Card(
        onClick = { onOpenUnit(ref.id) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f),
        colors = CardDefaults.cardColors(
            containerColor = if (total > 0 && done == total) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${ref.level} · ${ref.order}", style = MaterialTheme.typography.labelSmall)
                Text(ref.title, style = MaterialTheme.typography.titleMedium)
                if (ref.summary.isNotBlank()) Text(ref.summary, style = MaterialTheme.typography.bodySmall)
                Text(
                    when (status) {
                        UnitStatus.COMING_SOON -> "Coming soon"
                        UnitStatus.LOCKED -> "Finish the previous unit to unlock"
                        UnitStatus.UNLOCKED -> "$done / $total lessons"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            when {
                status != UnitStatus.UNLOCKED -> Icon(Icons.Filled.Lock, contentDescription = "Locked")
                total > 0 && done == total -> Icon(Icons.Filled.CheckCircle, contentDescription = "Completed")
                else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Open")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnitScreen(
    unitId: String,
    onBack: () -> Unit,
    onStartLesson: (unitId: String, lessonId: String) -> Unit,
    onOpenUnit: (String) -> Unit,
    onOpenChallenge: (unitId: String, tipId: String) -> Unit,
) {
    val o = rememberOverview()
    val unit = o?.content?.units?.get(unitId)
    val container = LocalAppContainer.current
    // Remembered so grammar-tip reminders can draw on refresher units the learner opened.
    LaunchedEffect(unitId) { container.settings.markUnitOpened(unitId) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(unit?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (o == null) {
            Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            return@Scaffold
        }
        if (unit == null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text("This unit is coming soon.", modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }
        val ref = o.content.course.units.firstOrNull { it.id == unitId }
        val locked = ref != null && o.progress.status(ref) == UnitStatus.LOCKED
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (unit.objective.isNotBlank()) Text(unit.objective, style = MaterialTheme.typography.bodyLarge)
                    if (unit.grammarFocus.isNotBlank()) {
                        Text("Grammar: ${unit.grammarFocus}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (locked) {
                        Text("Finish the previous unit to unlock these lessons (or enable “Unlock all units” in Settings).", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            val remedial = unit.remedialUnitIds.mapNotNull { id -> o.content.course.units.firstOrNull { it.id == id } }
            if (remedial.isNotEmpty()) {
                item {
                    Column {
                        Text("Need a refresher?", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            remedial.forEach { r ->
                                AssistChip(
                                    onClick = { onOpenUnit(r.id) },
                                    enabled = r.available && o.content.units.containsKey(r.id),
                                    label = { Text(if (r.available) r.title else "${r.title} (soon)") },
                                )
                            }
                        }
                    }
                }
            }
            item { Text("Lessons", style = MaterialTheme.typography.titleMedium) }
            items(unit.lessons, key = { it.id }) { lesson ->
                val done = lesson.id in o.completedLessons
                OutlinedCard(
                    onClick = { onStartLesson(unit.id, lesson.id) },
                    enabled = !locked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text(lesson.title) },
                        supportingContent = { Text("${lesson.exercises.size} exercises · about ${lesson.minutes} min") },
                        trailingContent = {
                            if (done) Icon(Icons.Filled.CheckCircle, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary)
                            else Icon(Icons.Filled.PlayArrow, contentDescription = "Start")
                        },
                    )
                }
            }
            if (unit.grammarTips.isNotEmpty()) {
                item {
                    Text("Grammar tips", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "Short reminders of this unit's rules, each with a quick challenge. They also turn up in your reminders.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(unit.grammarTips, key = { "tip-" + it.id }) { tip ->
                    GrammarTipCard(tip, onTry = { onOpenChallenge(unit.id, tip.id) })
                }
            }
            if (unit.vocabulary.isNotEmpty()) {
                item {
                    Text("Vocabulary", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "These words join your review deck when you finish a lesson in this unit.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(unit.vocabulary) { v ->
                    Column {
                        ItalianText(v.it, style = MaterialTheme.typography.bodyLarge)
                        Text(v.en, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GrammarTipCard(tip: GrammarTip, onTry: () -> Unit) {
    var expanded by rememberSaveable(tip.id) { mutableStateOf(false) }
    OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tip.rule, style = MaterialTheme.typography.titleSmall)
            Text(tip.tip, style = MaterialTheme.typography.bodyMedium)
            if (expanded) {
                tip.example?.let { ex ->
                    ItalianText(ex.it, style = MaterialTheme.typography.bodyLarge)
                    if (ex.en.isNotBlank()) {
                        Text(ex.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (tip.example != null) {
                Text("Tap for an example", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tip.challenge != null) {
                FilledTonalButton(onClick = onTry) { Text("Prova la sfida") }
            }
        }
    }
}
