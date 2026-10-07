package com.auwire.iamkhata.feature.workspace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.DataRow
import com.auwire.iamkhata.core.model.RowStatus
import com.auwire.iamkhata.core.model.ThemeMode
import com.auwire.iamkhata.core.model.WorkspaceFeatures
import com.auwire.iamkhata.core.model.displayState
import com.auwire.iamkhata.core.ui.DrawerAction
import com.auwire.iamkhata.core.ui.ThemeModeSection

@Composable
internal fun WorkspaceDrawer(
    selectedRow: DataRow?,
    features: WorkspaceFeatures,
    themeMode: ThemeMode,
    onAddRow: () -> Unit,
    onEditRow: () -> Unit,
    onVoidRow: () -> Unit,
    onReverseRow: () -> Unit,
    onAddColumn: () -> Unit,
    onClean: () -> Unit,
    onPivot: () -> Unit,
    onImportCsv: () -> Unit,
    onExportCsv: () -> Unit,
    onExportFilteredCsv: () -> Unit,
    hasAppliedFilter: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val canEdit = selectedRow != null &&
        !selectedRow.isLocked &&
        (selectedRow.status == RowStatus.DRAFT || selectedRow.status == RowStatus.FINAL)
    val canAccountingAction = selectedRow != null &&
        !selectedRow.isLocked &&
        selectedRow.status == RowStatus.FINAL

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            Text(
                "Khata",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
            )
            DrawerAction("Add row", onClick = onAddRow)
            DrawerAction(
                "Edit selected row",
                enabled = canEdit,
                onClick = onEditRow,
            )
            DrawerAction(
                "Void selected final row",
                enabled = canAccountingAction,
                onClick = onVoidRow,
            )
            DrawerAction(
                "Reverse selected final row",
                enabled = canAccountingAction,
                onClick = onReverseRow,
            )
            DrawerAction("Add column", onClick = onAddColumn)

            if (selectedRow != null) {
                Text(
                    buildString {
                        append("Selected #${selectedRow.id} • ${selectedRow.displayState()} • r${selectedRow.revision}")
                        if (selectedRow.isLocked) append(" • immutable")
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Data tools",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontWeight = FontWeight.Bold,
            )
            if (features.cleaning) {
                DrawerAction("Clean draft column", onClick = onClean)
            }
            if (features.pivot) {
                DrawerAction("Pivot", onClick = onPivot)
            }
            if (features.importExport) {
                DrawerAction("Import CSV", onClick = onImportCsv)
                DrawerAction("Export all CSV", onClick = onExportCsv)
                DrawerAction(
                    "Export filtered CSV",
                    enabled = hasAppliedFilter,
                    onClick = onExportFilteredCsv,
                )
            }

            ThemeModeSection(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
    }
}
