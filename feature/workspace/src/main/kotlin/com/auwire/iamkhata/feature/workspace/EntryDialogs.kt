package com.auwire.iamkhata.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.ColumnRole
import com.auwire.iamkhata.core.model.ColumnType
import com.auwire.iamkhata.core.model.DataRow
import com.auwire.iamkhata.core.model.DatasetColumn
import com.auwire.iamkhata.core.model.RowStatus

@Composable
internal fun AddColumnDialog(
    onDismiss: () -> Unit,
    onAdd: (String, ColumnType) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ColumnType.TEXT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add column") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Column name") },
                    singleLine = true,
                )
                Selector(
                    label = "Type",
                    value = type,
                    options = ColumnType.entries.filter { it != ColumnType.FORMULA },
                    display = ColumnType::name,
                    onSelect = { type = it },
                )
                Text("State and Balance are system-managed and cannot be added as custom columns.")
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, type) }, enabled = name.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun RowEditorDialog(
    columns: List<DatasetColumn>,
    row: DataRow?,
    onDismiss: () -> Unit,
    onSave: (Map<Long, String>, RowStatus) -> Unit,
) {
    val values = remember(columns, row?.id, row?.revision) {
        mutableStateMapOf<Long, String>().apply {
            row?.values?.forEach { (columnId, cell) ->
                this[columnId] = cell.rawValue
            }
        }
    }
    val canFinalize = columns
        .filter { it.required }
        .all { values[it.id].orEmpty().isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (row == null) {
                    "Add row"
                } else {
                    "Edit row #${row.id} • revision ${row.revision}"
                },
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row?.let {
                    Text("Current state: ${it.status.name}")
                    if (it.status == RowStatus.FINAL) {
                        Text("Final rows stay final after correction. Use Void or Reverse for accounting corrections that should preserve history.")
                    }
                }
                columns
                    .filter { it.type != ColumnType.FORMULA && it.role != ColumnRole.STOCK_STATUS }
                    .forEach { column ->
                        OutlinedTextField(
                            value = values[column.id].orEmpty(),
                            onValueChange = { values[column.id] = it },
                            label = {
                                Text(column.displayName + if (column.required) " *" else "")
                            },
                            singleLine = true,
                        )
                    }
            }
        },
        confirmButton = {
            when (row?.status) {
                RowStatus.FINAL -> {
                    Button(
                        enabled = canFinalize,
                        onClick = { onSave(values.toMap(), RowStatus.FINAL) },
                    ) {
                        Text("Save correction")
                    }
                }

                RowStatus.REVERSED,
                RowStatus.VOID -> Unit

                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onSave(values.toMap(), RowStatus.DRAFT) },
                        ) {
                            Text("Save draft")
                        }
                        Button(
                            enabled = canFinalize,
                            onClick = { onSave(values.toMap(), RowStatus.FINAL) },
                        ) {
                            Text("Finalize")
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AccountingActionDialog(
    title: String,
    explanation: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(explanation)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason *") },
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = reason.isNotBlank(),
                onClick = { onConfirm(reason) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
