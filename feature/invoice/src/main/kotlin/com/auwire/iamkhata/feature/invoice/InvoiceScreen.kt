package com.auwire.iamkhata.feature.invoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.SalesDocument
import com.auwire.iamkhata.core.model.SalesDocumentStatus
import com.auwire.iamkhata.core.model.ThemeMode
import com.auwire.iamkhata.core.ui.AuwireTopBar
import com.auwire.iamkhata.core.ui.DrawerAction
import com.auwire.iamkhata.core.ui.ThemeModeSection
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun InvoiceScreen(
    viewModel: InvoiceViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var customerDialogOpen by remember { mutableStateOf(false) }
    var orderDialogOpen by remember { mutableStateOf(false) }

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
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        "Orders & Invoices",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                    )
                    DrawerAction(
                        label = "Add customer",
                        onClick = { closeDrawerThen { customerDialogOpen = true } },
                    )
                    DrawerAction(
                        label = "New sales order",
                        enabled = state.parties.isNotEmpty() && state.stock.isNotEmpty(),
                        onClick = { closeDrawerThen { orderDialogOpen = true } },
                    )
                    ThemeModeSection(
                        themeMode = themeMode,
                        onThemeModeChange = {
                            onThemeModeChange(it)
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                AuwireTopBar(
                    section = "Invoices",
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
                    "Draft → Confirmed (stock reserved) → Invoiced (stock + Khata posted).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Customers: ${state.parties.size} • Documents: ${state.documents.size}",
                    style = MaterialTheme.typography.bodySmall,
                )

                state.message?.let { message ->
                    AssistChip(
                        onClick = viewModel::clearMessage,
                        label = { Text(message) },
                    )
                }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                if (state.documents.isEmpty()) {
                    Text(
                        if (state.parties.isEmpty()) {
                            "Use ☰ → Add customer, then create your first sales order."
                        } else if (state.stock.isEmpty()) {
                            "Add inventory products before creating a sales order."
                        } else {
                            "Use ☰ → New sales order."
                        },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.documents, key = SalesDocument::id) { document ->
                            SalesDocumentCard(
                                document = document,
                                onConfirm = { viewModel.confirmOrder(document.id) },
                                onIssue = { viewModel.issueInvoice(document.id) },
                                onCancel = { viewModel.cancelOrder(document.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (customerDialogOpen) {
        AddCustomerDialog(
            onDismiss = { customerDialogOpen = false },
            onSave = { name, phone, email, address, taxId ->
                viewModel.createCustomer(name, phone, email, address, taxId)
                customerDialogOpen = false
            },
        )
    }

    if (orderDialogOpen && state.parties.isNotEmpty() && state.stock.isNotEmpty()) {
        NewSalesOrderDialog(
            parties = state.parties,
            stock = state.stock,
            onDismiss = { orderDialogOpen = false },
            onSave = { partyId, lines, amountPaid, note ->
                viewModel.createOrder(partyId, lines, amountPaid, note)
                orderDialogOpen = false
            },
        )
    }
}

@Composable
private fun SalesDocumentCard(
    document: SalesDocument,
    onConfirm: () -> Unit,
    onIssue: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                document.invoiceNumber ?: document.orderNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (document.invoiceNumber != null) {
                Text("Order: ${document.orderNumber}")
            }
            Text("${document.partyName} • ${document.status.name.replace('_', ' ')}")
            Text("Date: ${documentDate(document.effectiveAt)}")
            Text("Total: ${FixedPoint.moneyDisplay(document.totalMinor)}")
            Text("Paid: ${FixedPoint.moneyDisplay(document.amountPaidMinor)}")
            Text("Balance: ${FixedPoint.moneyDisplay(document.balanceMinor)}")
            document.note.takeIf(String::isNotBlank)?.let {
                Text("Note: $it")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (document.status) {
                    SalesDocumentStatus.DRAFT -> {
                        Button(onClick = onConfirm) { Text("Confirm") }
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }

                    SalesDocumentStatus.CONFIRMED -> {
                        Button(onClick = onIssue) { Text("Issue invoice") }
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }

                    SalesDocumentStatus.INVOICED,
                    SalesDocumentStatus.CANCELLED,
                    -> Unit
                }
            }
        }
    }
}

private fun documentDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
