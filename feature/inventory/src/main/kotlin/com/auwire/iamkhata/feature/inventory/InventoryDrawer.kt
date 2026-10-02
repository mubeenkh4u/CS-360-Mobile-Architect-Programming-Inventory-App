package com.auwire.iamkhata.feature.inventory

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
import com.auwire.iamkhata.core.model.ThemeMode
import com.auwire.iamkhata.core.ui.DrawerAction
import com.auwire.iamkhata.core.ui.ThemeModeSection

/** Inventory-specific actions presented in the collapsible left drawer. */
@Composable
internal fun InventoryDrawer(
    hasProducts: Boolean,
    importExportEnabled: Boolean,
    themeMode: ThemeMode,
    onAddProduct: () -> Unit,
    onAdjustStock: () -> Unit,
    onNewSale: () -> Unit,
    onNewPurchase: () -> Unit,
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
                "Inventory",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
            )
            DrawerAction("Add product", onClick = onAddProduct)
            DrawerAction(
                "Adjust base stock",
                enabled = hasProducts,
                onClick = onAdjustStock,
            )
            DrawerAction(
                "New sale",
                enabled = hasProducts,
                onClick = onNewSale,
            )
            DrawerAction(
                "New purchase",
                enabled = hasProducts,
                onClick = onNewPurchase,
            )

            if (importExportEnabled) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Data transfer",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                )
                DrawerAction("Import inventory CSV", onClick = onImportCsv)
                DrawerAction(
                    "Export inventory CSV",
                    enabled = hasProducts,
                    onClick = onExportCsv,
                )
            }

            ThemeModeSection(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
    }
}
