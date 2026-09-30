package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.model.StockDocument
import com.auwire.iamkhata.core.model.StockDocumentRequest
import com.auwire.iamkhata.core.model.StockPostingResult
import com.auwire.iamkhata.core.model.StockSnapshot
import kotlinx.coroutines.flow.Flow

/** Stable inventory contract consumed by UI/business features. */
interface InventoryRepository {
    fun observeStock(): Flow<List<StockSnapshot>>
    fun observeTentativeDocuments(): Flow<List<StockDocument>>

    suspend fun createProduct(
        sku: String,
        name: String,
        unit: String,
        reorderLevelMicros: Long,
        defaultSaleRateMinor: Long? = null,
        defaultPurchaseRateMinor: Long? = null,
    ): Long

    suspend fun adjustBaseStock(
        productId: Long,
        quantityDeltaMicros: Long,
        note: String,
        effectiveAt: Long = System.currentTimeMillis(),
    ): Long

    suspend fun saveTentativeDocument(request: StockDocumentRequest): Long

    /** Creates and commits stock + Khata atomically. */
    suspend fun createAndCommit(request: StockDocumentRequest): StockPostingResult

    suspend fun commitDocument(documentId: Long): StockPostingResult

    suspend fun cancelDocument(documentId: Long)
}
