package com.auwire.iamkhata.core.model

/**
 * Inventory uses fixed-point arithmetic.
 *
 * One physical unit equals [QUANTITY_SCALE] micros. Monetary values are stored
 * in minor currency units (for PKR, paisa) to avoid binary floating-point
 * rounding in source-of-truth business records.
 */
const val QUANTITY_SCALE: Long = 1_000_000L
const val MONEY_SCALE: Long = 100L

enum class StockDocumentKind {
    SALE,
    PURCHASE,
}

enum class StockDocumentStatus {
    TENTATIVE,
    COMMITTED,
    CANCELLED,
}

enum class StockMovementKind {
    OPENING,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    PURCHASE_RECEIPT,
    SALE_ISSUE,
}

data class Product(
    val id: Long,
    val sku: String,
    val name: String,
    val unit: String,
    val reorderLevelMicros: Long,
    val defaultSaleRateMinor: Long?,
    val defaultPurchaseRateMinor: Long?,
    val active: Boolean,
)

data class StockSnapshot(
    val product: Product,
    val onHandMicros: Long,
    val reservedOutgoingMicros: Long,
    val tentativeIncomingMicros: Long,
) {
    val availableToPromiseMicros: Long
        get() = onHandMicros - reservedOutgoingMicros

    val projectedMicros: Long
        get() = availableToPromiseMicros + tentativeIncomingMicros

    val belowReorderLevel: Boolean
        get() = availableToPromiseMicros <= product.reorderLevelMicros
}

data class StockLineInput(
    val productId: Long,
    val quantityMicros: Long,
    val unitRateMinor: Long,
)

data class StockDocumentRequest(
    val kind: StockDocumentKind,
    val party: String,
    val reference: String,
    val effectiveAt: Long,
    val amountPaidMinor: Long,
    val lines: List<StockLineInput>,
    val note: String = "",
)

data class StockDocument(
    val id: Long,
    val kind: StockDocumentKind,
    val status: StockDocumentStatus,
    val party: String,
    val reference: String,
    val effectiveAt: Long,
    val totalMinor: Long,
    val amountPaidMinor: Long,
    val note: String,
)

data class StockPostingResult(
    val documentId: Long,
    val ledgerRowIds: List<Long>,
)

/** Runtime inventory policy; policy changes do not require data-model changes. */
data class InventoryPolicy(
    val tentativeStockEnabled: Boolean = true,
    val allowNegativeCommittedStock: Boolean = false,
)
