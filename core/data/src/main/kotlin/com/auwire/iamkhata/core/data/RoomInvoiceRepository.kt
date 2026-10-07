package com.auwire.iamkhata.core.data

import androidx.room.withTransaction
import com.auwire.iamkhata.core.database.IamDatabase
import com.auwire.iamkhata.core.database.PartyEntity
import com.auwire.iamkhata.core.database.SalesDocumentEntity
import com.auwire.iamkhata.core.database.SalesDocumentLineEntity
import com.auwire.iamkhata.core.database.SalesDocumentProjection
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.Party
import com.auwire.iamkhata.core.model.PartyType
import com.auwire.iamkhata.core.model.SalesDocument
import com.auwire.iamkhata.core.model.SalesDocumentStatus
import com.auwire.iamkhata.core.model.SalesOrderRequest
import com.auwire.iamkhata.core.model.StockDocumentKind
import com.auwire.iamkhata.core.model.StockDocumentRequest
import com.auwire.iamkhata.core.model.StockDocumentStatus
import com.auwire.iamkhata.core.model.StockLineInput
import com.auwire.iamkhata.core.model.StockPostingResult
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Coordinates sales orders with the existing stock + Khata transaction path.
 *
 * DRAFT: commercial document only.
 * CONFIRMED: creates a TENTATIVE SALE, reserving inventory.
 * INVOICED: commits that sale, posting physical stock and locked Khata rows.
 */
class RoomInvoiceRepository(
    private val database: IamDatabase,
    signer: IntegritySigner,
    private val inventoryRepository: InventoryRepository,
) : InvoiceRepository {
    private val invoiceDao = database.invoiceDao()
    private val inventoryDao = database.inventoryDao()
    private val auditWriter = AuditWriter(database.auditDao(), signer)

    override fun observeParties(): Flow<List<Party>> =
        invoiceDao.observeParties().map { rows ->
            rows.map { row ->
                Party(
                    id = row.id,
                    type = PartyType.valueOf(row.type),
                    name = row.name,
                    phone = row.phone,
                    email = row.email,
                    address = row.address,
                    taxId = row.taxId,
                    active = row.active,
                )
            }
        }

    override fun observeSalesDocuments(): Flow<List<SalesDocument>> =
        invoiceDao.observeSalesDocuments().map { rows ->
            rows.map(SalesDocumentProjection::toModel)
        }

    override suspend fun createParty(
        type: PartyType,
        name: String,
        phone: String,
        email: String,
        address: String,
        taxId: String,
    ): Long = database.withTransaction {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Customer name is required." }

        val now = System.currentTimeMillis()
        val id = invoiceDao.insertParty(
            PartyEntity(
                type = type.name,
                name = cleanName,
                phone = phone.trim(),
                email = email.trim(),
                address = address.trim(),
                taxId = taxId.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        auditWriter.append(
            action = "CREATE_PARTY",
            targetType = "PARTY",
            targetId = id.toString(),
            payloadSummary = "type=${type.name};name=$cleanName",
            timestamp = now,
        )
        id
    }

    override suspend fun createDraftOrder(request: SalesOrderRequest): Long =
        database.withTransaction {
            val party = requireNotNull(invoiceDao.getParty(request.partyId)) {
                "Customer not found."
            }
            require(party.active) { "Inactive customers cannot be used." }
            require(party.type == PartyType.CUSTOMER.name) {
                "Sales orders require a customer."
            }
            require(request.lines.isNotEmpty()) { "Add at least one order line." }
            require(request.amountPaidMinor >= 0) { "Payment cannot be negative." }

            val validated = request.lines.map { line ->
                require(line.quantityMicros > 0) { "Line quantity must be positive." }
                require(line.unitRateMinor >= 0) { "Line rate cannot be negative." }
                val product = requireNotNull(inventoryDao.getProduct(line.productId)) {
                    "Product not found."
                }
                require(product.active) { "Inactive products cannot be ordered." }
                Triple(
                    line,
                    product,
                    FixedPoint.lineTotalMinor(line.quantityMicros, line.unitRateMinor),
                )
            }
            val subtotal = validated.fold(0L) { total, item ->
                Math.addExact(total, item.third)
            }
            val discount = 0L
            val tax = 0L
            val total = Math.addExact(Math.subtractExact(subtotal, discount), tax)
            require(request.amountPaidMinor <= total) {
                "Payment cannot exceed the order total."
            }

            val now = System.currentTimeMillis()
            val draft = SalesDocumentEntity(
                orderNumber = "PENDING-${UUID.randomUUID()}",
                invoiceNumber = null,
                status = SalesDocumentStatus.DRAFT.name,
                partyId = request.partyId,
                effectiveAt = request.effectiveAt,
                dueAt = request.dueAt,
                subtotalMinor = subtotal,
                discountMinor = discount,
                taxMinor = tax,
                totalMinor = total,
                amountPaidMinor = request.amountPaidMinor,
                stockDocumentId = null,
                note = request.note.trim(),
                createdAt = now,
                updatedAt = now,
            )
            val documentId = invoiceDao.insertSalesDocument(draft)
            val orderNumber = documentNumber("ORD", documentId, request.effectiveAt)
            invoiceDao.updateSalesDocument(
                draft.copy(
                    id = documentId,
                    orderNumber = orderNumber,
                ),
            )
            invoiceDao.insertSalesDocumentLines(
                validated.map { (line, product, lineTotal) ->
                    SalesDocumentLineEntity(
                        salesDocumentId = documentId,
                        productId = line.productId,
                        description = product.name,
                        quantityMicros = line.quantityMicros,
                        unitRateMinor = line.unitRateMinor,
                        discountMinor = 0,
                        taxRateBasisPoints = 0,
                        taxMinor = 0,
                        lineTotalMinor = lineTotal,
                    )
                },
            )
            auditWriter.append(
                action = "CREATE_SALES_ORDER",
                targetType = "SALES_DOCUMENT",
                targetId = documentId.toString(),
                payloadSummary = "order=$orderNumber;party=${party.name};lines=${validated.size};totalMinor=$total",
                timestamp = now,
            )
            documentId
        }

    override suspend fun confirmOrder(documentId: Long) {
        database.withTransaction {
            val document = requireNotNull(invoiceDao.getSalesDocument(documentId)) {
                "Order not found."
            }
            require(document.status == SalesDocumentStatus.DRAFT.name) {
                "Only draft orders can be confirmed."
            }
            val party = requireNotNull(invoiceDao.getParty(document.partyId)) {
                "Customer not found."
            }
            val lines = invoiceDao.getSalesDocumentLines(documentId)
            require(lines.isNotEmpty()) { "Order has no lines." }

            val stockDocumentId = inventoryRepository.saveTentativeDocument(
                StockDocumentRequest(
                    kind = StockDocumentKind.SALE,
                    party = party.name,
                    reference = document.orderNumber,
                    effectiveAt = document.effectiveAt,
                    amountPaidMinor = document.amountPaidMinor,
                    lines = lines.map { line ->
                        StockLineInput(
                            productId = line.productId,
                            quantityMicros = line.quantityMicros,
                            unitRateMinor = line.unitRateMinor,
                        )
                    },
                    note = document.note,
                ),
            )

            val now = System.currentTimeMillis()
            invoiceDao.updateSalesDocument(
                document.copy(
                    status = SalesDocumentStatus.CONFIRMED.name,
                    stockDocumentId = stockDocumentId,
                    updatedAt = now,
                ),
            )
            auditWriter.append(
                action = "CONFIRM_SALES_ORDER",
                targetType = "SALES_DOCUMENT",
                targetId = documentId.toString(),
                payloadSummary = "order=${document.orderNumber};stockDocument=$stockDocumentId",
                timestamp = now,
            )
        }
    }

    override suspend fun issueInvoice(documentId: Long): StockPostingResult =
        database.withTransaction {
            val document = requireNotNull(invoiceDao.getSalesDocument(documentId)) {
                "Order not found."
            }
            require(document.status == SalesDocumentStatus.CONFIRMED.name) {
                "Only confirmed orders can be invoiced."
            }
            val stockDocumentId = requireNotNull(document.stockDocumentId) {
                "Confirmed order is missing its stock reservation."
            }
            val stockDocument = requireNotNull(inventoryDao.getDocument(stockDocumentId)) {
                "Stock reservation not found."
            }
            require(stockDocument.status == StockDocumentStatus.TENTATIVE.name) {
                "Stock reservation is no longer tentative."
            }

            val invoiceNumber = documentNumber("INV", document.id, System.currentTimeMillis())
            val now = System.currentTimeMillis()
            inventoryDao.updateDocument(
                stockDocument.copy(
                    reference = invoiceNumber,
                    updatedAt = now,
                ),
            )

            val posting = inventoryRepository.commitDocument(stockDocumentId)
            invoiceDao.updateSalesDocument(
                document.copy(
                    invoiceNumber = invoiceNumber,
                    status = SalesDocumentStatus.INVOICED.name,
                    updatedAt = now,
                ),
            )
            auditWriter.append(
                action = "ISSUE_INVOICE",
                targetType = "SALES_DOCUMENT",
                targetId = documentId.toString(),
                payloadSummary = "order=${document.orderNumber};invoice=$invoiceNumber;stockDocument=$stockDocumentId",
                timestamp = now,
            )
            posting
        }

    override suspend fun cancelOrder(documentId: Long) {
        database.withTransaction {
            val document = requireNotNull(invoiceDao.getSalesDocument(documentId)) {
                "Order not found."
            }
            require(document.status != SalesDocumentStatus.INVOICED.name) {
                "Issued invoices must be corrected by reversal/credit note."
            }
            require(document.status != SalesDocumentStatus.CANCELLED.name) {
                "Order is already cancelled."
            }

            if (document.status == SalesDocumentStatus.CONFIRMED.name) {
                inventoryRepository.cancelDocument(
                    requireNotNull(document.stockDocumentId) {
                        "Confirmed order is missing its stock reservation."
                    },
                )
            }

            val now = System.currentTimeMillis()
            invoiceDao.updateSalesDocument(
                document.copy(
                    status = SalesDocumentStatus.CANCELLED.name,
                    updatedAt = now,
                ),
            )
            auditWriter.append(
                action = "CANCEL_SALES_ORDER",
                targetType = "SALES_DOCUMENT",
                targetId = documentId.toString(),
                payloadSummary = "order=${document.orderNumber}",
                timestamp = now,
            )
        }
    }

    private fun documentNumber(prefix: String, id: Long, timestamp: Long): String {
        val year = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .year
        return String.format(Locale.ROOT, "%s-%d-%06d", prefix, year, id)
    }

    private fun SalesDocumentProjection.toModel() = SalesDocument(
        id = id,
        orderNumber = orderNumber,
        invoiceNumber = invoiceNumber,
        status = SalesDocumentStatus.valueOf(status),
        partyId = partyId,
        partyName = partyName,
        effectiveAt = effectiveAt,
        dueAt = dueAt,
        subtotalMinor = subtotalMinor,
        discountMinor = discountMinor,
        taxMinor = taxMinor,
        totalMinor = totalMinor,
        amountPaidMinor = amountPaidMinor,
        stockDocumentId = stockDocumentId,
        note = note,
    )
}
