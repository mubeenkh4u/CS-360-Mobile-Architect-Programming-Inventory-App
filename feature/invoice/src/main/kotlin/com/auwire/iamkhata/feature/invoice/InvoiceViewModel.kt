package com.auwire.iamkhata.feature.invoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auwire.iamkhata.core.data.InventoryRepository
import com.auwire.iamkhata.core.data.InvoiceRepository
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.Party
import com.auwire.iamkhata.core.model.PartyType
import com.auwire.iamkhata.core.model.SalesDocument
import com.auwire.iamkhata.core.model.SalesOrderLineInput
import com.auwire.iamkhata.core.model.SalesOrderRequest
import com.auwire.iamkhata.core.model.StockSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderLineForm(
    val productId: Long,
    val quantity: String,
    val rate: String,
)

data class InvoiceUiState(
    val parties: List<Party> = emptyList(),
    val stock: List<StockSnapshot> = emptyList(),
    val documents: List<SalesDocument> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class InvoiceViewModel(
    private val repository: InvoiceRepository,
    inventoryRepository: InventoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InvoiceUiState())
    val state: StateFlow<InvoiceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeParties().collect { parties ->
                _state.update { it.copy(parties = parties) }
            }
        }
        viewModelScope.launch {
            inventoryRepository.observeStock().collect { stock ->
                _state.update { it.copy(stock = stock) }
            }
        }
        viewModelScope.launch {
            repository.observeSalesDocuments().collect { documents ->
                _state.update { it.copy(documents = documents) }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun createCustomer(
        name: String,
        phone: String,
        email: String,
        address: String,
        taxId: String,
    ) = launchAction {
        repository.createParty(
            type = PartyType.CUSTOMER,
            name = name,
            phone = phone,
            email = email,
            address = address,
            taxId = taxId,
        )
        "Customer created."
    }

    fun createOrder(
        partyId: Long,
        lines: List<OrderLineForm>,
        amountPaid: String,
        note: String,
    ) = launchAction {
        repository.createDraftOrder(
            SalesOrderRequest(
                partyId = partyId,
                effectiveAt = System.currentTimeMillis(),
                amountPaidMinor = FixedPoint.parseMoney(amountPaid.ifBlank { "0" }),
                lines = lines.map { line ->
                    SalesOrderLineInput(
                        productId = line.productId,
                        quantityMicros = FixedPoint.parseQuantity(line.quantity),
                        unitRateMinor = FixedPoint.parseMoney(line.rate),
                    )
                },
                note = note,
            ),
        )
        "Draft sales order created."
    }

    fun confirmOrder(documentId: Long) = launchAction {
        repository.confirmOrder(documentId)
        "Order confirmed. Inventory is now reserved."
    }

    fun issueInvoice(documentId: Long) = launchAction {
        repository.issueInvoice(documentId)
        "Invoice issued. Stock and Khata were posted."
    }

    fun cancelOrder(documentId: Long) = launchAction {
        repository.cancelOrder(documentId)
        "Order cancelled."
    }

    private fun launchAction(block: suspend () -> String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            runCatching { block() }
                .onSuccess { message ->
                    _state.update { it.copy(busy = false, message = message) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            message = error.message ?: "The operation could not be completed.",
                        )
                    }
                }
        }
    }
}

class InvoiceViewModelFactory(
    private val repository: InvoiceRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(InvoiceViewModel::class.java))
        return InvoiceViewModel(repository, inventoryRepository) as T
    }
}
