package com.auwire.iamkhata.core.model

/** Canonical ledger column metadata shared by ledger and inventory features. */
data class LedgerColumnDefinition(
    val name: String,
    val type: ColumnType,
    val role: ColumnRole,
    val required: Boolean = false,
    val isProtected: Boolean = false,
)

/**
 * Minimum semantic schema required for integrated Khata and stock posting.
 *
 * Datasets remain extensible: these definitions are only the protected semantic
 * baseline and users can add any number of custom columns at runtime.
 */
object LedgerDefaults {
    const val DATASET_NAME = "Ledger"

    val columns = listOf(
        LedgerColumnDefinition("Date", ColumnType.DATE, ColumnRole.DATE, required = true, isProtected = true),
        LedgerColumnDefinition("Party", ColumnType.TEXT, ColumnRole.PARTY),
        LedgerColumnDefinition("Particulars", ColumnType.TEXT, ColumnRole.PARTICULARS, isProtected = true),
        LedgerColumnDefinition("Product", ColumnType.TEXT, ColumnRole.PRODUCT),
        LedgerColumnDefinition("SKU", ColumnType.TEXT, ColumnRole.SKU),
        LedgerColumnDefinition("Quantity", ColumnType.DECIMAL, ColumnRole.QUANTITY),
        LedgerColumnDefinition("Rate", ColumnType.CURRENCY, ColumnRole.RATE),
        LedgerColumnDefinition("Debit", ColumnType.CURRENCY, ColumnRole.DEBIT, isProtected = true),
        LedgerColumnDefinition("Credit", ColumnType.CURRENCY, ColumnRole.CREDIT, isProtected = true),
        LedgerColumnDefinition("Reference", ColumnType.TEXT, ColumnRole.REFERENCE),
        LedgerColumnDefinition("Stock Status", ColumnType.CATEGORY, ColumnRole.STOCK_STATUS, isProtected = true),
    )
}
