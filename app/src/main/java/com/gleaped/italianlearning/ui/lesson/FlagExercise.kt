package com.gleaped.italianlearning.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.domain.flags.ExerciseFlag
import com.gleaped.italianlearning.domain.flags.FlagLog
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * "Segnala": marks the exercise on screen as wrong, unclear or too strict, with an optional
 * note. Flags are kept on the phone and can be sent on from Settings.
 */
@Composable
fun FlagExerciseButton(unitId: String, lessonId: String, index: Int, exerciseId: String?, type: String) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var done by remember(unitId, lessonId, index, exerciseId) { mutableStateOf(false) }
    var note by remember(unitId, lessonId, index, exerciseId) { mutableStateOf("") }
    TextButton(onClick = { open = true }) { Text(if (done) "Segnalato" else "Segnala") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Flag this exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Use it when the answer was right but marked wrong, the Italian sounds off, or the task is unclear. " +
                            "A note helps but is optional.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(FlagLog.MAX_NOTE) },
                        label = { Text("What is wrong?") },
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    done = true
                    val flag = ExerciseFlag(
                        id = FlagLog.key(unitId, lessonId, index, exerciseId),
                        unitId = unitId,
                        lessonId = lessonId,
                        exerciseIndex = index,
                        exerciseId = exerciseId,
                        type = type,
                        note = note,
                        flaggedAtMillis = System.currentTimeMillis(),
                    )
                    // Outlives the dialog, so the flag is kept even if the lesson is closed straight away.
                    container.appScope.launch { container.flags.add(flag) }
                }) { Text("Save flag") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
