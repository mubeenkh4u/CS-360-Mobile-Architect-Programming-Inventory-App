package com.auwire.iamkhata.core.data

import androidx.room.withTransaction
import com.auwire.iamkhata.core.database.IamDatabase
import com.auwire.iamkhata.core.database.ProductEntity
import com.auwire.iamkhata.core.database.StockDocumentEntity
import com.auwire.iamkhata.core.database.StockDocumentLineEntity
import com.auwire.iamkhata.core.database.StockMovementEntity
import com.auwire.iamkhata.core.model.ColumnRole
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.InventoryPolicy
import com.auwire.iamkhata.core.model.InventoryRules
import com.auwire.iamkhata.core.model.Product
import com.auwire.iamkhata.core.model.StockDocument
import com.auwire.iamkhata.core.model.StockDocumentKind
import com.auwire.iamkhata.core.model.StockDocumentRequest
import com.auwire.iamkhata.core.model.StockDocumentStatus
import com.auwire.iamkhata.core.model.StockMovementKind
import com.auwire.iamkhata.core.model.StockPostingResult
import com.auwire.iamkhata.core.model.StockSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Transactional inventory + Khata implementation.
 *
 * Committed stock is an immutable movement journal. Tentative documents affect
 * availability/projections only. A document commitment writes stock movements
 * and official Khata rows inside the same Room transaction.
 */
class RoomInventoryRepository(
    private val database: IamDatabase,
    signer: IntegritySigner,
    private val policy: InventoryPolicy,
) : InventoryRepository {
    private val inventoryDao = database.inventoryDao()
    private val auditWriter = AuditWriter(database.auditDao(), signer)
    private val ledgerWriter = LedgerRowWriter(database, auditWriter)

    override fun observeStock(): Flow<List<StockSnapshot>> =
        inventoryDao.observeStock().map { rows ->
            rows.map { row ->
                StockSnapshot(
                    product = Product(
                        id = row.id,
                        sku = row.sku,
                        name = row.name,
                        unit = row.unit,
                        reorderLevelMicros = row.reorderLevelMicros,
                        defaultSaleRateMinor = row.defaultSaleRateMinor,
                        defaultPurchaseRateMinor = row.defaultPurchaseRateMinor,
                        active = row.active,
                    ),
                    onHandMicros = row.onHandMicros,
                    reservedOutgoingMicros = row.reservedOutgoingMicros,
                    tentativeIncomingMicros = row.tentativeIncomingMicros,
                )
            }
        }

    override fun observeTentativeDocuments(): Flow<List<StockDocument>> =
        inventoryDao.observeTentativeDocuments().map { documents ->
            documents.map { it.toModel() }
        }

    override suspend fun createProduct(
        sku: String,
        name: String,
        unit: String,
        reorderLevelMicros: Long,
        defaultSaleRateMinor: Long?,
        defaultPurchaseRateMinor: Long?,
    ): Long {
        val cleanSku = sku.trim().uppercase()
        val cleanName = name.trim()
        val cleanUnit = unit.trim()

        require(cleanSku.isNotEmpty()) { "SKU is required." }
        require(cleanName.isNotEmpty()) { "Product name is required." }
        require(cleanUnit.isNotEmpty()) { "Unit is required." }
        require(reorderLevelMicros >= 0) { "Reorder level cannot be negative." }
        require(defaultSaleRateMinor == null || defaultSaleRateMinor >= 0)
        require(defaultPurchaseRateMinor == null || defaultPurchaseRateMinor >= 0)

        return database.withTransaction {
            require(inventoryDao.getProductBySku(cleanSku) == null) {
                "SKU already exists."
            }
            val now = System.currentTimeMillis()
            val id = inventoryDao.insertProduct(
                ProductEntity(
                    sku = cleanSku,
                    name = cleanName,
                    unit = cleanUnit,
                    reorderLevelMicros = reorderLevelMicros,
                    defaultSaleRateMinor = defaultSaleRateMinor,
                    defaultPurchaseRateMinor = defaultPurchaseRateMinor,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            auditWriter.append(
                action = "CREATE_PRODUCT",
                targetType = "PRODUCT",
                targetId = id.toString(),
                payloadSummary = "sku=$cleanSku",
                timestamp = now,
            )
            id
        }
    }

    override suspend fun adjustBaseStock(
        productId: Long,
        quantityDeltaMicros: Long,
        note: String,
        effectiveAt: Long,
    ): Long {
        require(quantityDeltaMicros != 0L) { "Stock adjustment cannot be zero." }

        return database.withTransaction {
            val product = requireNotNull(inventoryDao.getProduct(productId)) {
                "Product not found."
            }
            require(product.active) { "Inactive products cannot be adjusted." }

            if (quantityDeltaMicros < 0 && !policy.allowNegativeCommittedStock) {
                val onHand = inventoryDao.onHandMicros(productId)
                require(onHand + quantityDeltaMicros >= 0) {
                    "Adjustment would make committed stock negative."
                }
            }

            val now = System.currentTimeMillis()
            val movementId = inventoryDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    documentId = null,
                    kind = if (quantityDeltaMicros > 0) {
                        StockMovementKind.ADJUSTMENT_IN.name
                    } else {
                        StockMovementKind.ADJUSTMENT_OUT.name
                    },
                    quantityDeltaMicros = quantityDeltaMicros,
                    effectiveAt = effectiveAt,
                    createdAt = now,
                    note = note.trim(),
                ),
            )
            auditWriter.append(
                action = "ADJUST_BASE_STOCK",
                targetType = "STOCK_MOVEMENT",
                targetId = movementId.toString(),
                payloadSummary = "product=$productId;delta=$quantityDeltaMicros",
                timestamp = now,
            )
            movementId
        }
    }

    override suspend fun saveTentativeDocument(request: StockDocumentRequest): Long {
        require(policy.tentativeStockEnabled) { "Tentative stock is disabled." }
        return database.withTransaction {
            createDocument(request, StockDocumentStatus.TENTATIVE)
        }
    }

    override suspend fun createAndCommit(
        request: StockDocumentRequest,
    ): StockPostingResult = database.withTransaction {
        val id = createDocument(request, StockDocumentStatus.TENTATIVE)
        commitInternal(id)
    }

    override suspend fun commitDocument(documentId: Long): StockPostingResult =
        database.withTransaction {
            commitInternal(documentId)
        }

    override suspend fun cancelDocument(documentId: Long) {
        database.withTransaction {
            val document = requireNotNull(inventoryDao.getDocument(documentId)) {
                "Document not found."
            }
            require(document.status == StockDocumentStatus.TENTATIVE.name) {
                "Only tentative documents can be cancelled."
            }

            val now = System.currentTimeMillis()
            inventoryDao.updateDocument(
                document.copy(
                    status = StockDocumentStatus.CANCELLED.name,
                    updatedAt = now,
                ),
            )
            auditWriter.append(
                action = "CANCEL_STOCK_DOCUMENT",
                targetType = "STOCK_DOCUMENT",
                targetId = documentId.toString(),
                payloadSummary = "kind=${document.kind}",
                timestamp = now,
            )
        }
    }

    private suspend fun createDocument(
        request: StockDocumentRequest,
        status: StockDocumentStatus,
    ): Long {
        val party = request.party.trim()
        require(party.isNotEmpty()) { "Party is required." }
        require(request.lines.isNotEmpty()) { "At least one stock line is required." }
        require(request.amountPaidMinor >= 0) { "Payment cannot be negative." }

        val validated = request.lines.map { line ->
            require(line.quantityMicros > 0) { "Line quantity must be positive." }
            require(line.unitRateMinor >= 0) { "Line rate cannot be negative." }
            val product = requireNotNull(inventoryDao.getProduct(line.productId)) {
                "Product not found."
            }
            require(product.active) { "Inactive products cannot be used." }
            Triple(
                line,
                product,
                FixedPoint.lineTotalMinor(line.quantityMicros, line.unitRateMinor),
            )
        }

        val totalMinor = validated.fold(0L) { total, item ->
            Math.addExact(total, item.third)
        }
        require(request.amountPaidMinor <= totalMinor) {
            "Payment cannot exceed the document total."
        }

        val now = System.currentTimeMillis()
        val reference = request.reference.trim().ifEmpty {
            "${request.kind.name}-${now}"
        }
        val documentId = inventoryDao.insertDocument(
            StockDocumentEntity(
                kind = request.kind.name,
                status = status.name,
                party = party,
                reference = reference,
                effectiveAt = request.effectiveAt,
                totalMinor = totalMinor,
                amountPaidMinor = request.amountPaidMinor,
                note = request.note.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )

        inventoryDao.insertDocumentLines(
            validated.map { (line, _, lineTotal) ->
                StockDocumentLineEntity(
                    documentId = documentId,
                    productId = line.productId,
                    quantityMicros = line.quantityMicros,
                    unitRateMinor = line.unitRateMinor,
                    lineTotalMinor = lineTotal,
                )
            },
        )

        auditWriter.append(
            action = "CREATE_STOCK_DOCUMENT",
            targetType = "STOCK_DOCUMENT",
            targetId = documentId.toString(),
            payloadSummary = "kind=${request.kind.name};status=${status.name};lines=${validated.size};totalMinor=$totalMinor",
            timestamp = now,
        )
        return documentId
    }

    private suspend fun commitInternal(documentId: Long): StockPostingResult {
        val document = requireNotNull(inventoryDao.getDocument(documentId)) {
            "Document not found."
        }
        require(document.status == StockDocumentStatus.TENTATIVE.name) {
            "Only tentative documents can be committed."
        }

        val kind = StockDocumentKind.valueOf(document.kind)
        val lines = inventoryDao.getDocumentLines(documentId)
        require(lines.isNotEmpty()) { "Document has no lines." }

        val products = lines
            .map { it.productId }
            .distinct()
            .associateWith { id ->
                requireNotNull(inventoryDao.getProduct(id)) { "Product not found." }
            }

        if (kind == StockDocumentKind.SALE) {
            lines.groupBy { it.productId }.forEach { (productId, productLines) ->
                val requested = productLines.fold(0L) { total, line ->
                    Math.addExact(total, line.quantityMicros)
                }
                val canCommit = InventoryRules.canCommitSale(
                    onHandMicros = inventoryDao.onHandMicros(productId),
                    reservedByOtherDocumentsMicros =
                        inventoryDao.reservedOutgoingExcluding(productId, documentId),
                    requestedMicros = requested,
                    allowNegativeCommittedStock = policy.allowNegativeCommittedStock,
                )
                require(canCommit) {
                    "Insufficient available stock for ${products.getValue(productId).name}."
                }
            }
        }

        val now = System.currentTimeMillis()
        inventoryDao.insertMovements(
            lines.map { line ->
                StockMovementEntity(
                    productId = line.productId,
                    documentId = documentId,
                    kind = if (kind == StockDocumentKind.SALE) {
                        StockMovementKind.SALE_ISSUE.name
                    } else {
                        StockMovementKind.PURCHASE_RECEIPT.name
                    },
                    quantityDeltaMicros = if (kind == StockDocumentKind.SALE) {
                        -line.quantityMicros
                    } else {
                        line.quantityMicros
                    },
                    effectiveAt = document.effectiveAt,
                    createdAt = now,
                    note = document.reference,
                )
            },
        )

        val ledgerId = ledgerWriter.ensureLedger()
        val ledgerRows = mutableListOf<Long>()
        val date = Instant.ofEpochMilli(document.effectiveAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

        lines.forEach { line ->
            val product = products.getValue(line.productId)
            val financialRole = if (kind == StockDocumentKind.SALE) {
                ColumnRole.DEBIT
            } else {
                ColumnRole.CREDIT
            }
            ledgerRows += ledgerWriter.appendByRole(
                datasetId = ledgerId,
                values = mapOf(
                    ColumnRole.DATE to date,
                    ColumnRole.PARTY to document.party,
                    ColumnRole.PARTICULARS to "${kind.name.lowercase().replaceFirstChar { it.uppercase() }} - ${product.name}",
                    ColumnRole.PRODUCT to product.name,
                    ColumnRole.SKU to product.sku,
                    ColumnRole.QUANTITY to FixedPoint.quantityDecimal(line.quantityMicros),
                    ColumnRole.RATE to FixedPoint.moneyDecimal(line.unitRateMinor),
                    financialRole to FixedPoint.moneyDecimal(line.lineTotalMinor),
                    ColumnRole.REFERENCE to document.reference,
                    ColumnRole.STOCK_STATUS to StockDocumentStatus.COMMITTED.name,
                ),
                auditAction = "POST_STOCK_TO_LEDGER",
                auditTargetId = documentId.toString(),
                timestamp = now,
            )
        }

        if (document.amountPaidMinor > 0) {
            val paymentRole = if (kind == StockDocumentKind.SALE) {
                ColumnRole.CREDIT
            } else {
                ColumnRole.DEBIT
            }
            val description = if (kind == StockDocumentKind.SALE) {
                "Payment received"
            } else {
                "Payment paid"
            }
            ledgerRows += ledgerWriter.appendByRole(
                datasetId = ledgerId,
                values = mapOf(
                    ColumnRole.DATE to date,
                    ColumnRole.PARTY to document.party,
                    ColumnRole.PARTICULARS to description,
                    paymentRole to FixedPoint.moneyDecimal(document.amountPaidMinor),
                    ColumnRole.REFERENCE to document.reference,
                    ColumnRole.STOCK_STATUS to StockDocumentStatus.COMMITTED.name,
                ),
                auditAction = "POST_PAYMENT_TO_LEDGER",
                auditTargetId = documentId.toString(),
                timestamp = now,
            )
        }

        inventoryDao.updateDocument(
            document.copy(
                status = StockDocumentStatus.COMMITTED.name,
                updatedAt = now,
            ),
        )
        auditWriter.append(
            action = "COMMIT_STOCK_DOCUMENT",
            targetType = "STOCK_DOCUMENT",
            targetId = documentId.toString(),
            payloadSummary = "kind=${document.kind};movements=${lines.size};ledgerRows=${ledgerRows.size}",
            timestamp = now,
        )

        return StockPostingResult(documentId, ledgerRows)
    }

    private fun StockDocumentEntity.toModel() = StockDocument(
        id = id,
        kind = StockDocumentKind.valueOf(kind),
        status = StockDocumentStatus.valueOf(status),
        party = party,
        reference = reference,
        effectiveAt = effectiveAt,
        totalMinor = totalMinor,
        amountPaidMinor = amountPaidMinor,
        note = note,
    )
}
