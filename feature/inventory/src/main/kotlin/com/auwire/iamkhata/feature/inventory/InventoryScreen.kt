package com.auwire.iamkhata.feature.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.StockDocument
import com.auwire.iamkhata.core.model.StockDocumentKind
import com.auwire.iamkhata.core.model.StockSnapshot
import com.auwire.iamkhata.core.model.ThemeMode
import com.auwire.iamkhata.core.ui.AuwireTopBar
import kotlinx.coroutines.launch

/** Inventory workspace integrated with the canonical Khata ledger. */
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    tentativeStockEnabled: Boolean,
    importExportEnabled: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var addProductOpen by remember { mutableStateOf(false) }
    var adjustOpen by remember { mutableStateOf(false) }
    var saleOpen by remember { mutableStateOf(false) }
    var purchaseOpen by remember { mutableStateOf(false) }
    var cardView by remember { mutableStateOf(true) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsv {
                context.contentResolver.openOutputStream(uri, "wt")
            }
        }
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
            InventoryDrawer(
                hasProducts = state.stock.isNotEmpty(),
                importExportEnabled = importExportEnabled,
                themeMode = themeMode,
                onAddProduct = { closeDrawerThen { addProductOpen = true } },
                onAdjustStock = { closeDrawerThen { adjustOpen = true } },
                onNewSale = { closeDrawerThen { saleOpen = true } },
                onNewPurchase = { closeDrawerThen { purchaseOpen = true } },
                onImportCsv = {
                    closeDrawerThen {
                        importLauncher.launch(INVENTORY_CSV_MIME_TYPES)
                    }
                },
                onExportCsv = {
                    closeDrawerThen {
                        exportLauncher.launch("Auwire-Inventory.csv")
                    }
                },
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
                    section = "Inventory",
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
                Text(
                    "Base stock is committed physical stock. Tentative sales reserve availability; tentative purchases add projected incoming stock.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = cardView,
                        onClick = { cardView = true },
                        label = { Text("Cards") },
                    )
                    FilterChip(
                        selected = !cardView,
                        onClick = { cardView = false },
                        label = { Text("Table") },
                    )
                }

                state.message?.let { message ->
                    AssistChip(
                        onClick = viewModel::clearMessage,
                        label = { Text(message) },
                    )
                }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                if (cardView) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                "Live stock",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (state.stock.isEmpty()) {
                            item {
                                Text("No products yet. Use ☰ → Add product or import inventory CSV.")
                            }
                        } else {
                            items(state.stock, key = { it.product.id }) { snapshot ->
                                StockSnapshotCard(snapshot)
                            }
                        }

                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tentative workflow",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (state.tentativeDocuments.isEmpty()) {
                            item { Text("No tentative sales or purchases.") }
                        } else {
                            items(state.tentativeDocuments, key = StockDocument::id) { document ->
                                TentativeDocumentCard(
                                    document = document,
                                    onCommit = { viewModel.commitDocument(document.id) },
                                    onCancel = { viewModel.cancelDocument(document.id) },
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Live stock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (state.stock.isEmpty()) {
                            Text("No products yet. Use ☰ → Add product or import inventory CSV.")
                        } else {
                            InventoryStockTable(
                                stock = state.stock,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Text(
                            "Tentative workflow",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (state.tentativeDocuments.isEmpty()) {
                            Text("No tentative sales or purchases.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(0.45f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.tentativeDocuments, key = StockDocument::id) { document ->
                                    TentativeDocumentCard(
                                        document = document,
                                        onCommit = { viewModel.commitDocument(document.id) },
                                        onCancel = { viewModel.cancelDocument(document.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addProductOpen) {
        AddProductDialog(
            onDismiss = { addProductOpen = false },
            onSave = { sku, name, unit, reorder, saleRate, purchaseRate ->
                viewModel.createProduct(sku, name, unit, reorder, saleRate, purchaseRate)
                addProductOpen = false
            },
        )
    }
    if (adjustOpen && state.stock.isNotEmpty()) {
        AdjustStockDialog(
            stock = state.stock,
            onDismiss = { adjustOpen = false },
            onSave = { productId, delta, note ->
                viewModel.adjustBaseStock(productId, delta, note)
                adjustOpen = false
            },
        )
    }
    if (saleOpen && state.stock.isNotEmpty()) {
        StockDocumentDialog(
            kind = StockDocumentKind.SALE,
            stock = state.stock,
            tentativeStockEnabled = tentativeStockEnabled,
            onDismiss = { saleOpen = false },
            onSave = { productId, party, reference, quantity, rate, paid, note, tentative ->
                viewModel.createDocument(
                    StockDocumentKind.SALE,
                    productId,
                    party,
                    reference,
                    quantity,
                    rate,
                    paid,
                    note,
                    tentative,
                )
                saleOpen = false
            },
        )
    }
    if (purchaseOpen && state.stock.isNotEmpty()) {
        StockDocumentDialog(
            kind = StockDocumentKind.PURCHASE,
            stock = state.stock,
            tentativeStockEnabled = tentativeStockEnabled,
            onDismiss = { purchaseOpen = false },
            onSave = { productId, party, reference, quantity, rate, paid, note, tentative ->
                viewModel.createDocument(
                    StockDocumentKind.PURCHASE,
                    productId,
                    party,
                    reference,
                    quantity,
                    rate,
                    paid,
                    note,
                    tentative,
                )
                purchaseOpen = false
            },
        )
    }
}

@Composable
private fun StockSnapshotCard(snapshot: StockSnapshot) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "${snapshot.product.name} • ${snapshot.product.sku}",
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Base / on hand: ${FixedPoint.quantityDecimal(snapshot.onHandMicros)} ${snapshot.product.unit}",
            )
            Text("Reserved sales: ${FixedPoint.quantityDecimal(snapshot.reservedOutgoingMicros)}")
            Text("Tentative incoming: ${FixedPoint.quantityDecimal(snapshot.tentativeIncomingMicros)}")
            Text("Available to promise: ${FixedPoint.quantityDecimal(snapshot.availableToPromiseMicros)}")
            Text("Projected: ${FixedPoint.quantityDecimal(snapshot.projectedMicros)}")
            if (snapshot.belowReorderLevel) {
                Text(
                    "At or below reorder level",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TentativeDocumentCard(
    document: StockDocument,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${document.kind.name} • ${document.reference}",
                fontWeight = FontWeight.Bold,
            )
            Text("Party: ${document.party}")
            Text("Total: ${FixedPoint.moneyDisplay(document.totalMinor)}")
            Text("Paid now: ${FixedPoint.moneyDisplay(document.amountPaidMinor)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCommit) { Text("Commit") }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

private val INVENTORY_CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/vnd.ms-excel",
    "text/plain",
)
