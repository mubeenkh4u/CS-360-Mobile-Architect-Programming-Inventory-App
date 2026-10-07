package com.auwire.iamkhata.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "business_parties",
    indices = [
        Index("type"),
        Index("name"),
        Index("taxId"),
        Index("active"),
    ],
)
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val phone: String,
    val email: String,
    val address: String,
    val taxId: String,
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "sales_documents",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StockDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["stockDocumentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["orderNumber"], unique = true),
        Index(value = ["invoiceNumber"], unique = true),
        Index("status"),
        Index("partyId"),
        Index("effectiveAt"),
        Index(value = ["stockDocumentId"], unique = true),
    ],
)
data class SalesDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String,
    val invoiceNumber: String?,
    val status: String,
    val partyId: Long,
    val effectiveAt: Long,
    val dueAt: Long?,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val amountPaidMinor: Long,
    val stockDocumentId: Long?,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "sales_document_lines",
    foreignKeys = [
        ForeignKey(
            entity = SalesDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["salesDocumentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("salesDocumentId"),
        Index("productId"),
    ],
)
data class SalesDocumentLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val salesDocumentId: Long,
    val productId: Long,
    val description: String,
    val quantityMicros: Long,
    val unitRateMinor: Long,
    val discountMinor: Long,
    val taxRateBasisPoints: Int,
    val taxMinor: Long,
    val lineTotalMinor: Long,
)
