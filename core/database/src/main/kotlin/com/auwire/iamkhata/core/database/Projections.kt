package com.auwire.iamkhata.core.database

/** Lightweight raw-query row identifier. */
data class RowIdProjection(val value: Long)

/** Lightweight raw-query count projection. */
data class LongValueProjection(val value: Long)

/** Raw analytics projection converted to a domain PivotCell by the repository. */
data class PivotProjection(
    val rowKey: String,
    val columnKey: String,
    val value: Double,
)


/** Derived running-balance projection for a requested ledger row. */
data class BalanceProjection(
    val rowId: Long,
    val balanceMinor: Long?,
)
