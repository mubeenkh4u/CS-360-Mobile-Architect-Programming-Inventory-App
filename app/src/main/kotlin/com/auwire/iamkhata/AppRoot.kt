package com.auwire.iamkhata

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auwire.iamkhata.core.model.WorkspaceFeatures
import com.auwire.iamkhata.feature.inventory.InventoryScreen
import com.auwire.iamkhata.feature.inventory.InventoryViewModel
import com.auwire.iamkhata.feature.inventory.InventoryViewModelFactory
import com.auwire.iamkhata.feature.workspace.WorkspaceScreen
import com.auwire.iamkhata.feature.workspace.WorkspaceViewModel
import com.auwire.iamkhata.feature.workspace.WorkspaceViewModelFactory

/** Top-level feature switch; individual domains remain independently usable. */
@Composable
fun AppRoot(container: AppContainer) {
    val workspaceViewModel: WorkspaceViewModel = viewModel(
        factory = WorkspaceViewModelFactory(container.datasetRepository),
    )

    if (!BuildConfig.FEATURE_INVENTORY) {
        WorkspaceScreen(
            viewModel = workspaceViewModel,
            features = workspaceFeatures(),
        )
        return
    }

    val inventoryViewModel: InventoryViewModel = viewModel(
        factory = InventoryViewModelFactory(container.inventoryRepository),
    )
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
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
        },
    ) { padding ->
        when (selected) {
            0 -> WorkspaceScreen(
                viewModel = workspaceViewModel,
                features = workspaceFeatures(),
                modifier = Modifier.padding(padding),
            )

            else -> InventoryScreen(
                viewModel = inventoryViewModel,
                tentativeStockEnabled = BuildConfig.FEATURE_TENTATIVE_STOCK,
                modifier = Modifier.padding(padding),
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
