package com.auwire.iamkhata.feature.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auwire.iamkhata.core.model.DataRow
import com.auwire.iamkhata.core.model.RowStatus
import com.auwire.iamkhata.core.model.ThemeMode
import com.auwire.iamkhata.core.model.WorkspaceFeatures
import com.auwire.iamkhata.core.model.displayState
import com.auwire.iamkhata.core.ui.AuwireTopBar
import kotlinx.coroutines.launch

@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    features: WorkspaceFeatures,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedRow = state.page.rows.firstOrNull { it.id == state.selectedRowId }
    val context = LocalContext.current
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var addColumnOpen by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorRow by remember { mutableStateOf<DataRow?>(null) }
    var voidOpen by remember { mutableStateOf(false) }
    var reverseOpen by remember { mutableStateOf(false) }
    var cleanOpen by remember { mutableStateOf(false) }
    var pivotOpen by remember { mutableStateOf(false) }
    var cardView by remember { mutableStateOf(false) }
    var exportFiltered by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            if (exportFiltered) {
                viewModel.exportFilteredCsv {
                    context.contentResolver.openOutputStream(uri, "wt")
                }
            } else {
                viewModel.exportCsv {
                    context.contentResolver.openOutputStream(uri, "wt")
                }
            }
        }
        exportFiltered = false
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importCsv {
                context.contentResolver.openInputStream(uri)
            }
        }
    }

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            WorkspaceDrawer(
                selectedRow = selectedRow,
                features = features,
                themeMode = themeMode,
                onAddRow = {
                    closeDrawerThen {
                        editorRow = null
                        editorOpen = true
                    }
                },
                onEditRow = {
                    closeDrawerThen {
                        editorRow = selectedRow
                        editorOpen = selectedRow != null &&
                            !selectedRow.isLocked &&
                            (selectedRow.status == RowStatus.DRAFT ||
                                selectedRow.status == RowStatus.FINAL)
                    }
                },
                onVoidRow = { closeDrawerThen { voidOpen = true } },
                onReverseRow = { closeDrawerThen { reverseOpen = true } },
                onAddColumn = { closeDrawerThen { addColumnOpen = true } },
                onClean = { closeDrawerThen { cleanOpen = true } },
                onPivot = { closeDrawerThen { pivotOpen = true } },
                onImportCsv = {
                    closeDrawerThen {
                        importLauncher.launch(CSV_MIME_TYPES)
                    }
                },
                onExportCsv = {
                    closeDrawerThen {
                        exportFiltered = false
                        exportLauncher.launch("AWi-k-Khata.csv")
                    }
                },
                onExportFilteredCsv = {
                    closeDrawerThen {
                        exportFiltered = true
                        exportLauncher.launch("AWi-k-Khata-filtered.csv")
                    }
                },
                hasAppliedFilter = state.appliedSearchText.isNotBlank(),
                onThemeModeChange = {
                    onThemeModeChange(it)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                AuwireTopBar(
                    section = "Khata",
                    onMenuClick = { scope.launch { drawerState.open() } },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.searchText,
                        onValueChange = viewModel::setSearchText,
                        label = { Text("Search all columns") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = viewModel::applySearch,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Apply")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !cardView,
                        onClick = { cardView = false },
                        label = { Text("Table") },
                    )
                    FilterChip(
                        selected = cardView,
                        onClick = { cardView = true },
                        label = { Text("Cards") },
                    )
                }

                Text(
                    buildString {
                        append("Rows: ${state.page.totalRows} • Data fields: ${state.columns.size} • Derived: State, Balance")
                        selectedRow?.let {
                            append(" • Selected #${it.id} [${it.displayState()}]")
                        }
                    },
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

                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                if (cardView) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.page.rows, key = DataRow::id) { row ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectRow(
                                            if (row.id == state.selectedRowId) null else row.id,
                                        )
                                    },
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        "Row #${row.id} • ${row.displayState()}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    state.columns.forEach { column ->
                                        val raw = row.values[column.id]?.rawValue.orEmpty()
                                        if (raw.isNotBlank()) {
                                            Text("${column.displayName}: $raw")
                                        }
                                        if (column.role == com.auwire.iamkhata.core.model.ColumnRole.CREDIT) {
                                            row.balanceMinor?.let {
                                                Text("Balance: ${com.auwire.iamkhata.core.model.FixedPoint.balanceDisplay(it)}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    DataGrid(
                        columns = state.columns,
                        rows = state.page.rows,
                        selectedRowId = state.selectedRowId,
                        sortColumnId = state.sort?.columnId,
                        sortDirection = state.sort?.direction,
                        onSelectRow = viewModel::selectRow,
                        onSort = viewModel::toggleSort,
                        modifier = Modifier.weight(1f),
                    )
                }

                PaginationBar(
                    offset = state.page.offset,
                    pageSize = state.page.limit,
                    loadedRows = state.page.rows.size,
                    totalRows = state.page.totalRows,
                    onPrevious = viewModel::previousPage,
                    onNext = viewModel::nextPage,
                )
            }
        }
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

    if (editorOpen) {
        RowEditorDialog(
            columns = state.columns,
            row = editorRow,
            onDismiss = { editorOpen = false },
            onSave = { values, status ->
                val target = editorRow
                if (target == null) {
                    viewModel.addRow(values, status)
                } else {
                    viewModel.updateRow(target, values, status)
                }
                editorOpen = false
            },
        )
    }

    if (voidOpen && selectedRow != null) {
        AccountingActionDialog(
            title = "Void row #${selectedRow.id}",
            explanation = "Voiding preserves the row and audit history but removes it from official balances and pivot totals. Use this only for an entry that should never have been posted.",
            confirmLabel = "Void row",
            onDismiss = { voidOpen = false },
            onConfirm = { reason ->
                viewModel.voidRow(selectedRow, reason)
                voidOpen = false
            },
        )
    }

    if (reverseOpen && selectedRow != null) {
        AccountingActionDialog(
            title = "Reverse row #${selectedRow.id}",
            explanation = "AWi&k will mark the source row REVERSED and create an immutable counter-entry dated today with Debit/Credit swapped. Both remain in history and net against each other.",
            confirmLabel = "Create reversal",
            onDismiss = { reverseOpen = false },
            onConfirm = { reason ->
                viewModel.reverseRow(selectedRow, reason)
                reverseOpen = false
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

private val CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/vnd.ms-excel",
    "text/plain",
)
