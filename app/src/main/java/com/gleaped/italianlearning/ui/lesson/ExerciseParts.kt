package com.gleaped.italianlearning.ui.lesson

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.domain.grading.Verdict
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import com.gleaped.italianlearning.ui.components.isDarkSurface
import com.gleaped.italianlearning.ui.theme.FeedbackColors

@Composable
fun verdictColor(verdict: Verdict): Color {
    val dark = isDarkSurface()
    return when (verdict) {
        Verdict.CORRECT -> if (dark) FeedbackColors.correctDark else FeedbackColors.correctLight
        Verdict.ALMOST -> if (dark) FeedbackColors.almostDark else FeedbackColors.almostLight
        Verdict.WRONG -> if (dark) FeedbackColors.wrongDark else FeedbackColors.wrongLight
    }
}

fun verdictTitle(verdict: Verdict): String = when (verdict) {
    Verdict.CORRECT -> "Giusto!"
    Verdict.ALMOST -> "Quasi!"
    Verdict.WRONG -> "Non proprio"
}

/** Extra space kept visible below a focused answer field. */
private val BelowFieldClearance = 32.dp

/**
 * Text input for typed answers: no autocorrect, so accents are the learner's own.
 *
 * Keyboard handling (#5): the app pads its root for the keyboard (AppNavigation), so every screen
 * ends at the top of the keyboard, and lesson screens pin their Check button in a bar there
 * (see [PrimaryActionHost]). When this field is focused and the keyboard opens, changes height
 * or the field grows, the field plus [BelowFieldClearance] is scrolled into view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Your answer",
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        // Re-run when the keyboard opens or changes height, and when the field grows while typing
        // (multi-line answers), so the line being typed never slips under the keyboard (#5).
        snapshotFlow { ime.getBottom(density) to size.height }
            .distinctUntilChanged()
            .collectLatest { (imeBottom, _) ->
                if (imeBottom <= 0) return@collectLatest
                // collectLatest restarts on every animation frame; this runs once the keyboard settles.
                delay(120)
                val extra = with(density) { BelowFieldClearance.toPx() }
                requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + extra))
            }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = if (label.isNotEmpty()) { { Text(label) } } else null,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                if (value.isNotBlank()) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onDone()
                }
            },
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
        ),
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .onSizeChanged { size = it }
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
    )
}

/**
 * The screen's main button (Check, Continue, Show answer…). Inside a [PrimaryActionHost] it is
 * not drawn in place: the host pins it in a bar at the bottom of the screen, directly above the
 * keyboard, so it can never be covered (#5). Elsewhere it is an ordinary full-width button.
 * Hides the keyboard before running [onClick], so feedback is not covered either.
 */
@Composable
fun CheckButton(enabled: Boolean, onClick: () -> Unit, label: String = "Check") {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val run = {
        keyboard?.hide()
        focusManager.clearFocus()
        onClick()
    }
    val host = LocalPrimaryAction.current
    if (host != null) {
        val token = remember { Any() }
        val latest by rememberUpdatedState(run)
        SideEffect { host.set(token, label, enabled) { latest() } }
        DisposableEffect(host, token) { onDispose { host.clear(token) } }
        return
    }
    Button(onClick = run, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(label)
    }
}

/** The pinned main action of a screen; see [CheckButton]. */
@Stable
class PrimaryActionState {
    var label by mutableStateOf<String?>(null)
        private set
    var enabled by mutableStateOf(false)
        private set
    private var owner: Any? = null
    private var action: () -> Unit = {}

    fun set(token: Any, label: String, enabled: Boolean, onClick: () -> Unit) {
        owner = token
        this.label = label
        this.enabled = enabled
        action = onClick
    }

    fun clear(token: Any) {
        if (owner !== token) return
        owner = null
        label = null
        enabled = false
        action = {}
    }

    fun click() {
        if (enabled) action()
    }
}

val LocalPrimaryAction = compositionLocalOf<PrimaryActionState?> { null }

/** Provides a [PrimaryActionState] to [content]; draw it with [PrimaryActionBar]. */
@Composable
fun PrimaryActionHost(state: PrimaryActionState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPrimaryAction provides state, content = content)
}

/** The bar holding the pinned main button; nothing when no button is registered. */
@Composable
fun PrimaryActionBar(state: PrimaryActionState) {
    val label = state.label ?: return
    Surface(tonalElevation = 3.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = state::click,
                enabled = state.enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(label, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** An answer option; after answering, the correct one turns green and a wrong pick red. */
@Composable
fun OptionButton(
    text: String,
    selected: Boolean,
    answered: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit,
) {
    val container = when {
        answered && isCorrect -> verdictColor(Verdict.CORRECT)
        answered && selected -> verdictColor(Verdict.WRONG)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    OutlinedButton(
        onClick = onClick,
        enabled = !answered,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = container,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(text, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
    }
}

/** "Type instead" / "Skip" row offered on every speaking exercise. */
@Composable
fun SpeechFallbackRow(
    showTypeInstead: Boolean,
    onTypeInstead: () -> Unit,
    onSkip: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTypeInstead) {
            TextButton(onClick = onTypeInstead, enabled = enabled) { Text("Type instead") }
        }
        TextButton(onClick = onSkip, enabled = enabled) { Text("Skip") }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun Hint(text: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Hint: $text",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
