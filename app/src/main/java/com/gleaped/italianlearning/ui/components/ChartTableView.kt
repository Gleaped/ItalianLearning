package com.gleaped.italianlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gleaped.italianlearning.content.reference.ChartTable

/** Below this width per column, cells get a fixed width and the table scrolls sideways. */
private val MinColumnWidth = 72.dp

/**
 * A compact chart: optional header row, zebra rows, cells that wrap rather than scroll.
 * Only when columns would get narrower than [MinColumnWidth] (very large font scales on a
 * small phone) does the table switch to fixed-width columns with horizontal scrolling.
 */
@Composable
fun ChartTableView(table: ChartTable, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val width = table.width
    if (width == 0 || table.rows.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTitle && table.title.isNotBlank()) {
            Text(
                table.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val minColumn = MinColumnWidth * fontScale.coerceAtLeast(1f)
                val scroll = maxWidth / width < minColumn
                val columnWidth: Dp? = if (scroll) minColumn + 24.dp else null
                val body = @Composable {
                    Column(if (scroll) Modifier.width(columnWidth!! * width) else Modifier.fillMaxWidth()) {
                        if (table.hasHeader) {
                            TableRow(
                                cells = table.columns + List((width - table.columns.size).coerceAtLeast(0)) { "" },
                                header = true,
                                background = MaterialTheme.colorScheme.surfaceVariant,
                                firstIsLabel = false,
                            )
                        }
                        table.rows.indices.forEach { i ->
                            if (i > 0 || table.hasHeader) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TableRow(
                                cells = table.cells(i),
                                header = false,
                                background = if (i % 2 == 1) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
                                firstIsLabel = table.columns.firstOrNull()?.isBlank() == true && width > 1,
                            )
                        }
                    }
                }
                if (scroll) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) { body() }
                } else {
                    body()
                }
            }
        }
        table.note?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean, background: Color, firstIsLabel: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 4.dp),
    ) {
        cells.forEachIndexed { i, cell ->
            val label = firstIsLabel && i == 0
            Text(
                text = inlineMarkup(cell, highlightColor()),
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else null,
                color = if (label) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    // The person/label column is a little narrower than the form columns.
                    .weight(if (label) 0.75f else 1f)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}
