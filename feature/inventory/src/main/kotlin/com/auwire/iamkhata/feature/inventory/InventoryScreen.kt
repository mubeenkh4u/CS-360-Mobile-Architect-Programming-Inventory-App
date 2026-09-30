package com.auwire.iamkhata.feature.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.StockDocument
import com.auwire.iamkhata.core.model.StockDocumentKind
import com.auwire.iamkhata.core.model.StockSnapshot

/** Inventory workspace integrated with the canonical Khata ledger. */
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    tentativeStockEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addProductOpen by remember { mutableStateOf(false) }
    var adjustOpen by remember { mutableStateOf(false) }
    var saleOpen by remember { mutableStateOf(false) }
    var purchaseOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Inventory & Stock",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Committed stock is physical/base stock. Tentative sales reserve availability; tentative purchases increase projected incoming stock.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { addProductOpen = true }) { Text("Add product") }
            OutlinedButton(
                onClick = { adjustOpen = true },
                enabled = state.stock.isNotEmpty(),
            ) { Text("Adjust stock") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { saleOpen = true },
                enabled = state.stock.isNotEmpty(),
            ) { Text("New sale") }
            Button(
                onClick = { purchaseOpen = true },
                enabled = state.stock.isNotEmpty(),
            ) { Text("New purchase") }
        }

        state.message?.let { message ->
            AssistChip(
                onClick = viewModel::clearMessage,
                label = { Text(message) },
            )
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

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
                item { Text("No products yet.") }
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
    if (adjustOpen) {
        AdjustStockDialog(
            stock = state.stock,
            onDismiss = { adjustOpen = false },
            onSave = { productId, delta, note ->
                viewModel.adjustBaseStock(productId, delta, note)
                adjustOpen = false
            },
        )
    }
    if (saleOpen) {
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
    if (purchaseOpen) {
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${snapshot.product.name}  •  ${snapshot.product.sku}",
                fontWeight = FontWeight.Bold,
            )
            Text("Base / on hand: ${FixedPoint.quantityDecimal(snapshot.onHandMicros)} ${snapshot.product.unit}")
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
