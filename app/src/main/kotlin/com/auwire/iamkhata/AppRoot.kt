package com.auwire.iamkhata

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auwire.iamkhata.core.model.ThemeMode
import com.auwire.iamkhata.core.model.WorkspaceFeatures
import com.auwire.iamkhata.feature.inventory.InventoryScreen
import com.auwire.iamkhata.feature.inventory.InventoryViewModel
import com.auwire.iamkhata.feature.inventory.InventoryViewModelFactory
import com.auwire.iamkhata.feature.workspace.WorkspaceScreen
import com.auwire.iamkhata.feature.workspace.WorkspaceViewModel
import com.auwire.iamkhata.feature.workspace.WorkspaceViewModelFactory

/**
 * Top-level feature switch.
 *
 * A plain Column is used instead of another Scaffold. Each feature screen owns
 * its own top app bar/insets, while NavigationBar owns the bottom inset. This
 * prevents the status-bar inset from being applied twice.
 */
@Composable
fun AppRoot(
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val workspaceViewModel: WorkspaceViewModel = viewModel(
        factory = WorkspaceViewModelFactory(container.datasetRepository),
    )

    if (!BuildConfig.FEATURE_INVENTORY) {
        WorkspaceScreen(
            viewModel = workspaceViewModel,
            features = workspaceFeatures(),
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )
        return
    }

    val inventoryViewModel: InventoryViewModel = viewModel(
        factory = InventoryViewModelFactory(container.inventoryRepository),
    )
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (selected) {
                0 -> WorkspaceScreen(
                    viewModel = workspaceViewModel,
                    features = workspaceFeatures(),
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                )

                else -> InventoryScreen(
                    viewModel = inventoryViewModel,
                    tentativeStockEnabled = BuildConfig.FEATURE_TENTATIVE_STOCK,
                    importExportEnabled = BuildConfig.FEATURE_IMPORT_EXPORT,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                )
            }
        }

        NavigationBar {
            NavigationBarItem(
                selected = selected == 0,
                onClick = { selected = 0 },
                icon = { Text("K") },
                label = { Text("Khata") },
            )
            NavigationBarItem(
                selected = selected == 1,
                onClick = { selected = 1 },
                icon = { Text("I") },
                label = { Text("Inventory") },
            )
        }
    }
}

private fun workspaceFeatures() = WorkspaceFeatures(
    cleaning = BuildConfig.FEATURE_CLEANING,
    pivot = BuildConfig.FEATURE_PIVOT,
    importExport = BuildConfig.FEATURE_IMPORT_EXPORT,
    cloudSync = BuildConfig.FEATURE_CLOUD_SYNC,
)
