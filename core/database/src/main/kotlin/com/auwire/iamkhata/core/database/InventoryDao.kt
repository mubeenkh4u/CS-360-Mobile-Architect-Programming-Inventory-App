package com.auwire.iamkhata.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Inventory journal, product master and tentative-document persistence. */
@Dao
interface InventoryDao {
    @Insert
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("SELECT * FROM inventory_products WHERE id = :productId LIMIT 1")
    suspend fun getProduct(productId: Long): ProductEntity?

    @Query("SELECT * FROM inventory_products WHERE sku = :sku LIMIT 1")
    suspend fun getProductBySku(sku: String): ProductEntity?

    @Query(
        """
        SELECT
            p.id,
            p.sku,
            p.name,
            p.unit,
            p.reorderLevelMicros,
            p.defaultSaleRateMinor,
            p.defaultPurchaseRateMinor,
            p.active,
            COALESCE((
                SELECT SUM(m.quantityDeltaMicros)
                FROM stock_movements m
                WHERE m.productId = p.id
            ), 0) AS onHandMicros,
            COALESCE((
                SELECT SUM(l.quantityMicros)
                FROM stock_document_lines l
                JOIN stock_documents d ON d.id = l.documentId
                WHERE l.productId = p.id
                  AND d.status = 'TENTATIVE'
                  AND d.kind = 'SALE'
            ), 0) AS reservedOutgoingMicros,
            COALESCE((
                SELECT SUM(l.quantityMicros)
                FROM stock_document_lines l
                JOIN stock_documents d ON d.id = l.documentId
                WHERE l.productId = p.id
                  AND d.status = 'TENTATIVE'
                  AND d.kind = 'PURCHASE'
            ), 0) AS tentativeIncomingMicros
        FROM inventory_products p
        WHERE p.active = 1
        ORDER BY p.name COLLATE NOCASE, p.id
        """
    )
    fun observeStock(): Flow<List<StockSnapshotProjection>>

    @Query(
        """
        SELECT COALESCE(SUM(quantityDeltaMicros), 0)
        FROM stock_movements
        WHERE productId = :productId
        """
    )
    suspend fun onHandMicros(productId: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(l.quantityMicros), 0)
        FROM stock_document_lines l
        JOIN stock_documents d ON d.id = l.documentId
        WHERE l.productId = :productId
          AND d.status = 'TENTATIVE'
          AND d.kind = 'SALE'
          AND d.id != :excludedDocumentId
        """
    )
    suspend fun reservedOutgoingExcluding(
        productId: Long,
        excludedDocumentId: Long,
    ): Long

    @Insert
    suspend fun insertDocument(document: StockDocumentEntity): Long

    @Update
    suspend fun updateDocument(document: StockDocumentEntity)

    @Query("SELECT * FROM stock_documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocument(documentId: Long): StockDocumentEntity?

    @Query(
        """
        SELECT * FROM stock_documents
        WHERE status = 'TENTATIVE'
        ORDER BY effectiveAt DESC, id DESC
        """
    )
    fun observeTentativeDocuments(): Flow<List<StockDocumentEntity>>

    @Insert
    suspend fun insertDocumentLines(lines: List<StockDocumentLineEntity>)

    @Query(
        """
        SELECT * FROM stock_document_lines
        WHERE documentId = :documentId
        ORDER BY id
        """
    )
    suspend fun getDocumentLines(documentId: Long): List<StockDocumentLineEntity>

    @Insert
    suspend fun insertMovement(movement: StockMovementEntity): Long

    @Insert
    suspend fun insertMovements(movements: List<StockMovementEntity>)
}
