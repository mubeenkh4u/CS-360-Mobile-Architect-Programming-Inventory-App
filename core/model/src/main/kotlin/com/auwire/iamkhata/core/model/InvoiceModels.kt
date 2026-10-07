package com.auwire.iamkhata.core.model

enum class PartyType {
    CUSTOMER,
    SUPPLIER,
}

enum class SalesDocumentStatus {
    DRAFT,
    CONFIRMED,
    INVOICED,
    CANCELLED,
}

data class Party(
    val id: Long,
    val type: PartyType,
    val name: String,
    val phone: String,
    val email: String,
    val address: String,
    val taxId: String,
    val active: Boolean,
)

data class SalesOrderLineInput(
    val productId: Long,
    val quantityMicros: Long,
    val unitRateMinor: Long,
)

data class SalesOrderRequest(
    val partyId: Long,
    val effectiveAt: Long,
    val dueAt: Long? = null,
    val amountPaidMinor: Long = 0,
    val lines: List<SalesOrderLineInput>,
    val note: String = "",
)

data class SalesDocument(
    val id: Long,
    val orderNumber: String,
    val invoiceNumber: String?,
    val status: SalesDocumentStatus,
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
) {
    val balanceMinor: Long
        get() = totalMinor - amountPaidMinor
}
