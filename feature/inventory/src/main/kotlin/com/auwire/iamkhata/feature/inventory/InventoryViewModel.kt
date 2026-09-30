package com.auwire.iamkhata.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auwire.iamkhata.core.data.InventoryRepository
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.StockDocument
import com.auwire.iamkhata.core.model.StockDocumentKind
import com.auwire.iamkhata.core.model.StockDocumentRequest
import com.auwire.iamkhata.core.model.StockLineInput
import com.auwire.iamkhata.core.model.StockSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InventoryUiState(
    val stock: List<StockSnapshot> = emptyList(),
    val tentativeDocuments: List<StockDocument> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

/** Coordinates inventory intents; Compose never talks directly to persistence. */
class InventoryViewModel(
    private val repository: InventoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeStock().collect { stock ->
                _state.update { it.copy(stock = stock) }
            }
        }
        viewModelScope.launch {
            repository.observeTentativeDocuments().collect { documents ->
                _state.update { it.copy(tentativeDocuments = documents) }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun createProduct(
        sku: String,
        name: String,
        unit: String,
        reorderLevel: String,
        saleRate: String,
        purchaseRate: String,
    ) = launchAction {
        repository.createProduct(
            sku = sku,
            name = name,
            unit = unit,
            reorderLevelMicros = FixedPoint.parseQuantity(reorderLevel.ifBlank { "0" }),
            defaultSaleRateMinor = saleRate.takeIf(String::isNotBlank)?.let(FixedPoint::parseMoney),
            defaultPurchaseRateMinor = purchaseRate.takeIf(String::isNotBlank)?.let(FixedPoint::parseMoney),
        )
        "Product created."
    }

    fun adjustBaseStock(
        productId: Long,
        quantityDelta: String,
        note: String,
    ) = launchAction {
        repository.adjustBaseStock(
            productId = productId,
            quantityDeltaMicros = FixedPoint.parseQuantity(quantityDelta),
            note = note,
        )
        "Base stock adjusted."
    }

    fun createDocument(
        kind: StockDocumentKind,
        productId: Long,
        party: String,
        reference: String,
        quantity: String,
        rate: String,
        amountPaid: String,
        note: String,
        tentative: Boolean,
    ) = launchAction {
        val request = StockDocumentRequest(
            kind = kind,
            party = party,
            reference = reference,
            effectiveAt = System.currentTimeMillis(),
            amountPaidMinor = FixedPoint.parseMoney(amountPaid.ifBlank { "0" }),
            lines = listOf(
                StockLineInput(
                    productId = productId,
                    quantityMicros = FixedPoint.parseQuantity(quantity),
                    unitRateMinor = FixedPoint.parseMoney(rate),
                ),
            ),
            note = note,
        )

        if (tentative) {
            repository.saveTentativeDocument(request)
            "Tentative ${kind.name.lowercase()} saved."
        } else {
            repository.createAndCommit(request)
            "${kind.name.lowercase().replaceFirstChar { it.uppercase() }} committed to stock and Khata."
        }
    }

    fun commitDocument(documentId: Long) = launchAction {
        repository.commitDocument(documentId)
        "Document committed to stock and Khata."
    }

    fun cancelDocument(documentId: Long) = launchAction {
        repository.cancelDocument(documentId)
        "Tentative document cancelled."
    }

    private fun launchAction(block: suspend () -> String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            runCatching { block() }
                .onSuccess { message -> _state.update { it.copy(busy = false, message = message) } }
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

class InventoryViewModelFactory(
    private val repository: InventoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(InventoryViewModel::class.java))
        return InventoryViewModel(repository) as T
    }
}
