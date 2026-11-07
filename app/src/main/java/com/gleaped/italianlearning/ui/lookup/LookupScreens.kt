package com.gleaped.italianlearning.ui.lookup

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gleaped.italianlearning.AppContainer
import com.gleaped.italianlearning.content.reference.ChartTable
import com.gleaped.italianlearning.data.dictionary.DictionaryAvailability
import com.gleaped.italianlearning.domain.dictionary.FormsParser
import com.gleaped.italianlearning.domain.dictionary.LemmaRow
import com.gleaped.italianlearning.domain.dictionary.LookupGroup
import com.gleaped.italianlearning.ui.components.ChartTableView
import com.gleaped.italianlearning.ui.components.ItalianText
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import com.gleaped.italianlearning.ui.components.LocalWordLookup
import com.gleaped.italianlearning.ui.components.SpeakerButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ state

sealed interface DictionaryUi {
    data object Loading : DictionaryUi
    data object NotInstalled : DictionaryUi
    data class Ready(val query: String, val results: List<LookupGroup>, val searching: Boolean) : DictionaryUi
}

class LookupViewModel(private val container: AppContainer) : ViewModel() {
    /** The field's text; Compose state so typing never lags behind the IME. */
    var query by mutableStateOf("")
        private set

    var ui by mutableStateOf<DictionaryUi>(DictionaryUi.Loading)
        private set

    var sources by mutableStateOf<String?>(null)
        private set

    val recent = container.settings.recentLookups

    init {
        viewModelScope.launch {
            val availability = container.dictionary.availability()
            if (availability !is DictionaryAvailability.Ready) {
                ui = DictionaryUi.NotInstalled
                return@launch
            }
            sources = availability.sources
            snapshotFlow { query }.collectLatest { q ->
                if (q.isBlank()) {
                    ui = DictionaryUi.Ready(q, emptyList(), searching = false)
                    return@collectLatest
                }
                ui = (ui as? DictionaryUi.Ready)?.copy(searching = true) ?: DictionaryUi.Ready(q, emptyList(), true)
                delay(SEARCH_DEBOUNCE_MS)
                val results = container.dictionary.search(q)
                ui = DictionaryUi.Ready(q, results, searching = false)
            }
        }
    }

    fun setQuery(value: String) {
        query = value
    }

    fun recordLookup(lemma: String) {
        viewModelScope.launch { container.settings.addRecentLookup(lemma) }
    }

    fun clearRecent() {
        viewModelScope.launch { container.settings.clearRecentLookups() }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 150L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LookupViewModel(container) }
        }
    }
}

// ------------------------------------------------------------------ lookup screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupScreen(onBack: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm: LookupViewModel = viewModel(factory = LookupViewModel.factory(container))
    val recent by vm.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    var showSources by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    // Autofocus once; coming back from an entry should not pop the keyboard again.
    var autoFocused by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        // Keyboard-safe like the lesson player: the list ends at the top of the keyboard.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Dizionario") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val ui = vm.ui
            if (ui is DictionaryUi.NotInstalled) {
                NotInstalledMessage()
                return@Column
            }
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                placeholder = { Text("Cerca una parola: andò, casa, bello…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) {
                if (!autoFocused) {
                    autoFocused = true
                    focusRequester.requestFocus()
                }
            }
            when (ui) {
                DictionaryUi.Loading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                is DictionaryUi.Ready -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (vm.query.isBlank()) {
                        if (recent.isEmpty()) {
                            item {
                                Text(
                                    "Type any form of a word: a conjugated verb, a plural, a feminine adjective.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }
                        } else {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Recenti", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                    TextButton(onClick = vm::clearRecent) { Text("Cancella") }
                                }
                            }
                            items(recent, key = { "r-$it" }) { word ->
                                Text(
                                    word,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.setQuery(word) }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }
                        }
                    } else if (ui.results.isEmpty() && !ui.searching && ui.query == vm.query) {
                        item {
                            Text(
                                "Nessun risultato per “${vm.query.trim()}”.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        items(ui.results, key = { it.lemma.id }) { group ->
                            ResultRow(group) {
                                vm.recordLookup(group.lemma.lemma)
                                focus.clearFocus()
                                onOpenEntry(group.lemma.id)
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    item { SourcesLine(vm.sources) { showSources = true } }
                }
                DictionaryUi.NotInstalled -> Unit
            }
        }
    }
    if (showSources) SourcesSheet(vm.sources) { showSources = false }
}

@Composable
private fun NotInstalledMessage(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dictionary not installed", style = MaterialTheme.typography.titleMedium)
        Text(
            "The offline dictionary isn’t installed in this version of the app (or couldn’t be opened). Everything else works as usual.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultRow(group: LookupGroup, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        LemmaHeadline(group.lemma)
        if (group.lemma.firstGloss.isNotBlank()) {
            Text(group.lemma.firstGloss, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        group.matchLine?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LemmaHeadline(lemma: LemmaRow) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(lemma.lemma, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(
            lemma.posLabel,
            style = MaterialTheme.typography.labelMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun SourcesLine(sources: String?, onClick: () -> Unit) {
    if (sources == null) return
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text("Fonti", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesSheet(sources: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Fonti", style = MaterialTheme.typography.titleMedium)
            Text(sources ?: "—", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ------------------------------------------------------------------ entry screen

private sealed interface EntryLoad {
    data object Loading : EntryLoad
    data object Missing : EntryLoad
    data class Ready(val lemma: LemmaRow, val sources: String?) : EntryLoad
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEntryScreen(lemmaId: Long, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val load by produceState<EntryLoad>(EntryLoad.Loading, lemmaId) {
        val availability = container.dictionary.availability()
        val lemma = container.dictionary.entry(lemmaId)
        value = if (lemma == null) EntryLoad.Missing else EntryLoad.Ready(lemma, (availability as? DictionaryAvailability.Ready)?.sources)
    }
    var showSources by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((load as? EntryLoad.Ready)?.lemma?.lemma.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val l = load) {
                EntryLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                EntryLoad.Missing -> Text(
                    "This word isn't in the dictionary.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                is EntryLoad.Ready -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EntryBody(l.lemma, allGlosses = true)
                    FormsSection(l.lemma)
                    SourcesLine(l.sources) { showSources = true }
                }
            }
        }
    }
    if (showSources) SourcesSheet((load as? EntryLoad.Ready)?.sources) { showSources = false }
}

/** Headword, glosses, Italian definition, example and notes. */
@Composable
private fun EntryBody(lemma: LemmaRow, allGlosses: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(lemma.lemma, style = MaterialTheme.typography.headlineSmall)
                Text(
                    lemma.posLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SpeakerButton(lemma.lemma)
        }
        val glosses = if (allGlosses) lemma.glosses else lemma.glosses.take(3)
        if (glosses.size == 1) {
            Text(glosses.single(), style = MaterialTheme.typography.bodyLarge)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                glosses.forEachIndexed { i, g ->
                    Row {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                        Text(g, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        lemma.glossIt?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        lemma.exampleIt?.takeIf { it.isNotBlank() }?.let { ex ->
            Column {
                ItalianText(ex, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                lemma.exampleEn?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        lemma.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/** Every form, as collapsible tables (verbs grouped by mood); the first table starts open. */
@Composable
private fun FormsSection(lemma: LemmaRow) {
    val groups = remember(lemma.id) { FormsParser.group(lemma.pos, FormsParser.parse(lemma.formsJson)) }
    if (groups.isEmpty()) return
    // Expanded state per table, keyed "group/index"; the very first table is open by default.
    val expanded = remember(lemma.id) { mutableStateMapOf("0/0" to true) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Forme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        groups.forEachIndexed { gi, group ->
            group.title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = if (gi == 0) 4.dp else 12.dp, bottom = 2.dp),
                )
            }
            group.tables.forEachIndexed { ti, table ->
                val key = "$gi/$ti"
                CollapsibleTable(
                    table = table,
                    expanded = expanded[key] == true,
                    onToggle = { expanded[key] = expanded[key] != true },
                    moodTitle = group.title,
                )
            }
        }
    }
}

@Composable
private fun CollapsibleTable(table: ChartTable, expanded: Boolean, onToggle: () -> Unit, moodTitle: String?) {
    // Under a mood heading, "Congiuntivo presente" reads better as just "presente".
    val label = table.title.let { t ->
        if (moodTitle != null && t.startsWith(moodTitle, ignoreCase = true) && t.length > moodTitle.length) {
            t.substring(moodTitle.length).trim().replaceFirstChar { it.uppercase() }
        } else {
            t.ifBlank { "Forme" }
        }
    }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            ChartTableView(table, showTitle = false, modifier = Modifier.padding(bottom = 8.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ------------------------------------------------------------------ long-press sheet

/**
 * Provides long-press word lookup to [content] (see LookupText) and shows the result in a
 * small bottom sheet. "Tutte le forme" opens the full entry.
 */
@Composable
fun WordLookupHost(onOpenEntry: (Long) -> Unit, content: @Composable () -> Unit) {
    var word by remember { mutableStateOf<String?>(null) }
    val onLookup = remember { { w: String -> word = w } }
    CompositionLocalProvider(LocalWordLookup provides onLookup) { content() }
    word?.let { w ->
        WordLookupSheet(
            word = w,
            onDismiss = { word = null },
            onOpenEntry = { id ->
                word = null
                onOpenEntry(id)
            },
        )
    }
}

private sealed interface SheetLoad {
    data object Loading : SheetLoad
    data object NotInstalled : SheetLoad
    data class Ready(val groups: List<LookupGroup>) : SheetLoad
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordLookupSheet(word: String, onDismiss: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val load by produceState<SheetLoad>(SheetLoad.Loading, word) {
        val availability = container.dictionary.availability()
        value = if (availability !is DictionaryAvailability.Ready) {
            SheetLoad.NotInstalled
        } else {
            val all = container.dictionary.search(word, maxGroups = 12)
            // A long-pressed word is an exact form: prefer exact matches over prefix guesses.
            val exact = all.filter { it.matches.isNotEmpty() }
            SheetLoad.Ready((exact.ifEmpty { all }).take(3))
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val l = load) {
                SheetLoad.Loading -> Box(Modifier.fillMaxWidth().height(96.dp)) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                SheetLoad.NotInstalled -> NotInstalledMessage()
                is SheetLoad.Ready -> {
                    if (l.groups.isEmpty()) {
                        Text("Nessun risultato per “$word”.", style = MaterialTheme.typography.bodyLarge)
                    }
                    l.groups.forEachIndexed { i, group ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            group.matchLine?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            EntryBody(group.lemma, allGlosses = false)
                            TextButton(onClick = {
                                container.appScope.launch { container.settings.addRecentLookup(group.lemma.lemma) }
                                onOpenEntry(group.lemma.id)
                            }) { Text("Tutte le forme") }
                        }
                    }
                }
            }
        }
    }
}
