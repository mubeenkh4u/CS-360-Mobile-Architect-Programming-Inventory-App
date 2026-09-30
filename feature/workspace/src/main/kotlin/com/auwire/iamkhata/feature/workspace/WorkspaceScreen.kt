package com.auwire.iamkhata.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auwire.iamkhata.core.model.WorkspaceFeatures

/** Main GUI surface for dynamic tabular data. */
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    features: WorkspaceFeatures,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addColumnOpen by remember { mutableStateOf(false) }
    var addRowOpen by remember { mutableStateOf(false) }
    var cleanOpen by remember { mutableStateOf(false) }
    var pivotOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "IAM Khata Data Workbench",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { addRowOpen = true }, enabled = state.columns.isNotEmpty()) {
                Text("Add row")
            }
            OutlinedButton(onClick = { addColumnOpen = true }) { Text("Add column") }
            if (features.cleaning) {
                OutlinedButton(onClick = { cleanOpen = true }) { Text("Clean") }
            }
            if (features.pivot) {
                OutlinedButton(onClick = { pivotOpen = true }) { Text("Pivot") }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.searchText,
                onValueChange = viewModel::setSearchText,
                label = { Text("Search all columns") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::applySearch, modifier = Modifier.padding(top = 8.dp)) {
                Text("Apply")
            }
        }

        Text(
            "Rows: ${state.page.totalRows}  •  Columns: ${state.columns.size}",
            style = MaterialTheme.typography.bodySmall,
        )

        state.message?.let { message ->
            Surface(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.small,
                onClick = viewModel::clearMessage,
            ) {
                Text(message, Modifier.padding(8.dp))
            }
        }

        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        DataGrid(
            columns = state.columns,
            rows = state.page.rows,
            sortColumnId = state.sort?.columnId,
            sortDirection = state.sort?.direction,
            onSort = viewModel::toggleSort,
            modifier = Modifier.weight(1f),
        )

        PaginationBar(
            offset = state.page.offset,
            pageSize = state.page.limit,
            loadedRows = state.page.rows.size,
            totalRows = state.page.totalRows,
            onPrevious = viewModel::previousPage,
            onNext = viewModel::nextPage,
        )
    }

    if (addColumnOpen) {
        AddColumnDialog(
            onDismiss = { addColumnOpen = false },
            onAdd = { name, type ->
                viewModel.addColumn(name, type)
                addColumnOpen = false
            },
        )
    }
    if (addRowOpen) {
        AddRowDialog(
            columns = state.columns,
            onDismiss = { addRowOpen = false },
            onAdd = {
                viewModel.addRow(it)
                addRowOpen = false
            },
        )
    }
    if (cleanOpen) {
        CleanColumnDialog(
            columns = state.columns,
            onDismiss = { cleanOpen = false },
            onApply = { column, operation ->
                viewModel.cleanColumn(column.id, operation)
                cleanOpen = false
            },
        )
    }
    if (pivotOpen) {
        PivotBuilderDialog(
            columns = state.columns,
            onDismiss = { pivotOpen = false },
            onRun = {
                viewModel.runPivot(it)
                pivotOpen = false
            },
        )
    }
    state.pivot?.let {
        PivotResultDialog(result = it, onDismiss = viewModel::dismissPivot)
    }
}
