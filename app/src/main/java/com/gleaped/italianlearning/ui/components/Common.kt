package com.gleaped.italianlearning.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Speaker button that reads Italian text aloud. */
@Composable
fun SpeakerButton(text: String, modifier: Modifier = Modifier, slow: Boolean = false) {
    val tts = LocalAppContainer.current.tts
    IconButton(onClick = { tts.speak(text, slow) }, modifier = modifier) {
        Icon(
            painter = painterResource(if (slow) R.drawable.ic_slow else R.drawable.ic_volume),
            contentDescription = if (slow) "Listen slowly" else "Listen",
            tint = LocalContentColor.current,
        )
    }
}

/** Italian text with a speaker button next to it. */
@Composable
fun ItalianText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = style, modifier = Modifier.weight(1f))
        SpeakerButton(text)
    }
}

/** Inline **bold** and *italic* markup. */
fun inlineMarkup(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
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

/** Markdown-lite: paragraphs separated by blank lines, `- ` bullets, inline bold/italic. */
@Composable
fun RichText(body: String, modifier: Modifier = Modifier) {
    val paragraphs = body.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    Column(modifier = modifier) {
        paragraphs.forEach { paragraph ->
            val lines = paragraph.lines()
            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.startsWith("- ") || line.startsWith("• ")) {
                        Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                            Text("•", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(8.dp))
                            Text(inlineMarkup(line.drop(2)), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else if (line.isNotEmpty()) {
                        Text(inlineMarkup(line), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
