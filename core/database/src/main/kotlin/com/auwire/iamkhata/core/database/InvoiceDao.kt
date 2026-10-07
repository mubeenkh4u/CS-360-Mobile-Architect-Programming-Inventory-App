package com.auwire.iamkhata.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Insert
    suspend fun insertParty(party: PartyEntity): Long

    @Update
    suspend fun updateParty(party: PartyEntity)

    @Query("SELECT * FROM business_parties WHERE id = :partyId LIMIT 1")
    suspend fun getParty(partyId: Long): PartyEntity?

    @Query(
        """
        SELECT * FROM business_parties
        WHERE active = 1
        ORDER BY name COLLATE NOCASE, id
        """,
    )
    fun observeParties(): Flow<List<PartyEntity>>

    @Insert
    suspend fun insertSalesDocument(document: SalesDocumentEntity): Long

    @Update
    suspend fun updateSalesDocument(document: SalesDocumentEntity)

    @Query("SELECT * FROM sales_documents WHERE id = :documentId LIMIT 1")
    suspend fun getSalesDocument(documentId: Long): SalesDocumentEntity?

    @Query(
        """
        SELECT
            d.id,
            d.orderNumber,
            d.invoiceNumber,
            d.status,
            d.partyId,
            p.name AS partyName,
            d.effectiveAt,
            d.dueAt,
            d.subtotalMinor,
            d.discountMinor,
            d.taxMinor,
            d.totalMinor,
            d.amountPaidMinor,
            d.stockDocumentId,
            d.note
        FROM sales_documents d
        JOIN business_parties p ON p.id = d.partyId
        ORDER BY d.createdAt DESC, d.id DESC
        """,
    )
    fun observeSalesDocuments(): Flow<List<SalesDocumentProjection>>

    @Insert
    suspend fun insertSalesDocumentLines(lines: List<SalesDocumentLineEntity>)

    @Query(
        """
        SELECT * FROM sales_document_lines
        WHERE salesDocumentId = :documentId
        ORDER BY id
        """,
    )
    suspend fun getSalesDocumentLines(documentId: Long): List<SalesDocumentLineEntity>
}
