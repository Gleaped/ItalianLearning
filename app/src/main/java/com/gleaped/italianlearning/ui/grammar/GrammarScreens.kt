package com.gleaped.italianlearning.ui.grammar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.content.reference.GrammarReference
import com.gleaped.italianlearning.content.reference.ReferenceParser
import com.gleaped.italianlearning.content.reference.ReferenceTopic
import com.gleaped.italianlearning.ui.components.ChartTableView
import com.gleaped.italianlearning.ui.components.ItalianText
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.RichText

private sealed interface ReferenceLoad {
    data object Loading : ReferenceLoad
    data object Missing : ReferenceLoad
    data class Ready(val reference: GrammarReference) : ReferenceLoad
}

@Composable
private fun rememberReference(): ReferenceLoad {
    val container = LocalAppContainer.current
    val state by produceState<ReferenceLoad>(ReferenceLoad.Loading, container) {
        value = container.reference.load()?.let { ReferenceLoad.Ready(it) } ?: ReferenceLoad.Missing
    }
    return state
}

/** Small CEFR level tag. */
@Composable
fun LevelChip(level: String, modifier: Modifier = Modifier) {
    if (level.isBlank()) return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(level, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Grammatica: every rule, grouped by section, with a filter field. */
@Composable
fun GrammarScreen(onOpenTopic: (String) -> Unit) {
    val load = rememberReference()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    when (load) {
        ReferenceLoad.Loading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        ReferenceLoad.Missing -> Box(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "The grammar reference isn't installed in this version of the app.",
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        is ReferenceLoad.Ready -> {
            val sections = remember(load.reference, query) { ReferenceParser.filter(load.reference, query) }
            Column(Modifier.fillMaxSize().imePadding()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Cerca una regola") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (sections.isEmpty()) {
                        item {
                            Text(
                                "No rules match “$query”.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    sections.forEach { section ->
                        item(key = "s-${section.id}") {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                            )
                        }
                        items(section.topics, key = { "t-${section.id}-${it.id}" }) { topic ->
                            TopicRow(topic) { onOpenTopic(topic.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(topic: ReferenceTopic, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topic.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            LevelChip(topic.level)
        }
        if (topic.summary.isNotBlank()) {
            Text(
                topic.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One rule: summary, explanation, charts, examples, pitfalls and the units that practise it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GrammarTopicScreen(topicId: String, onBack: () -> Unit, onOpenUnit: (String) -> Unit) {
    val container = LocalAppContainer.current
    val load = rememberReference()
    val topic = (load as? ReferenceLoad.Ready)?.reference?.topic(topicId)
    val unitTitles by produceState(emptyMap<String, String>(), container) {
        value = container.content.course().units.associate { it.id to it.title }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topic?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                load is ReferenceLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                topic == null -> Text(
                    "This rule isn't available.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                )
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val section = (load as ReferenceLoad.Ready).reference.sectionOf(topic.id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelChip(topic.level)
                        if (section != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(section.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (topic.summary.isNotBlank()) {
                        Text(topic.summary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (topic.body.isNotBlank()) RichText(topic.body)
                    topic.tables.forEach { ChartTableView(it) }
                    if (topic.examples.isNotEmpty()) {
                        Column {
                            SectionHeading("Esempi")
                            topic.examples.forEachIndexed { i, ex ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    ItalianText(ex.it, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                    if (ex.en.isNotBlank()) {
                                        Text(ex.en, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (topic.pitfalls.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Attenzione", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                                topic.pitfalls.forEach { p ->
                                    Row {
                                        Text("•", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.width(8.dp))
                                        Text(p, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                    val related = topic.relatedUnitIds.filter { it in unitTitles }
                    if (related.isNotEmpty()) {
                        Column {
                            SectionHeading("Nelle unità")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                related.forEach { id ->
                                    AssistChip(onClick = { onOpenUnit(id) }, label = { Text(unitTitles[id].orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
