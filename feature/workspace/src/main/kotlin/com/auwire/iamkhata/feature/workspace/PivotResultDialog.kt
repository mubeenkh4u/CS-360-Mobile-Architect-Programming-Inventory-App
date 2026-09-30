package com.auwire.iamkhata.feature.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.PivotResult
import java.text.NumberFormat
import java.util.Locale

/** Read-only pivot output; source rows are never mutated. */
@Composable
internal fun PivotResultDialog(
    result: PivotResult,
    onDismiss: () -> Unit,
) {
    val number = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val lookup = remember(result) { result.cells.associateBy { it.rowKey to it.columnKey } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pivot result") },
        text = {
            Column(
                Modifier
                    .height(420.dp)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState()),
            ) {
                Row {
                    Text("Row", Modifier.width(TableCellWidth), fontWeight = FontWeight.Bold)
                    result.columnKeys.forEach {
                        Text(it, Modifier.width(TableCellWidth), fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider()
                result.rowKeys.take(MAX_PIVOT_ROWS).forEach { row ->
                    Row {
                        Text(row, Modifier.width(TableCellWidth))
                        result.columnKeys.forEach { column ->
                            Text(
                                lookup[row to column]?.value?.let(number::format).orEmpty(),
                                Modifier.width(TableCellWidth),
                            )
                        }
                    }
                    HorizontalDivider()
                }
                if (result.rowKeys.size > MAX_PIVOT_ROWS) {
                    Text("Showing the first $MAX_PIVOT_ROWS pivot rows.")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
    )
}

private const val MAX_PIVOT_ROWS = 200
