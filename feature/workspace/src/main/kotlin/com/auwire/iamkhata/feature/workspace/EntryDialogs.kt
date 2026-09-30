package com.auwire.iamkhata.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.ColumnType
import com.auwire.iamkhata.core.model.DatasetColumn

/** Adds a runtime column without requiring an app/database schema migration. */
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
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, type) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Dynamic row form generated from the active dataset schema. */
@Composable
internal fun AddRowDialog(
    columns: List<DatasetColumn>,
    onDismiss: () -> Unit,
    onAdd: (Map<Long, String>) -> Unit,
) {
    val values = remember(columns) { mutableStateMapOf<Long, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add row") },
        text = {
            Column(
                Modifier.height(420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                columns.filter { it.type != ColumnType.FORMULA }.forEach { column ->
                    OutlinedTextField(
                        value = values[column.id].orEmpty(),
                        onValueChange = { values[column.id] = it },
                        label = { Text(column.displayName + if (column.required) " *" else "") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onAdd(values.toMap()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
