package com.gleaped.italianlearning.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gleaped.italianlearning.domain.flags.FlagLog
import com.gleaped.italianlearning.ui.components.LocalAppContainer
import kotlinx.coroutines.launch

/** The exercises flagged while learning, with a button to send the list on so they can be corrected. */
@Composable
fun FlagsSection() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val flags by container.flags.flags.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { container.flags.load() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Flagged exercises", style = MaterialTheme.typography.titleSmall)
        Text(
            if (flags.isEmpty()) {
                "Tap Segnala in a lesson when an answer is marked wrong but was right, or an exercise is unclear. Flags are listed here."
            } else {
                "${flags.size} flagged. Send the list so the exercises can be corrected."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (flags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, FlagLog.shareText(flags))
                    // No app able to receive text is unusual but must not crash the screen.
                    runCatching { context.startActivity(Intent.createChooser(send, "Send flagged exercises")) }
                }) { Text("Send the list") }
                OutlinedButton(onClick = { scope.launch { container.flags.clear() } }) { Text("Clear") }
            }
        }
    }
}
