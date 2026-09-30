package com.auwire.iamkhata.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Product master data. Products are retired instead of physically deleted. */
@Entity(
    tableName = "inventory_products",
    indices = [
        Index(value = ["sku"], unique = true),
        Index("name"),
        Index("active"),
    ],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sku: String,
    val name: String,
    val unit: String,
    val reorderLevelMicros: Long,
    val defaultSaleRateMinor: Long?,
    val defaultPurchaseRateMinor: Long?,
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Commercial stock document. TENTATIVE documents reserve/project stock but do
 * not alter physical stock or the official Khata until committed.
 */
@Entity(
    tableName = "stock_documents",
    indices = [
        Index("status"),
        Index("kind"),
        Index("effectiveAt"),
        Index("reference"),
    ],
)
data class StockDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val status: String,
    val party: String,
    val reference: String,
    val effectiveAt: Long,
    val totalMinor: Long,
    val amountPaidMinor: Long,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Line items are fixed-point and remain immutable after document commitment. */
@Entity(
    tableName = "stock_document_lines",
    foreignKeys = [
        ForeignKey(
            entity = StockDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("documentId"), Index("productId")],
)
data class StockDocumentLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val productId: Long,
    val quantityMicros: Long,
    val unitRateMinor: Long,
    val lineTotalMinor: Long,
)

/**
 * Immutable committed stock movement.
 *
 * Current physical/base stock is derived from this journal rather than stored as
 * a mutable quantity field, preserving a complete stock history.
 */
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StockDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("productId"),
        Index("documentId"),
        Index("effectiveAt"),
    ],
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val documentId: Long?,
    val kind: String,
    val quantityDeltaMicros: Long,
    val effectiveAt: Long,
    val createdAt: Long,
    val note: String,
)
