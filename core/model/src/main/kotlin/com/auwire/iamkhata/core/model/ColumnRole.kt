package com.auwire.iamkhata.core.model

/**
 * Optional semantic meaning attached to a column.
 *
 * Roles let accounting features understand a ledger without forcing every
 * dataset to have the same physical schema.
 */
enum class ColumnRole {
    NONE,
    DATE,
    PARTY,
    PARTICULARS,
    QUANTITY,
    RATE,
    DEBIT,
    CREDIT,
}
