package com.auwire.iamkhata.core.database

/** Query projection for live committed/tentative stock status. */
data class StockSnapshotProjection(
    val id: Long,
    val sku: String,
    val name: String,
    val unit: String,
    val reorderLevelMicros: Long,
    val defaultSaleRateMinor: Long?,
    val defaultPurchaseRateMinor: Long?,
    val active: Boolean,
    val onHandMicros: Long,
    val reservedOutgoingMicros: Long,
    val tentativeIncomingMicros: Long,
)
