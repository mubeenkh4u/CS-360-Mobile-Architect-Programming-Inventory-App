package com.auwire.iamkhata.feature.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.StockDocumentKind
import com.auwire.iamkhata.core.model.StockSnapshot

@Composable
internal fun AddProductDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    var sku by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("pcs") }
    var reorder by remember { mutableStateOf("0") }
    var saleRate by remember { mutableStateOf("") }
    var purchaseRate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add product") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(sku, { sku = it }, label = { Text("SKU") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Product name") }, singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true)
                OutlinedTextField(reorder, { reorder = it }, label = { Text("Reorder level") }, singleLine = true)
                OutlinedTextField(saleRate, { saleRate = it }, label = { Text("Default sale rate") }, singleLine = true)
                OutlinedTextField(purchaseRate, { purchaseRate = it }, label = { Text("Default purchase rate") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = sku.isNotBlank() && name.isNotBlank() && unit.isNotBlank(),
                onClick = { onSave(sku, name, unit, reorder, saleRate, purchaseRate) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AdjustStockDialog(
    stock: List<StockSnapshot>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String) -> Unit,
) {
    var selected by remember(stock) { mutableStateOf(stock.first()) }
    var delta by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust committed/base stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InventorySelector(
                    "Product",
                    selected,
                    stock,
                    { "${it.product.name} (${it.product.sku})" },
                ) { selected = it }
                Text(
                    "Current base stock: ${FixedPoint.quantityDecimal(selected.onHandMicros)} ${selected.product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    delta,
                    { delta = it },
                    label = { Text("Quantity change (+ add / - remove)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    note,
                    { note = it },
                    label = { Text("Reason / note") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = delta.isNotBlank(),
                onClick = { onSave(selected.product.id, delta, note) },
            ) { Text("Post adjustment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun StockDocumentDialog(
    kind: StockDocumentKind,
    stock: List<StockSnapshot>,
    tentativeStockEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String, String, String, String, Boolean) -> Unit,
) {
    var selected by remember(stock) { mutableStateOf(stock.first()) }
    var party by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var rate by remember(kind, stock) {
        mutableStateOf(defaultRate(stock.first(), kind))
    }
    var paid by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var tentative by remember { mutableStateOf(tentativeStockEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == StockDocumentKind.SALE) "New sale" else "New purchase") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InventorySelector(
                    "Product",
                    selected,
                    stock,
                    { "${it.product.name} (${it.product.sku})" },
                ) {
                    selected = it
                    rate = defaultRate(it, kind)
                }
                if (kind == StockDocumentKind.SALE) {
                    Text(
                        "Available now: ${FixedPoint.quantityDecimal(selected.availableToPromiseMicros)} ${selected.product.unit}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Projected after tentative incoming: ${FixedPoint.quantityDecimal(selected.projectedMicros)} ${selected.product.unit}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                OutlinedTextField(party, { party = it }, label = { Text("Party / customer / supplier") }, singleLine = true)
                OutlinedTextField(reference, { reference = it }, label = { Text("Reference (optional)") }, singleLine = true)
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, singleLine = true)
                OutlinedTextField(rate, { rate = it }, label = { Text("Rate") }, singleLine = true)
                OutlinedTextField(paid, { paid = it }, label = { Text("Amount paid now") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true)

                if (tentativeStockEnabled) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(checked = tentative, onCheckedChange = { tentative = it })
                        Text(
                            if (tentative) {
                                "Keep tentative: reserve/project stock, do not post Khata yet"
                            } else {
                                "Commit now: update base stock and Khata atomically"
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = party.isNotBlank() && quantity.isNotBlank() && rate.isNotBlank(),
                onClick = {
                    onSave(
                        selected.product.id,
                        party,
                        reference,
                        quantity,
                        rate,
                        paid,
                        note,
                        tentativeStockEnabled && tentative,
                    )
                },
            ) {
                Text(if (tentativeStockEnabled && tentative) "Save tentative" else "Commit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun defaultRate(
    snapshot: StockSnapshot,
    kind: StockDocumentKind,
): String {
    val rate = if (kind == StockDocumentKind.SALE) {
        snapshot.product.defaultSaleRateMinor
    } else {
        snapshot.product.defaultPurchaseRateMinor
    }
    return rate?.let(FixedPoint::moneyDecimal).orEmpty()
}
