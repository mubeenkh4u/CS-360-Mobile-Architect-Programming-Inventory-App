package com.auwire.iamkhata.feature.invoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.Party
import com.auwire.iamkhata.core.model.StockSnapshot

@Composable
internal fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var taxId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add customer") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") })
                OutlinedTextField(email, { email = it }, label = { Text("Email") })
                OutlinedTextField(address, { address = it }, label = { Text("Address") })
                OutlinedTextField(taxId, { taxId = it }, label = { Text("NTN / CNIC") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone, email, address, taxId) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private data class DraftOrderLine(
    val productId: Long,
    val quantity: String,
    val rate: String,
)

@Composable
internal fun NewSalesOrderDialog(
    parties: List<Party>,
    stock: List<StockSnapshot>,
    onDismiss: () -> Unit,
    onSave: (Long, List<OrderLineForm>, String, String) -> Unit,
) {
    val firstProduct = stock.first()
    var partyId by remember { mutableStateOf(parties.first().id) }
    var amountPaid by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    val lines = remember {
        mutableStateListOf(
            DraftOrderLine(
                productId = firstProduct.product.id,
                quantity = "1",
                rate = firstProduct.product.defaultSaleRateMinor
                    ?.let(FixedPoint::moneyDecimal)
                    ?: "0",
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New sales order") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PartyPicker(
                    parties = parties,
                    selectedId = partyId,
                    onSelected = { partyId = it },
                )

                lines.forEachIndexed { index, line ->
                    OrderLineEditor(
                        lineNumber = index + 1,
                        line = line,
                        stock = stock,
                        canRemove = lines.size > 1,
                        onChange = { lines[index] = it },
                        onRemove = { lines.removeAt(index) },
                    )
                }

                OutlinedButton(
                    onClick = {
                        val product = stock.first()
                        lines += DraftOrderLine(
                            productId = product.product.id,
                            quantity = "1",
                            rate = product.product.defaultSaleRateMinor
                                ?.let(FixedPoint::moneyDecimal)
                                ?: "0",
                        )
                    },
                ) { Text("Add item") }

                OutlinedTextField(
                    value = amountPaid,
                    onValueChange = { amountPaid = it },
                    label = { Text("Payment received now") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notes") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        partyId,
                        lines.map { line ->
                            OrderLineForm(
                                productId = line.productId,
                                quantity = line.quantity,
                                rate = line.rate,
                            )
                        },
                        amountPaid,
                        note,
                    )
                },
                enabled = lines.isNotEmpty() &&
                    lines.all { it.quantity.isNotBlank() && it.rate.isNotBlank() },
            ) { Text("Save draft") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PartyPicker(
    parties: List<Party>,
    selectedId: Long,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = parties.firstOrNull { it.id == selectedId } ?: parties.first()

    Column {
        Text("Customer")
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selected.name)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                parties.forEach { party ->
                    DropdownMenuItem(
                        text = { Text(party.name) },
                        onClick = {
                            onSelected(party.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderLineEditor(
    lineNumber: Int,
    line: DraftOrderLine,
    stock: List<StockSnapshot>,
    canRemove: Boolean,
    onChange: (DraftOrderLine) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = stock.firstOrNull { it.product.id == line.productId } ?: stock.first()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Item $lineNumber")
            if (canRemove) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text("${selected.product.name} · ${selected.product.sku}")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                stock.forEach { snapshot ->
                    DropdownMenuItem(
                        text = {
                            Text("${snapshot.product.name} · ${snapshot.product.sku}")
                        },
                        onClick = {
                            onChange(
                                line.copy(
                                    productId = snapshot.product.id,
                                    rate = snapshot.product.defaultSaleRateMinor
                                        ?.let(FixedPoint::moneyDecimal)
                                        ?: line.rate,
                                ),
                            )
                            expanded = false
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = line.quantity,
                onValueChange = { onChange(line.copy(quantity = it)) },
                label = { Text("Qty") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = line.rate,
                onValueChange = { onChange(line.copy(rate = it)) },
                label = { Text("Rate") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            "Available: ${FixedPoint.quantityDecimal(selected.availableToPromiseMicros)} ${selected.product.unit}",
        )
    }
}
