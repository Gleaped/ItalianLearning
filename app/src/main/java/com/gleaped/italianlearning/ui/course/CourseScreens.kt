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
import androidx.compose.material3.LinearProgressIndicator
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

/** Course tab: one card per level (#7); a level opens its modules and units. */
@Composable
fun CourseMapScreen(onOpenLevel: (String) -> Unit, onOpenUnit: (String) -> Unit) {
    val o = rememberOverview()
    if (o == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }
    val course = o.content.course
    // Main path first (A1 → C1), then exam prep, then refreshers.
    val levels = course.levels.sortedBy { when { it.remedial -> 2; it.isExam -> 1; else -> 0 } }
    val progress = o.progress.levelProgress().associateBy { it.levelId }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(levels, key = { it.id }) { level ->
            val p = progress[level.id]
            val refs = course.units.filter { it.level == level.id }
            val unitsDone = refs.count { o.progress.isUnitComplete(it.id) }
            Card(
                onClick = { onOpenLevel(level.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (level.alwaysOpen || level.isExam) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(level.title, style = MaterialTheme.typography.titleLarge)
                    if (level.description.isNotBlank()) {
                        Text(level.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (p != null && p.total > 0) {
                        LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        when {
                            level.alwaysOpen -> "Always open · $unitsDone of ${refs.size} units done"
                            level.isExam -> "Always open · ${refs.size} units"
                            else -> "$unitsDone of ${refs.size} units done"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
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
        }
        items(orphans, key = { it.id }) { ref -> UnitRow(ref, o, onOpenUnit) }
    }
}

/** One level: its modules as headings, each with its units in study order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelScreen(levelId: String, onBack: () -> Unit, onOpenUnit: (String) -> Unit) {
    val o = rememberOverview()
    val level = o?.content?.course?.levels?.firstOrNull { it.id == levelId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(level?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (o == null || level == null) {
            Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            return@Scaffold
        }
        val course = o.content.course
        val units = course.units.filter { it.level == levelId }.sortedBy { it.order }
        val modules = course.modulesOf(levelId)
        val grouped: List<Pair<String?, List<UnitRef>>> = buildList {
            modules.forEach { m -> add(m.id to units.filter { it.module == m.id }) }
            val loose = units.filter { u -> modules.none { it.id == u.module } }
            if (loose.isNotEmpty()) add(null to loose)
        }.filter { it.second.isNotEmpty() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (level.description.isNotBlank()) {
                item(key = "desc") {
                    Text(level.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            grouped.forEachIndexed { i, (moduleId, refs) ->
                val module = modules.firstOrNull { it.id == moduleId }
                item(key = "module-${moduleId ?: "other"}") {
                    val done = refs.count { o.progress.isUnitComplete(it.id) }
                    Column(Modifier.padding(top = if (i == 0) 4.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            module?.title ?: "Altre unità",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        module?.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("$done of ${refs.size} done", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                items(refs, key = { it.id }) { ref -> UnitRow(ref, o, onOpenUnit) }
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
                if (ref.track == "congiuntivo") {
                    Text("Percorso congiuntivo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(ref.title, style = MaterialTheme.typography.titleMedium)
                if (ref.summary.isNotBlank()) Text(ref.summary, style = MaterialTheme.typography.bodyMedium)
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
