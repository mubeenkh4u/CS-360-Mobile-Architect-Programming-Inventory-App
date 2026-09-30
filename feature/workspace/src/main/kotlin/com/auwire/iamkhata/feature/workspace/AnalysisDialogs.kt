package com.auwire.iamkhata.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.Aggregation
import com.auwire.iamkhata.core.model.CleaningOperation
import com.auwire.iamkhata.core.model.ColumnType
import com.auwire.iamkhata.core.model.DatasetColumn
import com.auwire.iamkhata.core.model.PivotSpec

/** Safe in-place cleaning for non-protected text/category columns. */
@Composable
internal fun CleanColumnDialog(
    columns: List<DatasetColumn>,
    onDismiss: () -> Unit,
    onApply: (DatasetColumn, CleaningOperation) -> Unit,
) {
    val eligible = columns.filter {
        !it.isProtected && (it.type == ColumnType.TEXT || it.type == ColumnType.CATEGORY)
    }
    var selected by remember(eligible) { mutableStateOf(eligible.firstOrNull()) }
    var operation by remember { mutableStateOf(CleaningOperation.TRIM_WHITESPACE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clean column") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (eligible.isEmpty()) {
                    Text("Add a non-protected text/category column before using cleaning tools.")
                } else {
                    Selector(
                        "Column",
                        selected!!,
                        eligible,
                        DatasetColumn::displayName,
                    ) { selected = it }
                    Selector(
                        "Operation",
                        operation,
                        CleaningOperation.entries,
                        { it.name.replace('_', ' ') },
                    ) { operation = it }
                    Text(
                        "Cleaning is transactional and audited. Protected accounting columns are not modified in-place.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selected != null,
                onClick = { selected?.let { onApply(it, operation) } },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Visual pivot definition; no query language is exposed to the user. */
@Composable
internal fun PivotBuilderDialog(
    columns: List<DatasetColumn>,
    onDismiss: () -> Unit,
    onRun: (PivotSpec) -> Unit,
) {
    val numeric = columns.filter { it.type.isNumeric() }
    var rowColumn by remember(columns) { mutableStateOf(columns.firstOrNull()) }
    var columnColumn by remember { mutableStateOf<DatasetColumn?>(null) }
    var valueColumn by remember(numeric) { mutableStateOf(numeric.firstOrNull()) }
    var aggregation by remember { mutableStateOf(Aggregation.SUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pivot builder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (columns.isEmpty() || numeric.isEmpty()) {
                    Text("A pivot needs a grouping column and a numeric value column.")
                } else {
                    Selector("Rows", rowColumn!!, columns, DatasetColumn::displayName) {
                        rowColumn = it
                    }
                    OptionalColumnSelector("Columns", columnColumn, columns) {
                        columnColumn = it
                    }
                    Selector("Values", valueColumn!!, numeric, DatasetColumn::displayName) {
                        valueColumn = it
                    }
                    Selector("Aggregate", aggregation, Aggregation.entries, Aggregation::name) {
                        aggregation = it
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = rowColumn != null && valueColumn != null,
                onClick = {
                    onRun(
                        PivotSpec(
                            rowColumnId = rowColumn!!.id,
                            columnColumnId = columnColumn?.id,
                            valueColumnId = valueColumn!!.id,
                            aggregation = aggregation,
                        ),
                    )
                },
            ) { Text("Run") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun ColumnType.isNumeric(): Boolean =
    this == ColumnType.INTEGER ||
        this == ColumnType.DECIMAL ||
        this == ColumnType.CURRENCY ||
        this == ColumnType.PERCENTAGE
