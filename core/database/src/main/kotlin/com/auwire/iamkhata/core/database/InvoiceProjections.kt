package com.auwire.iamkhata.core.database

data class SalesDocumentProjection(
    val id: Long,
    val orderNumber: String,
    val invoiceNumber: String?,
    val status: String,
    val partyId: Long,
    val partyName: String,
    val effectiveAt: Long,
    val dueAt: Long?,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val amountPaidMinor: Long,
    val stockDocumentId: Long?,
    val note: String,
)
