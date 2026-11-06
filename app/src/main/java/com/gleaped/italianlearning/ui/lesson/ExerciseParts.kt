package com.gleaped.italianlearning.ui.lesson

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.domain.grading.Verdict
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
    Verdict.CORRECT -> "Esatto! ✓"
    Verdict.ALMOST -> "Quasi!"
    Verdict.WRONG -> "Non proprio"
}

/** Text input for typed answers: no autocorrect, so accents are the learner's own. */
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CheckButton(enabled: Boolean, onClick: () -> Unit, label: String = "Check") {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label)
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
