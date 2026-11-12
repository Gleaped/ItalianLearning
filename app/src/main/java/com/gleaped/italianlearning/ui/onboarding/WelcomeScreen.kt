package com.gleaped.italianlearning.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.domain.profile.LearnerName

/**
 * First run: asks for the learner's name, used wherever lesson dialogues address them (#7).
 * It can be changed later in Settings; skipping uses a neutral default.
 */
@Composable
fun WelcomeScreen(onSave: (String) -> Unit, onSkip: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val clean = LearnerName.clean(name)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Benvenuto! Benvenuta!", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Short daily lessons from B1 to C1, with the CELI 3 exam on the way. First, what should people in the lessons call you?",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Come ti chiami?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(LearnerName.MAX_LENGTH) },
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (clean.isNotEmpty()) onSave(clean) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "You can change it any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSave(clean) },
                enabled = clean.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Cominciamo", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
        }
    }
}
