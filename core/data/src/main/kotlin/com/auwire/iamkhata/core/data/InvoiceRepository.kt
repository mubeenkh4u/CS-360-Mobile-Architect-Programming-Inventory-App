package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.model.Party
import com.auwire.iamkhata.core.model.PartyType
import com.auwire.iamkhata.core.model.SalesDocument
import com.auwire.iamkhata.core.model.SalesOrderRequest
import com.auwire.iamkhata.core.model.StockPostingResult
import kotlinx.coroutines.flow.Flow

interface InvoiceRepository {
    fun observeParties(): Flow<List<Party>>
    fun observeSalesDocuments(): Flow<List<SalesDocument>>

    suspend fun createParty(
        type: PartyType,
        name: String,
        phone: String = "",
        email: String = "",
        address: String = "",
        taxId: String = "",
    ): Long

    suspend fun createDraftOrder(request: SalesOrderRequest): Long
    suspend fun confirmOrder(documentId: Long)
    suspend fun issueInvoice(documentId: Long): StockPostingResult
    suspend fun cancelOrder(documentId: Long)
}
