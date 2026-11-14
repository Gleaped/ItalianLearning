package com.gleaped.italianlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.AppContainer
import com.gleaped.italianlearning.R
import com.gleaped.italianlearning.content.RuleBlock
import com.gleaped.italianlearning.content.RuleMarkup
import com.gleaped.italianlearning.ui.theme.HighlightColors

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Speaker button that reads Italian text aloud. */
@Composable
fun SpeakerButton(
    text: String,
    modifier: Modifier = Modifier,
    slow: Boolean = false,
    /** Use the other speaker's voice (the partner in a role-play). */
    second: Boolean = false,
) {
    val tts = LocalAppContainer.current.tts
    IconButton(onClick = { tts.speak(text, slow, second = second) }, modifier = modifier) {
        Icon(
            painter = painterResource(if (slow) R.drawable.ic_slow else R.drawable.ic_volume),
            contentDescription = if (slow) "Listen slowly" else "Listen",
            tint = LocalContentColor.current,
        )
    }
}

/** Italian text with a speaker button next to it (long-press a word to look it up, where offered). */
@Composable
fun ItalianText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    /** What the speaker button reads, when it differs from what is shown. */
    speak: String = text,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LookupText(text = text, style = style, modifier = Modifier.weight(1f))
        SpeakerButton(speak)
    }
}

/** Inline **bold**, *italic* and ==highlight== markup; highlights use [highlight] in bold. */
fun inlineMarkup(text: String, highlight: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("==", i) -> {
                val end = text.indexOf("==", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlight)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append("==")
                    i += 2
                }
            }
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append("**")
                    i += 2
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1 && !text.startsWith("**", end)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append('*')
                    i += 1
                }
            }
            else -> {
                append(text[i])
                i += 1
            }
        }
    }
}

/** The accent colour for ==highlighted== text in the current theme. */
@Composable
fun highlightColor(): Color = if (isDarkSurface()) HighlightColors.dark else HighlightColors.light

/**
 * Rule-card markup (see [RuleMarkup]): paragraphs, bullets, pipe tables and "watch out" callouts,
 * with inline bold, italic and ==highlight==. Words can be long-pressed to look them up.
 */
@Composable
fun RichText(body: String, modifier: Modifier = Modifier) {
    val blocks = remember(body) { RuleMarkup.parse(body) }
    val accent = highlightColor()
    val style = MaterialTheme.typography.bodyLarge
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        blocks.forEach { block ->
            when (block) {
                is RuleBlock.Paragraph -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.lines.forEach { LookupText(inlineMarkup(it, accent), style = style) }
                }
                is RuleBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.items.forEach { item ->
                        Row(modifier = Modifier.padding(start = 4.dp)) {
                            Text("•", style = style, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            LookupText(inlineMarkup(item, accent), style = style)
                        }
                    }
                }
                is RuleBlock.Table -> ChartTableView(block.table, showTitle = false)
                is RuleBlock.Callout -> Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("!", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
                        LookupText(inlineMarkup(block.text, MaterialTheme.colorScheme.onSecondaryContainer), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
