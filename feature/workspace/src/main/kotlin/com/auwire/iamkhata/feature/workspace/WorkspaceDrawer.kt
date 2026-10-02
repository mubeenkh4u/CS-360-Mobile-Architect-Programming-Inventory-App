package com.auwire.iamkhata.feature.workspace

import androidx.compose.foundation.layout.Column
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
import com.auwire.iamkhata.core.model.ThemeMode
import com.auwire.iamkhata.core.model.WorkspaceFeatures
import com.auwire.iamkhata.core.ui.DrawerAction
import com.auwire.iamkhata.core.ui.ThemeModeSection

/** Khata-specific actions presented in the collapsible left drawer. */
@Composable
internal fun WorkspaceDrawer(
    selectedRow: DataRow?,
    features: WorkspaceFeatures,
    themeMode: ThemeMode,
    onAddRow: () -> Unit,
    onEditRow: () -> Unit,
    onAddColumn: () -> Unit,
    onClean: () -> Unit,
    onPivot: () -> Unit,
    onImportCsv: () -> Unit,
    onExportCsv: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
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
                enabled = selectedRow != null && !selectedRow.isLocked,
                onClick = onEditRow,
            )
            DrawerAction("Add column", onClick = onAddColumn)

            if (selectedRow != null) {
                Text(
                    if (selectedRow.isLocked) {
                        "Row #${selectedRow.id} is system-managed and locked."
                    } else {
                        "Selected #${selectedRow.id} • ${selectedRow.status.name} • r${selectedRow.revision}"
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
                DrawerAction("Clean column", onClick = onClean)
            }
            if (features.pivot) {
                DrawerAction("Pivot", onClick = onPivot)
            }
            if (features.importExport) {
                DrawerAction("Import CSV", onClick = onImportCsv)
                DrawerAction("Export CSV", onClick = onExportCsv)
            }

            ThemeModeSection(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
    }
}
