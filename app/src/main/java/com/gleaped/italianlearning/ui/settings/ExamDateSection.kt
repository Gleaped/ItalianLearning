package com.gleaped.italianlearning.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Choose, change or clear the day of the CELI 3 exam. It is optional and only drives the countdown and pace. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDateSection(examEpochDay: Long, onChange: (Long) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val format = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (examEpochDay > 0) {
                "Exam day: " + LocalDate.ofEpochDay(examEpochDay).format(format)
            } else {
                "No exam date set. With one, Today shows a countdown and how many lessons a week keep you on course."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = true }) { Text(if (examEpochDay > 0) "Change date" else "Set exam date") }
            if (examEpochDay > 0) TextButton(onClick = { onChange(0L) }) { Text("Clear") }
        }
    }
    if (picking) {
        // A date picker works in UTC milliseconds; with no date set it starts about six months ahead.
        val start = if (examEpochDay > 0) examEpochDay else LocalDate.now().plusDays(180).toEpochDay()
        val state = rememberDatePickerState(initialSelectedDateMillis = start * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                Button(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { ms ->
                            onChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                        }
                        picking = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
