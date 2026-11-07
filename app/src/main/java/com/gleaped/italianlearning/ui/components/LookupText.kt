package com.gleaped.italianlearning.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.gleaped.italianlearning.domain.dictionary.WordNormalizer

/**
 * Set by screens that offer long-press word lookup (lesson player, challenge). When null,
 * [LookupText] is a plain Text.
 */
val LocalWordLookup = staticCompositionLocalOf<((String) -> Unit)?> { null }

@Composable
fun LookupText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign? = null,
) = LookupText(AnnotatedString(text), modifier, style, color, fontWeight, fontStyle, textAlign)

/**
 * Text whose words can be looked up with a long press (when [LocalWordLookup] is provided).
 * Taps and scrolls pass through untouched: the gesture only consumes events once a long
 * press has been recognised, so parents (cards, scrolling columns) behave as before.
 */
@Composable
fun LookupText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign? = null,
) {
    val onLookup = LocalWordLookup.current
    if (onLookup == null) {
        Text(text, modifier = modifier, color = color, fontWeight = fontWeight, fontStyle = fontStyle, textAlign = textAlign, style = style)
        return
    }
    val currentLookup by rememberUpdatedState(onLookup)
    val haptics = LocalHapticFeedback.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        color = color,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textAlign = textAlign,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(text) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                val result = layout ?: return@awaitEachGesture
                val word = WordNormalizer.wordAt(text.text, result.getOffsetForPosition(press.position))
                    ?: return@awaitEachGesture
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentLookup(word)
                // Swallow the rest of this press so a parent click does not also fire.
                press.consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        },
    )
}
