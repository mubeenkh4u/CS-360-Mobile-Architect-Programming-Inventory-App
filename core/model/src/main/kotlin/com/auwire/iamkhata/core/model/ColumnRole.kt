package com.auwire.iamkhata.core.model

/**
 * Optional semantic meaning attached to a column.
 *
 * Roles let business features locate canonical fields without forcing every
 * dataset to have the same physical schema.
 */
enum class ColumnRole {
    NONE,
    DATE,
    PARTY,
    PARTICULARS,
    PRODUCT,
    SKU,
    QUANTITY,
    RATE,
    DEBIT,
    CREDIT,
    REFERENCE,
    STOCK_STATUS,
}
