package com.gleaped.italianlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.domain.srs.Rating
import com.gleaped.italianlearning.ui.theme.FeedbackColors

/** How a rating looks: a plain label, what it means, and its colour pair (light, dark). */
private data class RatingStyle(val label: String, val meaning: String, val light: Color, val dark: Color)

private val Styles = mapOf(
    Rating.AGAIN to RatingStyle("Forgot", "I didn't know it", FeedbackColors.wrongLight, FeedbackColors.wrongDark),
    Rating.HARD to RatingStyle("Hard", "I got it, just", FeedbackColors.almostLight, FeedbackColors.almostDark),
    Rating.GOOD to RatingStyle("Got it", "I knew it", FeedbackColors.correctLight, FeedbackColors.correctDark),
    Rating.EASY to RatingStyle("Easy", "Too easy", Color(0xFFD6E6FF), Color(0xFF203C66)),
)

/**
 * The four recall ratings as a 2 × 2 grid of large, coloured buttons with plain labels (#6).
 * [whenBack] gives, per rating, when the card returns ("tomorrow", "in 6 days"); without it each
 * button says what the rating means instead.
 */
@Composable
fun RatingChoices(
    onRate: (Rating) -> Unit,
    modifier: Modifier = Modifier,
    whenBack: ((Rating) -> String)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("How well did you know it?", style = MaterialTheme.typography.titleMedium)
        listOf(listOf(Rating.AGAIN, Rating.HARD), listOf(Rating.GOOD, Rating.EASY)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { rating ->
                    RatingButton(rating, whenBack?.invoke(rating), Modifier.weight(1f)) { onRate(rating) }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(rating: Rating, whenBack: String?, modifier: Modifier, onClick: () -> Unit) {
    val style = Styles.getValue(rating)
    val container = if (isDarkSurface()) style.dark else style.light
    val sub = whenBack?.let { "Back $it" } ?: style.meaning
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics { contentDescription = "${style.label}. $sub" },
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(style.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(sub, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
